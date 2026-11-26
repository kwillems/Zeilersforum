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
        assertEquals(null, p.readers) // als gast staat "Wie leest er mee?" er niet
        assertEquals("Algemene vragen techniek & onderhoud", p.forumName)
        assertTrue(p.forumUrl!!.startsWith("https://zeilersforum.nl/index.php/forum-hoofdmenu-37/82-"))
    }

    @Test fun newCountLabel() {
        val html = """<table id="kflattable"><tr>
            <td><a class="ktopic-title km" href="/index.php/forum-hoofdmenu-37/82-x/611296-winterklaar-maken">winterklaar maken<sup class="knewchar">(2 Nieuw)</sup></a></td>
            </tr><tr><td><a class="ktopic-title km" href="/index.php/forum-hoofdmenu-37/82-x/611297-ander">Ander onderwerp</a></td></tr></table>"""
        val t = ForumParser.parseTopics(html, "https://zeilersforum.nl/").topics
        assertEquals("winterklaar maken", t[0].title)
        assertEquals(2, t[0].newCount)
        assertEquals("Ander onderwerp", t[1].title)
        assertEquals(0, t[1].newCount)
    }

    @Test fun readersBlock() {
        // Opbouw zoals op de site als je bent ingelogd: een kop, daarna de namen als profiellinks.
        val html = """<html><body><div id="Kunena">
            <h2>Wie leest er mee?</h2>
            <div><a class="kwho-user" href="http://www.zeilersforum.nl/index.php/mijn-gegevens-mijn-menu-21/6718-hans-v">Hans V</a>
            <a class="kwho-user" href="/index.php/mijn-gegevens-mijn-menu-21/kwillems">kwillems</a>
            <a class="kwho-moderator" href="/index.php/mijn-gegevens-mijn-menu-21/1-nostres">Nostres</a>
            <a class="kwho-user" href="/index.php/mijn-gegevens-mijn-menu-21/2-pam49">pam49</a></div>
            <table class="klist-actions"><tr><td><a href="/x/reply">Antwoord onderwerp</a></td></tr></table>
            </div></body></html>"""
        val readers = ForumParser.parseReaders(org.jsoup.Jsoup.parse(html, "https://zeilersforum.nl/index.php/x"))!!
        assertEquals(listOf("Hans V", "kwillems", "Nostres", "pam49"), readers.map { it.name })
        assertEquals(MemberRole.MODERATOR, readers[2].role)
        assertTrue(readers.all { it.profileUrl.startsWith("https://zeilersforum.nl/") })
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

    // ---------- Privéberichten (geanonimiseerde pagina's: lange teksten zijn "[tekst]") ----------

    @Test fun pmInbox() {
        val p = ForumParser.parsePmList(fixture("pm_inbox.html"), "https://zeilersforum.nl/index.php?option=com_uddeim&task=inbox")
        assertEquals(20, p.items.size)
        assertTrue(p.totalPages > 1)
        assertTrue(p.total > 20)
        assertTrue(p.items.all { it.id.matches(Regex("\\d+")) && it.partner.isNotBlank() && it.date.isNotBlank() })
        assertTrue(p.items.all { it.partnerUrl?.startsWith("https://zeilersforum.nl/") == true })
    }

    @Test fun pmOutbox() {
        val p = ForumParser.parsePmList(fixture("pm_outbox.html"), "https://zeilersforum.nl/index.php?option=com_uddeim&task=outbox")
        assertTrue(p.items.isNotEmpty())
        assertTrue(p.items.any { it.unread }) // ontvanger heeft het nog niet gelezen
    }

    @Test fun pmShow() {
        val html = fixture("pm_show.html")
        // Het berichtnummer komt uit de (lokale) testpagina, zodat er geen echt nummer in de code staat.
        val id = Regex("name=\"messageid\" value=\"(\\d+)\"").find(html)!!.groupValues[1]
        val m = ForumParser.parsePm(html, "https://zeilersforum.nl/index.php?option=com_uddeim&task=show&messageid=$id")
        assertEquals(id, m.id)
        assertTrue(m.partner.isNotBlank())
        assertTrue(m.date.contains("2026"))
        assertTrue(m.blocks.isNotEmpty())
        assertTrue(m.blocks.any { it is Block.Quote })
        val f = m.replyForm!!
        assertTrue(f.postUrl.contains("task=save"))
        assertEquals(id, f.fields["messageid"])
        assertTrue(f.fields["to_id"]!!.isNotBlank())
    }

    @Test fun pmNewForm() {
        val f = ForumParser.pmNewForm(fixture("pm_new.html"), "https://zeilersforum.nl/index.php?option=com_uddeim&task=new")
        assertTrue(f.postUrl.contains("task=save"))
        assertTrue("to_id" in f.fields && "messageid" in f.fields)
    }

    @Test fun pmUnreadModule() {
        val n = ForumParser.pmUnread(fixture("pm_inbox.html"))
        assertTrue(n != null && n >= 0)
    }

    @Test fun avatarViaSearch() {
        // Zoekresultaten van één gebruiker (search_user.html); de gebruikersnaam staat in ZF_USER.
        val user = System.getenv("ZF_USER")
        assumeTrue("ZF_USER niet gezet", user != null)
        val url = ForumParser.avatarOf(fixture("search_user.html"), user!!)
        assertTrue(url != null && url.startsWith("https://zeilersforum.nl/images/comprofiler/"))
    }

    @Test fun noOwnAvatarAsGuest() {
        val doc = org.jsoup.Jsoup.parse(fixture("index.html"), ForumParser.BASE)
        assertEquals(null, ForumParser.myAvatar(doc))
    }
}
