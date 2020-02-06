package quoterbot

import argparser.ArgParser
import argparser.spec.ArgResult
import argparser.tokenize
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import quoter.DisplayType
import quoter.Quoter
import rocks.waffle.telekt.bot.Bot
import rocks.waffle.telekt.contrib.filters.CommandFilter
import rocks.waffle.telekt.contrib.filters.TextableTelegramEvent
import rocks.waffle.telekt.dispatcher.Dispatcher
import rocks.waffle.telekt.dispatcher.Filter
import rocks.waffle.telekt.dispatcher.HandlerScope
import rocks.waffle.telekt.types.Message
import rocks.waffle.telekt.types.replymarkup.InlineKeyboardButton
import rocks.waffle.telekt.types.replymarkup.KeyboardButton
import rocks.waffle.telekt.util.InlineKeyboardMarkup
import rocks.waffle.telekt.util.ReplyKeyboardMarkup
import rocks.waffle.telekt.util.answerOn
import rocks.waffle.telekt.util.handlerregistration.*

val json = Json(JsonConfiguration.Stable);

var configFilename: String = ""

lateinit var quoter: Quoter<HttpClientEngineConfig>
    private set

lateinit var bot: Bot
    private set

lateinit var dp: Dispatcher
    private set

class TextStartsWithFilter<T : TextableTelegramEvent>(private val text: String?) : Filter<T>() {
    override suspend fun test(scope: HandlerScope, value: T): Boolean =
        (value.eventText != null && text != null)
                && value.eventText!!.startsWith(text)
}

suspend fun main(args: Array<String>) {
    //Redirect slf4j-simple to System.out from System.err
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.out")
    configFilename = if (args.size > 1) args[1] else "config.json"
    init()
    dp.dispatch {
        messages {
            initCommands()
        }
        callbackQuerys {
            handle(TextStartsWithFilter("quote::confirm")) { query ->
                val ss = query.data?.split("::", limit=5) ?: emptyList()
                if(ss.size < 5) {
                    println("Invalid quote-confirm query: ${query.data}") //TODO replace with normal log
                    bot.answerCallbackQuery(query.id, "Что-то пошло сильно не так")
                    return@handle
                }

            }
        }
    }
    dp.poll()
    close()
}

private fun init() {
    quoter = Quoter("http://52.48.142.75:6741/api/v2/quote/", Apache, Config.quoterKey)
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
    addCommand()
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

suspend fun <Q : HttpClientEngineConfig, T> Quoter<Q>.tryGetQuote(body: suspend Quoter<Q>.() -> T): T? {
    return try {
        body()
    } catch (e: Throwable) {
        e.printStackTrace()
        null
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
            listOf(q.tryGetQuote { byId(id, r) })
        } else {
            q.tryGetQuote {
                when {
                    all.present -> q.all(r)
                    quoteRange.isNotEmpty -> q.byRange(quoteRange.from ?: 1, quoteRange.to ?: q.total(r), r)
                    else -> q.random(count, r)
                }
            }
        }?.filterNotNull()
        if (quotes == null) {
            bot.answerOn(
                msg, """
                         Не удалось получить цитатки!
                         Проверьте репозиторий (скорее всего должен быть вида uadaf:*)
                         Если правильный, пинать автора.""".trimIndent()
            );
            return@command
        }
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
            if (reply.length + quoteRep.length > 4096) {
                bot.answerOn(msg, reply.toString())
                reply = StringBuilder()
            }
            reply.append(quoteRep)
        }
        if (reply.isNotEmpty()) {
            bot.answerOn(msg, reply.toString())
        }
    }
}

