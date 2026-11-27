package tuibook

import java.nio.file.Path

internal class LocalData(root: Path) {
    val library = LocalLibrary(root)

    companion object {
        fun default(): LocalData {
            val path = System.getenv("TUIBOOK_DATA_DIR")?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".tuibook")
            return LocalData(path.toAbsolutePath().normalize())
        }
    }
}
