package nl.kwillems.zeilersforum.ui.editor

import nl.kwillems.zeilersforum.data.Block

/**
 * Nieuwe foto's staan in de tekst als [attachment:n]…[/attachment], maar het forum kent ze nog niet
 * (ze worden pas bij versturen geüpload). Voor het voorbeeld gaan ze als merkteken naar het forum
 * en komen ze daarna als de foto uit je galerij op die plek terug.
 */
object PreviewPhotos {
    private val TAG = Regex("""\[attachment:(\d+)][^\[]*\[/attachment]""")
    private val MARK = Regex("""ZFFOTO(\d+)ZF""")
    private val EDGE_BREAKS = Regex("""^(\s|<br\s*/?>)+|(\s|<br\s*/?>)+$""", RegexOption.IGNORE_CASE)

    fun marker(key: Int) = "ZFFOTO${key}ZF"

    /** Vervangt de verwijzingen naar nieuwe foto's ([keys]) door een merkteken op een eigen regel. */
    fun withMarkers(text: String, keys: Set<Int>): String = TAG.replace(text) { m ->
        val key = m.groupValues[1].toIntOrNull()
        if (key != null && key in keys) "\n${marker(key)}\n" else m.value
    }

    /** Zet op de plek van elk merkteken de foto ([uris]: key → adres van de foto op het toestel). */
    fun withPhotos(blocks: List<Block>, uris: Map<Int, String>): List<Block> = blocks.flatMap { b ->
        when (b) {
            is Block.Text -> split(b.html, uris)
            is Block.Quote -> listOf(Block.Quote(withPhotos(b.blocks, uris)))
            else -> listOf(b)
        }
    }

    private fun split(html: String, uris: Map<Int, String>): List<Block> {
        if (!MARK.containsMatchIn(html)) return listOf(Block.Text(html))
        val out = mutableListOf<Block>()
        fun text(part: String) {
            val t = part.replace(EDGE_BREAKS, "")
            if (t.isNotBlank()) out += Block.Text(t)
        }
        var last = 0
        for (m in MARK.findAll(html)) {
            text(html.substring(last, m.range.first))
            uris[m.groupValues[1].toInt()]?.let { out += Block.Image(it, null) }
            last = m.range.last + 1
        }
        text(html.substring(last))
        return out
    }
}
