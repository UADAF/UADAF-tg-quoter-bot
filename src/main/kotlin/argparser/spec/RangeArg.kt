package argparser.spec

class RangeArgResult(val from: Int?, val to: Int?) : ArgResult() {
    override val type: String = "range"

    operator fun component1() = from
    operator fun component2() = to

    val isEmpty
        get() = from == null && to == null

    val isNotEmpty
        get() = !isEmpty
}

class RangeArgSpec(name: String) : ArgSpec<RangeArgResult>(name) {

    override val type: String = "range"

    override fun parse(arg: String): RangeArgResult? {
        val match = "(-?\\d+)?:(-?\\d+)?".toRegex().matchEntire(arg)
        return if(match != null) {
            try {
                val a = match.groups[1]?.value?.toInt()
                val b = match.groups[2]?.value?.toInt()
                RangeArgResult(a, b)
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid number", e)
            }
        } else {
            null
        }
    }

    override fun default() = RangeArgResult(null, null)

    override fun equals(other: Any?): Boolean {
        if(this === other) {
            return true
        }
        return super.equals(other) && other is RangeArgSpec
    }

    override fun hashCode() = super.hashCode() * 7

}