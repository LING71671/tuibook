package tuibook

import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import tuibook.cli.CommandOutput
import tuibook.cli.SourceCommands
import tuibook.source.OpdsConfig
import tuibook.source.SearchResult
import tuibook.source.SourceFailure
import tuibook.source.SourceHttp
import tuibook.source.SourceInfo
import tuibook.source.SourceStatus
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SourceCommandsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val root get() = temporary.root.toPath().resolve("data")
    private val catalog = URI.create("https://catalog.example/books.atom")
    private val file = catalog.resolve("story.epub")
    private val epub = epubFixture()
    private val http = SourceHttp { uri, _ ->
        when {
            uri == catalog -> feed.encodeToByteArray()
            uri == file -> epub
            uri.host == "en.wikisource.org" -> throw SourceFailure("SOURCE_UNAVAILABLE", "Catalog unavailable.")
            else -> error("Unexpected source request: $uri")
        }
    }

    @Test
    fun `a partial source failure keeps results available for download`() {
        addCatalog()
        execute("source", "disable", "wikisource-zh")
        execute("source", "disable", "wolne-lektury")

        val search = Json.decodeFromString<SearchResult>(execute("search", "Short Story"))
        val result = search.results.single()
        val failure = search.sourceErrors.single()
        assertEquals("A Short Story", result.title)
        assertEquals("wikisource-en", failure.sourceId)
        assertEquals("SOURCE_UNAVAILABLE", failure.code)

        val book = Json.decodeFromString<LocalBook>(execute("download", result.sourceBookId))
        assertEquals(result.sourceBookId, book.sourceBookId)
        assertEquals(book, LocalData(root).library.list().single())
        assertContentEquals(epub, Files.readAllBytes(Path.of(book.path)))
    }

    @Test
    fun `built in restoration and external removal persist between commands`() {
        addCatalog()
        execute("source", "remove", "wikisource-en")
        assertEquals(SourceStatus.REMOVED, sourceStatuses()["wikisource-en"])

        execute("source", "restore", "wikisource-en")
        execute("source", "remove", "example")
        val statuses = sourceStatuses()
        assertEquals(SourceStatus.ENABLED, statuses["wikisource-en"])
        assertFalse(statuses.containsKey("example"))
    }

    private fun addCatalog() {
        val config = temporary.newFile("catalog.json").toPath()
        Files.writeString(config, Json.encodeToString(OpdsConfig("example", "Example Catalog", catalog.toString())))
        execute("source", "add", config.toString())
    }

    private fun sourceStatuses(): Map<String, SourceStatus> =
        Json.decodeFromString<List<SourceInfo>>(execute("source", "list")).associate { it.id to it.status }

    private fun execute(vararg args: String): String {
        val stdout = ByteArrayOutputStream()
        PrintStream(stdout, true, Charsets.UTF_8).use { out ->
            PrintStream(OutputStream.nullOutputStream()).use { err ->
                val library = OnlineLibrary(LocalData(root), http)
                SourceCommands(library, CommandOutput(true, out, err)).execute(args.toList())
            }
        }
        return stdout.toString(Charsets.UTF_8)
    }

    private fun epubFixture(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".encodeToByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private companion object {
        val feed = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>urn:example:story</id>
                <title>A Short Story</title>
                <link rel="http://opds-spec.org/acquisition/open-access"
                      type="application/epub+zip" href="story.epub"/>
              </entry>
            </feed>
        """.trimIndent()
    }
}
