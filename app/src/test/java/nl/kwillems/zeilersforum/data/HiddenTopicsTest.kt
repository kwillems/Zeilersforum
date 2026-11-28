package nl.kwillems.zeilersforum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HiddenTopicsTest {
    @Test
    fun roundTrip_keepsPipesInTitle() {
        val h = HiddenTopics.Hidden("611313", "Vraag | antwoord", 1234L)
        assertEquals(h, HiddenTopics.decode(h.id, HiddenTopics.encode(h)))
    }

    @Test
    fun decode_rejectsGarbage() {
        assertNull(HiddenTopics.decode("1", null))
        assertNull(HiddenTopics.decode("1", "geen tijd"))
    }
}
