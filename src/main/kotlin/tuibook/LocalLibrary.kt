package tuibook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tuibook.source.BookFormat
import tuibook.source.isEpub
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale

@Serializable
internal enum class ReadingStatus {
    @SerialName("unread") UNREAD,
    @SerialName("reading") READING,
    @SerialName("finished") FINISHED,
}

@Serializable
internal data class LocalBook(
    val bookId: String,
    val sourceBookId: String? = null,
    val title: String,
    val sourceUrl: String? = null,
    val format: String,
    val path: String = "",
    val favorite: Boolean = false,
    val status: ReadingStatus = ReadingStatus.UNREAD,
)

internal class LibraryFailure(val code: String, message: String) : IOException(message)

internal class LocalLibrary(root: Path) {
    private val storage = LibraryStorage(root)

    fun list(query: String? = null, favoritesOnly: Boolean = false): List<LocalBook> = storage.locked {
        storage.readBooks()
            .filter {
                val matchesTitle = query == null || it.title.contains(query, ignoreCase = true)
                matchesTitle && (!favoritesOnly || it.favorite)
            }
            .sortedWith(compareBy({ it.title.lowercase(Locale.ROOT) }, { it.bookId }))
    }

    fun find(reference: String): LocalBook = storage.locked { storage.resolve(reference) }

    fun importFile(file: Path, title: String? = null): LocalBook {
        require(Files.isRegularFile(file)) { "Choose an existing book file: $file." }
        val filename = file.fileName.toString()
        val format = BookFormat.entries.firstOrNull { filename.endsWith(".${it.extension}", ignoreCase = true) }
            ?: throw IllegalArgumentException("Supported import formats: EPUB and TXT.")
        val bookTitle = title ?: filename.substringBeforeLast('.').ifBlank { filename }
        return storage.publish(format) { target ->
            val digest = sha256()
            digest.update("import:${format.extension}\u0000".encodeToByteArray())
            DigestInputStream(Files.newInputStream(file), digest).use { Files.copy(it, target) }
            val isEmpty = Files.size(target) == 0L
            if (isEmpty || (format == BookFormat.EPUB && !Files.newInputStream(target).isEpub())) {
                val formatName = format.extension.uppercase(Locale.ROOT)
                throw LibraryFailure("INVALID_BOOK_FILE", "$file is not a readable $formatName file.")
            }
            LocalBook(bookId(digest.digest()), title = bookTitle, format = format.extension)
        }
    }

    fun setFavorite(reference: String, favorite: Boolean): LocalBook = edit(reference) { it.copy(favorite = favorite) }

    fun setStatus(reference: String, status: ReadingStatus): LocalBook = edit(reference) { it.copy(status = status) }

    fun rename(reference: String, title: String): LocalBook = edit(reference) { it.copy(title = title) }

    fun remove(reference: String): LocalBook = storage.locked { storage.archive(storage.resolve(reference)) }

    private fun edit(reference: String, transform: (LocalBook) -> LocalBook): LocalBook = storage.locked {
        val book = transform(storage.resolve(reference))
        storage.writeMetadata(book)
        book
    }
}

private fun bookId(digest: ByteArray): String = "book-${HexFormat.of().formatHex(digest)}"

private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")
