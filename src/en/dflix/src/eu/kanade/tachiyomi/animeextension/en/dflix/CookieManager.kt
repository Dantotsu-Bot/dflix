package eu.kanade.tachiyomi.animeextension.en.dflix

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class CookieManager {

    private val cookieUrl = "https://dflix.discoveryftp.net/login/demo".toHttpUrl()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .build()
    }

    private val cookies: List<Cookie> by lazy { fetchCookies() }

    private fun fetchCookies(): List<Cookie> {
        val request = Request.Builder().url(cookieUrl).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    response.headers("Set-Cookie").mapNotNull { Cookie.parse(cookieUrl, it) }
                } else {
                    emptyList()
                }
            }
        } catch (e: IOException) {
            println("Failed to fetch cookies: ${e.message}")
            emptyList()
        }
    }

    fun getCookiesHeaders(): String = cookies.joinToString("; ") { "${it.name}=${it.value}" }
}
