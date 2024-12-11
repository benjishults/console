package bps.console.app

open class CancelException(
    message: String = "Canceling work in progress",
    val handler: CancelHandler,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Throw this if you want to try again at the current menu in the application.
 */
class TryAgainAtMostRecentMenuException(
    message: String = "Canceling work in progress",
    cause: Throwable? = null,
) : CancelException(message, TryAgainAtMostRecentMenu, cause)

/**
 * Throw this if you want to pop the most recent menu in the application.
 */
class PopMostRecentMenuCancelException(
    message: String = "Canceling work in progress",
    cause: Throwable? = null,
) : CancelException(message, PopMostRecentMenu, cause)

/**
 * Throw this if you want to pop some number of menus in the application.
 */
class PopMenusCancelException(
    menusToPop: Int,
    message: String = "Canceling work in progress",
    cause: Throwable? = null,
) : CancelException(message, PopMenus(menusToPop), cause) {
    init {
        require(menusToPop >= 0) { "menusToPop must not be negative" }
    }
}

interface CancelHandler : (MenuSession) -> Unit

data object TryAgainAtMostRecentMenu : CancelHandler {
    override fun invoke(menuSession: MenuSession) {
        // the default behavior is desired.  NO-OP.
    }
}

data object PopMostRecentMenu : CancelHandler {
    override fun invoke(menuSession: MenuSession) {
        menuSession.pop()
    }
}

data class PopMenus(val menusToPop: Int) : CancelHandler {
    init {
        require(menusToPop >= 0) { "menusToPop must not be negative" }
    }

    override fun invoke(menuSession: MenuSession) {
        repeat(menusToPop) {
            menuSession.pop()
        }
    }
}
