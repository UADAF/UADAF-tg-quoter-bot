package quoterbot

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.content
import java.io.File

object Config {

    val tgToken: String
    val admins: List<String>
    init {
        check(configFilename != "") { "Config was accessed before config filename was set" }
        val cfg = json.parseJson(File(configFilename).readText()).jsonObject
        tgToken = checkNotNull(cfg["tg_token"]?.content) { "Invalid config: tg_token not found" }
        val adminsList = checkNotNull(cfg.getArrayOrNull("admins")) { "Invalid config: unable to read admins" }
        admins = adminsList.map {
            check(it is JsonPrimitive) { "Invalid config: unable to read admins" }
            val adm = it.content
            check(adm.startsWith("@")) { "Invalid config: excepted admins to be in for '@tag'" }
            adm.removePrefix("@").toLowerCase()
        }
    }

}