package bps.console.inputs

import bps.console.SimpleConsoleIoTestFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime

class TimestampPromptTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val outputs: MutableList<String> = mutableListOf()
    override val inputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()
        var now: LocalDateTime = LocalDateTime.parse("2024-08-09T00:00:00")
        var subject = TimestampPrompt(
            queryAcceptDefault = "Use current time [Y]? ",
            default = now,
            inputReader = inputReader,
            outPrinter = outPrinter,
        )
        "run prompt accepting default in America/Chicago" {
            inputs.add("")
            subject.getResult() shouldBe now
            outputs shouldContainExactly listOf("Use current time [Y]? ")
        }
        "run prompt with inputs America/Chicago" {
            inputs.addAll(listOf("n", "2024", "8", "9", "0", "0", "0"))
            subject.getResult() shouldBe now
            outputs shouldContainExactly listOf(
                "Use current time [Y]? ",
                "         year [2024]: ",
                "   month (1-12) [ 8]: ",
                "   day of month [ 9]: ",
                "hour (24-clock) [ 0]: ",
                " minute of hour [ 0]: ",
                "         second [ 0]: ",
            )
        }
        subject = TimestampPrompt(
            "Use current time [Y]? ",
            default = now,
            inputReader = inputReader,
            outPrinter = outPrinter,
        )
        "run prompt accepting default in America/Los_Angeles" {
            inputs.add("")
            subject.getResult() shouldBe now
            outputs shouldContainExactly listOf("Use current time [Y]? ")
        }
        "run prompt with inputs in America/Los_Angeles" {
            inputs.addAll(listOf("n", "2024", "8", "9", "0", "0", "0"))
            subject.getResult() shouldBe now
            outputs shouldContainExactly listOf(
                "Use current time [Y]? ",
                "         year [2024]: ",
                "   month (1-12) [ 8]: ",
                "   day of month [ 9]: ",
                "hour (24-clock) [ 0]: ",
                " minute of hour [ 0]: ",
                "         second [ 0]: ",
            )
        }
        "run prompt non-numeric entry" {
            inputs.addAll(listOf("n", "2024", "8", "b", "y", "9", "b", "n"))
            subject.getResult().shouldBeNull()
            outputs shouldContainExactly listOf(
                "Use current time [Y]? ",
                "         year [2024]: ",
                "   month (1-12) [ 8]: ",
                "   day of month [ 9]: ",
                """
                    |
                    |No day of month entered.
                    |
                    |
                """.trimMargin(),
                "Try again? [Y/n]: ",
                "   day of month [ 9]: ",
                "hour (24-clock) [ 0]: ",
                """
                    |
                    |No hour entered.
                    |
                    |
                """.trimMargin(),
                "Try again? [Y/n]: ",
            )
        }
    }
}
