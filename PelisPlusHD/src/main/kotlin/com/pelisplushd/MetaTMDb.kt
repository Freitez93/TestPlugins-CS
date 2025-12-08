package com.pelisplushd

import com.lagradost.cloudstream3.*
import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.collections.getOrNull

//-------------------------------------//
//        MetaProvider TMDb            //
//-------------------------------------//
suspend fun getMeta_TMDb(id: String?, type: TvType): getMetaResponse? {
    val tmdbType = if (type == TvType.Movie) "movie" else "tv"
    val params = mapOf(
        "api_key" to BuildConfig.TMDB_API,
        "language" to "es-MX",
        "append_to_response" to "credits,external_ids,videos,production_countries,alternative_titles"
    )

    var tmdbID = id.toString()
    if (tmdbID.startsWith("tt")){
        tmdbID = getTMDbID_From_IMDbID(tmdbID, type) ?: return null
    }

    return try {
        val tmdbUrl = "https://api.themoviedb.org/3/$tmdbType/$tmdbID"
        val tmdbRes = app.get(tmdbUrl, params = params).parsedSafe<TMDbApiResponse>()
            ?: return null

        val genres = tmdbRes.genres?.mapNotNull { it.name } ?: emptyList()
        val release_date = if (type == TvType.Movie) tmdbRes.release_date else tmdbRes.first_Air_Date
        val trailers = tmdbRes.videos?.results?.filter { it.type == "Trailer" }
            ?.map { "https://www.youtube.com/watch?v=${it.key}" }
            ?: emptyList()
        val isCartoon = genres.contains("Animation")
        val isAnime = isCartoon && (tmdbRes.original_language == "zh" || tmdbRes.original_language == "ja")
        val isAsian = !isAnime && (tmdbRes.original_language == "zh" || tmdbRes.original_language == "ko")

        getMetaResponse(
            tmdb_id = tmdbRes.id?.toString(),
            imdb_id = tmdbRes.imdbId ?: tmdbRes.external_ids?.imdb_id,
            isBollywood = tmdbRes.production_countries?.any { it.name == "India"} ?: false,
            isCartoon = isCartoon,
            isAnime = isAnime,
            isAsian = isAsian,
            title = tmdbRes.title ?: tmdbRes.name,
            orgTitle = tmdbRes.original_title ?: tmdbRes.original_name,
            jpTitle = tmdbRes.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
            releaseDate = release_date,
            runtime = tmdbRes.runtime,
            status = tmdbRes.status,
            plot = tmdbRes.overview?.takeIf { it.isNotBlank() },
            score = tmdbRes.vote_average,
            posterUrl = getImageUrl(tmdbRes.poster_path),
            backgroundUrl = getImageUrl(tmdbRes.backdrop_path),
            genres = genres,
            actors = tmdbRes.credits?.cast?.mapNotNull { cast ->
                val castName = cast.name ?: cast.original_name ?: return@mapNotNull null
                ActorData(
                    Actor(castName, getImageUrl(cast.profilePath)), roleString = cast.character
                )
            } ?: emptyList(),
            episodes = if (type != TvType.Movie && tmdbRes.seasons != null) getEpisodesList(tmdbID, tmdbRes.seasons) else null,
            trailers = trailers
        )
    } catch (e: Exception) {
        null
    }
}

// Cambiar la ID de IMDb al TMDb ID
suspend fun getTMDbID_From_IMDbID(IMDb_ID: String, type: TvType): String? {
    if (!IMDb_ID.startsWith("tt")) return null
    val tmdbUrl = "https://api.themoviedb.org/3/find/$IMDb_ID"
    val params = mapOf(
        "api_key" to BuildConfig.TMDB_API,
        "external_source" to "imdb_id"
    )
    return try {
        val findResponse = app.get(tmdbUrl, params = params).parsedSafe<TMDbFindResponse>()
        if (type == TvType.Movie) {
            findResponse?.movieResults?.getOrNull(0)?.id?.toString()
        } else {
            findResponse?.tvResults?.getOrNull(0)?.id?.toString()
        }
    } catch (e: Exception) {
        null
    }
}

