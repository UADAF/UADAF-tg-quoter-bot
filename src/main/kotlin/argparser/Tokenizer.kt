package argparser

fun tokenize(s: String): List<String> {
    val ret = mutableListOf<String>()
    var nextArg = StringBuilder()
    var singleQuote = false
    var doubleQuote = false
    s.forEach {
        when (it) {
            ' ' -> {
                if (!singleQuote && !doubleQuote) {
                    ret.add(nextArg.toString())
                    nextArg = StringBuilder()
                } else {
                    nextArg.append(it)
                }
            }
            '\'' -> {
                if (doubleQuote) {
                    nextArg.append(it)
                } else {
                    singleQuote = !singleQuote
                }
            }
            '"' -> {
                if (singleQuote) {
                    nextArg.append(it)
                } else {
                    doubleQuote = !doubleQuote
                }
            }
            else -> nextArg.append(it)
        }
    }
    require(!(singleQuote || doubleQuote)) { "Invalid quotes" }
    if(nextArg.isNotEmpty()) {
        ret.add(nextArg.toString())
    }
    return ret
}