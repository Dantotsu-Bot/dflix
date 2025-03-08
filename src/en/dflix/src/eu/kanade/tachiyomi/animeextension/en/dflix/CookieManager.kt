package eu.kanade.tachiyomi.animeextension.en.dflix

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object CookieManager {
    private val cookieUrl = "https://dflix.discoveryftp.net/login/demo".toHttpUrl()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .build()

    private var cookies: List<Cookie>? = null

    private fun fetchCookies(): List<Cookie> {
        if (cookies != null) return cookies!!

        val request = Request.Builder().url(cookieUrl).build()
        cookies = try {
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

        return cookies!!
    }

    fun getCookiesHeaders(): String = fetchCookies().joinToString("; ") { "${it.name}=${it.value}" }
}
