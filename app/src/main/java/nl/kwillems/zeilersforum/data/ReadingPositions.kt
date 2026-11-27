package nl.kwillems.zeilersforum.data

import android.content.SharedPreferences

/**
 * Onthoudt per onderwerp waar je was gebleven: het bericht bovenaan je scherm en de pagina
 * waarop het stond. Alleen op dit toestel; de oudste worden weggegooid boven [MAX].
 */
class ReadingPositions(private val prefs: SharedPreferences) {

    data class Position(val page: Int, val postId: String, val time: Long)

    fun get(topicId: String): Position? = decode(prefs.getString(topicId, null))

    fun save(topicId: String, page: Int, postId: String) {
        if (topicId.isBlank() || postId.isBlank() || page < 1) return
        val current = get(topicId)
        if (current != null && current.page == page && current.postId == postId) return
        val editor = prefs.edit().putString(topicId, encode(Position(page, postId, System.currentTimeMillis())))
        val all = prefs.all
        if (all.size > MAX) {
            all.mapNotNull { (k, v) -> decode(v as? String)?.let { k to it.time } }
                .sortedBy { it.second }
                .take(all.size - MAX)
                .forEach { (k, _) -> if (k != topicId) editor.remove(k) }
        }
        editor.apply()
    }

    fun clear(topicId: String) = prefs.edit().remove(topicId).apply()

    companion object {
        const val MAX = 300

        fun encode(p: Position) = "${p.page}|${p.postId}|${p.time}"

        fun decode(s: String?): Position? {
            val parts = s?.split('|') ?: return null
            if (parts.size != 3) return null
            val page = parts[0].toIntOrNull() ?: return null
            val time = parts[2].toLongOrNull() ?: return null
            return Position(page, parts[1], time)
        }
    }
}
