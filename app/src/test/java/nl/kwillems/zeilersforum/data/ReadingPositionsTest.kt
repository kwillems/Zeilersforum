package nl.kwillems.zeilersforum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingPositionsTest {
    @Test fun encodeDecode() {
        val p = ReadingPositions.Position(page = 7, postId = "1710580", time = 1795810000000)
        assertEquals(p, ReadingPositions.decode(ReadingPositions.encode(p)))
    }

    @Test fun badValues() {
        assertNull(ReadingPositions.decode(null))
        assertNull(ReadingPositions.decode("kapot"))
        assertNull(ReadingPositions.decode("x|1|2"))
    }
}
