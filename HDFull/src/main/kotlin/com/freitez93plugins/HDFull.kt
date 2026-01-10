package com.freitez93plugins

import android.util.Base64
import android.util.Log
import android.content.SharedPreferences
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar

class HDFullProvider(sharedPref: SharedPreferences? = null) : MainAPI() {
    override var mainUrl = "https://hdfull.love"
    override var name = "HDFull"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    // Datos de Inicio de Sesion
    val guid = sharedPref?.getString("guid", null)
    val PHPSESSID = sharedPref?.getString("PHPSESSID", null)
    var latestCookie: Map<String, String> = mapOf(
        "language" to "es",
        "PHPSESSID" to "$PHPSESSID",
        "guid" to "$guid",
    )

    // -
    override val mainPage = if (guid != null) {
        mainPageOf(
            Pair("$mainUrl/peliculas-estreno", "Películas"),
            Pair("$mainUrl/lista/netflix-originals-peliculas-171", "(Películas) Netflix Originals"),
            Pair("$mainUrl/series", "Series"),
            Pair("$mainUrl/tags-tv/anime", "(Series) Animes"),
            Pair("$mainUrl/lista/series-netflix-originals-170", "(Series) Netflix Originals"),
            Pair("$mainUrl/lista/hbo-series-429", "(Series) HBO")
        )
    } else {
        mainPageOf(
            Pair("$mainUrl/login", "Inicia Sesión en Configuraciones")
        )
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val searchNum = if (request.data.contains("/lista")) "$page" else "date/$page"
        val searchURL = if (page > 1) "${request.data}/${searchNum}" else request.data

        val document = app.get(searchURL, cookies = latestCookie).documentLarge
        val hasNextPage = document.selectFirst("#filter > a:last-child")?.text() == "»"

        val home = document.select("div.center > div").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            list = listOf(HomePageList(request.name, home, request.name == "Últimos agregados")),
            hasNext = hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/buscar"
        val csfrDoc = app.post(url,
            cookies = latestCookie,
            referer = "$mainUrl/buscar",
            data = mapOf(
                "menu" to "search",
                "query" to query,
            )
        ).document

        val csfr = csfrDoc.selectFirst("input[value*='sid']")!!.attr("value")
        Log.d("HDFull", "search: $csfr")

        val doc = app.post(url,
            cookies = latestCookie,
            referer = "$mainUrl/buscar",
            data = mapOf(
                "__csrf_magic" to csfr,
                "menu" to "search",
                "query" to query,
            )
        ).document
    
        Log.d("HDFull", "search: $doc")
        return doc.select("div.span-6.tt.view").mapNotNull { 
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, cookies = latestCookie).document
        val title = document.selectFirst("meta[property='og:title']")!!.attr("content")
        val backimage = document.selectFirst("meta[name='twitter:image']")?.attr("content")
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")
        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
        val tags = document.select("a[itemprop=genre]").map { it.text() }
        val year = document.selectFirst("a[href*='/buscar/year']")?.text().let { it?.toIntOrNull() }
        val score = document.selectFirst("meta[itemprop='ratingValue']")?.attr("content")
        val imdb = document.selectFirst("a[href*=imdb]")?.attr("href")?.let { imdbUrlToId(it) }

        val isAnime = tags.find { it == "Anime" }
        val isType = if (!isAnime.isNullOrEmpty()) TvType.Anime else getType(url)
        val meta = getMeta_TMDb(imdb, isType)

        var episodes = if (isType != TvType.Movie) {
            val sid = document.html().substringAfter("var sid = '").substringBefore("';")
            document.select("div[itemprop=season]").flatMap { seasonDiv ->
                val seasonNumber = seasonDiv.selectFirst("a img")?.attr("original-title")
                    ?.substringAfter("Temporada")?.trim()?.toIntOrNull()
                val result = app.post(
                    "$mainUrl/a/episodes",
                    cookies = latestCookie,
                    data = mapOf(
                        "action" to "season",
                        "start" to "0",
                        "limit" to "0",
                        "show" to sid,
                        "season" to "$seasonNumber",
                    )
                )

                val episodesJson = AppUtils.parseJson<List<EpisodeJson>>(result.document.text())
                episodesJson.amap {
                    val episodeNumber = it.episode?.toIntOrNull()
                    val epTitle = it.title?.es?.trim() ?: "Episodio $episodeNumber"
                    val epurl = "$url/temporada-${it.season}/episodio-${it.episode}"

                    // Buscar el video que coincida con season y episode
                    val episodeInfo = meta?.episodes?.firstOrNull { 
                        it.season == seasonNumber && it.episode == episodeNumber 
                    }

                    val airDate = episodeInfo?.air_date ?: it.dateAired?.let { it.split(" ")[0] }
                    newEpisode(epurl){
                        addDate(airDate)
                        this.score = Score.from10(episodeInfo?.vote_average)
                        this.name = episodeInfo?.name ?: epTitle
                        this.description = episodeInfo?.overview
                        this.runTime = episodeInfo?.runtime
                        this.season = seasonNumber
                        this.episode = episodeNumber
                        this.posterUrl = episodeInfo?.still_path?.replace("/original/", "/w500/") 
                            ?: "https://hdfullcdn.cc/tthumb/220x124/${it.thumbnail}"
                    }
                }
            }
        } else listOf()

        return when (isType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(title, url, isType, episodes) {
                    addImdbId(meta?.imdb_id ?: imdb)
                    addTMDbId(meta?.tmdb_id)
                    addTrailer(meta?.trailers)
                    this.score = Score.from10(meta?.score ?: score)
                    this.posterUrl = meta?.posterUrl ?: poster
                    this.backgroundPosterUrl = meta?.backgroundUrl ?: backimage
                    this.plot = meta?.plot ?: description
                    this.tags = meta?.genres ?: tags
                    this.year = year
                    this.posterHeaders = mapOf("Referer" to "$mainUrl/")
                    this.showStatus = getStatus(meta?.status)
                    this.nextAiring = meta?.nextAiring?.let { nextAiring ->
                        val airDate = nextAiring.air_date ?: return@let null
                        val unixTime = runCatching {
                            LocalDate.parse(airDate).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                        }.getOrNull() ?: return@let null

                        NextAiring(
                            episode = nextAiring.episode_number ?: return@let null,
                            //season = nextAiring.season_number ?: return@let null,
                            unixTime = unixTime
                        )
                    }
                }
            }

            TvType.Anime -> {
                newAnimeLoadResponse(title, url, isType) {
                    addImdbId(meta?.imdb_id ?: imdb)
                    addTMDbId(meta?.tmdb_id)
                    addTrailer(meta?.trailers)
                    addEpisodes(DubStatus.None, episodes)
                    this.score = Score.from10(meta?.score ?: score)
                    this.posterUrl = meta?.posterUrl ?: poster
                    this.backgroundPosterUrl = meta?.backgroundUrl ?: backimage
                    this.plot = meta?.plot ?: description
                    this.tags = meta?.genres ?: tags
                    this.year = year
                    this.posterHeaders = mapOf("Referer" to "$mainUrl/")
                    this.showStatus = getStatus(meta?.status)
                    this.nextAiring = meta?.nextAiring?.let { nextAiring ->
                        val airDate = nextAiring.air_date ?: return@let null
                        val unixTime = runCatching {
                            LocalDate.parse(airDate).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                        }.getOrNull() ?: return@let null

                        NextAiring(
                            episode = nextAiring.episode_number ?: return@let null,
                            //season = nextAiring.season_number ?: return@let null,
                            unixTime = unixTime
                        )
                    }
                }
            }

            TvType.Movie -> {
                newMovieLoadResponse(title, url, isType, url) {
                    addImdbId(meta?.imdb_id ?: imdb)
                    addTMDbId(meta?.tmdb_id)
                    addTrailer(meta?.trailers)
                    this.score = Score.from10(meta?.score ?: score)
                    this.posterUrl = meta?.posterUrl ?: poster
                    this.backgroundPosterUrl = meta?.backgroundUrl ?: backimage
                    this.plot = meta?.plot ?: description
                    this.tags = meta?.genres ?: tags
                    this.year = year
                    this.posterHeaders = mapOf("Referer" to "$mainUrl/")
                }
            }

            else -> null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, cookies = latestCookie).document
        val hash = doc.select("script").firstOrNull { 
            it.html().contains("var ad =")
        }?.html()?.substringAfter("var ad = '")?.substringBefore("';")

