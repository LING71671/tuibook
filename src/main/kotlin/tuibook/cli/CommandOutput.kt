package tuibook.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tuibook.LocalBook
import java.io.PrintStream

internal class CommandOutput(val json: Boolean, val stdout: PrintStream, val stderr: PrintStream) {
    fun book(book: LocalBook) {
        if (json) stdout.println(libraryJson.encodeToString(book))
        else stdout.println("${book.bookId}\t${book.path}")
    }

    fun failure(code: String, message: String, exitCode: Int): Int {
        if (json) stdout.println(Json.encodeToString(CommandFailure(ErrorDetail(code, message))))
        else stderr.println("$code: $message")
        return exitCode
    }

    companion object {
        val libraryJson = Json { encodeDefaults = true }
    }
}

@Serializable private data class CommandFailure(val error: ErrorDetail)
@Serializable private data class ErrorDetail(val code: String, val message: String)
