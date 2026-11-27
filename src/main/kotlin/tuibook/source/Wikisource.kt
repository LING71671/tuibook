package tuibook.source

import kotlinx.serialization.Serializable
import java.net.URI

internal class Wikisource(
    private val http: SourceHttp,
    private val language: String,
    override val name: String,
) : BookSource {
    override val id = "wikisource-$language"
    private val site = "https://$language.wikisource.org"

    override fun search(query: String): List<SourceBook> {
        val parameters = "action=query&list=search&srnamespace=0&srlimit=$SOURCE_SEARCH_LIMIT&srprop="
        val response = http.readJson<SearchResponse>(api("$parameters&srsearch=${query.urlEncode()}"))
        val results = response.query?.search ?: invalidResponse()
        return results.map { book(it.pageid.toString(), it.title, pageUrl(it.title)) }
    }

    override fun download(itemId: String): BookDownload {
        val pageId = itemId.toLongOrNull()
        require(pageId != null && pageId > 0) { "Invalid Wikisource page ID." }
        val response = http.readJson<PagesResponse>(api("action=query&pageids=$pageId&redirects=1"))
        val page = response.query?.pages?.singleOrNull() ?: invalidResponse()
        if (page.missing || page.pageid == null || page.ns != 0) {
            throw SourceFailure("BOOK_NOT_FOUND", "Wikisource page $itemId is unavailable.")
        }
        val export = URI("https://ws-export.wmcloud.org/?format=epub&lang=$language&page=${page.title.urlEncode()}")
        val book = book(pageId.toString(), page.title, pageUrl(page.title))
        return BookDownload(book, BookFormat.EPUB, http.readEpub(export))
    }

    private fun pageUrl(title: String) = "$site/wiki/${title.replace(' ', '_').urlEncode()}"

    private fun api(query: String) = URI("$site/w/api.php?format=json&formatversion=2&$query")

    private fun invalidResponse(): Nothing =
        throw SourceFailure("INVALID_SOURCE_RESPONSE", "Wikisource returned unexpected page data.")

    @Serializable private data class SearchResponse(val query: SearchQuery? = null)
    @Serializable private data class SearchQuery(val search: List<SearchHit>)
    @Serializable private data class SearchHit(val pageid: Long, val title: String)
    @Serializable private data class PagesResponse(val query: PageQuery? = null)
    @Serializable private data class PageQuery(val pages: List<Page>)
    @Serializable
    private data class Page(val pageid: Long? = null, val ns: Int, val title: String, val missing: Boolean = false)
}