        if (!hash.isNullOrEmpty()) {
            HDFullExtractor.load(hash, data, subtitleCallback, callback)
        }

        return true
    }

    // --------------------------------
    //       Funciónes auxiliares
    // --------------------------------
    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toSearchResult(): SearchResponse {
        var title = this.select("h5.left a.link").attr("title")
        var href = this.select("h5.left a.link").attr("href")
        val posterUrl = this.select("img").attr("src")
        val score = this.selectFirst("div.rating")?.text()?.toDoubleOrNull()?.let {
            "%.1f".format(it / 10).replace(",", ".")
        } ?: "0.0"

        val isType = getType(href)

        Log.d("HDFull", "Title: $title | Href: $href | posterUrl: $posterUrl | Type: $isType | Score: $score")
        return when (isType) {
            TvType.TvSeries -> newTvSeriesSearchResponse(title, href, isType) {
                this.posterUrl = fixUrl(posterUrl)
                this.score = Score.from(score, 10)
            }

            else -> newMovieSearchResponse(title, href, isType) {
                this.posterUrl = fixUrl(posterUrl)
                this.score = Score.from(score, 10)
            }
        }
    }

    // Función para detectar el TvType
    private fun getType(text: String): TvType {
        return when {
            text.contains("pelicula") -> TvType.Movie
            text.contains("/anime") -> TvType.Anime
            else -> TvType.TvSeries
        }
    }

    // Funcíon para el estado de la serie.
    fun getStatus(t: String?): ShowStatus {
        return when (t) {
            "Returning Series" -> ShowStatus.Ongoing
            "Released" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    // --------------------------------
    //            Data class
    // --------------------------------
    data class EpisodeJson(
        val episode: String?,
        val season: String?,
        @JsonProperty("date_aired") val dateAired: String?,
        val thumbnail: String?,
        val permalink: String?,
        val show: Show?,
        val id: String?,
        val title: Title?,
        val languages: List<String>? = null
    ) {
        data class Show(
            val title: Title?,
            val id: String?,
            val permalink: String?,
            val thumbnail: String?
        )

        data class Title(
            val es: String?,
            val en: String?
        )
    }
}