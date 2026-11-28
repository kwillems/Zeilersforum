package nl.kwillems.zeilersforum.ui.editor

import nl.kwillems.zeilersforum.data.Block
import nl.kwillems.zeilersforum.data.ForumParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTest {
    @Test fun previewJson() {
        assertEquals("<b>x</b>", ForumParser.previewHtml("""{"preview":"<b>x</b>"}"""))
        // Niet ingelogd: het forum geeft een lege lijst.
        assertNull(ForumParser.previewHtml("[]"))
        assertNull(ForumParser.previewHtml("kapot"))
    }

    @Test fun newPhotosGetMarkersAndComeBackAsImages() {
        val text = "Kijk:\n[attachment:2]foto_2.jpg[/attachment]\nMooi he [attachment=176038]oud.png[/attachment]"
        val marked = PreviewPhotos.withMarkers(text, setOf(2))
        assertTrue(marked.contains(PreviewPhotos.marker(2)))
        // Bestaande bijlage (met =id) blijft staan: die kent het forum al.
        assertTrue(marked.contains("[attachment=176038]oud.png[/attachment]"))

        val forum = listOf(Block.Text("Kijk:<br>${PreviewPhotos.marker(2)}<br>Mooi he"), Block.Quote(listOf(Block.Text(PreviewPhotos.marker(9)))))
        val out = PreviewPhotos.withPhotos(forum, mapOf(2 to "content://media/2"))
        assertEquals(Block.Text("Kijk:"), out[0])
        assertEquals(Block.Image("content://media/2", null), out[1])
        assertEquals(Block.Text("Mooi he"), out[2])
        // Onbekend merkteken (foto weggehaald): verdwijnt zonder foto.
        assertEquals(Block.Quote(emptyList()), out[3])
    }

    @Test fun smileysFromForm() {
        val html = """<form id="postform"><textarea name="message"></textarea></form>
            <div id="smilie"><img src="/media/kunena/emoticons/cool.png" alt="B) " onclick="kbbcode.focus().insert(' B) ', 'after', false);">
            <img src="/media/kunena/emoticons/laughing.png" alt=":laugh: " onclick="kbbcode.focus().insert(' :laugh: ', 'after', false);"></div>
            <div class="kmsgtext"><img class="bbcode_smiley" src="/media/kunena/emoticons/smile.png" alt=":)"></div>"""
        val form = ForumParser.postForm(html, "https://zeilersforum.nl/index.php/forum-hoofdmenu-37/x/1-y/reply", "")
        assertEquals(listOf("B)", ":laugh:"), form.smileys.map { it.code })
        assertEquals("https://zeilersforum.nl/media/kunena/emoticons/cool.png", form.smileys[0].image)
    }
}
