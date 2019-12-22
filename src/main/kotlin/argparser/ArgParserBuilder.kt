package argparser

import argparser.spec.*

@DslMarker
annotation class ArgParserDsl


@ArgParserDsl
class ArgParserBuilder {

    val parser = ArgParser()

    private fun r(spec: ArgSpec<*>) = parser.register(spec)

    fun plain(name: String)
            = r(PlainArgSpec(name))

    fun flag(name: String, flagname: String? = null, shortname: Char? = null)
            = r(FlagArgSpec(name, flagname, shortname))

    fun range(name: String)
            = r(RangeArgSpec(name))

    fun value(name: String, argname: String? = null)
            = r(ValueArgSpec(name, argname))

}

fun argparser(init: ArgParserBuilder.() -> Unit): ArgParser {
    val b = ArgParserBuilder()
    b.init()
    return b.parser
}

fun plain(name: String) = PlainArgSpec(name)

fun flag(name: String, flagname: String? = null, shortname: Char? = null) = FlagArgSpec(name, flagname, shortname)

fun range(name: String) = RangeArgSpec(name)

fun value(name: String, argname: String? = null) = ValueArgSpec(name, argname)