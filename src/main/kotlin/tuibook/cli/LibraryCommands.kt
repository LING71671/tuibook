package tuibook.cli

import tuibook.LocalLibrary
import tuibook.ReadingStatus
import java.nio.file.Path

internal class LibraryCommands(
    private val library: LocalLibrary,
    private val sink: CommandOutput,
) {
    fun execute(args: List<String>) {
        when (args.first()) {
            "list" -> list(args.drop(1))
            "add" -> add(args)
            "favorite" -> favorite(args)
            "status" -> status(args)
            "remove" -> remove(args)
            "update" -> update(args)
            else -> error("Unsupported library command")
        }
    }

    private fun list(args: List<String>) {
        val queryIndex = args.indexOf("--query")
        val query = if (queryIndex < 0) null else {
            val value = args.getOrNull(queryIndex + 1)?.trim()
            require(!value.isNullOrEmpty() && !value.startsWith("--")) { "Enter a value for --query." }
            value
        }
        val flags = args.filterIndexed { index, _ -> queryIndex < 0 || index !in queryIndex..queryIndex + 1 }
        require(flags.all { it in listFlags } && flags.size == flags.distinct().size) {
            "Usage: tuibook list [--plain] [--favorites] [--query <title>] [--json]"
        }
        val books = library.list(query, "--favorites" in flags)
        if (sink.json) sink.stdout.println(CommandOutput.libraryJson.encodeToString(books)) else books.forEach {
            val title = it.title.replace(terminalControls, " ")
            val favorite = if (it.favorite) "favorite" else ""
            sink.stdout.println("${it.bookId}\t$title\t${it.status.name.lowercase()}\t$favorite")
        }
    }

    private fun add(args: List<String>) {
        val title = titleOption(args, "Usage: tuibook add <path> [--title <title>] [--json]")
        sink.book(library.importFile(Path.of(args[1]), title))
    }

    private fun favorite(args: List<String>) {
        require(args.size == BOOK_ACTION_WORDS && args[1] in favoriteActions) {
            "Usage: tuibook favorite add|remove <book ID or title> [--json]"
        }
        sink.book(library.setFavorite(args[2], args[1] == "add"))
    }

    private fun status(args: List<String>) {
        require(args.size == BOOK_ACTION_WORDS) {
            "Usage: tuibook status <book ID or title> unread|reading|finished [--json]"
        }
        val status = ReadingStatus.entries.firstOrNull { it.name.equals(args[2], ignoreCase = true) }
            ?: throw IllegalArgumentException("Reading status must be unread, reading, or finished.")
        sink.book(library.setStatus(args[1], status))
    }

    private fun remove(args: List<String>) {
        require(args.size == 2) { "Usage: tuibook remove <book ID or title> [--json]" }
        sink.book(library.remove(args[1]))
    }

    private fun update(args: List<String>) {
        val title = titleOption(args, "Usage: tuibook update <book ID or title> --title <new title> [--json]")
        requireNotNull(title) { "Provide the new title with --title." }
        sink.book(library.rename(args[1], title))
    }

    private fun titleOption(args: List<String>, usage: String): String? {
        val options = args.drop(2)
        require(args.size >= 2 && (options.isEmpty() || (options.size == 2 && options.first() == "--title"))) { usage }
        return options.lastOrNull()?.trim()?.also { require(it.isNotEmpty()) { "The title must not be blank." } }
    }

    companion object {
        private const val BOOK_ACTION_WORDS = 3
        val names = setOf("list", "add", "favorite", "status", "remove", "update")
        private val listFlags = setOf("--plain", "--favorites")
        private val favoriteActions = setOf("add", "remove")
        private val terminalControls = Regex("[\\p{Cc}]")
    }
}
