package bps.console.io

// NOTE will probably replace this with context parameters once Kotlin has those.
data class WithIo(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
)
