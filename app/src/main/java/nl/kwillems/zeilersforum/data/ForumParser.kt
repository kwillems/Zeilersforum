package nl.kwillems.zeilersforum.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Leest de HTML-pagina's van zeilersforum.nl (Joomla + Kunena, template "blue_eagle").
 * Alles wat van de opbouw van de site afhangt, staat in dit bestand.
 */
object ForumParser {

    const val BASE = "https://zeilersforum.nl"

    // ---------- Recente onderwerpen ----------

    fun parseTopics(html: String, url: String): TopicsPage {
        val doc = Jsoup.parse(html, url)
        val rows = doc.select("table#kflattable tr").filter { it.selectFirst("a.ktopic-title") != null }
        val topics = rows.mapNotNull { row ->
            val titleLink = row.selectFirst("a.ktopic-title") ?: return@mapNotNull null
            val topicUrl = titleLink.absUrl("href")
            val lastLink = row.selectFirst("span.ktopic-latest-post a[href]")
            TopicSummary(
                id = topicId(topicUrl),
                title = titleLink.text(),
                url = topicUrl,
                preview = titleLink.attr("title").trim(),
                category = row.selectFirst("span.ktopic-category a")?.text().orEmpty(),
                author = row.selectFirst("span.ktopic-by a")?.text().orEmpty(),
                startedAt = row.selectFirst("span.ktopic-posted-time")?.text()
                    ?.removePrefix("Onderwerp gestart op")?.trim().orEmpty(),
                replies = row.selectFirst("td.kcol-ktopicreplies strong")?.text()?.toIntOrNull() ?: 0,
                views = row.selectFirst("span.ktopic-views-number")?.text()?.toIntOrNull() ?: 0,
                lastPostBy = row.selectFirst("span.ktopic-latest-post a.kwho-user, span.ktopic-latest-post a[class^=kwho]")?.text().orEmpty(),
                lastPostAt = row.selectFirst("span.ktopic-date")?.text().orEmpty(),
                lastPostAgo = row.selectFirst("span.ktopic-date")?.attr("title").orEmpty(),
                lastPostUrl = lastLink?.absUrl("href") ?: topicUrl,
                hasAttachment = row.selectFirst("span.ktopicattach") != null,
            )
        }
        val (page, total) = pagination(doc)
        return TopicsPage(topics, page, total)
    }

    // ---------- Een onderwerp ----------

    fun parseTopic(html: String, url: String): TopicPage {
        val doc = Jsoup.parse(html, url)
        val title = doc.selectFirst("div.kheader h1 span")?.text()?.removePrefix("Onderwerp:")?.trim()
            ?: doc.selectFirst("span.kmsgtitle")?.text().orEmpty()

        val posts = mutableListOf<Post>()
        // Elk bericht = een kop (div.kmsg-header) gevolgd door een table.kmsg.
        for (header in doc.select("div.kmsg-header")) {
            val table = header.nextElementSiblings().firstOrNull { it.tagName() == "table" && it.hasClass("kmsg") } ?: continue
            posts += parsePost(header, table)
        }

        val (page, total) = pagination(doc)
        val perPage = perPage(doc)
        if (posts.isEmpty() && doc.text().contains("Toegang geweigerd")) {
            throw ForumException("Toegang geweigerd")
        }
        return TopicPage(
            title = title,
            url = url,
            posts = posts,
            page = page,
            totalPages = total,
            perPage = perPage,
            replyUrl = findReplyUrl(doc),
            loggedIn = isLoggedIn(doc),
        )
    }

    private fun parsePost(header: Element, table: Element): Post {
        val id = header.selectFirst("span[class^=kmsg-id] a[id]")?.id()
            ?: header.selectFirst("span[class^=kmsg-id] a")?.text()?.removePrefix("#").orEmpty()
        val dateEl = header.selectFirst("span.kmsgdate")
        val textEl = table.selectFirst("div.kmsgtext")
        val attachments = table.select("div.kmsgattach ul.kfile-attach li").mapNotNull { li ->
            val a = li.selectFirst("a[href]") ?: return@mapNotNull null
            Attachment(
                url = fixUrl(a.absUrl("href")),
                thumb = li.selectFirst("img")?.absUrl("src")?.let(::fixUrl),
                name = a.attr("title").ifBlank { a.text() }.trim(),
            )
        }
        return Post(
            id = id,
            author = table.selectFirst("li.kpost-username a, li.kpost-username")?.text().orEmpty(),
            avatar = table.selectFirst("li.kpost-avatar img")?.absUrl("src")?.takeIf { it.isNotBlank() }?.let(::fixUrl),
            rank = table.selectFirst("li.kpost-userrank")?.text().orEmpty(),
            postCount = table.selectFirst("li.kpost-userposts")?.text()?.substringAfter(":")?.trim().orEmpty(),
            online = table.selectFirst("span.kbuttononline-yes") != null,
            date = dateEl?.text().orEmpty(),
            dateAgo = dateEl?.attr("title").orEmpty(),
            title = header.selectFirst("span.kmsgtitle")?.text().orEmpty(),
            blocks = textEl?.let { contentBlocks(it) } ?: emptyList(),
            attachments = attachments,
            plainText = textEl?.let { plainText(it) }.orEmpty(),
        )
    }

