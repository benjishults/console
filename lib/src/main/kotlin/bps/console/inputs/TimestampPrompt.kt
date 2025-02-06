package bps.console.inputs

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

/**
 * This will be caught and mined for its message by the [SimplePrompt.getResult] function.
 * It isn't user for anything but its message, at present, so isn't visible externally.
 */
private class UserNotEnteringDate(
    message: String = "User doesn't want to enter a date",
    cause: Throwable? = null,
) : Exception(message, cause)

class TimestampPrompt(
    queryAcceptDefault: String,
    default: LocalDateTime,
    dateOnly: Boolean = false,
    inputReader: InputReader = DefaultInputReader,
    outPrinter: OutPrinter = DefaultOutPrinter,
) : SimplePromptWithDefault<LocalDateTime>(
    basicPrompt = queryAcceptDefault,
    defaultValue =
        if (dateOnly)
            default
                .apply { LocalDateTime(year, monthNumber, dayOfMonth, 0, 0, 0) }
        else
            default,
    inputReader = inputReader,
    outPrinter = outPrinter,
    transformer = { acceptDefault: String ->
        when (acceptDefault) {
            "Y", "y", "" -> {
                default
            }
            else -> {
                val year: Int =
                    SimplePromptWithDefault(
                        String.format("%13s [%4d]: ", "year", default.year),
                        default.year,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No year entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                val month: Int =
                    SimplePromptWithDefault(
                        String.format("%15s [%2d]: ", "month (1-12)", default.month.value),
                        default.month.value,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No month entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                val day: Int =
                    SimplePromptWithDefault(
                        String.format("%15s [%2d]: ", "day of month", default.dayOfMonth),
                        default.dayOfMonth,
                        inputReader,
                        outPrinter,
                        additionalValidation = AcceptNothingStringValidator("No day of month entered."),
                    ) { it.toInt() }
                        .getResult()
                        ?: throw UserNotEnteringDate()
                val hour: Int =
                    if (dateOnly)
                        0
                    else
                        SimplePromptWithDefault(
                            String.format("%15s [%2d]: ", "hour (24-clock)", default.hour),
                            default.hour,
                            inputReader,
                            outPrinter,
                            additionalValidation = AcceptNothingStringValidator("No hour entered."),
                        ) { it.toInt() }
                            .getResult()
                            ?: throw UserNotEnteringDate()
                val minute: Int =
                    if (dateOnly)
                        0
                    else
                        SimplePromptWithDefault(
                            String.format("%15s [%2d]: ", "minute of hour", default.minute),
                            default.minute,
                            inputReader,
                            outPrinter,
                            additionalValidation = AcceptNothingStringValidator("No minute entered."),
                        ) { it.toInt() }
                            .getResult()
                            ?: throw UserNotEnteringDate()
                val second: Int =
                    if (dateOnly)
                        0
                    else
                        SimplePromptWithDefault(
                            String.format("%15s [%2d]: ", "second", default.second),
                            default.second,
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
    timeZone: TimeZone,
    clock: Clock,
    queryForNow: String = "Use current time [Y]? ",
    dateOnly: Boolean = false,
): LocalDateTime? =
    TimestampPrompt(
        queryForNow,
        default =
            clock.now()
                .toLocalDateTime(timeZone),
        inputReader = inputReader,
        outPrinter = outPrinter,
        dateOnly = dateOnly,
    )
        .getResult()

/**
 * @return `null` if the user doesn't enter a proper date.
 */
fun WithIo.getTimestampFromUser(
    queryAcceptDefault: String = "Use current time [Y]? ",
    default: LocalDateTime, // = clock.now().toLocalDateTime(timeZone),
    dateOnly: Boolean = false,
): LocalDateTime? =
    TimestampPrompt(
        queryAcceptDefault = queryAcceptDefault,
        dateOnly = dateOnly,
        inputReader = inputReader,
        outPrinter = outPrinter,
        default = default,
    )
        .getResult()
