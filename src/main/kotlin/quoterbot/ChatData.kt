package quoterbot

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import rocks.waffle.telekt.types.Chat
import java.io.File
import java.io.IOException

data class ChatData(val chatId: Long = -1, var boundRepo: String = "") {

    constructor(chatId: Long, data: JsonObject)
            : this(
        chatId,
        data.getPrimitive("bound_repo").content
    )

    fun toJson(): JsonElement = JsonObject(mapOf(
        "bound_repo" to JsonPrimitive(boundRepo)
    ))

}

object ChatDataStorage {
    const val dataFilename = "internal/chat_data.json"
    val data: MutableMap<Long, ChatData> = mutableMapOf()

    init {
        val dataFile = File(dataFilename)
        if(dataFile.exists()) {
            val dataJson = json.parseJson(dataFile.readText()).jsonObject
            dataJson.forEach { id, d ->
                val lid = id.toLong()
                data[lid] = ChatData(lid, d.jsonObject)
            }
        }
    }

    fun getData(chat: Chat): ChatData {
        return data.computeIfAbsent(chat.id) { ChatData(it) }
    }

    fun save(): Boolean {
        val jsonData = mutableMapOf<String, JsonElement>()
        data.forEach { (id, d) ->
            jsonData[id.toString()] = d.toJson()
        }
        val json = JsonObject(jsonData)
        return try {
            val dataFile = File(dataFilename)
            if(!dataFile.exists()) {
                dataFile.parentFile.mkdirs()
                dataFile.createNewFile()
            }
            dataFile.writeText(json.toString())
            true
        } catch(e: IOException) {
            //TODO: replace with logger
            println("Unable to save chat data!")
            e.printStackTrace()
            false
        }
    }

}