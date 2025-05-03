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

        val path = executeWithRetry(
            request = GET("https://${httpUrl.host}/$id/download", dlHeaders),
            5, listOf(204)).headers["hx-redirect"].orEmpty()

        return if (path.isNotEmpty()) {
            val videoUrl = if (path.startsWith("http")) path else "https://${httpUrl.host}$path"
            val size = getSize(videoUrl, videoHeaders)
            listOf(Video(videoUrl, "${prefix}${size}", videoUrl, videoHeaders))
        } else if (proxyUrl?.isNotEmpty() == true) {
            val videoUrl = executeWithRetry(GET(proxyUrl + id), 5, listOf(200)).parseAs<UrlDto>().url
            val size = getSize(videoUrl, videoHeaders)
            listOf(Video(videoUrl, "${prefix}${size}", videoUrl, videoHeaders))
        } else {
            emptyList()
        }
    }

    private fun getSize(url: String, headers: Headers): String {
        val response = executeWithRetry(
            request = GET(url, headers), 3, listOf(200))
        response.use {
            val size = it.header("Content-Length")?.toLongOrNull()
            if (size != null) {
                return formatBytes(size)
            }
        }
        return "Unknown"
    }

    private fun executeWithRetry(request: Request, maxRetries: Int, validCodes: List<Int>): Response {
        var retries = 0
        var lastResponse: Response? = null

        while (retries < maxRetries) {
            if (lastResponse != null) {
                lastResponse.close()
            }
            lastResponse = client.newCall(request).execute()
            if (lastResponse.code in validCodes) {
                return lastResponse
            }
            retries++
            if (retries < maxRetries) {
                Thread.sleep(1000)
            }
        }
        return lastResponse!!
    }

    private fun formatBytes(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1] + "B"
        return String.format("%.2f %s", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }

    @Serializable
    data class UrlDto(val url: String)
}
