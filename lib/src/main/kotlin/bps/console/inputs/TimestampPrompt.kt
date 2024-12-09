package bps.console.inputs

import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.io.WithIo
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.lang.Exception

class UserNotEnteringDate(
    message: String = "User doesn't want to enter a date",
    cause: Throwable? = null,
) : Exception(message, cause)

class TimestampPrompt(
    queryForNow: String,
    timeZone: TimeZone,
    clock: Clock = Clock.System,
    inputReader: InputReader = DefaultInputReader,
    outPrinter: OutPrinter = DefaultOutPrinter,
    now: LocalDateTime = clock.now().toLocalDateTime(timeZone),
) : SimplePromptWithDefault<LocalDateTime>(
    basicPrompt = queryForNow,
    defaultValue = now,
    inputReader = inputReader,
    outPrinter = outPrinter,
    transformer = { acceptDefault: String ->
        when (acceptDefault) {
            "Y", "y", "" -> {
                now
            }
            else -> {
                val year: Int =
                    SimplePromptWithDefault(
                        "         year [${now.year}]: ",
                        now.year,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No year entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                val month: Int =
                    SimplePromptWithDefault(
                        String.format("   month (1-12) [%2d]: ", now.month.value),
                        now.month.value,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No month entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                val day: Int =
                    SimplePromptWithDefault(
                        String.format("   day of month [%2d]: ", now.dayOfMonth),
                        now.dayOfMonth,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No day of month entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                val hour: Int =
                    SimplePromptWithDefault(
                        String.format("hour (24-clock) [%2d]: ", now.hour),
                        now.hour,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No hour entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                val minute: Int =
                    SimplePromptWithDefault(
                        String.format(" minute of hour [%2d]: ", now.minute),
                        now.minute,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No minute entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                val second: Int =
                    SimplePromptWithDefault(
                        String.format("         second [%2d]: ", now.second),
                        now.second,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No second entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                LocalDateTime.parse(
                    String.format(
                        "%04d-%02d-%02dT%02d:%02d:%02d",
                        year,
                        month,
                        day,
                        hour,
                        minute,
                        second,
                    ),
                )
            }
        }
    },
) {

    /**
     * This will be called when:
     * 1. entry to 'Use current time \[Y]? ' prompt is blank OR
     * 2. entry is negative then user decides not to enter a date (i.e., [transformer] throws [UserNotEnteringDate])
     * This implementation returns:
     * 1. [defaultValue] if the input was blank,
     * 2. otherwise, `null`
     */
    override fun actionOnInvalid(input: String, message: String): LocalDateTime? =
        if (input.isBlank())
            defaultValue
        else
            null

}

/**
 * @return `null` if the user doesn't enter a proper date.
 */
fun WithIo.getTimestampFromUser(
    queryForNow: String = "Use current time [Y]? ",
    timeZone: TimeZone,
    clock: Clock,
): Instant? =
    TimestampPrompt(
        queryForNow,
        timeZone,
        clock,
        inputReader,
        outPrinter,
    )
        .getResult()
        ?.toInstant(timeZone)


//fun LocalDateTime.toInstantForTimeZone(timeZone: TimeZone): Instant =
//    ZonedDateTime
//        .of(this, timeZone.toZoneId())
//        .toInstant()
