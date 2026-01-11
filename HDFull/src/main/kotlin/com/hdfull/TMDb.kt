package com.hdfull

import android.util.Log
import com.lagradost.cloudstream3.*
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlin.collections.getOrNull

open class TMDb(lang: String? = null) {
    // API_KEY de TMDb
    private val API_KEY = BuildConfig.TMDB_API
    private val LANG = lang ?: "es-MX"

    suspend fun getDetails(
        id: String?,
        type: TvType
    ): DetailsResponse? {
        val tmdbType = if (type == TvType.Movie) "movie" else "tv"
        val params = mapOf(
            "api_key" to API_KEY,
            "language" to LANG,
            "append_to_response" to "credits,external_ids,videos,production_countries,alternative_titles"
        )

        var tmdbID = id.toString()
        if (tmdbID.startsWith("tt")){
            tmdbID = getTMDbID_From_IMDbID(tmdbID, type) ?: return null
        }

        return try {
            val tmdbUrl = "https://api.themoviedb.org/3/$tmdbType/$tmdbID"
            val tmdbRes = app.get(tmdbUrl, params = params).parsedSafe<Response>()
                ?: return null

            val genres = tmdbRes.genres?.mapNotNull { it.name } ?: emptyList()
            val release_date = if (type == TvType.Movie) tmdbRes.release_date else tmdbRes.first_Air_Date
            val trailers = tmdbRes.videos?.results?.filter { it.type == "Trailer" }
                ?.map { "https://www.youtube.com/watch?v=${it.key}" }
                ?: emptyList()
            val isCartoon = genres.contains("Animation")
            val isAnime = isCartoon && (tmdbRes.original_language == "zh" || tmdbRes.original_language == "ja")
            val isAsian = !isAnime && (tmdbRes.original_language == "zh" || tmdbRes.original_language == "ko")

            DetailsResponse(
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
                nextAiring = tmdbRes.next_episode_to_air,
                runtime = tmdbRes.runtime,
                status = tmdbRes.status,
                plot = tmdbRes.overview?.takeIf { it.isNotBlank() },
                score = tmdbRes.vote_average,
                posterUrl = fixUrl(tmdbRes.poster_path),
                backgroundUrl = fixUrl(tmdbRes.backdrop_path),
                genres = genres,
                actors = tmdbRes.credits?.cast?.mapNotNull { cast ->
                    val castName = cast.name ?: cast.original_name ?: return@mapNotNull null
                    ActorData(
                        Actor(castName, fixUrl(cast.profilePath)), roleString = cast.character
                    )
                } ?: emptyList(),
                episodes = if (type != TvType.Movie && tmdbRes.seasons != null) getEpisodesList(tmdbID, tmdbRes.seasons) else null,
                trailers = trailers
            )
        } catch (e: Exception) {
            null
        }
    }

