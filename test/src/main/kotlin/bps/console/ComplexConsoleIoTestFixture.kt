package bps.console

import bps.console.app.MenuApplication
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.core.spec.Spec
import io.kotest.matchers.collections.shouldContainExactly
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.use

/**
 * This fixture allows the application to pause between tests (allowing the JDBC connection to remain
 * open between tests).  Using the [validateInteraction] function will work for most use cases.
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
 * ```
 * ## Expert Mode
 *
 * The application will pause automatically
 * * when [inputs] is empty
 * * just prior to printing the next output
 *
 * After re-populating the [inputs] list, you can resume the application by calling [resumeApplication].
 *
 * After calling [resumeApplication], the application will resume and run through the new inputs.
 * You will want to immediately call [waitForApplicationPause] so that the application will run through your inputs
 * prior to validating the results.
 *
 * If you want to capture the Quitting output and ensure that the application thread ends before you go on to the
 * next test, then you might ought to be using the [SimpleConsoleIoTestFixture] instead.  However, if you need to
 * use this in that way for some reason, here's how:
 *
 * ```kotlin
 *             val applicationThread = thread(name = "Application Thread 1") {
 *                 application
 *                     .use {
 *                         it.run()
 *                     }
 *             }
 *             inputs.addAll(listOf("4", "4", "4", "1", "5", "5", "4", "5", "5", "4", "7"))
 *             resume()
 *             waitForPause()
 *             // resume so that the process can end by printing the Quit message
 *             resume()
 *             // make sure this thread dies so as not to interfere with later tests
 *             applicationThread.join(Duration.ofMillis(20)).shouldBeTrue()
 *             outputs shouldContainExactly listOf( /* ...*/ )
 * ```
 */
interface ComplexConsoleIoTestFixture : SimpleConsoleIoTestFixture {

    companion object {

        enum class Owner {
            APP,
            TEST,
            APP_DONE,
        }

        operator fun invoke(debugging: Boolean = false): ComplexConsoleIoTestFixture =
            object : ComplexConsoleIoTestFixture {

                val awaitMillis =
                    if (debugging)
                        MAX_LONG_MILLIS_FOR_WAITING_FOR_PAUSE_DURING_DEBUGGING
                    else
                        ONE_SECOND_IN_MILLIS_FOR_WAITING_FOR_PAUSE

                lateinit var applicationThread: Thread

                // NOTE the thread clearing this is not the thread that adds to it
                override val inputs = CopyOnWriteArrayList<String>()
                override val inputReader = InputReader {
                    inputs.removeFirst()
                }

                // NOTE the thread clearing this is not the thread that adds to it
                override val outputs = CopyOnWriteArrayList<String>()

                // NOTE when the inputs is empty, the application will pause itself
                // NOTE only the application calls this
                override val outPrinter = OutPrinter {
                    if (inputs.isEmpty()) {
                        pauseApplication()
                    }
                    outputs.add(it)
                }

                private var owner: AtomicReference<Owner> = AtomicReference(Owner.TEST)

                /**
                 * When the application is starting up or waiting before printing output, this will be a CountDownLatch of 1
                 */
                // NOTE only allow write access to threads that have the lock to update owner
                private val waitForResume: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(1))

                /**
                 * When the test is starting up or waiting for the application to pause, this will be a CountDownLatch of 1
                 */
                // NOTE only allow write access to threads that have the lock to update owner
                private val waitForPause: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(1))

                override fun startApplication(application: MenuApplication): Thread {
                    applicationThread = thread(name = "Test Application Thread") {
                        application.use {
                            object : MenuApplication {
                                override fun runApplication() {
                                    it.runApplication()
                                }

                                override fun close() {
                                    it.close()
                                    // NOTE immediately give control back to test
                                    owner.set(Owner.APP_DONE)
                                    pauseApplication()
                                }
                            }
                                .runApplication()
                        }
                    }
                    return applicationThread
                }

                override fun stopApplication() {
                    applicationThread.interrupt()
                    applicationThread.join()
                    println("Application stopped")
                }

                /** Call [waitForApplicationPause] before validation to allow the application to finish processing.  The application will
                 * pause automatically when the [inputs] list is emptied.
                 */
                override fun waitForApplicationPause() {
                    while (owner.get() === Owner.TEST || owner.get() === Owner.APP_DONE) {
                        owner.updateAndGet { currentValue ->
                            when (currentValue) {
                                Owner.TEST -> {
                                    waitForPause.set(CountDownLatch(1))
                                    Owner.APP
                                }
                                Owner.APP_DONE -> throw IllegalStateException("application exited unexpectedly")
                                Owner.APP -> currentValue
                            }
                        }
                    }
                    // NOTE race condition here shouldn't make a difference (I worked it out on paper)
                    waitForPause.get().await(awaitMillis, TimeUnit.MILLISECONDS)
                }

                fun pauseApplication(): Boolean {
                    var exit = false
                    @Suppress("KotlinConstantConditions") // I don't think it's right about this
                    while ((owner.get() === Owner.APP || owner.get() === Owner.APP_DONE) && !exit) {
                        owner.updateAndGet { currentValue ->
                            when (currentValue) {
                                Owner.APP, Owner.APP_DONE -> {
                                    exit == true
                                    waitForResume.set(CountDownLatch(1))
                                    waitForPause.get().countDown()
                                    if (currentValue == Owner.APP)
                                        Owner.TEST
                                    else
                                        Owner.APP_DONE
                                }
                                else -> Owner.TEST
                            }
                        }
                    }
                    // NOTE for the race condition here, worst that can happen is that the test might resume the application
                    //      before it gets here... no problem
                    return if (owner.get() !== Owner.APP_DONE)
                        waitForResume.get().await(awaitMillis, TimeUnit.MILLISECONDS)
                    else
                        true
                }

                override fun resumeApplication() {
                    owner.updateAndGet { currentValue ->
                        when (currentValue) {
                            Owner.TEST -> waitForResume.get().countDown()
                            Owner.APP_DONE -> throw IllegalStateException("application exited unexpectedly")
                            Owner.APP -> {}
                        }
                        Owner.APP
                    }
                }
            }
    }

    /**
     * Should not be called from the application thread.
     * @throws IllegalArgumentException if [toInput] is empty
     */
    fun validateInteraction(expectedOutputs: List<String>, toInput: List<String>) {
        require(inputs.isNotEmpty())
        outputs.clear()
        inputs.addAll(toInput)
        resumeApplication()
        waitForApplicationPause()
        outputs shouldContainExactly expectedOutputs
    }

    // TODO add a function to validate outputs without expected input

    /** Should not be called from the application thread.  Call [waitForApplicationPause] before validation to
     * allow the application to finish processing.  The application will
     * pause automatically when the [inputs] list is emptied.
     */
    fun waitForApplicationPause()

    /**
     *  Should not be called from the application thread.
     */
    fun resumeApplication()

    fun startApplication(application: MenuApplication): Thread

    fun stopApplication()

    fun Spec.stopApplicationAfterSpec() {
        afterSpec {
            stopApplication()
        }
    }

}
