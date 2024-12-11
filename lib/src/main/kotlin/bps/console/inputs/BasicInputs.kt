package bps.console.inputs

import bps.console.io.WithIo
import java.math.BigDecimal

fun String.toCurrencyAmountOrNull(): BigDecimal? =
    try {
        BigDecimal(this).setScale(2)
    } catch (e: NumberFormatException) {
        null
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
