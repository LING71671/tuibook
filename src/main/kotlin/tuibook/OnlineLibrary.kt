package tuibook

import kotlinx.serialization.json.Json
import tuibook.source.OpdsConfig
import tuibook.source.SourceFailure
import tuibook.source.SourceInfo
import tuibook.source.SourceRegistry
import tuibook.source.SourceSettings
import tuibook.source.SourceStatus
import java.nio.file.Files
import java.nio.file.Path

internal enum class SourceAction { ENABLE, DISABLE, REMOVE, RESTORE }

internal class OnlineLibrary(private val data: LocalData) {
    fun search(query: String, sourceId: String?) = registry().search(query, sourceId)

    fun download(sourceBookId: String): LocalBook = data.library.findDownload(sourceBookId)
        ?: data.library.save(registry().download(sourceBookId))

    fun refresh(reference: String): LocalBook {
        val book = data.library.find(reference)
        val sourceBookId = book.sourceBookId
            ?: throw LibraryFailure("NO_BOOK_SOURCE", "This book was imported locally. Use --title to rename it.")
        return data.library.refresh(book.bookId, registry().download(sourceBookId))
    }

    fun sources(): List<SourceInfo> = registry().list()

    fun importSource(file: Path) {
        val config = Json.decodeFromString<OpdsConfig>(Files.readString(file))
        config.validate()
        data.updateSources { settings ->
            require(SourceRegistry(settings).list().none { it.id == config.id }) { "Source ID already exists: ${config.id}." }
            settings.copy(external = settings.external + config)
        }
    }

    fun changeSourceStatus(id: String, action: SourceAction) = data.updateSources { settings ->
        val source = SourceRegistry(settings).list().firstOrNull { it.id == id }
            ?: throw SourceFailure("UNKNOWN_SOURCE", "Unknown source: $id.")
        updatedSettings(settings, source, action)
    }

    private fun registry() = SourceRegistry(data.sourceSettings())

    private fun updatedSettings(settings: SourceSettings, source: SourceInfo, action: SourceAction): SourceSettings {
        val id = source.id
        val status = when (action) {
            SourceAction.ENABLE, SourceAction.DISABLE -> {
                require(source.status != SourceStatus.REMOVED) { "Restore the source before changing its enabled status." }
                if (action == SourceAction.ENABLE) SourceStatus.ENABLED else SourceStatus.DISABLED
            }
            SourceAction.REMOVE -> if (source.builtIn) SourceStatus.REMOVED else return settings.copy(
                external = settings.external.filterNot { it.id == id },
                status = settings.status - id,
            )
            SourceAction.RESTORE -> {
                require(source.builtIn && source.status == SourceStatus.REMOVED) { "Only a removed built-in source can be restored." }
                SourceStatus.ENABLED
            }
        }
        return settings.copy(status = settings.status + (id to status))
    }
}
