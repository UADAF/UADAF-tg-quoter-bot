package argparser.spec

class FlagArgResult(val present: Boolean) : ArgResult() {
    override val type: String = "flag"
}

class FlagArgSpec(name: String, val flagname: String? = null, val shortname: Char? = null) : ArgSpec<FlagArgResult>(name) {

    override val type: String = "flag"

    override fun parse(arg: String): FlagArgResult? {
        val n = flagname ?: name
        return if(arg == "--$n" || (shortname != null && arg == "-$shortname")) {
            FlagArgResult(true)
        } else {
            null
        }
    }

    override fun default() = FlagArgResult(false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return super.equals(other) && other is FlagArgSpec && other.flagname == flagname && other.shortname == shortname
    }

    override fun hashCode(): Int {
        var result = super.hashCode() * 3
        result = 31 * result + (flagname?.hashCode() ?: 0)
        result = 31 * result + (shortname?.hashCode() ?: 0)
        return result
    }

}