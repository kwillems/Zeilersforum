package nl.kwillems.zeilersforum.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Verzoeken die al zijn gestart voordat het scherm dat ze nodig heeft er is (bij een tik, of bij het
 * opstarten). Het scherm pakt het resultaat één keer op via [take]; zo gaat er geen tweede verzoek
 * naar het forum. Een resultaat telt maar [maxAgeMs]; daarna wordt gewoon opnieuw opgehaald.
 */
class Prefetched<T>(private val maxAgeMs: Long = 20_000L) {
    private val map = mutableMapOf<String, Pair<Long, Deferred<T>>>()

    private fun purge(now: Long) = map.entries.removeAll { now - it.value.first > maxAgeMs }

    fun start(key: String, scope: CoroutineScope, block: suspend () -> T) {
        val now = System.currentTimeMillis()
        synchronized(map) {
            purge(now)
            if (key in map) return
            map[key] = now to scope.async { block() }
        }
    }

    fun put(key: String, value: T) {
        synchronized(map) { map[key] = System.currentTimeMillis() to CompletableDeferred(value) }
    }

    /** Het resultaat voor [key], of null als er niets (meer) klaarstaat of het mislukte. */
    suspend fun take(key: String): T? {
        val entry = synchronized(map) { map.remove(key) } ?: return null
        if (System.currentTimeMillis() - entry.first > maxAgeMs) return null
        return try {
            entry.second.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
