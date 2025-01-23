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
 * the outputs (and allowing the JDBC connection to remain open between tests).  Using the [validateInteraction]
 * function will work for most use cases.
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

// TODO add a function to validate outputs without expected input

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

        enum class Owner {
            /**
             * Indicates that the test is starting up and either thread might try to update data first.
             */
            START_UP,

            /**
             * Indicates that the application thread owns the permit on [takeTurns]
             */
            APP,

            /**
             * Indicates that the application is waiting for input from the test.
             */
            TEST,

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
                 * 1. This will only change values when the application thread owns the permit on [takeTurns]
                 * 2. If the value is APP, then the application thread definitely owns a permit
                 * 3. This is set to TEST when the application is waiting for input
                 */
                @Volatile
                private var owner: Owner = Owner.START_UP

                /**
                 * Ensure the application and test aren't messing each other up.
                 *
                 * Should never have more than one permit.
                 *
                 * When [owner] is [Owner.START_UP], the application cannot output until the test is ready.  That
                 * readiness is indicated by the test creating a permit with a call to [Semaphore.release].
                 */
                // NOTE the test is expected to release (or create) a permit when it has inputs ready for the application
                // NOTE application should be careful not to call release unless it has a permit otherwise another permit will be created
                private val takeTurns = object : Semaphore(0, true) {
                    override fun acquire() {
                        // NOTE this is a not-completely-effective way to try to catch cases when there are plural permits
                        if (availablePermits() > 1)
                            throw IllegalStateException("takeTurns must never have more than one permit")
                        super.acquire()
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
                    if (owner === Owner.APP) {
                        if (inputs.isNotEmpty())
                            inputs.removeFirst()
                        else {
                            // application expected input that wasn't there
                            owner = Owner.APP_DONE
                            takeTurns.release()
                            throw IllegalStateException("application was expecting more input than what was provided")
                        }
                    } else if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                        owner = Owner.APP_DONE
                        throw IllegalStateException("Unable to acquire permit for application thread")
                    } else {
                        // NOTE at this point, the application thread has a permit and owns the data.
                        owner = Owner.APP
                        inputs.removeFirst()
                    }
                }

                // NOTE the application can only output when it owns a permit on [takeTurns]
                // NOTE when the inputs is empty, the application will release the permit, set the owner as TEST and wait for another permit
                // NOTE only the application calls this
                override val outPrinter = OutPrinter {
                    // FIXME there is probably a slight race condition here.  On startup, the app might get to this code
                    //       at the same moment the test is turning over control.  Can we say that only the app thread
                    //       can change the value of owner?
                    if (owner !== Owner.APP) {
                        if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                            owner = Owner.APP_DONE
                            throw IllegalStateException("Unable to acquire permit for application thread")
                        } else {
                            // NOTE at this point, the application thread has a permit and owns the data.
                            owner = Owner.APP
                        }
                    }
                    // NOTE at this point, the application thread has a permit and owns the data.
                    while (inputs.isEmpty()) {
                        owner = Owner.TEST
                        takeTurns.release()
                        // NOTE the semaphore is fair so the test thread should pick it up.
                        // NOTE we want to allow this to be interrupted by the test thread
                        if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                            owner = Owner.APP_DONE
                            throw IllegalStateException("Unable to acquire permit for application thread")
                        } else {
                            owner = Owner.APP
                        }
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
                    when (owner) {
                        Owner.TEST, Owner.START_UP -> {
                            owner = Owner.APP
                            takeTurns.release()
                        }
                        Owner.APP_DONE -> fail("application exited unexpectedly")
                        Owner.APP -> fail("test and application running simultaneously")
                    }
                    while (owner !== Owner.APP_DONE && outputs.isEmpty()) {
                        println("Test waiting for application thread")
                        if (!takeTurns.tryAcquire(awaitMillis, TimeUnit.MILLISECONDS)) {
                            stopApplication()
                            fail("Unable to acquire permit from application thread")
                        }
                    }
                }

                override fun validateApplicationTerminated() {
                    applicationThread.join(awaitMillis)
                    applicationThread.state shouldBe Thread.State.TERMINATED
                    owner shouldBe Owner.APP_DONE
                }

                override fun validateApplicationPaused() {
                    owner shouldNotBe Owner.APP_DONE
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
                                val priorOwner = owner
                                owner = Owner.APP_DONE
                                if (priorOwner === Owner.APP) {
                                    takeTurns.release()
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
                    applicationThread.join()
                    println("Application stopped")
                }

            }
    }

}
