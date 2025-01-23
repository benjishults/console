@file:Suppress("unused")

package bps.console

import bps.console.app.MenuApplication
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.Spec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * This fixture allows the application and the test validations to interact with each other in a thread-safe way.
 * The application is run in its own thread but it will pause between tests to allow the test code to validate
 * the outputs.
 *
 * Usage:
 *
 * ```kotlin
 *                 stopApplicationAfterSpec()
 *                 startApplication(app)
 *                 validateInteraction(
 *                     expectedOutputs = listOf("Enter the amount of income: "),
 *                     toInput = listOf("5000"),
 *                 )
 *                 validateInteraction(
 *                     expectedOutputs = listOf(
 *                         "Enter description of income [income into $defaultCheckingAccountName]: ",
 *                         "Use current time [Y]? ",
 *                         """
 *                         |Select account receiving the income:
 *                         | 1.   5,000.00 | Checking
 *                         | 2.       0.00 | Wallet
 *                         | 3. Back (b)
 *                         | 4. Quit (q)
 *                         |""".trimMargin(),
 *                         "Enter selection: ",
 *                     ),
 *                     toInput = listOf("", "", "2"),
 *                 )
 *                 validateFinalOutput(listOf("Quitting"))
 * ```
 *
 * The running application will pause automatically when [inputs] is empty just prior to printing its next output
 *
 * After re-populating the [inputs] list and prior to validating the results, resume the application by calling
 * [waitForApplicationProcessing] so that the application will run through your inputs and produce its output.
 *
 */
interface ComplexConsoleIoTestFixture : SimpleConsoleIoTestFixture {

    /**
     * Allows the application to run only until it consumes all the given input at which point the application
     * is paused.
     *
     * Validates
     * 1. that the inputs are all consumed
     * 2. that the outputs are as expected
     * 3. that the application is paused but not terminated (via a call to [validateApplicationPaused]).
     *
     * Should not be called from the application thread.
     * @throws IllegalArgumentException if [toInput] is empty
     */
    fun validateInteraction(expectedOutputs: List<String>, toInput: List<String>) {
        require(toInput.isNotEmpty())
        outputs.clear()
        inputs.shouldBeEmpty()
        inputs.addAll(toInput)
        waitForApplicationProcessing()
        inputs.shouldBeEmpty()
        outputs shouldContainExactly expectedOutputs
        validateApplicationPaused()
    }

    /**
     * Allows the application to run until it quits.
     *
     * Validates
     * 1. that the application consumes the given input
     * 2. that the application expects no more input than what is provided
     * 3. that the expected output is produced
     * 4. that the application thread terminates after consuming the input (via a call to [validateApplicationTerminated]).
     *
     * Should not be called from the application thread.
     * @param toInput may be empty.  Should contain any input that is necessary to cause the application to quit.
     */
    fun validateFinalOutput(
        expectedOutputs: List<String>,
        toInput: List<String> = emptyList(),
    ) {
        outputs.clear()
        val dummyMessage = "SHOULD NOT BE CONSUMED"
        inputs.addAll(toInput)
        inputs.add(dummyMessage)
        waitForApplicationProcessing()
        withClue("The application expected more input than was provided prior to quitting.") {
            inputs shouldContainExactly listOf(dummyMessage)
        }
        outputs shouldContainExactly expectedOutputs
        validateApplicationTerminated()
    }

    /**
     * Validates that the application thread is terminated.
     */
    fun validateApplicationTerminated()

    /**
     * Validates that the application thread is paused but not terminated.
     */
    fun validateApplicationPaused()

    /**
     * Should not be called from the application thread.  Call [waitForApplicationProcessing] after setting [inputs]
     * and before validation of [outputs] in order to
     * allow the application to finish processing.  The application will
     * pause automatically when it has processed all provided [inputs] and emptied that list.
     */
    fun waitForApplicationProcessing()

    /**
     * Starts the application thread in a way that will cooperate with the test.
     */
    fun startApplicationForTesting(application: MenuApplication): Thread

    /**
     * Interrupt the application thread in case it failed to stop on its own or via a call to [validateFinalOutput].
     */
    fun stopApplication()

    /**
     * Arranges for [stopApplication] to be called after all tests in the [Spec] are finished.
     */
    fun Spec.stopApplicationAfterSpec() {
        afterSpec {
            stopApplication()
        }
    }

