package tuibook.source

import kotlinx.serialization.Serializable
import java.util.concurrent.Executors

@Serializable
internal enum class SourceStatus { ENABLED, DISABLED, REMOVED }

@Serializable
internal data class SourceSettings(
    val status: Map<String, SourceStatus> = emptyMap(),
    val external: List<OpdsConfig> = emptyList(),
)

@Serializable
internal data class SourceInfo(val id: String, val name: String, val builtIn: Boolean, val status: SourceStatus)

@Serializable
internal data class SearchResult(val results: List<SourceBook>, val sourceErrors: List<SearchError> = emptyList())

@Serializable
internal data class SearchError(val sourceId: String, val code: String, val message: String)

internal class SourceRegistry(settings: SourceSettings) {
    private val http = SourceHttp()
    private val builtIn = listOf(
        Wikisource(http, "zh", "Chinese Wikisource"),
        Wikisource(http, "en", "English Wikisource"),
        WolneLektury(http),
    ).associateBy { it.id }
    private val sources: Map<String, BookSource> = builtIn + settings.external.associate { it.id to OpdsSource(http, it) }
    private val status = settings.status

    fun list(): List<SourceInfo> = sources.values.map {
        SourceInfo(it.id, it.name, it.id in builtIn, status[it.id] ?: SourceStatus.ENABLED)
    }

    fun search(query: String, sourceId: String? = null): SearchResult {
        val enabled = if (sourceId != null) listOf(activeSource(sourceId))
        else sources.values.filter { (status[it.id] ?: SourceStatus.ENABLED) == SourceStatus.ENABLED }
        if (enabled.isEmpty()) throw SourceFailure("NO_ENABLED_SOURCES", "Enable a book source before searching.")
        return Executors.newVirtualThreadPerTaskExecutor().use { pool ->
            val results = enabled.map { source ->
                pool.submit<SearchResult> {
                    try {
                        SearchResult(source.search(query))
                    } catch (failure: SourceFailure) {
                        SearchResult(emptyList(), listOf(SearchError(source.id, failure.code, failure.message.orEmpty())))
                    }
                }
            }.map { it.get() }
            val errors = results.flatMap { it.sourceErrors }
            if (errors.size == enabled.size) {
                throw SourceFailure(errors.first().code, errors.joinToString("; ") { "${it.sourceId}: ${it.message}" })
            }
            SearchResult(results.flatMap { it.results }, errors)
        }
    }

    fun download(sourceBookId: String): BookDownload {
        val sourceId = sourceBookId.substringBefore(':')
        val itemId = sourceBookId.substringAfter(':', "")
        require(sourceId.isNotBlank() && itemId.isNotBlank()) { "Use the sourceBookId returned by search." }
        return activeSource(sourceId).download(itemId)
    }

    private fun activeSource(id: String): BookSource {
        return when (status[id]) {
            SourceStatus.REMOVED -> throw SourceFailure("SOURCE_REMOVED", "Source $id has been removed.")
            SourceStatus.DISABLED -> throw SourceFailure("SOURCE_DISABLED", "Source $id is disabled.")
            else -> sources[id] ?: throw SourceFailure("UNKNOWN_SOURCE", "Unknown source: $id.")
        }
    }
}