fun CommandBuilder.addCommand() {
    fun addCmd() {
        val parser = ArgParser()
        val repo by parser.value("repo")
        val leftover by parser.leftoverDelegate()
        command("add", parser) { msg ->
            if (msg.from?.username !in Config.admins) {
                bot.answerOn(msg, "Только админ добавлять цитаты.")
                return@command
            }
            if (leftover.size > 1) {
                val qauthor = leftover[0]
                val quote = leftover.subList(1, leftover.size).joinToString(" ")
                val data = ChatDataStorage.getData(msg.chat)
                val r = when {
                    repo.value != null -> repo.value!!
                    data.boundRepo.isEmpty() -> quoter.defaultRepo
                    else -> data.boundRepo
                }

                val qadder = msg.from?.username ?: "Unknown_tg"
                val dt = if (quote.count { it == '\n' } > 1) DisplayType.DIALOG else DisplayType.TEXT

                val id = TempQuoteStorage.saveQuote(TempQuoteStorage.TempQuote(r, qadder, qauthor, dt, quote, msg))
                if (id == null) {
                    bot.answerOn(
                        msg,
                        "Ого. Тут буффер цитаток забился. Кто-то где-то не подтвердил и не отклонил мнооого цитат..."
                    )
                    return@command
                }
                bot.answerOn(msg, "Добавить цитату? (id подтверждения: $id)\n\n $qauthor:\n $quote",
                    replyMarkup = ReplyKeyboardMarkup {
                        +KeyboardButton("/confirm $id")
                        +KeyboardButton("/deny $id")
                    })
            } else {
                bot.answerOn(msg, "Цитака не указана, а первое слово это автор")
            }
        }
    }
    fun confirmCmd() {
        val parser = ArgParser()
        val leftover by parser.leftoverDelegate()
        command("confirm", parser) { msg ->
            if (msg.from?.username !in Config.admins) {
                bot.answerOn(msg, "Только админ добавлять цитаты.")
                return@command
            }
            try {
                if(leftover.isEmpty()) {
                    bot.answerOn(msg, "Эту команду можно использовать только с id подтверждения")
                    return@command
                }
                val id = leftover[0].toInt()
                val q = TempQuoteStorage.takeQuote(id)
                if (q == null) {
                    println("Couldn't find quote in buffer...") //TODO replace with normal log
                    bot.answerOn(msg, "Не могу найти такую цитатку. Возможно, неправильный id")
                    return@command
                }
                val res = quoter.add(
                    q.adder,
                    q.authors,
                    q.quote,
                    q.dt,
                    emptyList(),
                    q.repo
                )
                if(res.response.status != HttpStatusCode.OK) {
                    bot.answerOn(msg, "Что-то пошло не так")
                    println(res.response.status) //TODO replace with normal log
                    println(res.request.url)
                    println(res.request.content)
                } else {
                    bot.answerOn(msg, "Добавлено!")
                }
            } catch (e: Throwable) {
                bot.answerOn(msg, "Что-то пошло не так.")
                println("Couldn't add quote") //TODO replace with normal log
                e.printStackTrace()
            }
        }
    }
    fun denyCmd() {
        val parser = ArgParser()
        val leftover by parser.leftoverDelegate()
        command("confirm", parser) { msg ->
            if (msg.from?.username !in Config.admins) {
                bot.answerOn(msg, "Только админ добавлять цитаты.")
                return@command
            }
            try {
                if(leftover.isEmpty()) {
                    bot.answerOn(msg, "Эту команду можно использовать только с id подтверждения")
                    return@command
                }
                val id = leftover[0].toInt()
                val q = TempQuoteStorage.takeQuote(id)
                if (q == null) {
                    println("Couldn't find quote in buffer...") //TODO replace with normal log
                    bot.answerOn(msg, "Не могу найти такую цитатку. Возможно, неправильный id")
                    return@command
                }
                bot.answerOn(msg, "Отменено.")
            } catch (e: Throwable) {
                bot.answerOn(msg, "Что-то пошло не так.")
                println("Couldn't add quote") //TODO replace with normal log
                e.printStackTrace()
            }
        }
    }
    addCmd()
    confirmCmd()
    denyCmd()
}

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