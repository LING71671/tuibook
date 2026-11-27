package tuibook.source

import kotlinx.serialization.Serializable
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

@Serializable
internal data class OpdsConfig(val id: String, val name: String, val catalogUrl: String) {
    fun validate() {
        require(Regex("[a-z][a-z0-9-]{0,62}").matches(id)) { "Invalid source ID: $id." }
        require(name.isNotBlank()) { "Source name must not be blank." }
        val uri = URI.create(catalogUrl)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null) {
            "OPDS catalogUrl must be an HTTPS URL."
        }
    }
}

internal class OpdsSource(private val http: SourceHttp, private val config: OpdsConfig) : BookSource {
    override val id = config.id
    override val name = config.name

    private val catalog = URI.create(config.catalogUrl)

    override fun search(query: String): List<SourceBook> = entries()
        .filter { it.book.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true) }
        .take(SOURCE_SEARCH_LIMIT)
        .map { it.book }

    override fun download(itemId: String): BookDownload {
        val entry = entries().firstOrNull { it.book.sourceBookId == "$id:$itemId" }
            ?: throw SourceFailure("BOOK_NOT_FOUND", "No direct EPUB acquisition link matches this OPDS book ID.")
        return BookDownload(entry.book, BookFormat.EPUB, http.readEpub(entry.epub))
    }

    private fun entries(): List<Entry> {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            factory.newDocumentBuilder().parse(ByteArrayInputStream(http.get(catalog)))
        } catch (failure: SAXException) {
            throw SourceFailure("INVALID_SOURCE_RESPONSE", "$name did not return a valid Atom feed.", failure)
        }
        val root = document.documentElement
        if (root.namespaceURI != ATOM || root.localName != "feed") {
            throw SourceFailure("INVALID_SOURCE_RESPONSE", "$name did not return an OPDS 1 acquisition feed.")
        }
        val nodes = root.getElementsByTagNameNS(ATOM, "entry")
        return (0 until nodes.length).mapNotNull { index -> (nodes.item(index) as Element).toEntry() }
            .distinctBy { it.book.sourceBookId }
    }

    private fun Element.toEntry(): Entry? {
        val title = atomText("title")
        val remoteId = atomText("id")
        if (title.isNullOrBlank() || remoteId.isNullOrBlank()) return null
        val links = getElementsByTagNameNS(ATOM, "link")
        val epub = (0 until links.length).asSequence()
            .map { links.item(it) as Element }
            .filter { it.getAttribute("type").substringBefore(';').trim() == "application/epub+zip" }
            .filter { it.getAttribute("rel") in PUBLIC_ACQUISITIONS }
            .mapNotNull { resolve(it.getAttribute("href")) }
            .firstOrNull()
        return epub?.let { file ->
            val digest = MessageDigest.getInstance("SHA-256").digest(remoteId.encodeToByteArray())
            val itemId = HexFormat.of().formatHex(digest)
            val author = (getElementsByTagNameNS(ATOM, "author").item(0) as? Element)?.atomText("name").orEmpty()
            Entry(book(itemId, title, catalog.toString()), author, file)
        }
    }

    private fun Element.atomText(tag: String): String? = getElementsByTagNameNS(ATOM, tag).item(0)?.textContent?.trim()

    private fun resolve(href: String): URI? {
        if (href.isBlank()) return null
        return try {
            catalog.resolve(href).takeIf { it.scheme == "https" && it.host != null && it.userInfo == null }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private data class Entry(val book: SourceBook, val author: String, val epub: URI)

    private companion object {
        const val ATOM = "http://www.w3.org/2005/Atom"
        val PUBLIC_ACQUISITIONS = setOf(
            "http://opds-spec.org/acquisition",
            "http://opds-spec.org/acquisition/open-access",
        )
    }
}
