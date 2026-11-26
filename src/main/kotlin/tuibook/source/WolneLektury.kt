package tuibook.source

import kotlinx.serialization.Serializable
import java.net.URI
import java.text.Normalizer
import java.util.Locale

internal class WolneLektury(private val http: SourceHttp) : BookSource {
    override val id = "wolne-lektury"
    override val name = "Wolne Lektury"

    private val catalog by lazy { http.readJson<List<Summary>>(URI("$baseUrl/api/books/")) }

    override fun search(query: String): List<SourceBook> {
        val term = normalize(query)
        return catalog.asSequence()
            .filter { normalize(it.title).contains(term) || normalize(it.author).contains(term) }
            .take(12)
            .map { book(it.slug, it.title, it.url) }
            .toList()
    }

    override fun download(itemId: String): BookDownload {
        require(slugPattern.matches(itemId)) { "Invalid Wolne Lektury slug." }
        val details = http.readJson<Details>(URI("$baseUrl/api/books/$itemId/"))
        val file = details.epub?.let { URI.create(it) }
        if (file?.scheme != "https" || file.host != "wolnelektury.pl") {
            throw SourceFailure("FORMAT_UNAVAILABLE", "Wolne Lektury has no EPUB link for $itemId.")
        }
        return BookDownload(book(itemId, details.title, details.url), BookFormat.EPUB, http.readEpub(file))
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        .replace(diacritics, "")
        .replace('ł', 'l')

    @Serializable private data class Summary(val slug: String, val title: String, val author: String, val url: String)
    @Serializable private data class Details(val title: String, val url: String, val epub: String? = null)

    private companion object {
        const val baseUrl = "https://wolnelektury.pl"
        val slugPattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val diacritics = Regex("\\p{M}+")
    }
}
