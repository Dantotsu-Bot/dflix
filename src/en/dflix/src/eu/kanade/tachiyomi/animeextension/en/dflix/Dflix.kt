package eu.kanade.tachiyomi.animeextension.en.dflix

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.api.get

class Dflix : AnimeCatalogueSource, AnimeHttpSource() {

    override val name = "Dflix"

    override val baseUrl = "https://dflix.discoveryftp.net"

    override val lang = "en"

    override val supportsLatest = true

    val cm = CookieManager()
    val cookieHeader = cm.getCookiesHeaders()

    // ============================== Popular ===============================

    override suspend fun getPopularAnime(page: Int): AnimesPage = getLatestUpdates(page)

    override fun popularAnimeParse(response: Response): AnimesPage = TODO()

    override fun popularAnimeRequest(page: Int): Request = TODO()

    // =============================== Latest ===============================

    override suspend fun getLatestAnime(page: Int): AnimesPage {
        val req = Request.Builder()
            .url("$baseUrl/m/recent/$page")
            .addHeader("Cookie", cookieHeader)
            .build()

        val calledData = client.newCall(req).execute()
        val document = calledData.use { it.asJsoup() }

        val animeList = document.select("div.card a.cfocus").map { element ->
            val card = element.parent()
            SAnime.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                thumbnail_url = element.selectFirst("img")!!.attr("src")
                title = card.selectFirst("div.details h3")!!.text()
            }
        }
        return AnimesPage(animeList, hasNextPage = true)
    }

    override fun latestAnimeParse(response: Response): AnimesPage = TODO()

    override fun latestAnimeRequest(page: Int): Request = TODO()

    // =============================== Search ===============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        TODO()
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        TODO()

    override fun searchAnimeParse(response: Response): AnimesPage = TODO()

    // =========================== Anime Details ============================
    override fun getAnimeUrl(anime: SAnime): String = TODO()

    override fun animeDetailsRequest(anime: SAnime): Request = TODO()

    override fun animeDetailsParse(response: Response): SAnime = TODO()

    // ============================== Episodes ==============================

    private fun episodesRequest(totalEpisodes: String, id: String): List<SEpisode> {
        val request = GET("localhost", headers)
        val epResponse = client.newCall(request).execute()
        val document = epResponse.asJsoup()
        return document.select("a").map(::episodeFromElement)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val totalEpisodes = document.select(episodeListSelector()).last()!!.attr("ep_end")
        val id = document.select("input#movie_id").attr("value")
        return episodesRequest(totalEpisodes, id)
    }

    override fun episodeListSelector() = "ul#episode_page li a"

    override fun episodeFromElement(element: Element): SEpisode {
        val ep = element.selectFirst("div.name")!!.ownText().substringAfter(" ")
        return SEpisode.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            episode_number = ep.toFloat()
            name = "Episode $ep"
        }
    }

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()

    private fun getHosterVideos(className: String, serverUrl: String): List<Video> {
        return emptyList()
    }

    override fun videoListSelector() = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================= Utilities ==============================
    private fun Document.getInfo(text: String): String? {
        val base = selectFirst("p.type:has(span:containsOwn($text))") ?: return null
        return base.select("a").eachText().joinToString("")
            .ifBlank { base.ownText() }
            .takeUnless(String::isBlank)
    }

    override fun List<Video>.sort(): List<Video> = throw UnsupportedOperationException()

    private fun parseStatus(statusString: String): Int {
        return when (statusString) {
            "Ongoing" -> SAnime.ONGOING
            "Completed" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }
}
