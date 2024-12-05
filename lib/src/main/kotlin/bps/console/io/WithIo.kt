package bps.console.io

/**
 * NOTE: this will likely be replaced by context parameters in a future version (once that feature exists :smile:).
 */
interface WithIo {
    val inputReader: InputReader get() = DefaultInputReader
    val outPrinter: OutPrinter get() = DefaultOutPrinter
}
