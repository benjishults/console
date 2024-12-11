package bps.console.app

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.io.WithIo
import bps.console.menu.Menu

open class MenuApplicationWithQuit(
    topLevelMenu: Menu,
    override val inputReader: InputReader = DefaultInputReader,
    override val outPrinter: OutPrinter = DefaultOutPrinter,
) : MenuApplication, AutoCloseable, WithIo {

    private val menuSession: MenuSession = MenuSession(topLevelMenu)

    open fun quitAction(quitException: QuitException) {
        outPrinter.important(quitException.message!!)
    }

    /**
     * Calls [quitAction] when quit.
     */
    override fun runApplication() =
        try {
            while (true) {
                try {
                    with(menuSession.current()) {
                        runMenu(menuSession)
                    }
                } catch (cancelException: CancelException) {
                    outPrinter.important(cancelException.message!!)
                    cancelException.handler(menuSession)
                }
            }
        } catch (quit: QuitException) {
            quitAction(quit)
        }


    override fun close() {
        menuSession.close()
    }

}
