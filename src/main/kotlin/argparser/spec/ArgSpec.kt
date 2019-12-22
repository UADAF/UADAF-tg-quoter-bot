package argparser.spec

abstract class ArgResult {

    abstract val type: String

}

abstract class ArgSpec<out T : ArgResult>(val name: String) {

    abstract val type: String

    abstract fun parse(arg: String): T?

    abstract fun default(): T

    override fun equals(other: Any?): Boolean {
        return other is ArgSpec<*> && other.type == type && other.name == name
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}
