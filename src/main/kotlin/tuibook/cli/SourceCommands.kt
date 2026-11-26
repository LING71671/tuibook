package tuibook.cli

import kotlinx.serialization.json.Json
import tuibook.OnlineLibrary
import tuibook.SourceAction
import java.nio.file.Path

internal class SourceCommands(private val online: OnlineLibrary, private val sink: CommandOutput) {
    fun execute(args: List<String>) {
        when (args.first()) {
            "search" -> search(args)
            "download" -> {
                require(args.size == 2) { "Usage: tuibook download <sourceBookId> [--json]" }
                sink.book(online.download(args[1]))
            }
            "source" -> manage(args.drop(1))
            else -> error("Unsupported source command")
        }
    }

    private fun search(args: List<String>) {
        require(args.size == 2 || (args.size == 4 && args[2] == "--source")) {
            "Usage: tuibook search <query> [--source <id>] [--json]"
        }
        val query = args[1].trim()
        require(query.isNotEmpty() && !query.startsWith("--")) { "Enter a title or search term." }
        val result = online.search(query, args.getOrNull(3))
        if (sink.json) sink.stdout.println(Json.encodeToString(result)) else {
            result.results.forEach { sink.stdout.println("${it.sourceBookId}\t${it.title}") }
            result.sourceErrors.forEach { sink.stderr.println("${it.sourceId}: ${it.message}") }
        }
    }

    private fun manage(args: List<String>) {
        if (args == listOf("list")) {
            val sources = online.sources()
            if (sink.json) sink.stdout.println(Json.encodeToString(sources)) else sources.forEach {
                sink.stdout.println("${it.id}\t${it.status.name.lowercase()}\t${it.name}")
            }
            return
        }
        require(args.size == 2) { "Usage: tuibook source list | add <file> | enable|disable|remove|restore <id>" }
        if (args[0] == "add") online.importSource(Path.of(args[1])) else {
            val action = SourceAction.entries.firstOrNull { it.name.lowercase() == args[0] }
                ?: throw IllegalArgumentException("Unknown source action: ${args[0]}.")
            online.changeSourceStatus(args[1], action)
        }
        if (sink.json) sink.stdout.println(Json.encodeToString(mapOf("status" to "ok")))
    }

    companion object {
        val names = setOf("search", "download", "source")
    }
}