    /** Ingelogd als de pagina geen inlogformulier met wachtwoordveld meer toont. */
    fun isLoggedIn(doc: Document): Boolean =
        doc.selectFirst("input[name=passwd]") == null && doc.selectFirst("body") != null && doc.select("div.kmsg-header, table#kflattable, form").isNotEmpty()

    fun isLoggedIn(html: String): Boolean = isLoggedIn(Jsoup.parse(html, BASE))

    /** De knop "Reageer" bovenaan het onderwerp; die is er alleen als je bent ingelogd. */
    private fun findReplyUrl(doc: Document): String? {
        fun pick(els: List<Element>) = els.map { it.absUrl("href") }
            .firstOrNull { it.contains("/reply") && !it.contains("quote") }
        return pick(doc.select("table.klist-actions a[href]")) ?: pick(doc.select("a[href]"))
    }

    private fun pagination(doc: Document): Pair<Int, Int> {
        val nav = doc.selectFirst("td.klist-pages-all ul.kpagination") ?: return 1 to 1
        val current = nav.selectFirst("span.pagenav")?.text()?.toIntOrNull() ?: 1
        val max = nav.select(".pagenav").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: current
        return current to maxOf(current, max)
    }

    /** Aantal berichten per pagina, afgeleid uit de ?start= van pagina 2. */
    private fun perPage(doc: Document): Int {
        val nav = doc.selectFirst("td.klist-pages-all ul.kpagination") ?: return 25
        for (a in nav.select("a.pagenav")) {
            val n = a.text().toIntOrNull() ?: continue
            val start = Regex("[?&]start=(\\d+)").find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (n > 1 && start > 0) return start / (n - 1)
        }
        return 25
    }

    // ---------- Inhoud van een bericht ----------

    private val SMILEYS = mapOf(
        "smile" to "🙂", "grin" to "😁", "laughing" to "😆", "wink" to "😉", "silly" to "😜",
        "tongue" to "😛", "sad" to "🙁", "angry" to "😠", "blink" to "😳", "blush" to "😊",
        "cheerful" to "😄", "cool" to "😎", "devil" to "😈", "dizzy" to "😵", "ermm" to "😕",
        "kissing" to "😘", "pinch" to "🤏", "shocked" to "😲", "sick" to "🤢", "sideways" to "🙃",
        "unsure" to "🤔", "w00t" to "🤩", "wassat" to "😮", "whistling" to "😗", "woohoo" to "🥳",
        "alien" to "👽", "angel" to "😇", "pouty" to "😤", "heart" to "❤️", "unhappy" to "😞",
        "crying" to "😢", "kiss" to "😘", "tongueout" to "😛", "evil" to "😈",
    )

    private fun isSmiley(img: Element) =
        img.hasClass("bbcode_smiley") || img.attr("src").contains("/emoticons/")

    private fun smileyText(img: Element): String {
        val name = img.attr("src").substringAfterLast('/').substringBeforeLast('.').lowercase()
        return SMILEYS[name] ?: img.attr("alt").ifBlank { img.attr("title") }
    }

    fun contentBlocks(root: Element): List<Block> {
        val out = mutableListOf<Block>()
        val buf = StringBuilder()

        fun flush() {
            val html = buf.toString()
                .replace(Regex("^(\\s|<br\\s*/?>)+"), "")
                .replace(Regex("(\\s|<br\\s*/?>)+$"), "")
            if (Jsoup.parse(html).text().isNotBlank()) out += Block.Text(html)
            buf.setLength(0)
        }

        fun containsBlock(el: Element): Boolean =
            el.select("img").any { !isSmiley(it) } || el.selectFirst("iframe, div.kmsgtext-quote, blockquote") != null

        fun walk(node: Node, link: String?) {
            when (node) {
                is TextNode -> buf.append(org.jsoup.nodes.Entities.escape(node.wholeText.replace(Regex("[\\t\\r\\n]+"), " ")))
                is Element -> when {
                    node.tagName() == "img" && isSmiley(node) -> buf.append(smileyText(node))
                    node.tagName() == "img" -> {
                        flush()
                        val src = node.absUrl("src")
                        if (src.isNotBlank()) out += Block.Image(fixUrl(src), link?.let(::fixUrl))
                    }
                    node.tagName() == "iframe" -> {
                        flush()
                        out += Block.Video(videoUrl(node.absUrl("src")))
                    }
                    node.hasClass("kmsgtext-quote") || node.tagName() == "blockquote" -> {
                        flush()
                        out += Block.Quote(contentBlocks(node))
                    }
                    node.tagName() in setOf("script", "style") -> Unit
                    node.tagName() == "a" && containsBlock(node) -> {
                        val href = node.absUrl("href")
                        node.childNodes().forEach { walk(it, href.ifBlank { link }) }
                    }
                    containsBlock(node) -> {
                        if (node.isBlock) buf.append("<br>")
                        node.childNodes().forEach { walk(it, link) }
                        if (node.isBlock) buf.append("<br>")
                    }
                    else -> {
                        // Gewone inline/blokopmaak: links absoluut maken en als HTML doorgeven.
                        val copy = node.clone()
                        copy.select("a[href]").forEach { it.attr("href", it.absUrl("href")) }
                        if (copy.tagName() == "a") copy.attr("href", node.absUrl("href"))
                        copy.select("img").forEach { img ->
                            if (isSmiley(img)) img.replaceWith(TextNode(smileyText(img))) else img.remove()
                        }
                        if (copy.isBlock && copy.tagName() == "div") {
                            buf.append(copy.html()).append("<br>")
                        } else {
                            buf.append(copy.outerHtml())
                        }
                    }
                }
            }
        }
        root.childNodes().forEach { walk(it, null) }
        flush()
        return out
    }

