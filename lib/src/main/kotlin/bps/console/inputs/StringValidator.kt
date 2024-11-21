package bps.console.inputs

import org.apache.commons.validator.routines.EmailValidator
import java.math.BigDecimal

interface StringValidator : (String) -> Boolean {
    val errorMessage: String
}

data object NonBlankStringValidator : StringValidator {
    override fun invoke(entry: String): Boolean =
        entry.isNotBlank()

    override val errorMessage: String = "Entry must not be blank."

}

data object AcceptAnythingStringValidator : StringValidator {
    override fun invoke(entry: String): Boolean = true

    override val errorMessage: String = ""
}

data object EmailStringValidator : StringValidator {
    override fun invoke(entry: String): Boolean = EmailValidator.getInstance().isValid(entry)

    override val errorMessage: String = "Must enter a valid email address."
}

data object PositiveStringValidator : StringValidator {
    override fun invoke(input: String): Boolean =
        input
            .toCurrencyAmountOrNull()
            ?.let {
                it > BigDecimal.ZERO
            }
            ?: false

    override val errorMessage: String = "Amount must be positive"
}

data object NonNegativeStringValidator : StringValidator {
    override fun invoke(input: String): Boolean =
        input
            .toCurrencyAmountOrNull()
            ?.let {
                it >= BigDecimal.ZERO.setScale(2)
            }
            ?: false

    override val errorMessage: String = "Amount must be non-negative"
}

data class NotInListStringValidator(val list: List<String>, val label: String) : StringValidator {
    override fun invoke(input: String): Boolean =
        input !in list

    override val errorMessage: String = "Input must not be $label."
}

data class InRangeInclusiveStringValidator(
    val min: BigDecimal,
    val max: BigDecimal,
) : StringValidator {
    override fun invoke(input: String): Boolean =
        input
            .toCurrencyAmountOrNull()
            ?.let {
                it in min..max
            }
            ?: false

    override val errorMessage: String = "Amount must be between $min and $max"
}
