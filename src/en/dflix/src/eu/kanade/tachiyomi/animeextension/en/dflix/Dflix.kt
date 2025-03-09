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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
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

    override fun animeDetailsParse(response: Response): SAnime {
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

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = withContext(Dispatchers.IO) {
        val request = GET(anime.url, headers = cHeaders)
        val response = client.newCall(request).execute().asJsoup()
        val document = response.asJsoup()

        val type = getMediaType(document) ?: throw IllegalArgumentException("Unknown media type")

        if (type == "m") {
            getMovieMedia(document)
        } else {
            val seasonLinks = document.select("tbody tr th.card a[href^='/s/view/']")
                .map { it.attr("href") }
                .reversed()

            coroutineScope {
                seasonLinks.map { link ->
                    async {
                        val seasonRequest = GET("$baseUrl$link", headers = cHeaders)
                        val seasonResponse = client.newCall(seasonRequest).execute()
                        extractEpisode(seasonResponse.asJsoup())
                    }
                }.awaitAll().flatten().let { sortEpisodes(it) }
            }
        }
    }

    override fun episodeListRequest(anime: SAnime): Request = TODO()

    override fun episodeListParse(response: Response): List<SEpisode> = TODO()

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return listOf(
            Video(
                url = episode.url ?: "",
                videoUrl = episode.url ?: "",
                quality = episode.scanlator ?: "",
            ),
        )
    }

    // ============================= Utilities ==============================

    private fun getMovieMedia(document: Document): List<SEpisode> {
        val videoLink = document.select("div.col-md-12 a.btn").last()?.attr("href")?.toString()?.replace(" ", "%20")
        val qualitySize = document.select(".badge-wrapper .badge-fill").lastOrNull()?.text()?.replace("|", "•")

        return listOf(
            SEpisode.create().apply {
                url = videoLink ?: ""
                name = "Movie"
                episode_number = 1.0f
                scanlator = qualitySize ?: ""
            },
        )
    }

    private fun extractEpisode(document: Document): List<EpisodeData> {
        val episodeList = mutableListOf<EpisodeData>()

        val episodeContainers = document.select("div.container > div > div.card")

        episodeContainers.forEach { container ->
            val rawSeasonEpisode = container.select("h5").first()?.ownText()?.trim() ?: ""
            val seasonEpisode = rawSeasonEpisode.split("&nbsp;").first().trim() ?: ""
            val videoUrl = container.select("h5 a").attr("href").trim() ?: ""
            val size = container.select("h5 .badge-fill").text()
                .replace(Regex(".*\\s(\\d+\\.\\d+\\s+MB)$"), "$1")
                .trim()
            val episodeName = container.select("h4").first()?.ownText()?.trim() ?: ""
            val quality = container.select("h4 .badge-outline").first()?.text()?.trim() ?: ""

            if (seasonEpisode.isNotEmpty() && videoUrl.isNotEmpty()) {
                episodeList.add(
                    EpisodeData(
                        seasonEpisode = seasonEpisode,
                        videoUrl = videoUrl,
                        size = size,
                        episodeName = episodeName,
                        quality = quality,
                    ),
                )
            }
        }
        return episodeList
    }

    private fun sortEpisodes(episodes: List<EpisodeData>): List<SEpisode> {
        val result = mutableListOf<SEpisode>()
        var lastEpisode = 0
        var lastSeason = 0
        for (epInfo in episodes) {
            val seasonMatch = SEASON_PATTERN.find(epInfo.seasonEpisode)
            val episodeMatch = EPISODE_PATTERN.find(epInfo.seasonEpisode)
            val season = seasonMatch?.groupValues?.get(1)?.toInt() ?: lastSeason
            val episode = episodeMatch?.groupValues?.get(1)?.toInt()
            val episodeNumber = when {
                season == 0 && episode != null -> episode / 10f
                episode != null -> {
                    if (season > lastSeason) lastEpisode++
                    lastEpisode = if (season > lastSeason) lastEpisode else lastEpisode + 1
                    lastEpisode.toFloat()
                }
                else -> (++lastEpisode).toFloat()
            }
            result.add(
                SEpisode.create().apply {
                    url = epInfo.videoUrl ?: ""
                    name = epInfo.seasonEpisode + " " + epInfo.episodeName
                    episode_number = episodeNumber
                    scanlator = epInfo.quality + " • " + (epInfo.size ?: "")
                },
            )
            lastSeason = season
        }
        return result.reversed()
    }

    companion object {
        private val SEASON_PATTERN = Regex("S(\\d+)")
        private val EPISODE_PATTERN = Regex("EP (\\d+)")
    }

    data class EpisodeData(
        val seasonEpisode: String,
        val videoUrl: String,
        val size: String,
        val episodeName: String,
        val quality: String,
    )
}
