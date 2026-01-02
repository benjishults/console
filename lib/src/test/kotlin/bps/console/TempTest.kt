package bps.console

import bps.console.app.MenuApplicationWithQuit
import bps.console.menu.Menu
import bps.console.menu.ScrollingMultiSelectionMenu
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlin.math.pow
import kotlin.math.roundToLong

class TempTest : FreeSpec() {

    private fun format(double: Double, scale: Int): String {
        val longString = (double * 10.0.pow(scale)).roundToLong().toString()
        return buildString {
            append(longString.substring(0, longString.length - scale))
            append(".")
            append(longString.substring(longString.length - scale))
        }
    }


    init {
        "test" {
            format(12345.12345, 2) shouldBe "12345.12"
            format(12345.12345, 3) shouldBe "12345.123"
            format(9876.9876, 2) shouldBe "9876.99"
        }
    }
}

fun main() {
    MenuApplicationWithQuit(
        topLevelMenu = Menu(
            items = {
                add(
                    pushMenu(
                        label = { "print selected" },
                        to = {
                            ScrollingMultiSelectionMenu(
                                baseList = listOf("a", "b", "c"),
                            ) { session, items ->
                                session.pop()
                                println(items)
                            }
                        },
                    ),
                )
                add(
                    pushMenu(
                        label = { "print reverse selected" },
                        to = {
                            ScrollingMultiSelectionMenu(
                                baseList = listOf("a", "b", "c"),
                            ) { session, items ->
                                session.pop()
                                println(items.reversed())
                            }
                        },
                    ),
                )
                add(quitItem("blarg!"))
            },
        ),
    )
        .runApplication()
}
