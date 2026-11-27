package tuibook

import kotlinx.serialization.json.Json
import tuibook.source.BookFormat
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

internal class LibraryStorage(private val root: Path) {
    private val books = root.resolve("books")

    fun resolve(reference: String): LocalBook {
        if (isBookId(reference)) readBook(reference)?.let { return it }
        val matches = readBooks().filter { it.title.equals(reference, ignoreCase = true) }
        return when (matches.size) {
            0 -> throw bookNotFound(reference)
            1 -> matches.single()
            else -> throw LibraryFailure(
                "AMBIGUOUS_BOOK",
                "Multiple books match '$reference'. Use a book ID from tuibook list.",
            )
        }
    }

    fun readBooks(): List<LocalBook> = Files.newDirectoryStream(books) { isBookId(it.fileName.toString()) }
        .use { folders -> folders.mapNotNull { readBook(it.fileName.toString()) } }

    fun readBook(id: String): LocalBook? {
        require(isBookId(id)) { "Invalid local book ID." }
        val folder = books.resolve(id)
        val metadata = folder.resolve("book.json")
        if (Files.notExists(metadata)) return null
        val book = Json.decodeFromString<LocalBook>(Files.readString(metadata))
        if (book.bookId != id || BookFormat.entries.none { it.extension == book.format }) {
            throw LibraryFailure("INVALID_LIBRARY", "Invalid metadata for book $id.")
        }
        val content = folder.resolve("book.${book.format}")
        if (!Files.isRegularFile(content)) {
            throw LibraryFailure("MISSING_BOOK_FILE", "Missing book file: $content.")
        }
        return book.copy(path = content.toString())
    }

    fun publish(format: BookFormat, prepare: (Path) -> LocalBook): LocalBook {
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

    fun writeMetadata(book: LocalBook) {
        val path = books.resolve(book.bookId).resolve("book.json")
        replaceFile(path) { Files.writeString(it, Json.encodeToString(book)) }
    }

    fun archive(book: LocalBook): LocalBook {
        val trash = Files.createDirectories(root.resolve("trash"))
        val destination = trash.resolve("${book.bookId}-${UUID.randomUUID()}")
        move(books.resolve(book.bookId), destination)
        return book.copy(path = destination.resolve("book.${book.format}").toString())
    }

    fun <T> locked(action: () -> T): T = synchronized(processLock) {
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

    private companion object {
        val processLock = Any()
    }
}

private val bookIdPattern = Regex("book-[0-9a-f]{64}")

private fun isBookId(id: String): Boolean = id.matches(bookIdPattern)

internal fun bookNotFound(reference: String) = LibraryFailure("BOOK_NOT_FOUND", "No local book matches '$reference'.")
