package nl.kwillems.zeilersforum.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class BbEditorTest {
    private fun v(text: String, start: Int, end: Int = start) = TextFieldValue(text, TextRange(start, end))

    @Test fun wrapSelectionKeepsInnerTextSelected() {
        val out = BbEditor.bold(v("de nieuwe genua", 3, 9))
        assertEquals("de [b]nieuwe[/b] genua", out.text)
        assertEquals(TextRange(6, 12), out.selection)
    }

    @Test fun wrapWithoutSelectionPutsCursorBetweenTags() {
        val out = BbEditor.strike(v("abc", 3))
        assertEquals("abc[strike][/strike]", out.text)
        assertEquals(TextRange(11), out.selection)
    }

    @Test fun reversedSelectionWorks() {
        assertEquals("[i]ab[/i]c", BbEditor.italic(v("abc", 2, 0)).text)
    }

    @Test fun smileyWithoutDoubleSpaces() {
        assertEquals("Hoi :) ", BbEditor.smiley(v("Hoi", 3), ":) ").text)
        assertEquals("Hoi :) daar", BbEditor.smiley(v("Hoi daar", 3), ":)").text)
        assertEquals(":laugh: ", BbEditor.smiley(v("", 0), " :laugh: ").text)
    }

    @Test fun listFromSelectedLines() {
        val out = BbEditor.list(v("Lijst:\nreefbanden\n\nzeilnummer", 7, 29), ordered = false)
        assertEquals("Lijst:\n[ul]\n  [li]reefbanden[/li]\n  [li]zeilnummer[/li]\n[/ul]", out.text)
    }

    @Test fun emptyListPutsCursorInFirstItem() {
        val out = BbEditor.list(v("Tekst", 5), ordered = true)
        assertEquals("Tekst\n[ol]\n  [li][/li]\n[/ol]", out.text)
        assertEquals("Tekst\n[ol]\n  [li]".length, out.selection.start)
    }

    @Test fun linkUsesSelectionOrLabel() {
        assertEquals("zie [url=https://zeilersforum.nl]hier[/url]", BbEditor.link(v("zie hier", 4, 8), "zeilersforum.nl", "").text)
        assertEquals("[url=https://a.nl]de site[/url]", BbEditor.link(v("", 0), "https://a.nl", "de site").text)
        assertEquals("[url]https://a.nl[/url]", BbEditor.link(v("", 0), "a.nl", " ").text)
    }

    @Test fun colorAndSize() {
        assertEquals("[color=#BB0000]rood[/color]", BbEditor.color(v("rood", 0, 4), "#BB0000").text)
        assertEquals("[size=5]groot[/size]", BbEditor.size(v("groot", 0, 5), 5).text)
    }

    @Test fun schemeAdded() {
        assertEquals("https://www.youtube.com/watch?v=x", BbEditor.withScheme(" www.youtube.com/watch?v=x "))
        assertEquals("http://a.nl", BbEditor.withScheme("http://a.nl"))
    }
}
