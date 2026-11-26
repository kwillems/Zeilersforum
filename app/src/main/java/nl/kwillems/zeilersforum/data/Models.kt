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
)

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
)

typealias ReplyForm = PostForm

enum class PostOrder { OLDEST_FIRST, NEWEST_FIRST }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class NotLoggedInException : Exception("Niet ingelogd")
class ForumException(message: String) : Exception(message)
