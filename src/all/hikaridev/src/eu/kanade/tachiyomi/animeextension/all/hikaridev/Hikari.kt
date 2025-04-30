package eu.kanade.tachiyomi.animeextension.all.hikaridev

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.lib.chillxextractor.ChillxExtractor
import eu.kanade.tachiyomi.lib.filemoonextractor.FilemoonExtractor
import eu.kanade.tachiyomi.lib.streamwishextractor.StreamWishExtractor
import eu.kanade.tachiyomi.lib.vidhideextractor.VidHideExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.parseAs
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Hikari : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Hikari_Dev"

    override val baseUrl = "https://hikari.gg"

    private val apiUrl = "https://api.hikari.gg"

    override val lang = "all"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Origin", baseUrl)
        add("Referer", "$baseUrl/")
    }

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$apiUrl/api/anime/upcoming/?page=$page")

    override fun popularAnimeParse(response: Response): AnimesPage {
        val parsed = response.parseAs<PopularResponse>()

        val hasNextPage = false
        val animeList = parsed.results.map { it.toSAnime() }

        return AnimesPage(animeList, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiUrl/api/episode/new/?limit=300&language=EN")

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val parsed = response.parseAs<RecentResponse>()

        val animeList = parsed.results.map {
            SAnime.create().apply {
                url = it.uid.toString()
                title = it.title_en ?: it.title
                thumbnail_url = it.imageUrl
            }
        }

        return AnimesPage(animeList, false)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        TODO()
    }

    override fun searchAnimeParse(response: Response): AnimesPage =
        TODO()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Note: text search ignores filters"),
        AnimeFilter.Separator(),
        TypeFilter(),
        CountryFilter(),
        StatusFilter(),
        RatingFilter(),
        SourceFilter(),
        SeasonFilter(),
        LanguageFilter(),
        SortFilter(),
        AiringDateFilter(),
        GenreFilter(),
    )

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request =
        GET("$apiUrl/api/anime/uid/${anime.url}/")

    override fun animeDetailsParse(response: Response): SAnime {
        val parsed = response.parseAs<AnimeDTO>()

        return parsed.toSAnime()
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request =
        GET("$apiUrl/api/episode/uid/${anime.url}/")

    override fun episodeListParse(response: Response): List<SEpisode> {
        val uid = response.request.url.pathSegments[3]

        return episodes.map { ep ->
            SEpisode.create().apply {
                url = "/$uid/${ep.ep_id_name}/"
                name = "${ep.ep_id_number} - ${ep.ep_name}"
                episode_number = ep.ep_id_name.toFloatOrNull() ?: 0f
            }
        }.asReversed()
    }

    // ============================ Video Links =============================

    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val chillxExtractor by lazy { ChillxExtractor(client, headers) }
    private val streamwishExtractor by lazy { StreamWishExtractor(client, headers) }
    private val embedRegex = Regex("""getEmbed\(\s*(\d+)\s*,\s*(\d+)\s*,\s*'(\d+)'""")

    override fun videoListRequest(episode: SEpisode): Request =
        return GET("$apiUrl/api/embed/${episode.url}")

    override fun videoListParse(response: Response): List<Video> {
        TODO()
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!

        return sortedWith(
            compareBy(
                { it.quality.contains(quality) },
                { QUALITY_REGEX.find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
            ),
        ).reversed()
    }

    // ============================= Utilities ==============================

    private fun AnimeDTO.toSAnime(): SAnime {
        return SAnime.create().apply {
            url = uid
            title = ani_ename.ifEmpty { ani_name }
            artist = ani_studio
            author = ani_producers
            description = ani_synopsis
            genre = ani_genre
            status = when (ani_stats) {
                2 -> SAnime.COMPLETED
                3 -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }
            thumbnail_url = ani_poster
            initialized = true
        }
    }

    override fun getAnimeUrl(anime: SAnime): String {
        return "$baseUrl/info/${anime.url}"
    }

    override fun getEpisodeUrl(episode: SEpisode): String {
        return "$baseUrl/watch/${episode.url}"
    }

    companion object {
        private val QUALITY_REGEX = Regex("""(\d+)p""")

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "1080"
        private val PREF_QUALITY_VALUES = arrayOf("1080", "720", "480", "360")
        private val PREF_QUALITY_ENTRIES = PREF_QUALITY_VALUES.map {
            "${it}p"
        }.toTypedArray()
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)
    }
}
