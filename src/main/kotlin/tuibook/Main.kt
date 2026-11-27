package tuibook

import kotlinx.serialization.SerializationException
import tuibook.cli.CommandOutput
import tuibook.cli.LibraryCommands
import tuibook.cli.SourceCommands
import tuibook.source.SourceFailure
import java.io.IOException
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runCli(args, PrintStream(System.out, true, Charsets.UTF_8), System.err))
}

internal fun runCli(
    args: Array<String>,
    stdout: PrintStream,
    stderr: PrintStream,
    localData: LocalData? = null,
): Int {
    val arguments = args.filterIndexed { index, value ->
        value != "--json" || args.getOrNull(index - 1) == "--title"
    }
    val output = CommandOutput(arguments.size != args.size, stdout, stderr)
    return try {
        dispatch(arguments, localData ?: LocalData.default(), output)
        0
    } catch (failure: SourceFailure) {
        output.failure(failure.code, failure.message.orEmpty(), 1)
    } catch (failure: LibraryFailure) {
        output.failure(failure.code, failure.message.orEmpty(), 1)
    } catch (_: SerializationException) {
        output.failure("INVALID_DATA", "A local JSON file has an invalid format.", 1)
    } catch (failure: IllegalArgumentException) {
        output.failure("INVALID_ARGUMENT", failure.message.orEmpty(), 2)
    } catch (failure: IOException) {
        output.failure("IO_ERROR", failure.message.orEmpty(), 1)
    }
}

private fun dispatch(args: List<String>, data: LocalData, output: CommandOutput) {
    when (val command = args.firstOrNull()) {
        in LibraryCommands.names -> LibraryCommands(data.library, OnlineLibrary(data), output).execute(args)
        in SourceCommands.names -> SourceCommands(OnlineLibrary(data), output).execute(args)
        null, "help", "--help" -> output.stdout.println(usage)
        else -> throw IllegalArgumentException("Unknown command: $command. Run tuibook --help for supported commands.")
    }
}

private val usage = """
    tuibook list [--plain] [--favorites] [--query <title>] [--json]
    tuibook add <path> [--title <title>] [--json]
    tuibook favorite add|remove <book ID or title> [--json]
    tuibook status <book ID or title> unread|reading|finished [--json]
    tuibook update <book ID or title> [--title <new title>] [--json]
    tuibook remove <book ID or title> [--json]
    tuibook search <query> [--source <id>] [--json]
    tuibook download <sourceBookId> [--json]
    tuibook source list [--json]
    tuibook source add <opds-config.json>
    tuibook source enable|disable|remove|restore <id>
""".trimIndent()
