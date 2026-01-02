package bps.console.menu

import bps.console.app.MenuSession
import kotlin.math.max
import kotlin.math.min

/**
 * @param T the type of the selected item
 */
open class ScrollingSelectionMenu<T>(
    header: () -> String?,
    prompt: () -> String = { "Enter selection: " },
    limit: Int = 30,
    offset: Int = 0,
    itemListGenerator: (Int, Int) -> List<T>,
    extraItems: List<MenuItem> = emptyList(),
    labelGenerator: T.() -> String = { toString() },
    protected val actOnSelectedItem: (MenuSession, T) -> Unit,
) : BaseScrollingSelectionMenu<T>(header, prompt, limit, offset, itemListGenerator, extraItems, labelGenerator) {

    override val shortcutMap: MutableMap<String, MenuItem> = mutableMapOf()

    constructor(
        header: () -> String?,
        prompt: () -> String = { "Enter selection: " },
        limit: Int = 30,
        offset: Int = 0,
        baseList: List<T>,
        extraItems: List<MenuItem> = emptyList(),
        labelGenerator: T.() -> String = { toString() },
        actOnSelectedItem: (MenuSession, T) -> Unit,
    ) : this(
        header = header,
        prompt = prompt,
        limit = limit,
        offset = offset,
        itemListGenerator = { lim, off -> baseList.subList(off, min(baseList.size, off + lim)) },
        extraItems = extraItems,
        labelGenerator = labelGenerator,
        actOnSelectedItem = actOnSelectedItem,
    )

    init {
        require(limit > 0) { "limit must be > 0" }
        require(offset >= 0) { "offset must be >= 0" }
    }

    override fun previousPageMenuProducer(): ScrollingSelectionMenu<T> =
        ScrollingSelectionMenu(
            header,
            prompt,
            limit,
            max(offset - limit, 0),
            itemListGenerator,
            extraItems,
            labelGenerator,
            actOnSelectedItem,
        )

    override fun nextPageMenuProducer(): ScrollingSelectionMenu<T> =
        ScrollingSelectionMenu(
            header,
            prompt,
            limit,
            offset + limit,
            itemListGenerator,
            extraItems,
            labelGenerator,
            actOnSelectedItem,
        )

    override fun generateBaseMenuItemList() =
        itemListGenerator(limit, offset)
            .mapTo(mutableListOf()) { item ->
                item({ item.labelGenerator() }) { menuSession: MenuSession ->
                    actOnSelectedItem(menuSession, item)
                }
            }

}
