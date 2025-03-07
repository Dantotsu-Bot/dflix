package eu.kanade.tachiyomi.animeextension.en.dflix

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.api.get

class Dflix : AnimeCatalogueSource, AnimeHttpSource() {

    override val name = "Dflix"

    override val baseUrl = "https://dflix.discoveryftp.net"

    override val lang = "en"

    override val supportsLatest = true

    private val cm by lazy { CookieManager() }

    private val cookieHeader by lazy { cm.getCookiesHeaders() }

    private val cHeaders: Headers by lazy {
        Headers.Builder().apply {
            add("Cookie", cookieHeader)
        }.build()
    }

    // ============================== Popular ===============================

    override suspend fun getPopularAnime(page: Int): AnimesPage = getLatestUpdates(page)

    override fun popularAnimeParse(response: Response): AnimesPage = TODO()

    override fun popularAnimeRequest(page: Int): Request = TODO()

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/m/recent/$page", headers = cHeaders)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeList = document.select("div.card a.cfocus").map { element ->
            val card = element.parent()
            SAnime.create().apply {
                url = baseUrl + element.attr("href")
                thumbnail_url = element.selectFirst("img")?.attr("src") ?: "localhost"
                val baseTitle = card?.selectFirst("div.details h3")?.text() ?: "Unknown"
                val posterElement = element.selectFirst("div.poster")
                val posterTitle = posterElement?.attr("title") ?: ""
                title = if (posterTitle.contains("4K", ignoreCase = true)) {
                    "$baseTitle 4K"
                } else {
                    baseTitle
                }
            }
        }
        return AnimesPage(animeList, hasNextPage = true)
    }

    // =============================== Search ===============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        suspend fun fetchAnimeByType(type: String): List<SAnime> = withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply {
                add("term", query)
                add("types", type)
            }.build()

            val request = POST("$baseUrl/search", headers = cHeaders, body = body)
            val response = client.newCall(request).execute()
            val document = response.asJsoup()

            val animeList = document.select("div.moviesearchiteam a").map { element ->
                val card = element.selectFirst("div.p-1")
                SAnime.create().apply {
                    url = baseUrl + element.attr("href")
                    thumbnail_url = element.selectFirst("img")?.attr("src") ?: "localhost"

                    val baseTitle = card?.selectFirst("div.searchtitle")?.text() ?: "Unknown"

                    // Only movies has 4k Quality tag
                    title = if (type == "m") {
                        val qualityText = card?.selectFirst("div.searchdetails")?.text() ?: ""
                        if (qualityText.contains("4K", ignoreCase = true)) {
                            "$baseTitle 4K"
                        } else {
                            baseTitle
                        }
                    } else {
                        baseTitle
                    }
                }
            }

            response.close()
            animeList
        }

        val (movies, series) = coroutineScope {
            val moviesDeferred = async { fetchAnimeByType("m") }
            val seriesDeferred = async { fetchAnimeByType("s") }
            Pair(moviesDeferred.await(), seriesDeferred.await())
        }
        val combinedResults = movies + series

        return AnimesPage(combinedResults, hasNextPage = false)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        TODO()

    override fun searchAnimeParse(response: Response): AnimesPage = TODO()

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        return GET(anime.url, headers = cHeaders)
    }

    override fun animeDetailsParse(response: Response): SAnime = TODO()

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = TODO()

    override fun episodeListParse(response: Response): List<SEpisode> = TODO()

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> = TODO()
}