    // Obtiene la ID de TMDb desde una ID IMDb
    suspend fun getTMDbID_From_IMDbID(IMDb_ID: String, type: TvType): String? {
        if (!IMDb_ID.startsWith("tt")) return null
        val tmdbUrl = "https://api.themoviedb.org/3/find/$IMDb_ID"
        val params = mapOf(
            "api_key" to API_KEY,
            "external_source" to "imdb_id"
        )

        return try {
            val findResponse = app.get(tmdbUrl, params = params).parsedSafe<FindResponse>()
            if (type == TvType.Movie) {
                findResponse?.movieResults?.getOrNull(0)?.id?.toString()
            } else {
                findResponse?.tvResults?.getOrNull(0)?.id?.toString()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Obtiene una Lista de Episodios de una Serie.
    private suspend fun getEpisodesList(
        TMDb_ID: String,
        season: ArrayList<Response.Seasons>
    ): ArrayList<SeasonResponse.Episodes>? {
        val params = mapOf(
            "api_key" to API_KEY,
            "language" to LANG
        )

        return try {
            val allEpisodes = arrayListOf<SeasonResponse.Episodes>()

            // Usar coroutineScope para lanzar las solicitudes en paralelo
            coroutineScope {
                val deferredEpisodes = season
                    .filter { it.seasonNumber != 0 } // Filtrar temporadas especiales
                    .map { temp ->
                        async {
                            val seasonUrl = "https://api.themoviedb.org/3/tv/$TMDb_ID/season/${temp.seasonNumber}"
                            val seasonRes = app.get(seasonUrl, params = params).parsedSafe<SeasonResponse>()
                            seasonRes?.episodes?.map { ep ->
                                ep.copy(still_path = fixUrl(ep.still_path))
                            }
                        }
                    }

                // Esperar a que todas las solicitudes terminen y recolectar los resultados
                deferredEpisodes.forEach { deferred ->
                    deferred.await()?.let { episodes ->
                        allEpisodes.addAll(episodes)
                    }
                }
            }

            allEpisodes
        } catch (e: Exception) {
            Log.e("TMDb", "${e.printStackTrace()}") // O usar un logger
            null
        }
    }

    // Función auxiliar para construir URLs de imágenes de TMDb.
    private fun fixUrl(path: String?): String? {
        return path?.let {
            when {
                it.startsWith("/") -> "https://image.tmdb.org/t/p/original$it"
                else -> it // devolver el path tal cual (asumiendo que ya es una URL completa)
            }
        }
    }

    //-------------------------------------//
    //              data class             //
    //-------------------------------------//
    // Class Para la respuesta de la fun getMeta_TMDb
    data class DetailsResponse(
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
        val episodes: List<SeasonResponse.Episodes>?,
        val nextAiring: Response.NextEpisode?,
        val trailers: List<String>?,
        val isBollywood: Boolean?,
        val isCartoon: Boolean?,
        val isAnime: Boolean?,
        val isAsian: Boolean?
    )

    // Class Para Response
    data class Response(
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
        @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @JsonProperty("videos") val videos: ResultTrailer? = null,
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = null,
        @JsonProperty("next_episode_to_air") val next_episode_to_air: NextEpisode? = null,
        @JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null,
        @JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf()
    ) {
        data class Genres(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
        )

        // videos
        data class ResultTrailer(
            @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
        ) {
            data class Trailers(
                @JsonProperty("key") val key: String? = null,
                @JsonProperty("type") val type: String? = null,
            )
        }

        // External_ids
        data class ExternalIds(
            @JsonProperty("imdb_id") val imdb_id: String? = null,
            @JsonProperty("tvdb_id") val tvdb_id: Int? = null,
        )

        // cast
        data class Credits(
            @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
        ) {
            data class Cast(
                @JsonProperty("id") val id: Int? = null,
                @JsonProperty("name") val name: String? = null,
                @JsonProperty("original_name") val original_name: String? = null,
                @JsonProperty("character") val character: String? = null,
                @JsonProperty("profile_path") val profilePath: String? = null,
            )
        }

        // seasons
        data class Seasons(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("air_date") val airDate: String? = null,
            @JsonProperty("season_number") val seasonNumber: Int? = null,
        )

        // next_episode_to_air
        data class NextEpisode(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("overview") val overview: String? = null,
            @JsonProperty("air_date") val air_date: String? = null,
            @JsonProperty("episode_number") val episode_number: Int? = null,
            @JsonProperty("episode_type") val episode_type: String? = null,
            @JsonProperty("season_number") val season_number: Int? = null,
        )

        // alternative_titles
        data class ResultsAltTitles(
            @JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
        ) {
            data class AltTitles(
                @get:JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
                @get:JsonProperty("title") val title: String? = null,
                @get:JsonProperty("type") val type: String? = null,
            )
        }

        // production_countries
        data class ProductionCountries(
            @JsonProperty("name") val name: String? = null,
        )
    }

    // Class Para FindResponse
    data class FindResponse(
        @JsonProperty("movie_results") val movieResults: List<FindResult>?,
        @JsonProperty("tv_results") val tvResults: List<FindResult>?
    ) {
        data class FindResult(
            @JsonProperty("id") val id: String?
        )
    }

    // Class Para SeasonResponse
    data class SeasonResponse(
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>?
    ) {
        data class Episodes(
            @JsonProperty("name") val name: String?,
            @JsonProperty("season_number") val season: Int?,
            @JsonProperty("episode_number") val episode: Int?,
            @JsonProperty("overview") val overview: String?,
            @JsonProperty("air_date") val air_date: String?,
            @JsonProperty("runtime") val runtime: Int?,
            @JsonProperty("vote_average") val vote_average: Double?,
            @JsonProperty("still_path") val still_path: String?,
        )
    }
}