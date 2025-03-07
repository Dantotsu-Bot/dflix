package eu.kanade.tachiyomi.animeextension.en.dflix

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
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

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
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
                title = card.selectFirst("div.details h3")?.text() ?: "Unknown"
            }
        }
        return AnimesPage(animeList, hasNextPage = true)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage = TODO()

    override fun latestUpdatesRequest(page: Int): Request = TODO()

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

    override fun episodeListRequest(anime: SAnime): Request = TODO()

    override fun episodeListParse(response: Response): List<SEpisode> = TODO()

    // ============================ Video Links =============================

    override fun suspend getVideoList(episode: SEpisode): List<Video> = TODO()
}
