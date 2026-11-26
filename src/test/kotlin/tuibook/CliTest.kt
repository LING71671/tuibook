package tuibook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CliTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val data get() = LocalData(temporary.root.toPath().resolve("data"))

    @Test
    fun `invalid command arguments return structured errors`() {
        val commands = listOf(
            listOf("unknown"),
            listOf("list", "--query"),
            listOf("list", "--favorites", "--favorites"),
            listOf("search", "Story", "--source"),
            listOf("status", "Story", "waiting"),
            listOf("source", "invalid", "wikisource-en"),
        )
        for (command in commands) {
            val result = invoke(*(command + "--json").toTypedArray())
            assertEquals(2, result.exit, command.joinToString(" "))
            assertEquals("INVALID_ARGUMENT", result.errorCode())
            assertEquals("", result.stderr)
        }
    }

    @Test
    fun `book operations preserve titles with spaces and non ASCII characters`() {
        val file = temporary.newFile("draft notes.txt").toPath()
        Files.writeString(file, "A short story")
        val title = "Story \u4e66"
        val imported = invoke("add", file.toString(), "--title", title, "--json")
        assertEquals(0, imported.exit)
        val bookId = imported.json().jsonObject.getValue("bookId").jsonPrimitive.content

        assertEquals(0, invoke("favorite", "add", title, "--json").exit)
        assertEquals(0, invoke("status", bookId, "reading", "--json").exit)
        val matching = invoke("--json", "list", "--query", "Story", "--favorites")
        assertEquals(0, matching.exit)
        val book = matching.json().jsonArray.single().jsonObject
        assertEquals(title, book.getValue("title").jsonPrimitive.content)
        assertEquals("reading", book.getValue("status").jsonPrimitive.content)

        val plain = invoke("list", "--plain")
        assertEquals(0, plain.exit)
        assertContains(plain.stdout, "$bookId\t$title\treading\tfavorite")
    }

    @Test
    fun `a disabled source fails before searching the network`() {
        assertEquals(0, invoke("source", "disable", "wikisource-en", "--json").exit)
        val result = invoke("search", "The Raven", "--source", "wikisource-en", "--json")
        assertEquals(1, result.exit)
        assertEquals("SOURCE_DISABLED", result.errorCode())
    }

    @Test
    fun `an argument error without JSON is written to stderr`() {
        val result = invoke("add")
        assertEquals(2, result.exit)
        assertEquals("", result.stdout)
        assertContains(result.stderr, "INVALID_ARGUMENT: Usage: tuibook add")
    }

    private fun invoke(vararg args: String): Output {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val exit = PrintStream(stdout, true, Charsets.UTF_8).use { out ->
            PrintStream(stderr, true, Charsets.UTF_8).use { err ->
                runCli(arrayOf(*args), out, err, data)
            }
        }
        return Output(exit, stdout.toString(Charsets.UTF_8), stderr.toString(Charsets.UTF_8))
    }

    private data class Output(val exit: Int, val stdout: String, val stderr: String) {
        fun json() = Json.parseToJsonElement(stdout)

        fun errorCode() = json().jsonObject.getValue("error").jsonObject.getValue("code").jsonPrimitive.content
    }
}
