package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.io.WithIo

interface SimplePrompt<T : Any> : Prompt<T>, WithIo {
    // TODO specify that this shouldn't contain ending spaces or punctuation and make it so
    val basicPrompt: String
    override val inputReader: InputReader
    override val outPrinter: OutPrinter

    /**
     * returns `true` if the input is acceptable
     */
    val validator: StringValidator

    /**
     * transforms valid input into an instance of [T].
     */
    val transformer: (String) -> T

    /**
     * The default implementation:
     * 1. prints the [message] as important,
     * 2. asks if the user wants to try again
     * 3. returns `null` if they do not want to try again.
     */
    fun actionOnInvalid(input: String, message: String): T? {
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
    override fun getResult(): T? {
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

    @Suppress("UNCHECKED_CAST")
    companion object {
        operator fun <T : Any> invoke(
            basicPrompt: String,
            inputReader: InputReader = DefaultInputReader,
            outPrinter: OutPrinter = DefaultOutPrinter,
            validator: StringValidator = NonBlankStringValidator,
            transformer: (String) -> T = {
                it as T
            },
        ) =
            object : SimplePrompt<T> {
                override val basicPrompt: String = basicPrompt
                override val inputReader: InputReader = inputReader
                override val outPrinter: OutPrinter = outPrinter
                override val transformer: (String) -> T = transformer
                override val validator: StringValidator = validator
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
