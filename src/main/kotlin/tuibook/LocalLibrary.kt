package tuibook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tuibook.source.BookDownload
import tuibook.source.BookFormat
import tuibook.source.isEpub
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

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

internal class LocalLibrary(private val root: Path) {
    private val books = root.resolve("books")

    fun list(query: String? = null, favoritesOnly: Boolean = false): List<LocalBook> = locked {
        readBooks()
            .filter { (!favoritesOnly || it.favorite) && (query == null || it.title.contains(query, ignoreCase = true)) }
            .sortedWith(compareBy({ it.title.lowercase(Locale.ROOT) }, { it.bookId }))
    }

    fun find(reference: String): LocalBook = locked { resolve(reference) }

    fun findDownload(sourceBookId: String): LocalBook? = locked { readBook(downloadId(sourceBookId)) }

    fun importFile(file: Path, title: String? = null): LocalBook {
        require(Files.isRegularFile(file)) { "Choose an existing book file: $file." }
        val filename = file.fileName.toString()
        val format = BookFormat.entries.firstOrNull { filename.endsWith(".${it.extension}", ignoreCase = true) }
            ?: throw IllegalArgumentException("Supported import formats: EPUB and TXT.")
        val bookTitle = title ?: filename.substringBeforeLast('.').ifBlank { filename }
        return publish(format) { target ->
            val digest = sha256()
            digest.update("import:${format.extension}\u0000".encodeToByteArray())
            DigestInputStream(Files.newInputStream(file), digest).use { Files.copy(it, target) }
            if (Files.size(target) == 0L || (format == BookFormat.EPUB && !Files.newInputStream(target).isEpub())) {
                throw LibraryFailure("INVALID_BOOK_FILE", "$file is not a readable ${format.extension.uppercase(Locale.ROOT)} file.")
            }
            LocalBook(bookId(digest.digest()), title = bookTitle, format = format.extension)
        }
    }

    fun save(download: BookDownload): LocalBook {
        val (sourceBookId, _, title, sourceUrl) = download.book
        findDownload(sourceBookId)?.let { return it }
        return publish(download.format) { target ->
            Files.write(target, download.content)
            LocalBook(downloadId(sourceBookId), sourceBookId, title, sourceUrl, download.format.extension)
        }
    }

    fun setFavorite(reference: String, favorite: Boolean): LocalBook = edit(reference) { it.copy(favorite = favorite) }

    fun setStatus(reference: String, status: ReadingStatus): LocalBook = edit(reference) { it.copy(status = status) }

    fun rename(reference: String, title: String): LocalBook = edit(reference) { it.copy(title = title) }

    fun refresh(bookId: String, download: BookDownload): LocalBook = locked {
        val book = readBook(bookId) ?: throw bookNotFound(bookId)
        if (book.sourceBookId != download.book.sourceBookId || book.format != download.format.extension) {
            throw LibraryFailure("INCOMPATIBLE_UPDATE", "The source returned a different edition or file format.")
        }
        val content = Path.of(book.path)
        replaceFile(content) { Files.write(it, download.content) }
        book
    }

    fun remove(reference: String): LocalBook = locked {
        val book = resolve(reference)
        val trash = Files.createDirectories(root.resolve("trash"))
        val destination = trash.resolve("${book.bookId}-${UUID.randomUUID()}")
        move(books.resolve(book.bookId), destination)
        book.copy(path = destination.resolve("book.${book.format}").toString())
    }

    private fun edit(reference: String, transform: (LocalBook) -> LocalBook): LocalBook = locked {
        val book = transform(resolve(reference))
        replaceFile(books.resolve(book.bookId).resolve("book.json")) { Files.writeString(it, Json.encodeToString(book)) }
        book
    }

    private fun resolve(reference: String): LocalBook {
        if (isBookId(reference)) readBook(reference)?.let { return it }
        val matches = readBooks().filter { it.title.equals(reference, ignoreCase = true) }
        return when (matches.size) {
            0 -> throw bookNotFound(reference)
            1 -> matches.single()
            else -> throw LibraryFailure("AMBIGUOUS_BOOK", "Multiple books match '$reference'. Use a book ID from tuibook list.")
        }
    }

    private fun readBooks(): List<LocalBook> = Files.newDirectoryStream(books) { isBookId(it.fileName.toString()) }.use { folders ->
        folders.mapNotNull { readBook(it.fileName.toString()) }
    }

    private fun readBook(id: String): LocalBook? {
        require(isBookId(id)) { "Invalid local book ID." }
        val folder = books.resolve(id)
        val metadata = folder.resolve("book.json")
        if (Files.notExists(metadata)) return null
        val book = Json.decodeFromString<LocalBook>(Files.readString(metadata))
        if (book.bookId != id || BookFormat.entries.none { it.extension == book.format }) {
            throw LibraryFailure("INVALID_LIBRARY", "Invalid metadata for book $id.")
        }
        val content = folder.resolve("book.${book.format}")
        if (!Files.isRegularFile(content)) throw LibraryFailure("MISSING_BOOK_FILE", "Missing book file: $content.")
        return book.copy(path = content.toString())
    }

    private fun publish(format: BookFormat, prepare: (Path) -> LocalBook): LocalBook {
        Files.createDirectories(books)
        val stage = Files.createTempDirectory(books, ".book-")
        val filename = "book.${format.extension}"
        val body = stage.resolve(filename)
        val metadata = stage.resolve("book.json")
        try {
            val pending = prepare(body)
            val destination = books.resolve(pending.bookId)
            val book = pending.copy(path = destination.resolve(filename).toString())
            Files.writeString(metadata, Json.encodeToString(book))
            return locked {
                readBook(book.bookId) ?: run {
                    move(stage, destination)
                    book
                }
            }
        } finally {
            Files.deleteIfExists(body)
            Files.deleteIfExists(metadata)
            Files.deleteIfExists(stage)
        }
    }

    private fun <T> locked(action: () -> T): T = synchronized(processLock) {
        Files.createDirectories(books)
        FileChannel.open(books.resolve(".library.lock"), CREATE, WRITE).use { channel ->
            channel.lock().use { action() }
        }
    }

    private fun replaceFile(path: Path, write: (Path) -> Unit) {
        val temporary = Files.createTempFile(path.parent, ".book-", ".tmp")
        try {
            write(temporary)
            move(temporary, path, replace = true)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun move(from: Path, to: Path, replace: Boolean = false) {
        val options = if (replace) arrayOf(REPLACE_EXISTING) else emptyArray()
        try {
            Files.move(from, to, ATOMIC_MOVE, *options)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, *options)
        }
    }

    private fun downloadId(sourceBookId: String): String = bookId(sha256().digest(sourceBookId.encodeToByteArray()))

    private fun bookId(digest: ByteArray): String = "book-${HexFormat.of().formatHex(digest)}"

    private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun isBookId(id: String): Boolean = id.matches(bookIdPattern)

    private fun bookNotFound(reference: String) = LibraryFailure("BOOK_NOT_FOUND", "No local book matches '$reference'.")

    private companion object {
        val processLock = Any()
        val bookIdPattern = Regex("book-[0-9a-f]{64}")
    }
}
