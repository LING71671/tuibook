package tuibook

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalLibraryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val root get() = temporary.root.toPath().resolve("library")

    @Test
    fun `imports are copied and persisted without duplicates`() {
        val title = "Story \u4e66"
        val text = "First paragraph."
        val file = temporary.newFile("$title.txt").toPath()
        Files.writeString(file, text)
        val library = LocalLibrary(root)
        val initial = library.importFile(file)
        val second = library.importFile(file, "Duplicate")
        assertEquals(initial.bookId, second.bookId)

        library.setFavorite(title, true)
        library.setStatus(initial.bookId, ReadingStatus.READING)
        library.rename(initial.bookId, "New title")
        val reopened = LocalLibrary(root)
        assertEquals(emptyList(), reopened.list("Duplicate"))
        val saved = reopened.list("new", favoritesOnly = true).single()
        assertEquals(initial.bookId, saved.bookId)
        assertEquals(ReadingStatus.READING, saved.status)

        val removed = reopened.remove("New title")
        assertEquals(emptyList(), reopened.list())
        assertFalse(Files.exists(Path.of(saved.path)))
        assertEquals(text, Files.readString(file))
        assertEquals(text, Files.readString(Path.of(removed.path)))
    }

    @Test
    fun `ambiguous titles and invalid EPUBs leave the library intact`() {
        val library = LocalLibrary(root)
        for (name in listOf("first.txt", "second.txt")) {
            val file = temporary.newFile(name).toPath()
            Files.writeString(file, name)
            library.importFile(file, "Same title")
        }
        val invalid = temporary.newFile("invalid.epub").toPath()
        Files.writeString(invalid, "not an EPUB")
        assertEquals("INVALID_BOOK_FILE", assertFailsWith<LibraryFailure> { library.importFile(invalid) }.code)
        assertEquals("AMBIGUOUS_BOOK", assertFailsWith<LibraryFailure> { library.remove("Same title") }.code)
        assertEquals(2, library.list().size)
    }

    @Test
    fun `concurrent imports publish exactly one book`() {
        val file = temporary.newFile("story.txt").toPath()
        Files.writeString(file, "A story")
        val ids = Executors.newFixedThreadPool(4).use { workers ->
            List(8) { workers.submit<String> { LocalLibrary(root).importFile(file).bookId } }.map { it.get() }
        }
        assertEquals(1, ids.distinct().size)
        assertEquals(ids.first(), LocalLibrary(root).list().single().bookId)
    }
}