    companion object {

        class ApplicationExitException(msg: String) : Exception(msg)

        enum class AppStatus {
            /**
             * Indicates that the test is starting up and either thread might try to update data first.
             */
            START_UP,

            /**
             * Indicates that the application thread owns the permit on [takeTurns]
             */
            RUNNING,

            /**
             * Indicates that the application is waiting for input from the test.
             */
            PAUSED,

            /**
             * Indicates that the application thread has exited.
             */
            APP_DONE,
        }

        /**
         * Produces a working implementation of [ComplexConsoleIoTestFixture] that manages the application thread
         * via a [Semaphore] and special [OutPrinter] and [InputReader].
         */
        operator fun invoke(awaitMillis: Long): ComplexConsoleIoTestFixture =
            object : ComplexConsoleIoTestFixture {

                /**
                 * Should only be set by the application thread while it has a permit on [takeTurns]
                 *
                 * INVARIANTS:
                 * 1. This will only change values when the application thread owns the permit on [takeTurns].
                 * 2. If the value is [AppStatus.RUNNING], then the application thread definitely owns a permit.
                 * 3. If the application is paused, then this will be [AppStatus.PAUSED].
                 * 4. If the application is terminated, then this will be [AppStatus.APP_DONE].
                 * 5. If the application has not yet tried to read input or produce output, then this will be [AppStatus.START_UP].
                 */
                @Volatile
                private var appStatus: AppStatus = AppStatus.START_UP

                /**
                 * INVARIANTS:
                 * 1. If this is `true`, then the test has prepared some input and the application has not acknowledged it.
                 * 2. If this is `false`, then the TEST is free to take action.  The test should wait until this is `false` before taking back control unless [appStatus] is [AppStatus.APP_DONE]
                 * 3. This value will only be changed by a thread that has a permit on [takeTurns]
                 *
                 * The application will set this to `false` when it wakes up from waiting for a permit on [takeTurns].
                 *
                 * The application will only change this value when [appStatus] is [AppStatus.RUNNING].
                 *
                 * The test will only change this value when [appStatus] is not [AppStatus.RUNNING].
                 */
                @Volatile
                private var applicationHasUnackedTurnover: Boolean = false

                /**
                 * Ensure the application and test aren't messing each other up.
                 *
                 * Should never have more than one permit.
                 *
                 * When [appStatus] is [AppStatus.START_UP], the application cannot output until the test is ready.  That
                 * readiness is indicated by the test creating a permit with a call to [Semaphore.release].
                 */
                // NOTE the test is expected to release (or create) a permit when it has inputs ready for the application
                // NOTE application should be careful not to call release unless it has a permit otherwise another permit will be created
                private val takeTurns = object : Semaphore(0, true) {

//                    fun applicationTryAquire(timeout: Long, unit: TimeUnit?): Boolean {
//                        // NOTE this is a not-completely-effective way to try to catch cases when there are plural permits
//                        return tryAcquire(timeout, unit)
//                    }

                    override fun tryAcquire(timeout: Long, unit: TimeUnit?): Boolean {
                        // NOTE this is a not-completely-effective way to try to catch cases when there are plural permits
                        if (availablePermits() > 1)
                            throw IllegalStateException("takeTurns must never have more than one permit")
                        return super.tryAcquire(timeout, unit)
                    }

                    override fun release() {
                        // NOTE this is a not-completely-effective way to try to catch cases when there are plural permits
                        if (availablePermits() > 0)
                            throw IllegalStateException("takeTurns must never have more than one permit")
                        super.release()
                    }
                }

                lateinit var applicationThread: Thread

                // NOTE the thread clearing this is not the thread that adds to it
                override val inputs = CopyOnWriteArrayList<String>()

                // NOTE the thread clearing this is not the thread that adds to it
                override val outputs = CopyOnWriteArrayList<String>()

                override val inputReader = InputReader {
                    when (appStatus) {
                        AppStatus.RUNNING -> {
                            if (inputs.isNotEmpty())
                                inputs.removeFirst()
                            else {
                                // application expected input that wasn't there
                                applicationHasUnackedTurnover = false
                                appStatus = AppStatus.APP_DONE
                                takeTurns.release()
                                throw ApplicationExitException("application was expecting more input than what was provided")
                            }
                        }
                        AppStatus.START_UP, AppStatus.PAUSED -> {
                            if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                                appStatus = AppStatus.APP_DONE
                                throw ApplicationExitException("Unable to acquire permit for application thread within $awaitMillis milliseconds")
                            } else {
                                // NOTE at this point, the application thread has a permit and owns the data.
                                appStatus = AppStatus.RUNNING
                                inputs.removeFirst()
                            }
                        }
                        AppStatus.APP_DONE -> throw ApplicationExitException("application was caught asking for input after reporting it had terminated")
                    }
                }

                // NOTE the application can only output when it owns a permit on [takeTurns]
                // NOTE when the inputs is empty, the application will release the permit, set the owner as TEST and wait for another permit
                // NOTE only the application calls this
                // TODO how about allow output when inputs is empty?
                //       1. the application would start editing outputs right away (may not be a problem as long as test doesn't check those until application is paused.)
                //       2. test would have to expect outputs in a different way
                override val outPrinter = OutPrinter {
                    when (appStatus) {
                        AppStatus.RUNNING -> {}
                        AppStatus.START_UP, AppStatus.PAUSED, AppStatus.APP_DONE -> {
                            if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                                appStatus = AppStatus.APP_DONE
                                throw ApplicationExitException("Unable to acquire permit for application thread within $awaitMillis milliseconds")
                            } else {
                                // NOTE at this point, the application thread has a permit and owns the data.
                                appStatus = AppStatus.RUNNING
                            }
                        }
                    }
                    // NOTE at this point, appStatus is RUNNING and the application thread has a permit.
                    // NOTE pause if there are no inputs
                    while (inputs.isEmpty()) {
                        // NOTE can there be a race condition here?  I think not because the app thread owns a permit
                        applicationHasUnackedTurnover = false
                        do {
                            appStatus = AppStatus.PAUSED
                            takeTurns.release()
                            // NOTE we want to allow this to be interrupted by the test thread
                            if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                                appStatus = AppStatus.APP_DONE
                                throw ApplicationExitException("Unable to acquire permit for application thread within $awaitMillis milliseconds")
                            } else {
                                appStatus = AppStatus.RUNNING
                            }
                        } while (!applicationHasUnackedTurnover)
                    }
                    // NOTE at this point, the application thread has a permit and owns the data.
                    outputs.add(it)
                }

                /**
                 * Call [waitForApplicationProcessing] before validation to allow the application to finish processing.  The application will
                 * pause automatically when the [inputs] list is emptied.
                 */
                // NOTE only the test thread calls this
                override fun waitForApplicationProcessing() {
                    when (appStatus) {
                        AppStatus.PAUSED, AppStatus.START_UP -> {
                            applicationHasUnackedTurnover = true
//                            appStatus = AppStatus.RUNNING
                            takeTurns.release()
                        }
                        AppStatus.APP_DONE -> fail("application exited unexpectedly")
                        AppStatus.RUNNING -> {
                            stopApplication()
                            fail("test and application running simultaneously")
                        }
                    }
                    if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                        stopApplication()
                        fail("Unable to acquire permit from application thread within $awaitMillis milliseconds")
                    } else {
                        // NOTE this thread has a permit
                        while (appStatus !== AppStatus.APP_DONE && applicationHasUnackedTurnover) {
                            // NOTE must be that the application hasn't picked up the permit, yet.  We need to keep giving it chances.
                            println("Test waiting for application thread to take a permit")
                            takeTurns.release()
                            if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                                stopApplication()
                                fail("Unable to acquire permit from application thread within $awaitMillis milliseconds")
                            }
                        }
                    }
                }

                override fun validateApplicationTerminated() {
                    applicationThread.join(awaitMillis)
                    withClue("application thread should have terminated within $awaitMillis milliseconds") {
                        applicationThread.state shouldBe Thread.State.TERMINATED
                    }
                    appStatus shouldBe AppStatus.APP_DONE
                }

                override fun validateApplicationPaused() {
                    appStatus shouldNotBe AppStatus.APP_DONE
                    applicationThread.state shouldNotBe Thread.State.TERMINATED
                }

                /**
                 * This **must** be called prior to validation.
                 */
                override fun startApplicationForTesting(application: MenuApplication): Thread {
                    applicationThread = thread(
                        start = true,
                        name = "Application Thread",
                    ) {
                        object : MenuApplication by application {
                            override fun close() {
                                application.close()
                                // NOTE immediately give control back to test
                                if (appStatus == AppStatus.RUNNING || applicationHasUnackedTurnover) {
                                    appStatus = AppStatus.APP_DONE
                                    applicationHasUnackedTurnover = false
                                    takeTurns.release()
                                } else {
                                    appStatus = AppStatus.APP_DONE
                                }
                            }
                        }
                            .use {
                                it.runApplication()
                            }
                    }
                    return applicationThread
                }

                // NOTE only test code calls this
                override fun stopApplication() {
                    applicationThread.interrupt()
                    applicationThread.join(awaitMillis)
                    withClue("application thread should have terminated within $awaitMillis milliseconds") {
                        applicationThread.state shouldBe Thread.State.TERMINATED
                    }
                    println("Application stopped")
                }

            }
    }

}
