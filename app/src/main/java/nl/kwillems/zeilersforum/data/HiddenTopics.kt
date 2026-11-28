package nl.kwillems.zeilersforum.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Onderwerpen die je hebt verborgen, alleen op dit toestel. Per onderwerp-id de titel
 * (voor de lijst bij Instellingen) en wanneer je het verborg.
 */
class HiddenTopics(private val prefs: SharedPreferences) {

    data class Hidden(val id: String, val title: String, val time: Long)

    private val _items = MutableStateFlow(load())
    /** Nieuwste eerst. */
    val items: StateFlow<List<Hidden>> = _items

    fun hide(id: String, title: String) {
        if (id.isBlank()) return
        prefs.edit().putString(id, encode(Hidden(id, title, System.currentTimeMillis()))).apply()
        _items.value = load()
    }

    fun show(id: String) {
        prefs.edit().remove(id).apply()
        _items.value = load()
    }

    fun showAll() {
        prefs.edit().clear().apply()
        _items.value = emptyList()
    }

    private fun load(): List<Hidden> =
        prefs.all.mapNotNull { (k, v) -> decode(k, v as? String) }.sortedByDescending { it.time }

    companion object {
        fun encode(h: Hidden) = "${h.time}|${h.title}"

        fun decode(id: String, s: String?): Hidden? {
            val time = s?.substringBefore('|', "")?.toLongOrNull() ?: return null
            return Hidden(id, s.substringAfter('|'), time)
        }
    }
}
