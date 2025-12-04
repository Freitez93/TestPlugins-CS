package com.pelisplushd

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        Pair("$mainUrl/year/2025", "Últimos episodios"),
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
        val home = document.select(".Posters > a")
            .filter { it -> !it.attr("target").equals("_blank", ignoreCase = true) }
            .mapNotNull { it.toSearchResult() }
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

        // Recolectamos la Información de la Película/Serie/Dorama/Anime
        val title = fixTitle(document.selectFirst(".m-b-5")?.text() ?: "Título desconocido")
        val description = document.selectFirst("div.text-large")?.text()?.trim()
        val poster = document.selectFirst("meta[itemprop=image]")?.attr("content")
        val tags = document.select(".p-h-15.text-center a span.font-size-18.text-info.text-semibold")
            .map { it.text().trim() }

        // Si es una Serie, Dorama o Anime
        if (isType != TvType.Movie) {
            document.select("div.tab-pane .btn").map { li ->
                val href = li.selectFirst("a")?.attr("href") ?: return@map
                val btnEp = li.selectFirst(".btn-primary.btn-block")?.text() ?: ": Episodio Sin Nombre"
                val (season, episode, name) = parseEpisodeTitle(btnEp)
                episodes.add(
                    newEpisode(href) {
                        this.name = name
                        this.season = season
                        this.episode = episode
                    }
                )
            }
        }

        return when (isType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(title, url, isType, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.tags = tags
                }
            }
            TvType.Anime -> {
                newAnimeLoadResponse(title, url, isType) {
                    this.posterUrl = poster
                    addEpisodes(DubStatus.Dubbed, episodes.reversed())
                    this.plot = description
                    this.tags = tags
                }
            }
            TvType.Movie -> {
                newMovieLoadResponse(title, url, isType, url) {
                    this.posterUrl = poster
                    this.plot = description
                    this.tags = tags
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

                Log.d("Debug", videoUrls.toJson())
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

    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toSearchResult(): SearchResponse {
        val title = fixTitle(this.select(".listing-content > p").text())
        val href = this.select("a").attr("href")
        val posterUrl = fixUrl(this.select("img").attr("src"))
        val isType = getType(href)
        return when (isType) {
            TvType.Movie -> newMovieSearchResponse(title, href, isType) { this.posterUrl = posterUrl }
            TvType.Anime -> newAnimeSearchResponse(title, href, isType) { this.posterUrl = posterUrl }
            else -> newTvSeriesSearchResponse(title, href, isType) { this.posterUrl = posterUrl }
        }
    }

    // Función para extraer URLs de un script
    private fun fetchUrls(scriptData: String): List<String> {
        val urlRegex = Regex("""(https?://[^\s"']+)""")
        return urlRegex.findAll(scriptData).map { it.value }.toList()
    }

    // Función para decodificar base64
    private fun base64Decode(input: String): String {
        return try {
            String(android.util.Base64.decode(input, android.util.Base64.DEFAULT))
        } catch (e: Exception) {
            input // Devuelve el input original si falla la decodificación
        }
    }

    // Función para quitar el (Año) de los títulos
    private fun fixTitle(title: String?): String {
        return title?.replace(Regex("( \\(\\d{4}\\))"), "")?.trim() ?: "$title"
    }

    // Función para arreglar el nombre de los episodios
    private fun fixTitleEp(title: String?): String {
        return title?.split(':')?.getOrElse(1) { 
            title.split(':').getOrNull(0) ?: "Episodio desconocido" 
        }?.trim() ?: "Episodio desconocido"
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
