package eu.kanade.tachiyomi.animeextension.all.dflix

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class CookieManager {
    private val cookieUrl = "https://dflix.discoveryftp.net/login/demo".toHttpUrl()

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .build()

    @Volatile
    private var cookies: List<Cookie>? = null

    private fun getCookiesSafe(): List<Cookie> {
        return cookies ?: synchronized(this) {
            cookies ?: fetchCookies().also { cookies = it }
        }
    }

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
            throw Exception("Failed to get cookies: ${e.message}")
        }
    }

    fun getCookiesHeaders(): String = getCookiesSafe().joinToString("; ") { "${it.name}=${it.value}" }
}
