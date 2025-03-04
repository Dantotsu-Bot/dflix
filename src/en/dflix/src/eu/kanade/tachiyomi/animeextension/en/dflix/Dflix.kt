package eu.kanade.tachiyomi.animeextension.en.dflix

import android.app.Application
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parallelCatchingFlatMapBlocking
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Dflix : AnimeCatalogueSource, ParsedAnimeHttpSource() {

    override val name = "Dflix"

    override val baseUrl = "https://dflix.discoveryftp.net"

    override val lang = "en"

    override val supportsLatest = false

    val cm = CookieManager()
    val cookieHeader = cm.getCookiesHeaders()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Cookie", cookieHeaders)
        .add("Referer", "$baseUrl/")

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/m/recent/$page", headers)

    override fun popularAnimeSelector(): String = "div.card a.cfocus"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = element.selectFirst("img")!!.attr("src")
        title = element.selectFirst("h3")!!.text()
    }

    override fun popularAnimeNextPageSelector(): String = "div.card a.cfocus"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SAnime = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = throw UnsupportedOperationException()

    override fun searchAnimeSelector(): String = throw UnsupportedOperationException()

    override fun searchAnimeFromElement(element: Element): SAnime = throw UnsupportedOperationException()

    override fun searchAnimeNextPageSelector(): String = throw UnsupportedOperationException()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime {
        val infoDocument = document.selectFirst("div.anime-info a[href]")?.let {
            client.newCall(GET(it.absUrl("href"), headers)).execute().asJsoup()
        } ?: document

        return SAnime.create().apply {
            title = infoDocument.selectFirst("div.anime_info_body_bg > h1")!!.text()
            genre = infoDocument.getInfo("Genre:")
            status = parseStatus(infoDocument.getInfo("Status:").orEmpty())

            description = buildString {
                val summary = infoDocument.selectFirst("div.anime_info_body_bg > div.description")
                append(summary?.text())

                // add alternative name to anime description
                infoDocument.getInfo("Other name:")?.also {
                    if (isNotBlank()) append("\n\n")
                    append("Other name(s): $it")
                }
            }
        }
    }

    // ============================== Episodes ==============================
    private fun episodesRequest(totalEpisodes: String, id: String): List<SEpisode> {
        val request = GET("$AJAX_URL/load-list-episode?ep_start=0&ep_end=$totalEpisodes&id=$id", headers)
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

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val hosterSelection = preferences.getStringSet(PREF_HOSTER_KEY, PREF_HOSTER_DEFAULT)!!

        return document.select("div.anime_muti_link > ul > li").parallelCatchingFlatMapBlocking { server ->
            val className = server.className()
            if (!hosterSelection.contains(className)) return@parallelCatchingFlatMapBlocking emptyList()
            val serverUrl = server.selectFirst("a")
                ?.attr("abs:data-video")
                ?: return@parallelCatchingFlatMapBlocking emptyList()

            getHosterVideos(className, serverUrl)
        }
    }

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

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
        val server = preferences.getString(PREF_SERVER_KEY, PREF_SERVER_DEFAULT)!!

        return sortedWith(
            compareBy(
                { it.quality.contains(quality) },
                { Regex("""(\d+)p""").find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
                { it.quality.contains(server) },
            ),
        ).reversed()
    }

    private fun parseStatus(statusString: String): Int {
        return when (statusString) {
            "Ongoing" -> SAnime.ONGOING
            "Completed" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }
}
