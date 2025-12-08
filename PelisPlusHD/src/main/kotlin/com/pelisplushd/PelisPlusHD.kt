package com.pelisplushd

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.getOrNull
import org.jsoup.nodes.Element

class PelisPlusHD : MainAPI() {
    override var mainUrl = "https://pelisplushd.bz"
    override var name = "PelisPlusHD"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime)

    override val mainPage = mainPageOf(
        Pair("$mainUrl/year/2025", "Últimos agregados"),
        Pair("$mainUrl/peliculas/populares", "Películas"),
        Pair("$mainUrl/series/populares", "Series"),
        Pair("$mainUrl/generos/dorama", "Doramas"),
        Pair("$mainUrl/animes/populares", "Animes")
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val searchURL = if (page > 1) "${request.data}?page=$page" else request.data
        val document = app.get(searchURL).documentLarge
        val hasNextPage = document.selectFirst(".page-link[rel=next]")?.attr("href") != null
        val home = document.select(".Posters > a").filter { it -> 
            !it.attr("target").equals("_blank", ignoreCase = true) 
        }.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = listOf(createHomePageList(request.name, home, false)),
            hasNext = hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?s=$query").documentLarge
        return document.select(".Posters > a").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).documentLarge
        val episodes = ArrayList<Episode>()
        val isType = getType(url)
        val imdb = getImdb(document, isType)
        val meta = getMeta_TMDb(imdb, isType)

        // Recolectamos la Información de la Película/Serie/Dorama/Anime
        val (_title, _year) = fixTitle(document.selectFirst(".m-b-5")?.text() ?: "Título desconocido")
        val title = when {
            meta?.title != null && meta.title == meta.orgTitle -> _title
            else -> meta?.title?.takeIf { it.isNotBlank() } ?: _title
        }
        val year = meta?.releaseDate?.substringBefore("-")?.toIntOrNull() ?: _year
        val description = document.selectFirst("div.text-large")?.text()?.trim()
        val score = document.selectFirst("span.ion-md-star")?.text()?.split("/")?.getOrNull(0)?.trim()
        val tags = document.select(".p-v-20 > a > span").map { it.text().trim() }
        val imgPt = document.selectFirst("meta[itemprop=image]")?.attr("content")
        val imgBg = meta?.backgroundUrl ?: "https://images.metahub.space/background/small/$imdb/img"

        // Si es una Serie, Dorama o Anime
        if (isType != TvType.Movie) {
            val videos = meta?.episodes
            document.select("div.tab-pane .btn").map { li ->
                val href = li.selectFirst("a")?.attr("href") ?: return@map
                val btnEp = li.selectFirst(".btn-primary.btn-block")?.text() ?: ": Episodio Sin Nombre"
                val (season, episode, _name) = parseEpisodeTitle(btnEp)
                
                // Buscar el video que coincida con season y episode
                val videoInfo = videos?.firstOrNull { 
                    it.season == season && it.episode == episode 
                }

                Log.d("Freitez93", videoInfo?.still_path.toString())
                episodes.add(
                    newEpisode(href) {
                        this.name = videoInfo?.name ?: _name
                        this.season = season
                        this.episode = episode
                        this.description = videoInfo?.overview
                        this.runTime = videoInfo?.runtime
                        this.posterUrl = videoInfo?.still_path?.replace("/original/", "/w500/")
                        videoInfo?.air_date?.let { addDate(it) }
                    }
                )
            }
        }

        return when (isType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(title, url, isType, episodes) {
                    addImdbId(meta?.imdb_id ?: imdb)
                    addTMDbId(meta?.tmdb_id)
                    addTrailer(meta?.trailers)
                    this.backgroundPosterUrl = imgBg
                    this.posterUrl = meta?.posterUrl ?: imgPt
                    this.score = Score.from10(meta?.score ?: score)
                    this.plot = meta?.plot ?: description
                    this.year = year
                    this.showStatus = getStatus(meta?.status)
                    this.tags = meta?.genres ?: tags
                    this.actors = meta?.actors
                }
            }
            TvType.Anime -> {
                newAnimeLoadResponse(title, url, isType) {
                    addImdbId(meta?.imdb_id ?: imdb)
                    addTMDbId(meta?.tmdb_id)
                    addTrailer(meta?.trailers)
                    addEpisodes(DubStatus.Dubbed, episodes.reversed())
                    this.backgroundPosterUrl = imgBg
                    this.posterUrl = meta?.posterUrl ?: imgPt
                    this.score = Score.from10(meta?.score ?: score)
                    this.plot = meta?.plot ?: description
                    this.year = year
                    this.showStatus = getStatus(meta?.status)
                    this.tags = meta?.genres ?: tags
                    this.actors = meta?.actors
                }
            }
            TvType.Movie -> {
                newMovieLoadResponse(title, url, isType, url) {
                    addImdbId(meta?.imdb_id ?: imdb)
                    addTMDbId(meta?.tmdb_id)
                    addTrailer(meta?.trailers)
                    this.backgroundPosterUrl = imgBg
                    this.posterUrl = meta?.posterUrl ?: imgPt
                    this.score = Score.from10(meta?.score ?: score)
                    this.plot = meta?.plot ?: description
                    this.year = year
                    this.duration = meta?.runtime
                    this.tags = meta?.genres ?: tags
                    this.actors = meta?.actors
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
        return withContext(Dispatchers.IO) {
            try {
                val document = app.get(data).documentLarge
                val scriptContent = document.selectFirst("script:containsData(var video = [];)")?.data() ?: return@withContext false

                // Extraer todas las URLs del arreglo 'video'
                val videoUrls = mutableListOf<String>()
                val regex = Regex("video\\[(\\d+)\\]\\s*=\\s*'([^']+)'")
                regex.findAll(scriptContent).forEach { matchResult ->
                    val url = matchResult.groupValues[2]
                    videoUrls.add(url)
                }

                // Procesar cada URL según el servicio
                videoUrls.forEach { url ->
                    when {
                        url.contains("embed69.org/f/") -> {
                            val allLinksByLanguage = decryptLinks(url)
                            for ((language, links) in allLinksByLanguage) {
                                links.forEach { link ->
                                    loadSourceNameExtractor(language,link,"",subtitleCallback,callback)
                                }
                            }
                        }
                        url.contains("xupalace.org/embed.php") -> {
                            // Extractor no implementado
                        }
                        url.contains("xupalace.org/uqlink.php") -> {
                            val uqLoadFix = url.split("id=")[1].let { "https://uqload.cx/embed-$it.html" }
                            loadSourceNameExtractor("LAT", uqLoadFix, data, subtitleCallback, callback)
                        }
                        url.contains("waaw") -> {
                            // Extractor no implementado
                        }
                        else -> {
                            loadExtractor(url, data, subtitleCallback, callback)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                throw ErrorLoadingException("Error al cargar los enlaces: ${e.message}")
            }
        }
    }

    // --------------------------------
    //       Funciónes auxiliares
    // --------------------------------
    // Función para crear un HomePageList
    private fun createHomePageList(
        name: String,
        items: List<SearchResponse>,
        isHorizontal: Boolean = false
    ): HomePageList {
        return HomePageList(name, items, isHorizontal)
    }

    // Función para detectar el TvType
    private fun getType(link: String): TvType {
        return when {
            link.contains("/serie") -> TvType.TvSeries
            link.contains("/anime") -> TvType.Anime
            link.contains("/pelicula") -> TvType.Movie
            link.contains("/dorama") -> TvType.AsianDrama
            else -> TvType.Movie
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

    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toSearchResult(): SearchResponse {
        val (title, year) = fixTitle(this.select(".listing-content > p").text())
        val href = this.select("a").attr("href")
        val posterUrl = fixUrl(this.select("img").attr("src"))
        val isType = getType(href)
        return when (isType) {
            TvType.Movie -> newMovieSearchResponse(title, href, isType) {
                this.posterUrl = posterUrl
                this.year = year
                this.type = isType
            }
            TvType.Anime -> newAnimeSearchResponse(title, href, isType) {
                this.posterUrl = posterUrl
                this.year = year
                this.type = isType
            }
            else -> newTvSeriesSearchResponse(title, href, isType) {
                this.posterUrl = posterUrl
                this.year = year
                this.type = isType
            }
        }
    }

    // Función para extraer URLs de un script
    private fun fetchUrls(scriptData: String): List<String> {
        val urlRegex = Regex("""(https?://[^\s"']+)""")
        return urlRegex.findAll(scriptData).map { it.value }.toList()
    }

    // Función para extraer el IMDb-ID
    private suspend fun getImdb(input: org.jsoup.nodes.Document, type: TvType): String? {
        var document = input
        if (type != TvType.Movie) {
            val getEpUrl = input.selectFirst("div[role=tabpanel] a")?.attr("href")
            if (getEpUrl != null) {
                document = app.get(getEpUrl).documentLarge
            }
        }
        return document.selectFirst("script:containsData(var video = [];)")?.data()?.let { text: String ->
            imdbUrlToId(text.toString())
        }
    }

    // Función para quitar el (Año) de los títulos y devolver ("Titulo", año)
    private fun fixTitle(title: String?): Pair<String, Int?> {
        return if (title == null) {
            Pair("", null)
        } else {
            // Usar expresión regular para extraer el año entre paréntesis
            val regex = Regex("\\((\\d{4})\\)")
            val year = regex.find(title)?.groupValues?.get(1)?.toIntOrNull()
            val cleanTitle = title.replace(regex, "").trim()

            Pair(cleanTitle, year)
        }
    }

    // Función para arreglar el nombre de los episodios
    private fun parseEpisodeTitle(title: String): Triple<Int, Int, String> {
        // Expresión regular para extraer temporada, episodio y nombre
        val regex = Regex("""(?:T|Temporada)\s*(\d+)\s*[-–]\s*(?:E|Episodio)\s*(\d+):\s*(.+)""", RegexOption.IGNORE_CASE)

        return regex.find(title)?.let { res ->
            val numSeason = res.groupValues[1].toIntOrNull() ?: 0
            val numEpisode = res.groupValues[2].toIntOrNull() ?: 0
            val strName = res.groupValues[3].trim()
            Triple(numSeason, numEpisode, strName)
        } ?: Triple(0, 0, title) // Valor por defecto si no coincide con el patrón
    }
}
