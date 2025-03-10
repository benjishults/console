package bps.console

import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.core.spec.Spec

/**
 * Usage:
 *
 * 1. Call [clearInputsAndOutputsBeforeEach] to make sure the [inputs] and [outputs] are cleared between tests
 * 2. Plug the [inputReader] and [outPrinter] into your menus and application
 * 3. Populate the [inputs] list with the [String]s you want to use as inputs
 * 4. At the end of each test, validate that the [outputs] contains the outputs you expected
 *
 * Example:
 *
 * ```kotlin
 * class ScrollingSelectionMenuTest : FreeSpec(),
 *     SimpleConsoleIoTestFixture {
 *
 *     override val inputs: MutableList<String> = mutableListOf()
 *     override val outputs: MutableList<String> = mutableListOf()
 *
 *     init {
 *         clearInputsAndOutputsBeforeEach()
 *         "test ..." {
 *
 *             // set up application or menus omitted
 *
 *             inputs.addAll(listOf("2", "4", "2", "7"))
 *             MenuApplicationWithQuit(subject, inputReader, outPrinter)
 *                 .use {
 *                     it.run()
 *                 }
 *             outputs shouldContainExactly listOf(/* ... */)
 *         }
 *     }
 * }
 * ```
 */
interface SimpleConsoleIoTestFixture {

    val ignoreBlanks: Boolean
        get() = false

    val inputs: MutableList<String>
    val inputReader
        get() = InputReader {
            inputs.removeFirst()
        }

    val outputs: MutableList<String>
    val outPrinter: OutPrinter
        get() = OutPrinter { output: String ->
            if (!ignoreBlanks || output.isNotBlank())
                outputs.add(output)
        }

    fun Spec.clearInputsAndOutputsBeforeEach() {
        beforeEach {
            inputs.clear()
            outputs.clear()
        }
    }

    /**
     * Use this implementation for slightly better performance.
     */
    companion object : SimpleConsoleIoTestFixture {
        override val ignoreBlanks: Boolean = false
        override val inputs: MutableList<String> = mutableListOf()
        override val outputs: MutableList<String> = mutableListOf()
        override val outPrinter =
            OutPrinter { output: String ->
                if (!ignoreBlanks || output.isNotBlank())
                    outputs.add(output)
            }
        override val inputReader: InputReader =
            InputReader {
                inputs.removeFirst()
            }
    }

}
