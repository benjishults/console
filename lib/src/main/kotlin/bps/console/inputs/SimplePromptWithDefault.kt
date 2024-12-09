package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

/**
 * This [Prompt] will try calling [transformer] on any non-blank input.
 * @param additionalValidation if input is not blank and [transformer] throws an exception, then this will
 * be called.  If it returns `false`, then it's [StringValidator.errorMessage] will be printed as important.
 * If it returns `true` then we will call [transformer] again and its exception will propagate.
 * Defaults to [AcceptAnythingStringValidator].
 * @param transformer Called on entry after it has gone through [validator] or [additionalValidation] if that fails.
 * The default implementation simply casts to [T].
 * @param T the type of the result.
 */
open class SimplePromptWithDefault<T : Any>(
    basicPrompt: String,
    val defaultValue: T,
    inputReader: InputReader = DefaultInputReader,
    outPrinter: OutPrinter = DefaultOutPrinter,
    /**
     * if input is not blank and [transformer] throws an exception, then this will
     * be called on the input.  If it returns `false`, then it's [StringValidator.errorMessage] will be printed as
     * important.
     * If it returns `true` then we will call [transformer] again and its exception will propagate.
     * Defaults to [AcceptAnythingStringValidator].
     */
    val additionalValidation: StringValidator = AcceptAnythingStringValidator,
    /**
     * Called on entry after it has gone through [validator] or [additionalValidation] if that fails.
     *
     * The default implementation simply casts to [T]
     */
    @Suppress("UNCHECKED_CAST")
    transformer: (String) -> T = { it as T },
) : SimplePrompt<T>(
    basicPrompt = basicPrompt,
    validator = NonBlankStringValidator,
    transformer = transformer,
    inputReader = inputReader,
    outPrinter = outPrinter,
) {

    /**
     * This implementation:
     * 1. returns [defaultValue] if the input was blank,
     * 2. returns the result of [transformer] if [additionalValidation] passes,
     * 3. otherwise, prints [additionalValidation]'s [StringValidator.errorMessage] as important and calls [SimplePrompt.actionOnInvalid]
     *    which gives the user a chance to try again or returns `null`.
     */
    override fun actionOnInvalid(input: String, message: String): T? =
        if (input.isBlank())
            defaultValue
        else if (additionalValidation(input))
            transformer(input)
        else {
            outPrinter.important(additionalValidation.errorMessage)
            super.actionOnInvalid(input, "")
        }

}
