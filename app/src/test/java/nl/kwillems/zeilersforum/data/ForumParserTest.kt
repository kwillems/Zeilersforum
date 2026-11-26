package nl.kwillems.zeilersforum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Test de parser op echte, opgeslagen pagina's van zeilersforum.nl.
 * Zet ZF_FIXTURES op een map met recent.html, topic.html, foto.html, index.html,
 * cat.html, cat106.html en sres.html; zonder die map worden de tests overgeslagen.
 */
class ForumParserTest {

    private fun fixture(name: String): String {
        val dir = System.getenv("ZF_FIXTURES")
        assumeTrue("ZF_FIXTURES niet gezet", dir != null)
        val f = File(dir, name)
        assumeTrue("$name ontbreekt", f.exists())
        return f.readText()
    }

    @Test fun recentTopics() {
        val p = ForumParser.parseTopics(fixture("recent.html"), ForumRepository.RECENT_URL)
        assertEquals(40, p.topics.size)
        assertEquals(40, p.perPage)
        assertTrue(p.totalPages >= 2)
        val t = p.topics.first()
        assertTrue(t.title.isNotBlank() && t.url.startsWith("https://zeilersforum.nl/"))
        assertTrue(t.lastPostUrl.contains("start=") || t.lastPostUrl == t.url)
        assertFalse(p.loggedIn)
    }

    @Test fun topicPage() {
        val url = "https://zeilersforum.nl/index.php/forum-hoofdmenu-37/82-algemene-vragen-techniek-onderhoud/611296-winterklaar-maken?start=100"
        val p = ForumParser.parseTopic(fixture("topic.html"), url)
        assertTrue(p.posts.isNotEmpty())
        assertEquals(5, p.page)
        assertEquals(25, p.perPage)
        assertTrue(p.posts.all { it.author.isNotBlank() && it.id.isNotBlank() })
        assertTrue(p.posts.any { post -> post.blocks.any { it is Block.Quote } })
    }

    @Test fun photoTopic() {
        val url = "https://zeilersforum.nl/index.php/forum-hoofdmenu-37/fotoboek/251018-zomaar-een-leuke-of-mooie-foto"
        val p = ForumParser.parseTopic(fixture("foto.html"), url)
        assertTrue(p.posts.any { post -> post.blocks.any { it is Block.Image } })
        assertTrue(p.posts.any { it.attachments.isNotEmpty() })
        assertTrue(p.totalPages > 100)
    }

    @Test fun forumIndex() {
        val sections = ForumParser.parseIndex(fixture("index.html"), ForumRepository.INDEX_URL)
        assertTrue(sections.size >= 3)
        val cats = sections.flatMap { it.categories }
        assertTrue(cats.any { it.name == "Fotoboek" })
        assertTrue(cats.all { it.url.startsWith("https://zeilersforum.nl/") })
        println(sections.joinToString("\n") { s -> s.name + ": " + s.categories.joinToString { it.name + (if (it.children.isNotEmpty()) " (" + it.children.joinToString { c -> c.first } + ")" else "") } })
    }

    @Test fun categoryWithTopics() {
        val url = "https://zeilersforum.nl/index.php/forum-125/82-algemene-vragen-techniek-onderhoud"
        val p = ForumParser.parseTopics(fixture("cat.html"), url)
        assertEquals(40, p.topics.size)
        assertTrue(p.totalPages > 3)
        assertEquals(null, p.createUrl)
    }

    @Test fun sectionWithSubcategories() {
        val p = ForumParser.parseTopics(fixture("cat106.html"), "https://zeilersforum.nl/index.php/forum-125/106")
        assertTrue(p.topics.isEmpty())
        assertTrue(p.subcategories.size >= 8)
    }

    @Test fun searchResults() {
        val p = ForumParser.parseSearch(fixture("sres.html"), ForumRepository.SEARCH_URL + "?query=opencpn")
        assertEquals(25, p.results.size)
        assertEquals(95, p.total)
        assertEquals(4, p.totalPages)
        assertTrue(p.results.all { it.title.isNotBlank() && it.url.startsWith("https://") })
    }

    @Test fun loginForm() {
        val (action, fields) = ForumParser.loginForm(fixture("recent.html"))!!
        assertEquals("https://zeilersforum.nl/index.php/cb-login", action)
        assertTrue("cbsecuritym3" in fields && fields["option"] == "com_comprofiler")
    }
}
