package eu.kanade.tachiyomi.lib.buzzheavierextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parseAs
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class BuzzheavierExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
) {

    companion object {
        private val SIZE_REGEX = Regex("""Size\s*-\s*([^|]+)""")
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun videosFromUrl(url: String, prefix: String = "Buzzheavier - ", proxyUrl: String? = null): List<Video> {
        val httpUrl = url.toHttpUrl()
        val id = httpUrl.pathSegments.first()

        val dlHeaders = headers.newBuilder().apply {
            add("Accept", "*/*")
            add("Accept-Encoding", "gzip, deflate, br, zstd")
            add("HX-Current-URL", url)
            add("HX-Request", "true")
            add("Priority", "u=1, i")
            add("Referer", url)
        }.build()

        val videoHeaders = headers.newBuilder().apply {
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            add("Accept-Encoding", "gzip, deflate, br, zstd")
            add("Accept-Language", "en-US,en-GB;q=0.9,en;q=0.8")
            add("Priority", "u=0, i")
            add("Referer", url)
        }.build()

        val doc = client.newCall(GET(url, dlHeaders)).execute().asJsoup()
        val selected = doc.select("li").firstOrNull { it.text().contains("Details:") }
        val size = SIZE_REGEX.find(selected?.text().orEmpty())?.groupValues?.getOrNull(1)?.trim() ?: "Unknown"

        val downloadRequest = GET("https://${httpUrl.host}/$id/download", dlHeaders)
        val path = executeWithRetry(downloadRequest, 5, 204).headers["hx-redirect"].orEmpty()

        return if (path.isNotEmpty()) {
            val videoUrl = if (path.startsWith("http")) path else "https://${httpUrl.host}$path"
            listOf(Video(videoUrl, "${prefix}${size}", videoUrl, videoHeaders))
        } else if (proxyUrl?.isNotEmpty() == true) {
            val videoUrl = executeWithRetry(GET(proxyUrl + id), 5, 200).parseAs<UrlDto>().url
            listOf(Video(videoUrl, "${prefix}${size}", videoUrl, videoHeaders))
        } else {
            emptyList()
        }
    }

    private fun getSize(url: String, headers: Headers): String {
        val response = executeWithRetry(GET(url, headers), 3, 200)
        response.use {
            val size = it.header("Content-Length")?.toDoubleOrNull()
            if (size != null) {
                return formatBytes(size)
            }
        }
        return "Unknown"
    }

    private fun executeWithRetry(request: Request, maxRetries: Int, validCode: Int): Response {
        var response: Response? = null

        for (attempt in 0 until maxRetries) {
            response?.close()
            response = client.newCall(request).execute()

            if (response.code == validCode) {
                return response
            }

            if (attempt < maxRetries - 1) {
                Thread.sleep(1000)
            }
        }
        return response!!
    }

    private fun formatBytes(bytes: Double) = when {
            bytes >= 1 shl 30 -> "%.1f GB".format(bytes / (1 shl 30))
            bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1 shl 20))
            bytes >= 1 shl 10 -> "%.0f kB".format(bytes / (1 shl 10))
            else -> "$bytes bytes"
        }

    @Serializable
    data class UrlDto(val url: String)
}
