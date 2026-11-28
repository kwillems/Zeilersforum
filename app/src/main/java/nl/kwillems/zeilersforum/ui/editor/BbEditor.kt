package nl.kwillems.zeilersforum.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Opmaak met de BBCode van het forum, zoals de opmaakknoppen op de site die invoegen.
 * Met geselecteerde tekst komt de code eromheen (de tekst blijft geselecteerd); zonder selectie
 * komen de codes op de plek van de cursor, met de cursor ertussen.
 */
object BbEditor {

    private fun TextFieldValue.range(): Pair<Int, Int> {
        val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
        val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
        return start to end
    }

    /** [open] en [close] om de selectie, of op de cursor met de cursor ertussen. */
    fun wrap(v: TextFieldValue, open: String, close: String): TextFieldValue {
        val (start, end) = v.range()
        val selected = v.text.substring(start, end)
        val text = v.text.substring(0, start) + open + selected + close + v.text.substring(end)
        val innerStart = start + open.length
        return TextFieldValue(text, TextRange(innerStart, innerStart + selected.length))
    }

    /** Vervangt de selectie door [insert] (of zet het op de cursor); de cursor komt erachter. */
    fun insert(v: TextFieldValue, insert: String): TextFieldValue {
        val (start, end) = v.range()
        val text = v.text.substring(0, start) + insert + v.text.substring(end)
        return TextFieldValue(text, TextRange(start + insert.length))
    }

    /** Smiley-code met een spatie ervoor en erna, zoals de site doet, maar zonder dubbele spaties. */
    fun smiley(v: TextFieldValue, code: String): TextFieldValue {
        val (start, end) = v.range()
        val before = v.text.substring(0, start)
        val after = v.text.substring(end)
        val pre = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
        val post = if (after.isNotEmpty() && after.first().isWhitespace()) "" else " "
        return insert(v, pre + code.trim() + post)
    }

    fun bold(v: TextFieldValue) = wrap(v, "[b]", "[/b]")
    fun italic(v: TextFieldValue) = wrap(v, "[i]", "[/i]")
    fun underline(v: TextFieldValue) = wrap(v, "[u]", "[/u]")
    fun strike(v: TextFieldValue) = wrap(v, "[strike]", "[/strike]")
    fun quote(v: TextFieldValue) = wrap(v, "[quote]", "[/quote]")
    fun code(v: TextFieldValue) = wrap(v, "[code]", "[/code]")
    fun spoiler(v: TextFieldValue) = wrap(v, "[spoiler]", "[/spoiler]")
    fun center(v: TextFieldValue) = wrap(v, "[center]", "[/center]")
    fun color(v: TextFieldValue, hex: String) = wrap(v, "[color=$hex]", "[/color]")
    fun size(v: TextFieldValue, size: Int) = wrap(v, "[size=$size]", "[/size]")

    /**
     * Lijst ([ul] of [ol] met [li]). Geselecteerde regels worden elk een punt; zonder selectie
     * komt er een lege lijst met de cursor in het eerste punt. Altijd op eigen regels.
     */
    fun list(v: TextFieldValue, ordered: Boolean): TextFieldValue {
        val tag = if (ordered) "ol" else "ul"
        val (start, end) = v.range()
        val before = v.text.substring(0, start)
        val after = v.text.substring(end)
        val pre = if (before.isEmpty() || before.endsWith("\n")) "" else "\n"
        val post = if (after.isEmpty() || after.startsWith("\n")) "" else "\n"
        val lines = v.text.substring(start, end).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            val head = "$pre[$tag]\n  [li]"
            val block = "$head[/li]\n[/$tag]$post"
            return TextFieldValue(before + block + after, TextRange(start + head.length))
        }
        val block = pre + "[$tag]\n" + lines.joinToString("\n") { "  [li]$it[/li]" } + "\n[/$tag]" + post
        return TextFieldValue(before + block + after, TextRange(start + block.length))
    }

    /** Link: de selectie (of [label]) wordt de linktekst; zonder tekst alleen het adres. */
    fun link(v: TextFieldValue, url: String, label: String): TextFieldValue {
        val (start, end) = v.range()
        val selected = v.text.substring(start, end)
        val text = selected.ifBlank { label.trim() }
        val u = withScheme(url)
        return insert(v, if (text.isBlank()) "[url]$u[/url]" else "[url=$u]$text[/url]")
    }

    fun image(v: TextFieldValue, url: String) = insert(v, "[img]${withScheme(url)}[/img]")
    fun video(v: TextFieldValue, url: String) = insert(v, "[video]${withScheme(url)}[/video]")

    /** Voegt https:// toe als een adres zonder begint (bijv. "www.zeilersforum.nl"). */
    fun withScheme(url: String): String {
        val u = url.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(u)) u else "https://$u"
    }
}
