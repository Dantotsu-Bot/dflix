package eu.kanade.tachiyomi.animeextension.all.hikaridev

import kotlinx.serialization.Serializable

@Serializable
data class PopularResponse(
    val results: List<AnimeDTO>,
    val count: Int,
)

@Serializable
data class AnimeDTO(
    val id: Int,
    val ani_release: Int?,
    val view_count: Int,
    val view_count_month: Int,
    val view_count_years: Int,
    val ani_score: Double? = null,
    val uid: String,
    val ani_name: String,
    val ani_jname: String,
    val ani_synonyms: String,
    val ani_genre: String,
    val ani_type: Int,
    val ani_country: String,
    val ani_stats: Int,
    val ani_source: String,
    val ani_ep: String,
    val ani_synopsis: String,
    val ani_poster: String,
    val ani_release_season: Int,
    val ani_rate: String,
    val ani_quality: String,
    val ani_time: String,
    val ani_pv: String,
    val ani_aired: String,
    val ani_aired_fin: String,
    val ani_studio: String,
    val ani_producers: String,
    val ani_manga_url: String,
    val created_at: String,
    val updated_at: String,
    val ani_ename: String,
)

@Serializable
data class RecentResponse(
    val results: List<RecentDTO>,
    val count: Int,
)

@Serializable
data class RecentDTO(
    val uid: Int,
    val ep_id_name: String,
    val ep_name: String,
    val created_at: String,
    val title: String,
    val title_en: String?,
    val imageUrl: String,
)

@Serializable
data class EpisodeDTO(
    val ep_id_name: String,
    val ep_name: String,
)

@Serializable
data class VideoDTO(
    val embed_type: String,
    val embed_name: String,
    val embed_frame: String,
)
