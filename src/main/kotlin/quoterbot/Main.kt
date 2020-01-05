package quoterbot

import argparser.ArgParser
import argparser.spec.ArgResult
import argparser.tokenize
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.apache.Apache
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import quoter.Quoter
import rocks.waffle.telekt.bot.Bot
import rocks.waffle.telekt.contrib.filters.CommandFilter
import rocks.waffle.telekt.dispatcher.Dispatcher
import rocks.waffle.telekt.dispatcher.HandlerScope
import rocks.waffle.telekt.types.Message
import rocks.waffle.telekt.types.replymarkup.KeyboardButton
import rocks.waffle.telekt.util.ReplyKeyboardMarkup
import rocks.waffle.telekt.util.answerOn
import rocks.waffle.telekt.util.handlerregistration.HandlerDSL
import rocks.waffle.telekt.util.handlerregistration.dispatch
import rocks.waffle.telekt.util.handlerregistration.handle
import rocks.waffle.telekt.util.handlerregistration.messages
import rocks.waffle.telekt.util.replyTo

val json = Json(JsonConfiguration.Stable);

var configFilename: String = ""

lateinit var quoter: Quoter<HttpClientEngineConfig>
    private set

lateinit var bot: Bot
    private set

lateinit var dp: Dispatcher
    private set

suspend fun main(args: Array<String>) {
    //Redirect slf4j-simple to System.out from System.err
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.out")
    configFilename = if (args.size > 1) args[1] else "config.json"
    init()
    dp.dispatch {
        messages {
            handle(CommandFilter("start")) { msg ->
                val markup =  ReplyKeyboardMarkup() {
                    add(KeyboardButton("/quote"))
                }
                quoterbot.bot.replyTo(msg,"Can you hear me?", replyMarkup=markup)
            }
            initCommands()
        }
    }
    dp.poll()
    close()
}

private fun init() {
    quoter = Quoter("http://52.48.142.75:6741/api/v2/quote/", Apache)
    bot = Bot(Config.tgToken)
    dp = Dispatcher(bot)
}

typealias CommandBuilder = HandlerDSL<Message>

suspend inline fun ArgParser.with(msg: Message, body: (Map<String, ArgResult>) -> Unit) {
    if (msg.text == null) {
        bot.answerOn(msg, "Ожидалось текстовое сообщение...")
        return
    }
    val args = tokenize(msg.text!!).drop(1)
    with(args, body)
}

fun CommandBuilder.command(name: String, parser: ArgParser? = null, block: suspend HandlerScope.(Message) -> Unit) =
    handle(CommandFilter(name)) { msg ->
        if (parser == null) {
            block(msg)
        } else {
            parser.with(msg) {
                block(msg)
            }
        }
    }

fun CommandBuilder.initCommands() {
    bindCommand()
    quoteCommand()
    helpCommand()
}

fun CommandBuilder.bindCommand() {
    val parser = ArgParser()
    val repoL by parser.leftoverDelegate()
    command("bind", parser) { msg ->
        if (repoL.isEmpty()) {
            bot.answerOn(msg, "Укажите репозиторий к которому привязывать.")
            return@command
        }
        if (msg.from?.username !in Config.admins) {
            bot.answerOn(msg, "Только админ может привязать репозиторий.")
            return@command
        }
        val repo = repoL.joinToString(" ")
        val data = ChatDataStorage.getData(msg.chat)
        val oldRepo = data.boundRepo
        data.boundRepo = repo
        if (ChatDataStorage.save()) {
            bot.answerOn(msg, "Канал привязан к репозиторию '${repo}'.")
        } else {
            bot.answerOn(msg, "Что-то пошло не так. Пните админа.")
            data.boundRepo = oldRepo
        }
    }
}

fun CommandBuilder.quoteCommand() {
    val parser = ArgParser()
    val quoteRange by parser.range("quoteRange")
    val all by parser.flag("all", shortname = 'a')
    val repo by parser.value("repo")
    val arguments by parser.leftoverDelegate()
    command("quote", parser) { msg ->
        val (count, leftover) = extractCount(arguments)
        if (count != 1 && (leftover.isNotEmpty() || quoteRange.isNotEmpty || all.present)) {
            bot.answerOn(msg, "Количество нельзя указывать вместе с другими аргументами")
            return@command
        }
        if (all.present && quoteRange.isNotEmpty) {
            bot.answerOn(msg, "Нельзя одновременно все и какие-то")
        }
        val data = ChatDataStorage.getData(msg.chat)
        val r = when {
            repo.value != null -> repo.value!!
            data.boundRepo.isEmpty() -> quoter.defaultRepo
            else -> data.boundRepo
        }
        val q = quoter
        val quotes = if (leftover.isNotEmpty()) {
            val id = leftover[0].toIntOrNull()
            if (id == null) {
                bot.answerOn(msg, "Неправильный айдишник")
                return@command
            }
            listOf(q.byId(id, r))
        } else {
            when {
                all.present -> q.all(r)
                quoteRange.isNotEmpty -> q.byRange(quoteRange.from ?: 1, quoteRange.to ?: q.total(r), r)
                else -> q.random(count, r)
            }
        }.filterNotNull()
        if (quotes.isEmpty()) {
            bot.answerOn(msg, "Таких цитаток не найдено.")
            return@command
        }
        var reply = StringBuilder()
        quotes.forEach { quote ->
            val quoteRep = StringBuilder()
            quoteRep.append("#").append(quote.id.toString()).append(" ")
                .append(quote.authors.joinToString(", "))
                .append(":\n")
                .append(quote.content)
                .append("\n\n")
            if(reply.length + quoteRep.length > 4096) {
                bot.answerOn(msg, reply.toString())
                reply = StringBuilder()
            }
            reply.append(quoteRep)
        }
        if(reply.isNotEmpty()) {
            bot.answerOn(msg, reply.toString())
        }
    }
}

fun CommandBuilder.helpCommand() {}

fun extractCount(a: List<String>): Pair<Int, List<String>> {
    val countPos = a.indexOf("*")
    require(a.lastIndexOf("*") == countPos) { "'*' cannot be specified multiple times" }
    return if (countPos < 0) {
        1 to a
    } else {
        require(countPos < a.lastIndex) { "'*' must be followed by a number" }
        val count = requireNotNull(a[countPos + 1].toIntOrNull()) { "'*' must be followed by a number" }
        count to a.filterIndexed { i, _ -> i !in countPos..countPos + 1 }
    }
}

private suspend fun close() {
    dp.close()
    bot.close()
    quoter.close()
}