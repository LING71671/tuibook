package tuibook.source

import kotlinx.serialization.Serializable
import java.io.IOException

internal const val SOURCE_SEARCH_LIMIT = 12

interface BookSource {
    val id: String
    val name: String

    fun search(query: String): List<SourceBook>
    fun download(itemId: String): BookDownload
}

@Serializable
data class SourceBook(
    val sourceBookId: String,
    val sourceId: String,
    val title: String,
    val sourceUrl: String,
)

class BookDownload(val book: SourceBook, val format: BookFormat, val content: ByteArray)

class SourceFailure(val code: String, message: String, cause: Throwable? = null) : IOException(message, cause)

internal fun BookSource.book(itemId: String, title: String, sourceUrl: String) =
    SourceBook("$id:$itemId", id, title, sourceUrl)
