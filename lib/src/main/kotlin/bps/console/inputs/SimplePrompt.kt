package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.io.WithIo

open class SimplePrompt<T : Any>(
    // TODO specify that this shouldn't contain ending spaces or punctuation and make it so
    protected val basicPrompt: String,
    /**
     * returns `true` if the input is acceptable.  Defaults to [NonBlankStringValidator].
     */
    override val inputReader: InputReader = DefaultInputReader,
    override val outPrinter: OutPrinter = DefaultOutPrinter,
    protected val validator: StringValidator = NonBlankStringValidator,
    /**
     * transforms valid input into an instance of [T].  Default value simply casts to [T].
     */
    @Suppress("UNCHECKED_CAST")
    protected val transformer: (String) -> T = { it as T },
) : Prompt<T>, WithIo {


    /**
     * This is called if [validator] fails or [transformer] throws an exception.
     *
     * The default implementation:
     * 1. prints the [message] as important,
     * 2. asks if the user wants to try again
     * 3. returns `null` if they do not want to try again.
     */
    protected open fun actionOnInvalid(input: String, message: String): T? {
        outPrinter.important(message)
        return if (userDoesntSayNo("Try again?"))
            this.getResult()
        else
            null
    }

    /**
     * @return the result of applying [transformer] to the user's input if the input passes [validator].  Otherwise,
     * the result of calling [actionOnInvalid] passing the user's input and the [validator]'s [StringValidator.errorMessage].
     * If [transformer] throws an exception, the [actionOnInvalid] is called with that exception's message.
     */
    final override fun getResult(): T? {
        outPrinter(basicPrompt)
        return inputReader()
            .let { input: String ->
                if (validator(input))
                    try {
                        transformer(input)
                    } catch (e: Exception) {
                        actionOnInvalid(input, e.message ?: "Error transforming input")
                    }
                else {
                    actionOnInvalid(input, validator.errorMessage)
                }
            }
    }

}

fun WithIo.userDoesntSayNo(promptInitial: String = "Try again?") = SimplePrompt<Boolean>(
    basicPrompt = "$promptInitial [Y/n]: ",
    inputReader = inputReader,
    outPrinter = outPrinter,
    validator = AcceptAnythingStringValidator,
    transformer = { it !in listOf("n", "N") },
)
    .getResult()!!

fun WithIo.userSaysYes(promptInitial: String = "Try again?") = SimplePrompt<Boolean>(
    basicPrompt = "$promptInitial [y/N]: ",
    inputReader = inputReader,
    outPrinter = outPrinter,
    validator = AcceptAnythingStringValidator,
    transformer = { it in listOf("y", "Y") },
)
    .getResult()!!