    private fun videoUrl(src: String): String {
        val id = Regex("youtube(?:-nocookie)?\\.com/embed/([\\w-]+)").find(src)?.groupValues?.get(1)
        return if (id != null) "https://www.youtube.com/watch?v=$id" else src
    }

    fun plainText(root: Element): String {
        val copy = root.clone()
        copy.select("div.kmsgtext-quote, blockquote").remove()
        copy.select("img").forEach { if (isSmiley(it)) it.replaceWith(TextNode(smileyText(it))) else it.remove() }
        copy.select("br").append("\\n")
        copy.select("div, p").prepend("\\n")
        return copy.text().replace("\\n", "\n").lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // ---------- Formulieren ----------

    /** Verborgen velden en actie van het inlogformulier (Community Builder). */
    fun loginForm(html: String): Pair<String, LinkedHashMap<String, String>>? {
        val doc = Jsoup.parse(html, BASE)
        val form = doc.selectFirst("form.cbLoginForm, form#login-form") ?: return null
        if (form.selectFirst("input[name=passwd]") == null) return null
        val fields = LinkedHashMap<String, String>()
        form.select("input[type=hidden]").forEach { fields[it.attr("name")] = it.attr("value") }
        return form.absUrl("action").ifBlank { "$BASE/index.php/cb-login" } to fields
    }

    fun loginError(html: String): String? =
        Jsoup.parse(html).selectFirst("div.alert-error div.alert-message, div.alert-message")?.text()

    fun replyForm(html: String, url: String, fallbackSubject: String): ReplyForm {
        val doc = Jsoup.parse(html, url)
        val form = doc.selectFirst("form#postform, form[name=postform]")
            ?: doc.select("form").firstOrNull { it.selectFirst("textarea[name=message]") != null }
            ?: throw if (doc.selectFirst("input[name=passwd]") != null) NotLoggedInException()
            else ForumException("Reactieformulier niet gevonden")
        val fields = LinkedHashMap<String, String>()
        form.select("input[type=hidden]").forEach { if (it.attr("name").isNotBlank()) fields[it.attr("name")] = it.attr("value") }
        val subject = form.selectFirst("input[name=subject]")?.attr("value")?.takeIf { it.isNotBlank() } ?: fallbackSubject
        // Net als de oorspronkelijke app wordt naar de reactie-URL zelf gepost.
        return ReplyForm(postUrl = url, fields = fields, subject = subject)
    }

    fun replySucceeded(html: String): Boolean =
        html.contains("Bericht succesvol geplaatst")

    fun pageError(html: String): String? =
        Jsoup.parse(html).selectFirst("div.alert-error div.alert-message, div.alert-error")?.text()

    // ---------- Hulpfuncties ----------

    fun topicId(url: String): String =
        Regex("/(\\d+)-[^/?#]*").findAll(url).lastOrNull()?.groupValues?.get(1) ?: url

    /** Haalt ?start= en #anker weg. */
    fun baseTopicUrl(url: String): String = url.substringBefore('#').substringBefore('?')

    fun startOf(url: String): Int =
        Regex("[?&]start=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun pageUrl(baseUrl: String, page: Int, perPage: Int): String =
        if (page <= 1) baseUrl else "$baseUrl?start=${(page - 1) * perPage}"

    /** Spaties in bijlagenamen e.d. coderen, http-links van het forum zelf naar https. */
    fun fixUrl(url: String): String =
        url.replace(" ", "%20")
            .replace("http://zeilersforum.nl", BASE)
            .replace("http://www.zeilersforum.nl", BASE)
            .replace("https://www.zeilersforum.nl", BASE)
}
