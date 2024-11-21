package bps.console.inputs

import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.io.*
import kotlinx.datetime.*

/**
 * @throws IllegalStateException if the user opts out of entering a date
 */
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
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("year")
                val month: Int =
                    SimplePromptWithDefault(
                        "   month (1-12) [${now.month.value}]: ",
                        now.month.value,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("month")
                val day: Int =
                    SimplePromptWithDefault(
                        "   day of month [${now.dayOfMonth}]: ",
                        now.dayOfMonth,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("day of month")
                val hour: Int =
                    SimplePromptWithDefault(
                        "hour (24-clock) [${now.hour}]: ",
                        now.hour,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("hour")
                val minute: Int =
                    SimplePromptWithDefault(
                        " minute of hour [${now.minute}]: ",
                        now.minute,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("minute")
                val second: Int =
                    SimplePromptWithDefault(
                        "         second [${now.second}]: ",
                        now.second,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("second")
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
)

/**
 * Throws whatever exception is thrown by the [errorConverter] if the user gives up.
 * By default, this is a [TryAgainAtMostRecentMenuException].
 */
fun WithIo.getTimestampFromUser(
    queryForNow: String = "Use current time [Y]? ",
    timeZone: TimeZone,
    clock: Clock,
    errorConverter: (IllegalStateException) -> Nothing = {
        throw TryAgainAtMostRecentMenuException(
            "No ${it.message} entered.",
            it,
        )
    },
): Instant =
    try {
        TimestampPrompt(
            queryForNow,
            timeZone,
            clock,
            inputReader,
            outPrinter,
        )
            .getResult()!!
            .toInstant(timeZone)
    } catch (ex: IllegalStateException) {
        errorConverter(ex)
    }


//fun LocalDateTime.toInstantForTimeZone(timeZone: TimeZone): Instant =
//    ZonedDateTime
//        .of(this, timeZone.toZoneId())
//        .toInstant()
