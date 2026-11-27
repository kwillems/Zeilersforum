package nl.kwillems.zeilersforum.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentNumbersTest {
    private fun photo(key: Int, name: String = "foto_$key.jpg") =
        ForumRepository.Upload(name, ByteArray(0), "image/jpeg", inline = true, key = key)
    private fun file(name: String) = ForumRepository.Upload(name, ByteArray(0), "application/pdf", inline = false)

    @Test fun insertedPhotosAreRenumberedToUploadOrder() {
        // Foto 1 is weggehaald; foto 3 en 2 staan in de tekst. Uploadvolgorde: 2, 3.
        val text = "Begin\n[attachment:3]foto_3.jpg[/attachment]\nMidden\n[attachment:2]foto_2.jpg[/attachment]\nEind"
        val out = ForumRepository.withAttachmentNumbers(text, listOf(photo(2), photo(3)))
        assertEquals("Begin\n[attachment:2]foto_3.jpg[/attachment]\nMidden\n[attachment:1]foto_2.jpg[/attachment]\nEind", out)
    }

    @Test fun photosNotInTextAreNotAdded() {
        // Foto 2 staat niet in de tekst: alleen bijlage onder het bericht, niets stiekem onderaan.
        val text = "Tekst\n[attachment:1]foto_1.jpg[/attachment]"
        assertEquals(text, ForumRepository.withAttachmentNumbers(text, listOf(photo(1), photo(2))))
    }

    @Test fun filesCountForNumbering() {
        val text = "A\n[attachment:2]foto_2.jpg[/attachment]\nB\n[attachment:1]foto_1.jpg[/attachment]"
        val out = ForumRepository.withAttachmentNumbers(text, listOf(photo(1), file("route.gpx"), photo(2)))
        assertEquals("A\n[attachment:3]foto_2.jpg[/attachment]\nB\n[attachment:1]foto_1.jpg[/attachment]", out)
    }

    @Test fun existingAttachmentsAreLeftAlone() {
        val text = "[attachment=176025]oud.jpg[/attachment]"
        assertEquals(text, ForumRepository.withAttachmentNumbers(text, emptyList()))
    }
}
