package bps.console.menu

import bps.console.app.MenuSession
import kotlin.math.max
import kotlin.math.min

/**
 * @param T the type of the selected item
 */
open class ScrollingMultiSelectionMenu<T> private constructor(
    limit: Int = 30,
    offset: Int = 0,
    itemListGenerator: (Int, Int) -> List<T>,
    extraItems: List<MenuItem> = emptyList(),
    protected val actionLabel: () -> String = { "Take Action" },
    protected val actOnSelectedItems: (MenuSession, List<T>) -> Unit,
) : BaseScrollingSelectionMenu<T>(
    // NOTE need to pass this in but I'm overriding it below... see if this works
    header = { null },
    limit = limit,
    offset = offset,
    itemListGenerator = itemListGenerator,
) {

    private val selectedItems: MutableList<T> = mutableListOf()
    override val header: () -> String? = { String.format("Selected Items: (%d)", selectedItems.size) }
    override val labelGenerator: T.() -> String = {
        "[${
            if (this in selectedItems)
                "x"
            else
                " "
        }] $this"
    }
    override val shortcutMap: MutableMap<String, MenuItem> = mutableMapOf()
    override val extraItems: List<MenuItem> =
        listOf(
            // FIXME add "deselect all", "select all", and "take action"
            item(
                label = actionLabel,
                action = { actOnSelectedItems(it, selectedItems) },
            ),
            item(
                label = { "Select All" },
                shortcut = "s",
                action = { selectedItems.addAll(itemListGenerator(Int.MAX_VALUE, 0)) },
            ),
            item(
                label = { "Deselect All" },
                shortcut = "d",
                action = { selectedItems.clear() },
            ),
        ) +
                extraItems

    constructor(
        limit: Int = 30,
        offset: Int = 0,
        baseList: List<T>,
        extraItems: List<MenuItem> = emptyList(),
        actOnSelectedItems: (MenuSession, List<T>) -> Unit,
    ) : this(
        limit = limit,
        offset = offset,
        itemListGenerator = { lim, off -> baseList.subList(off, min(baseList.size, off + lim)) },
        extraItems = extraItems,
        actOnSelectedItems = actOnSelectedItems,
    )

    init {
        require(limit > 0) { "limit must be > 0" }
        require(offset >= 0) { "offset must be >= 0" }
    }

    override fun previousPageMenuProducer(): ScrollingMultiSelectionMenu<T> =
        ScrollingMultiSelectionMenu(
            limit = limit,
            offset = max(offset - limit, 0),
            extraItems = extraItems,
            itemListGenerator = itemListGenerator,
            actOnSelectedItems = actOnSelectedItems,
        )

    override fun nextPageMenuProducer(): ScrollingMultiSelectionMenu<T> =
        ScrollingMultiSelectionMenu(
            limit = limit,
            offset = offset + limit,
            itemListGenerator = itemListGenerator,
            extraItems = extraItems,
            actOnSelectedItems = actOnSelectedItems,
        )

    override fun generateBaseMenuItemList() =
        itemListGenerator(limit, offset)
            .mapTo(mutableListOf()) { item: T ->
                item({ item.labelGenerator() }) {
                    if (item in selectedItems) {
                        selectedItems.remove(item)
                    } else {
                        selectedItems.add(item)
                    }
                }
            }

}
