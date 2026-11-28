package nl.kwillems.zeilersforum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forumpagina's die als alleen het forumgedeelte (tmpl=component) worden opgehaald. */
class ComponentPagesTest {
    private val guestBox = """<div class="kcontainer" id="kprofilebox"><table class="kprofilebox"><tr><td class="kprofileboxcnt">
        <div class="k_guest"> Welkom, <b>Gasten</b> </div></td></tr></table></div>"""
    private fun memberBox(pmText: String = "Persoonlijke berichten") = """<div class="kcontainer" id="kprofilebox"><table class="kprofilebox"><tr>
        <td class="kprofileboxcnt"><a href="/index.php/component/uddeim/">$pmText</a> Welkom, <a href="/index.php/mijn-gegevens-mijn-menu-21/kwillems">kwillems</a>
        Laatst ingelogd: 28 nov 2026 05:02</td></tr></table></div>"""

    @Test fun guestInForumOnlyPageIsNotLoggedIn() {
        // Geen inlogveld van de website, wel "Welkom, Gasten" in het profielblok: niet ingelogd (dan opnieuw inloggen).
        assertFalse(ForumParser.isLoggedIn(guestBox + """<table id="kflattable"></table><form></form>"""))
        assertFalse(ForumParser.isLoggedIn(guestBox + """<div class="kmsg-header"></div>"""))
    }

    @Test fun memberInForumOnlyPageIsLoggedIn() {
        assertTrue(ForumParser.isLoggedIn(memberBox() + """<table id="kflattable"></table>"""))
        assertTrue(ForumParser.isLoggedIn(memberBox() + """<div class="kmsg-header"></div>"""))
    }

    @Test fun fullPagesStillWork() {
        // Hele pagina als gast: inlogveld van de website én "Welkom, Gasten".
        assertFalse(ForumParser.isLoggedIn("""<form><input name="username"><input name="passwd"></form>""" + guestBox))
        // Pagina zonder profielblok (bijv. privéberichten): oude regel.
        assertFalse(ForumParser.isLoggedIn("""<form><input name="passwd"></form>"""))
        assertTrue(ForumParser.isLoggedIn("""<form action="/x"></form><div id="uddeim-overview"></div>"""))
    }

    @Test fun pmCountFromProfileBox() {
        assertEquals(2, ForumParser.pmUnread(org.jsoup.Jsoup.parse(memberBox("Persoonlijke berichten (2 nieuw)"))))
        // Zonder getal: onbekend, zodat de teller niet ten onrechte op 0 springt.
        assertNull(ForumParser.pmUnread(org.jsoup.Jsoup.parse(memberBox())))
        assertNull(ForumParser.pmUnread(org.jsoup.Jsoup.parse(guestBox)))
    }

    @Test fun pmCountFromWebsiteModuleWins() {
        val module = """<div id="uddeim-module"><img src="/x/env_ani.gif"><div class="uddeim-module-row"></div><div class="uddeim-module-row"></div></div>"""
        assertEquals(2, ForumParser.pmUnread(org.jsoup.Jsoup.parse(module + memberBox("Persoonlijke berichten (5 nieuw)"))))
        assertEquals(0, ForumParser.pmUnread(org.jsoup.Jsoup.parse("""<div id="uddeim-module"></div>""")))
    }

    @Test fun componentRemovedFromLinks() {
        val html = """<a href="/t/611296-x?tmpl=component&amp;start=150">7</a>
            <a href="/r/recente-onderwerpen/sel-24?tmpl=component">s</a>
            <a href="/t/611296-x?start=25&amp;tmpl=component&amp;limit=25">2</a>
            <a href="/t/611296-x?start=50&tmpl=component">3</a>"""
        val out = ForumParser.withoutComponent(html)
        assertFalse(out.contains("tmpl"))
        assertTrue(out.contains("""href="/t/611296-x?start=150""""))
        assertTrue(out.contains("""href="/r/recente-onderwerpen/sel-24""""))
        assertTrue(out.contains("""href="/t/611296-x?start=25&amp;limit=25""""))
        assertTrue(out.contains("""href="/t/611296-x?start=50""""))
    }

    @Test fun componentUrl() {
        val b = "https://zeilersforum.nl/index.php/forum-hoofdmenu-37"
        assertEquals("$b/x/611296-y?start=150&tmpl=component", ForumRepository.componentUrl("$b/x/611296-y?start=150#1710742"))
        assertEquals("$b/recente-onderwerpen?sel=24&tmpl=component", ForumRepository.componentUrl("$b/recente-onderwerpen?sel=24"))
        assertEquals("$b/forum-125/index?tmpl=component", ForumRepository.componentUrl("$b/forum-125/index"))
    }
}
