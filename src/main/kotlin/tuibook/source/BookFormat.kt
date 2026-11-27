package tuibook.source

import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

private const val EPUB_MIMETYPE = "application/epub+zip"

enum class BookFormat(val extension: String) {
    EPUB("epub"),
    TEXT("txt"),
}

internal fun InputStream.isEpub(): Boolean = try {
    ZipInputStream(this).use { zip ->
        zip.nextEntry?.name == "mimetype" && zip.readNBytes(EPUB_MIMETYPE.length + 1).decodeToString() == EPUB_MIMETYPE
    }
} catch (_: IOException) {
    false
}
