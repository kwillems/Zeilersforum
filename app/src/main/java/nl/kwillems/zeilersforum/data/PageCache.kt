package nl.kwillems.zeilersforum.data

import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Bewaart de laatst opgehaalde forumpagina's (ingepakt) in de cachemap van de app, zodat een
 * lijst of onderwerp meteen getoond kan worden terwijl de nieuwe versie wordt opgehaald.
 * Het forum zelf staat geen caching toe (no-store), daarom doet de app het zelf.
 * Geen privéberichten. Android mag de cachemap zelf leegmaken als het toestel ruimte nodig heeft.
 */
class PageCache(private val dir: File, private val maxFiles: Int = 300) {

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.html.gz")
    }

    fun get(key: String): String? = runCatching {
        val f = file(key)
        if (!f.exists()) null else GZIPInputStream(f.inputStream()).bufferedReader().use { it.readText() }
    }.getOrNull()

    fun put(key: String, html: String) {
        runCatching {
            dir.mkdirs()
            val f = file(key)
            val tmp = File(dir, f.name + ".tmp")
            GZIPOutputStream(tmp.outputStream()).bufferedWriter().use { it.write(html) }
            tmp.renameTo(f)
            prune()
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".html.gz") } ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
    }
}