// Lista de Episodios
suspend fun getEpisodesList(TMDb_ID: String, season: ArrayList<TMDbSeasons>): ArrayList<TMDbApiSeasonRes.Episodes>? {
    val params = mapOf(
        "api_key" to BuildConfig.TMDB_API,
        "language" to "es-MX"
    )
    return try {
        val allEpisodes = arrayListOf<TMDbApiSeasonRes.Episodes>()
        season.forEach { temp ->
            if (temp.seasonNumber != 0) {
                val seasonUrl = "https://api.themoviedb.org/3/tv/$TMDb_ID/season/${temp.seasonNumber}"
                val seasonRes = app.get(seasonUrl, params = params).parsedSafe<TMDbApiSeasonRes>()
                seasonRes?.episodes?.let { episodes ->
                    allEpisodes.addAll(
                        episodes.map { ep ->
                            ep.copy(still_path = getImageUrl(ep.still_path))
                        }
                    )
                }
            }
        }
        allEpisodes
    } catch (e: Exception) {
        null
    }
}

// Funcion auxiliar para las imagenes.
private fun getImageUrl(link: String?): String? {
    if (link == null) return null
    return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
}

//-------------------------------------//
//              data class             //
//-------------------------------------//
// Class Para la respuesta de la fun getMeta_TMDb
data class getMetaResponse(
    val tmdb_id: String?,
    val imdb_id: String?,
    val title: String?,
    val orgTitle: String?,
    val jpTitle: String?,
    val releaseDate: String?,
    val status: String?,
    val plot: String?,
    val score: String?,
    val runtime: Int?,
    val posterUrl: String?,
    val backgroundUrl: String?,
    val genres: List<String>?,
    val actors: List<ActorData>?,
    val episodes: List<TMDbApiSeasonRes.Episodes>?,
    val trailers: List<String>?,
    val isBollywood: Boolean?,
    val isCartoon: Boolean?,
    val isAnime: Boolean?,
    val isAsian: Boolean?
)

// Class Para  TMDbApiResponse
data class TMDbApiResponse(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("imdb_id") val imdbId: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_title") val original_title: String? = null,
    @JsonProperty("original_name") val original_name: String? = null,
    @JsonProperty("original_language") val original_language: String? = null,
    @JsonProperty("poster_path") val poster_path: String? = null,
    @JsonProperty("backdrop_path") val backdrop_path: String? = null,
    @JsonProperty("release_date") val release_date: String? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("first_air_date") val first_Air_Date: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("vote_average") val vote_average: String? = null,
    @JsonProperty("genres") val genres: ArrayList<TMDbGenres>? = arrayListOf(),
    @JsonProperty("videos") val videos: TMDbResTrailer? = null,
    @JsonProperty("external_ids") val external_ids: TMDbExternalIds? = null,
    @JsonProperty("credits") val credits: TMDbCredits? = null,
    @JsonProperty("seasons") val seasons: ArrayList<TMDbSeasons>? = null,
    @JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null,
    @JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf()
)
data class TMDbGenres(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
)
data class TMDbResTrailer(
    @JsonProperty("results") val results: ArrayList<TMDbTrailers>? = arrayListOf(),
)
data class TMDbTrailers(
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
)
data class TMDbExternalIds(
    @JsonProperty("imdb_id") val imdb_id: String? = null,
    @JsonProperty("tvdb_id") val tvdb_id: Int? = null,
)
data class TMDbCredits(
    @JsonProperty("cast") val cast: ArrayList<TMDbCast>? = arrayListOf(),
)
data class TMDbCast(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_name") val original_name: String? = null,
    @JsonProperty("character") val character: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null,
)
data class TMDbSeasons(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
)
data class ResultsAltTitles(
    @JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
)
data class AltTitles(
    @get:JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
    @get:JsonProperty("title") val title: String? = null,
    @get:JsonProperty("type") val type: String? = null,
)
data class ProductionCountries(
    @JsonProperty("name") val name: String? = null,
)

// Class Para TMDbFindResponse
data class TMDbFindResponse(
    @JsonProperty("movie_results") val movieResults: List<MovieTvResult>?,
    @JsonProperty("tv_results") val tvResults: List<MovieTvResult>?
) {
    data class MovieTvResult(
        @JsonProperty("id") val id: String?
    )
}

// Class Para TMDbSeasonResponse
data class TMDbApiSeasonRes(
    @JsonProperty("episodes") val episodes: ArrayList<Episodes>?
){
    data class Episodes(
        @JsonProperty("name") val name: String?,
        @JsonProperty("season_number") val season: Int?,
        @JsonProperty("episode_number") val episode: Int?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("air_date") val air_date: String?,
        @JsonProperty("runtime") val runtime: Int?,
        @JsonProperty("still_path") val still_path: String?,
    )
}