package nl.kwillems.zeilersforum.data

/** Eén regel in de lijst met recente onderwerpen. */
data class TopicSummary(
    val id: String,
    val title: String,
    val url: String,
    val preview: String,
    val category: String,
    val author: String,
    val startedAt: String,
    val replies: Int,
    val views: Int,
    val lastPostBy: String,
    val lastPostAt: String,
    val lastPostAgo: String,
    /** Link naar de pagina met het laatste bericht (bevat ?start=…). */
    val lastPostUrl: String,
    val hasAttachment: Boolean,
    /** Aantal nieuwe berichten sinds je laatste bezoek ("(2 Nieuw)" op de site); 0 = geen. */
    val newCount: Int = 0,
)

data class TopicsPage(
    val topics: List<TopicSummary>,
    val page: Int,
    val totalPages: Int,
    val perPage: Int = 40,
    /** Subcategorieën die boven de onderwerpen staan (bij een categoriepagina). */
    val subcategories: List<Category> = emptyList(),
    /** Link "Nieuw onderwerp" (alleen als je bent ingelogd en het mag). */
    val createUrl: String? = null,
    val title: String = "",
    val loggedIn: Boolean = false,
)

enum class MemberRole(val label: String) {
    ADMIN("Websitebeheerder"), GLOBAL_MODERATOR("Algemene moderator"), MODERATOR("Moderator"),
    USER("Gebruiker"), BANNED("Geblokkeerd"),
}

data class OnlineMember(val name: String, val profileUrl: String, val role: MemberRole)



data class Category(
    val name: String,
    val url: String,
    val description: String,
    val topics: String,
    val replies: String,
    val lastTopic: String,
    val lastBy: String,
    val lastAgo: String,
    val lastUrl: String?,
    val children: List<Pair<String, String>> = emptyList(),
)

data class Section(val name: String, val url: String, val categories: List<Category>)

data class SearchResult(
    val title: String,
    val url: String,
    val author: String,
    val avatar: String?,
    val date: String,
    val dateAgo: String,
    val snippetHtml: String,
    val category: String,
)

data class SearchPage(val results: List<SearchResult>, val page: Int, val totalPages: Int, val total: Int)

/** Een te verzenden forumformulier (reactie of nieuw onderwerp). */
data class PostForm(
    val postUrl: String,
    val fields: LinkedHashMap<String, String>,
    val subject: String,
    /** Huidige tekst (BBCode) bij het bewerken van een bericht. */
    val message: String = "",
    /** false als de site het onderwerpveld grijs maakt (bewerken van een reactie). */
    val subjectEditable: Boolean = true,
    /** Bijlagen die al bij het bericht horen (bij bewerken). */
    val attachments: List<FormAttachment> = emptyList(),
    /** De smileys van het forum (uit de smileybalk van het formulier), met hun code. */
    val smileys: List<Smiley> = emptyList(),
)

/** Smiley van het forum: [code] (bijv. ":laugh:") en het plaatje. */
data class Smiley(val code: String, val image: String)

/**
 * Bestaande bijlage in het bewerkformulier. De site houdt hem alleen als het vakje
 * attachment[id] wordt meegestuurd; weglaten = verwijderen.
 */
data class FormAttachment(val id: String, val name: String, val url: String, val thumb: String?) {
    val keepField: String get() = "attachment[$id]"
}

/** Inhoud van een bericht, opgesplitst in blokken die Compose kan tonen. */
sealed interface Block {
    /** Opgemaakte tekst als (vereenvoudigde) HTML. */
    data class Text(val html: String) : Block
    data class Image(val src: String, val link: String?) : Block
    data class Quote(val blocks: List<Block>) : Block
    data class Video(val url: String) : Block
}

data class Attachment(val url: String, val thumb: String?, val name: String) {
    val isImage: Boolean
        get() = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
                || url.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
}

data class Post(
    val id: String,
    val author: String,
    val avatar: String?,
    val rank: String,
    val postCount: String,
    val online: Boolean,
    val date: String,
    val dateAgo: String,
    val title: String,
    val blocks: List<Block>,
    val attachments: List<Attachment>,
    /** Platte tekst van het bericht, voor citeren en delen. */
    val plainText: String,
    /** Link "Bewerk" (alleen bij je eigen berichten, zolang de site het toestaat). */
    val editUrl: String? = null,
    /** Link "Verwijder" (alleen bij je eigen bericht, zolang de site het toestaat). */
    val deleteUrl: String? = null,
)

data class TopicPage(
    val title: String,
    val url: String,
    val posts: List<Post>,
    /** 1-gebaseerd. */
    val page: Int,
    val totalPages: Int,
    val perPage: Int,
    val replyUrl: String?,
    val loggedIn: Boolean,
    /** "Wie leest er mee?": leden die dit onderwerp nu bekijken (alleen zichtbaar als je bent ingelogd). */
    val readers: List<OnlineMember>? = null,
    /** Forum (categorie) waarin het onderwerp staat, uit het kruimelpad. */
    val forumName: String = "",
    val forumUrl: String? = null,
)

typealias ReplyForm = PostForm

// ---------- Privéberichten (uddeIM) ----------

enum class PmBox { INBOX, OUTBOX }

/** Eén regel in postvak in of verzonden. */
data class PmSummary(
    val id: String,
    /** Afzender (postvak in) of ontvanger (verzonden). */
    val partner: String,
    val partnerUrl: String?,
    val partnerOnline: Boolean,
    val preview: String,
    val date: String,
    /** Postvak in: ik heb het nog niet gelezen. Verzonden: de ontvanger heeft het nog niet gelezen. */
    val unread: Boolean,
)

data class PmPage(val items: List<PmSummary>, val page: Int, val totalPages: Int, val total: Int)

data class PmMessage(
    val id: String,
    val partner: String,
    val partnerUrl: String?,
    val avatar: String?,
    val date: String,
    val blocks: List<Block>,
    /** Antwoordformulier onder het bericht (alleen bij ontvangen berichten). */
    val replyForm: PostForm?,
    val plainText: String,
)

enum class PostOrder { OLDEST_FIRST, NEWEST_FIRST }

/**
 * Hoe ver zoeken teruggaat ([sel] = waarde voor de site). Alles doorzoeken kost het forum ~20 s,
 * het afgelopen jaar ~1 s.
 */
enum class SearchPeriod(val label: String, val sel: String) {
    YEAR("Afgelopen jaar", "365"),
    ALL("Alles", "all"),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class NotLoggedInException : Exception("Niet ingelogd")
class ForumException(message: String) : Exception(message)
