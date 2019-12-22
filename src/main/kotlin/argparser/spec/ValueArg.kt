package argparser.spec

class ValueArgResult(val name: String?, val value: String?) : ArgResult() {
    override val type: String = "value"
}

class ValueArgSpec(name: String, val argname: String? = null) : ArgSpec<ValueArgResult>(name) {

    override val type: String = "value"

    override fun parse(arg: String): ValueArgResult? {
        val n = argname ?: name
        return if(arg.startsWith("--$n") || arg.startsWith("—$n")) {
            val value = arg
                .removePrefix("—")
                .removePrefix("--")
                .removePrefix(n)
                .removePrefix("=")
            ValueArgResult(n, if(value.isNotEmpty()) value else null)
        } else {
            null
        }
    }

    override fun default() = ValueArgResult(null, null)

    override fun equals(other: Any?): Boolean {
        if(this === other) {
            return true
        }
        return super.equals(other) && other is ValueArgSpec && other.argname == argname
    }

    override fun hashCode(): Int {
        var result = super.hashCode() * 11
        result = 31 * result + (argname?.hashCode() ?: 0)
        return result
    }
}