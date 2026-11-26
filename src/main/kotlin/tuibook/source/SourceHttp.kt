package tuibook.source

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

internal class SourceHttp {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    fun get(uri: URI, maxBytes: Int = 32 * 1024 * 1024): ByteArray {
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "Expected an HTTPS source URL." }
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "TUIBook/0.1 (terminal ebook reader)")
            .GET()
            .build()
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            return response.body().use { stream ->
                when (response.statusCode()) {
                    200 -> Unit
                    404 -> throw SourceFailure("BOOK_NOT_FOUND", "${uri.host} returned HTTP 404.")
                    else -> throw SourceFailure("SOURCE_UNAVAILABLE", "${uri.host} returned HTTP ${response.statusCode()}.")
                }
                val bytes = stream.readNBytes(maxBytes + 1)
                if (bytes.size > maxBytes) throw SourceFailure("DOWNLOAD_TOO_LARGE", "Response from ${uri.host} exceeds the size limit.")
                bytes
            }
        } catch (failure: SourceFailure) {
            throw failure
        } catch (failure: IOException) {
            throw SourceFailure("SOURCE_UNAVAILABLE", "Could not read a response from ${uri.host}.", failure)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SourceFailure("SOURCE_UNAVAILABLE", "Request to ${uri.host} was interrupted.", failure)
        }
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
        zip.nextEntry?.name == "mimetype" && zip.readNBytes(21).decodeToString() == "application/epub+zip"
    }
} catch (_: IOException) {
    false
}

internal fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
