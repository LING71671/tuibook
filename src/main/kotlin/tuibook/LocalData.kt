package tuibook

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import tuibook.source.SourceFailure
import tuibook.source.SourceSettings
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

internal class LocalData(private val root: Path) {
    private val settings = root.resolve("sources.json")
    val library = LocalLibrary(root)

    fun sourceSettings(): SourceSettings = if (Files.notExists(settings)) SourceSettings()
    else try {
        Json.decodeFromString(Files.readString(settings))
    } catch (failure: SerializationException) {
        throw SourceFailure("INVALID_SETTINGS", "Invalid source settings: $settings.", failure)
    }

    fun updateSources(update: (SourceSettings) -> SourceSettings) {
        Files.createDirectories(root)
        FileChannel.open(root.resolve("sources.lock"), CREATE, WRITE).use { file ->
            file.lock().use {
                val next = update(sourceSettings())
                val temporary = Files.createTempFile(root, "sources-", ".tmp")
                try {
                    Files.writeString(temporary, Json.encodeToString(next))
                    try {
                        Files.move(temporary, settings, ATOMIC_MOVE, REPLACE_EXISTING)
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(temporary, settings, REPLACE_EXISTING)
                    }
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
        }
    }

    companion object {
        fun default(): LocalData {
            val path = System.getenv("TUIBOOK_DATA_DIR")?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".tuibook")
            return LocalData(path.toAbsolutePath().normalize())
        }
    }
}
