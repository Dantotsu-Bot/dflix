package eu.kanade.tachiyomi.animeextension.en.dflix

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import org.jsoup.nodes.Document
import uy.kohesive.injekt.api.get

class Dflix : AnimeCatalogueSource {

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

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val request = GET("$baseUrl/m/recent/$page", headers = cHeaders)
        val response = client.newCall(request).execute()
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

    // =========================== Anime Details ============================

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val request = GET(anime.url, headers = cHeaders)
        val response = client.newCall(request).execute()
        val document = response.asJsoup()

        val type = getMediaType(document) ?: throw IllegalArgumentException("Unknown media type")

        return when (type) {
            "m" -> getMovieDetails(document)
            "s" -> getSeriesDetails(document)
            else -> throw IllegalArgumentException("Unsupported media type: $type")
        }
    }

    private fun getMediaType(document: Document): String? {
        val script = document.select("script")
        val scriptContent = script.html()
        return when {
           (scriptContent.contains("\"/m/lazyload/")) -> "m"
           (scriptContent.contains("\"/s/lazyload/")) -> "s"
           else -> null
        }
    }

    private fun getMovieDetails(document: Document): SAnime {
        return SAnime.create().apply {
            status = 2
            val thumbString = document.select("figure.movie-detail-banner img").attr("src")
            thumbnail_url = thumbString.replace(" ", "%20")
            val genreElements = document.select("div.ganre-wrapper a")
            val genreList = genreElements.map { it.text().replace(",", "").trim() }
            genre = genreList.joinToString(", ")
            description = document.select("p.storyline").text().trim()
        }
    }

    private fun getSeriesDetails(document: Document): SAnime {
        return SAnime.create().apply {
            status = 0
            val thumbString = document.select("div.movie-detail-banner img").attr("src")
            thumbnail_url = thumbString.replace(" ", "%20")
            val genreElements = document.select("div.ganre-wrapper a")
            val genreList = genreElements.map { it.text().replace(",", "").trim() }
            genre = genreList.joinToString(", ")
            description = document.select("p.storyline").text().trim()
        }
    }

    // ============================== Episodes ==============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = TODO()

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> = TODO()
}
