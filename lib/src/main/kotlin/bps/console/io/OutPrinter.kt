package bps.console.io

fun interface OutPrinter : (String) -> Unit {
    fun important(message: String) {
        if (message.isNotBlank())
            invoke("\n$message\n\n")
    }

    fun verticalSpace() {
        invoke("\n")
    }
}

val DefaultOutPrinter: OutPrinter = OutPrinter {
    print(it)
}
