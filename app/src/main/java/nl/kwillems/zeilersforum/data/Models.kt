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

data class ReplyForm(
    val postUrl: String,
    val fields: LinkedHashMap<String, String>,
    val subject: String,
)

enum class PostOrder { OLDEST_FIRST, NEWEST_FIRST }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class NotLoggedInException : Exception("Niet ingelogd")
class ForumException(message: String) : Exception(message)
