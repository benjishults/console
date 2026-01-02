package bps.console.menu

import kotlin.math.min

/**
 * @param T the type of the selected item
 */
abstract class BaseScrollingSelectionMenu<T>(
    override val header: () -> String?,
    override val prompt: () -> String = { "Enter selection: " },
    val limit: Int = 30,
    val offset: Int = 0,
    protected val itemListGenerator: (Int, Int) -> List<T>,
    protected open val extraItems: List<MenuItem> = emptyList(),
    protected open val labelGenerator: T.() -> String = { toString() },
) : Menu {

    override val shortcutMap: MutableMap<String, MenuItem> = mutableMapOf()

    constructor(
        header: () -> String?,
        prompt: () -> String = { "Enter selection: " },
        limit: Int = 30,
        offset: Int = 0,
        baseList: List<T>,
        extraItems: List<MenuItem> = emptyList(),
        labelGenerator: T.() -> String = { toString() },
    ) : this(
        header = header,
        prompt = prompt,
        limit = limit,
        offset = offset,
        itemListGenerator = { lim, off -> baseList.subList(off, min(baseList.size, off + lim)) },
        extraItems = extraItems,
        labelGenerator = labelGenerator,
    )

    init {
        require(limit > 0) { "limit must be > 0" }
        require(offset >= 0) { "offset must be >= 0" }
    }

    protected open fun MutableList<MenuItem>.incorporateItem(menuItem: MenuItem) {
        add(menuItem)
        if (menuItem.shortcut !== null)
            shortcutMap[menuItem.shortcut!!] = menuItem
    }

    abstract fun generateBaseMenuItemList(): MutableList<MenuItem>

    override var itemsGenerator: () -> List<MenuItem> = {
        generateBaseMenuItemList()
            .also { menuItems: MutableList<MenuItem> ->
                addNextAndPreviousItems(menuItems)
                addExtraItems(menuItems)
                addBackAndQuitItems(menuItems)
            }
    }

    private fun addExtraItems(menuItems: MutableList<MenuItem>) {
        extraItems.forEach { menuItems.incorporateItem(it) }
    }

    private fun addBackAndQuitItems(menuItems: MutableList<MenuItem>) {
        menuItems.incorporateItem(backItem)
        menuItems.incorporateItem(quitItem)
    }

    protected fun addNextAndPreviousItems(menuItems: MutableList<MenuItem>) {
        if (menuItems.size == limit) {
            menuItems.incorporateItem(
                item({ "Next Items" }, "n") { menuSession ->
                    menuSession.pop()
                    menuSession.push(nextPageMenuProducer())
                },
            )
        }
        if (offset > 0) {
            menuItems.incorporateItem(
                item({ "Previous Items" }, "p") { menuSession ->
                    menuSession.pop()
                    menuSession.push(previousPageMenuProducer())
                },
            )
        }
    }

    abstract fun previousPageMenuProducer(): BaseScrollingSelectionMenu<T>

    abstract fun nextPageMenuProducer(): BaseScrollingSelectionMenu<T>

}
