package quoterbot

import quoter.DisplayType
import rocks.waffle.telekt.types.Message

object TempQuoteStorage {

    data class TempQuote(val repo: String, val adder: String,
                         val authors: String, val dt: DisplayType,
                         val quote: String, val madeBy: Message
    )

    private val buffer = Array<TempQuote?>(1024) { null }
    private val freeIds = mutableSetOf<Int>()

    init {
        freeIds.addAll(buffer.indices)
    }

    fun saveQuote(q: TempQuote) : Int? {
        synchronized(buffer) {
            if (freeIds.isEmpty()) return null
            val id = freeIds.take(1)[0]
            freeIds.remove(id)
            buffer[id] = q
            return id
        }
    }

    fun takeQuote(id: Int): TempQuote? {
        synchronized(buffer) {
            val q = buffer[id]
            buffer[id] = null
            freeIds.add(id);
            return q
        }
    }

}