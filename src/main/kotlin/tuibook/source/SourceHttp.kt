package tuibook.source

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

private const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024
private const val EPUB_MIMETYPE = "application/epub+zip"

internal class SourceHttp {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    fun get(uri: URI, maxBytes: Int = MAX_RESPONSE_BYTES): ByteArray {
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "Expected an HTTPS source URL." }
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "TUIBook/0.1 (terminal ebook reader)")
            .GET()
            .build()
        try {
            val response = send(request)
            return response.body().use { stream ->
                checkStatus(uri, response.statusCode())
                readLimited(stream, uri, maxBytes)
            }
        } catch (failure: SourceFailure) {
            throw failure
        } catch (failure: IOException) {
            throw SourceFailure("SOURCE_UNAVAILABLE", "Could not read a response from ${uri.host}.", failure)
        }
    }

    private fun send(request: HttpRequest): HttpResponse<InputStream> =
        try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SourceFailure("SOURCE_UNAVAILABLE", "Request to ${request.uri().host} was interrupted.", failure)
        }

    private fun checkStatus(uri: URI, status: Int) {
        if (status == HTTP_OK) return
        val code = if (status == HTTP_NOT_FOUND) "BOOK_NOT_FOUND" else "SOURCE_UNAVAILABLE"
        throw SourceFailure(code, "${uri.host} returned HTTP $status.")
    }

    private fun readLimited(stream: InputStream, uri: URI, maxBytes: Int): ByteArray {
        val bytes = stream.readNBytes(maxBytes + 1)
        if (bytes.size > maxBytes) {
            throw SourceFailure("DOWNLOAD_TOO_LARGE", "Response from ${uri.host} exceeds the size limit.")
        }
        return bytes
    }

    inline fun <reified T> readJson(uri: URI): T {
        try {
            return json.decodeFromString(get(uri).decodeToString())
        } catch (failure: SerializationException) {
            throw SourceFailure("INVALID_SOURCE_RESPONSE", "${uri.host} returned unexpected catalog data.", failure)
        }
    }

    fun readEpub(uri: URI): ByteArray {
        val bytes = get(uri)
        if (!ByteArrayInputStream(bytes).isEpub()) {
            throw SourceFailure("INVALID_DOWNLOAD", "${uri.host} did not return an EPUB file.")
        }
        return bytes
    }
}

internal fun InputStream.isEpub(): Boolean = try {
    ZipInputStream(this).use { zip ->
        zip.nextEntry?.name == "mimetype" && zip.readNBytes(EPUB_MIMETYPE.length + 1).decodeToString() == EPUB_MIMETYPE
    }
} catch (_: IOException) {
    false
}

internal fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
