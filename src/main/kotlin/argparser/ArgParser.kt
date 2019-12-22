package argparser

import argparser.spec.*
import kotlin.reflect.KProperty

class ArgParser {

    private val specs: MutableMap<String, ArgSpec<*>> = mutableMapOf()

    var results: MutableMap<String, ArgResult> = mutableMapOf()
        private set

    var leftover = mutableListOf<String>()

    private fun parse(arg: String, visitedSpecs: Set<String>): Pair<String, ArgResult>? {
        //specs.entries to allow non-local return
        specs.entries.forEach { (name, spec) ->
            if(spec.name !in visitedSpecs) {
                val res = spec.parse(arg)
                if (res != null) {
                    return name to res
                }
            }
        }
        return null
    }

    private fun parseMultiflag(arg: String) {
        arg.substring(1).forEach { parseNormal("-$it") }
    }

    private fun parseNormal(arg: String) {
        val r = parse(arg, results.keys)
        if(r != null) {
            val (name, res) = r
            results[name] = res
        } else {
            leftover.add(arg)
        }
    }

    fun parse(args: List<String>): Map<String, ArgResult> {
        //We will not even try to do something, if parser has no specs
        if (specs.isEmpty()) {
            leftover.addAll(args)
            return results
        }
        args.forEach { arg ->
            if(arg.matches("-[a-zA-Z0-9]+".toRegex())) {
                parseMultiflag(arg)
            } else {
                parseNormal(arg)
            }
        }
        specs
            .filter { (name, _) -> name !in results }
            .forEach{ (name, spec) -> results[name] = spec.default()}
        return results
    }

    inline fun with(args: List<String>, body: (Map<String, ArgResult>) -> Unit) {
        val res = parse(args)
        try {
            body(res)
        } finally {
            reset()
        }
    }

    fun reset() {
        results = mutableMapOf()
        leftover = mutableListOf()
    }

    fun register(spec: ArgSpec<*>) {
        specs[spec.name] = spec
    }

    inline fun <reified T : ArgResult> delegate(name: String) = ArgDelegate { results[name] as T }

    inline fun <reified T : ArgResult> delegate(spec: ArgSpec<T>): ArgDelegate<T> {
        register(spec)
        return delegate(spec.name)
    }

    fun plain(name: String) = delegate(PlainArgSpec(name))

    fun flag(name: String, flagname: String? = null, shortname: Char? = null) = delegate(FlagArgSpec(name, flagname, shortname))

    fun range(name: String) = delegate(RangeArgSpec(name))

    fun value(name: String, argname: String? = null) = delegate(ValueArgSpec(name, argname))

    fun leftoverDelegate() = ArgDelegate { leftover.toList() }

    override fun equals(other: Any?): Boolean {
        if(this === other) {
            return true
        }
        if(other !is ArgParser) {
            return false
        }
        if(specs.size != other.specs.size) {
            return false
        }
        return specs.values.all { it in other.specs.values }
    }

    override fun hashCode(): Int {
        return specs.hashCode() * 3
    }


}

class ArgDelegate<T>(val getter: () -> T) {

    operator fun getValue(thisRef: Any?, kProp: KProperty<*>) = getter()

}

