package com.sololatino

import android.util.Log
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import kotlin.random.Random

class SoloLatinoProvider : MainAPI() {
    // Variables en Settings.
    private val showPreview         = SoloLatinoSettings.getShowTrailer()

    // Variables en MainAPI.
    override var mainUrl            = "https://sololatino.net"
    override var name               = "SoloLatino+"
    override var lang               = "mx"
    override val hasMainPage        = true
    override val hasQuickSearch     = true
    override val hasDownloadSupport = true
    override val supportedTypes     = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime, TvType.Cartoon)
    override val mainPage get()     = mainPageOf(
        *(try {
            SoloLatinoSettings.getOrderedAndEnabledCategories().toTypedArray()
        } catch (e: Exception) {
            Log.e("SoloLatino", "No se pudieron cargar las categorías, usando la lista de respaldo: ${e.message}")
            arrayOf(
                "/peliculas" to "Home: Películas",
                "/series" to "Home: Series",
                "/animes" to "Home: Animes",
                "/doramas" to "Home: Doramas"
            )
        })
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Construir la URL final añadiendo paginación si es necesario
        val baseUrl = fixUrl(request.data).addParameter("page", page.toString())
        val document = app.get(baseUrl).document
        // Verificar si existe una página siguiente buscando el enlace correspondiente
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        // Extraer los elementos de la lista usando el selector determinado
        val homeItems = document.select("div.card").mapNotNull {
            it.toMainPageResult()
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = homeItems,
                isHorizontalImages = false
            ),
            hasNext = hasNextPage
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query,1).items
    override suspend fun search(query: String, page: Int): SearchResponseList {
        // Realizar la solicitud a la API de búsqueda con la consulta proporcionada
        val response = app.get("$mainUrl/api/search/suggest?q=$query").text
        val jsonArray = JSONArray(response)
        val searchResults = mutableListOf<SearchResponse>()

        // Procesar cada elemento del array JSON y crear respuestas de búsqueda
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val type = item.optString("type", "person") // Usar "person" como tipo por defecto para filtrar
            // Saltar entradas que no sean contenido multimedia (ej. personas)
            if (type == "person") continue
            val title = item.getString("title")
            val year = item.optInt("year", 0).takeIf { it != 0 } // Usar 0 como default, pero convertir a null si es 0
            val posterUrl = item.optString("poster", "")
            val href = item.getString("url")
            // Crear la respuesta de búsqueda apropiada según el tipo de contenido
            searchResults.add(createSearchResponse(title,href, posterUrl, year, type))
        }
        return newSearchResponseList(searchResults)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge

        // Recolectamos la Información de la Película/Anime/OVA
        val mediaTitle    = document.selectFirst("div > img[style]")!!.attr("alt")
        val mediaCover    = document.selectFirst("div > img[style]")!!.attr("src")
        val mediaBdrop    = document.selectFirst("meta[property='og:image']")!!.attr("content")
        val mediaTtOrg    = document.selectFirst("div.detail-field:contains(Título original) > dd")?.text()
        val mediaLogo     = document.selectFirst("div > img[class*='mb-3']")?.attr("src")
        val mediaYear     = document.selectFirst("title")?.text()?.subStringBetween("(", ")")?.toIntOrNull()
        val mediaTime     = document.selectFirst("div[style] > span:nth-child(2)")!!.text()
        val mediaPlot     = document.selectFirst("p[class*='leading-relaxed']")?.text()
        val mediaImdb     = document.selectFirst("dd > a[href*='/tt']")?.attr("href")
        val mediaYtID     = document.selectFirst("button[data-trailer]")?.attr("data-trailer")
        val mediaScore    = document.selectFirst("span.rating-badge--tmdb, span.rating-badge__val")?.text()?.replace("◆", "")
        val mediaStatus   = document.selectFirst("span[style*='color:#888']")?.text()
        val mediaGenres   = document.select("a[href*='/genero/'][onmouseover]").map { it.text() }
        val mediaActors   = document.select("div[id*=scroll-cast-] > .cast-card").mapNotNull { castData ->
            val actorName = castData.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val actorImgn = castData.selectFirst("img")?.attr("src") ?: "https://i.postimg.cc/SQwzL4Tm/Not-Person.jpg"
            ActorData(
                Actor(actorName, actorImgn),
                roleString = castData.selectFirst("p[style]")?.text()
            )
        }

        val mediaEpisodes = ArrayList<Episode>()
        val isType = getTvType(document.selectFirst("span[class*='badge']")?.text())
        if (isType != TvType.Movie) {
            document.select("div[data-season-panel]").map { seasonData ->
                val seasonNumber = seasonData.attr("data-season-panel").toIntOrNull()
                seasonData.select("div > a").map { episodeData -> 
                    val episodeNumber  = episodeData.selectFirst("div[class*='min-w-0'] > p:nth-child(1)")?.text()?.toIntOrNull()
                    val episodeTitle   = episodeData.selectFirst("div[class*='min-w-0'] > p:nth-child(2)")?.text()
                    val episodePlot    = episodeData.selectFirst("div[class*='min-w-0'] > p:nth-child(3)")?.text()
                    val episodeDate    = episodeData.selectFirst("div[class*='min-w-0'] > p:nth-child(4)")?.text()
                    val episodePreview = episodeData.selectFirst("img")?.attr("src")
                    val episodeUrl     = episodeData.attr("href")
                    mediaEpisodes.add(
                        newEpisode(episodeUrl) {
                            this.name        = episodeTitle
                            this.season      = seasonNumber
                            this.episode     = episodeNumber
                            this.posterUrl   = if (episodePreview.isNullOrBlank()) mediaBdrop else episodePreview
                            // this.score       = null
                            this.description = episodePlot
                            addDate(episodeDate, "dd/mm/yyyy")
                            // this.runTime     = null
                        }
                    )
                }
            }
        }

        return when (isType) {
            TvType.TvSeries, TvType.AsianDrama -> newTvSeriesLoadResponse(mediaTitle, url, isType, mediaEpisodes) {
                this.posterUrl              = mediaCover
                this.backgroundPosterUrl    = mediaBdrop
                this.logoUrl                = mediaLogo
                this.year                   = mediaYear
                this.plot                   = mediaPlot
                this.score                  = Score.from(mediaScore, 10)
                //this.duration               = getDurationFromString(mediaTime)
                this.tags                   = mediaGenres
                this.actors                 = mediaActors
                this.showStatus             = getShowStatus(mediaStatus)
                addImdbUrl(mediaImdb)
                if (showPreview) {
                    addTrailer("https://www.youtube.com/watch?v=$mediaYtID")
                }
            }
            TvType.Anime, TvType.Cartoon -> newAnimeLoadResponse(mediaTitle, url, isType) {
                this.japName                = mediaTtOrg
                this.posterUrl              = mediaCover
                this.backgroundPosterUrl    = mediaBdrop
                this.logoUrl                = mediaLogo
                this.year                   = mediaYear
                this.plot                   = mediaPlot
                this.score                  = Score.from(mediaScore, 10)
                //this.duration               = getDurationFromString(mediaTime)
                this.tags                   = mediaGenres
                this.actors                 = mediaActors
                this.showStatus             = getShowStatus(mediaStatus)
                addEpisodes(DubStatus.Dubbed, mediaEpisodes)
                addImdbUrl(mediaImdb)
                if (showPreview) {
                    addTrailer("https://www.youtube.com/watch?v=$mediaYtID")
                }
            }
            else -> newMovieLoadResponse(mediaTitle, url, isType, url) {
                this.posterUrl              = mediaCover
                this.backgroundPosterUrl    = mediaBdrop
                this.logoUrl                = mediaLogo
                this.year                   = mediaYear
                this.plot                   = mediaPlot
                this.score                  = Score.from(mediaScore, 10)
                this.duration               = getDurationFromString(mediaTime)
                this.tags                   = mediaGenres
                this.actors                 = mediaActors
                addImdbUrl(mediaImdb)
                if (showPreview) {
                    addTrailer("https://www.youtube.com/watch?v=$mediaYtID")
                }
            }
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
                val listOfLinks = document.select("button[data-server-url]").mapNotNull { 
                    it.attr("data-server-url") 
                }.toMutableList()
                Log.d("SoloLatino", "Enlaces encontrados: $listOfLinks")

                // Procesar cada URL según el servicio
                listOfLinks.forEach { url ->
                    Log.d("SoloLatino", "Server: $url")
                    when {
                        url.contains("embed69.org") -> {
                            Embed69Extractor.load(url, data, subtitleCallback, callback)
                        }
                        url.contains("xupalace.org") || url.contains("re.sololatino.net") -> {
                            XupaLaceExtractor.load(url, data, subtitleCallback, callback)
                        }
                        url.contains("uqlink.php?id=") -> {
                            url.split("id=").getOrNull(1).let {
                                loadSourceNameExtractor(
                                    "LAT",
                                    "https://uqload.bz/embed-$it.html",
                                    data,
                                    subtitleCallback,
                                    callback
                                )
                            }
                        }
                        else -> {
                            loadSourceNameExtractor("LAT", url, data, subtitleCallback, callback)
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
    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toMainPageResult(): SearchResponse? {
        val title       = this.selectFirst("img")?.attr("alt") ?: return null
        val posterUrl   = this.selectFirst("img").getImg()
        val href        = this.selectFirst("a")?.attr("href") ?: return null
        val score       = this.selectFirst("span[class='card__rating']")?.text()?.replace("★", "")
        val year        = this.selectFirst("span[class='card__year']")?.text()?.toIntOrNull()
        val isType      = this.selectFirst("span[class*='badge']")?.text()

        return createSearchResponse(title, href, posterUrl, year, isType.toString(), score)
    }
    // Función para crear una respuesta de búsqueda basada en el tipo de contenido
    private fun createSearchResponse(
        title: String,
        href: String,
        posterUrl: String,
        year: Int?,
        type: String?,
        score: String? = null
    ): SearchResponse {
        val isType = getTvType(type)
        return when (isType) {
            TvType.TvSeries, TvType.AsianDrama -> newTvSeriesSearchResponse(title, href, isType) {
                this.posterUrl = posterUrl
                this.year = year
                this.score = Score.from(score, 10)
            }
            TvType.Anime, TvType.Cartoon -> newAnimeSearchResponse(title, href, isType) {
                this.posterUrl = posterUrl
                this.year = year
                this.score = Score.from(score, 10)
            }
            else -> newMovieSearchResponse(title, href, isType) {
                this.posterUrl = posterUrl
                this.year = year
                this.score = Score.from(score, 10)
            }
        }
    }
    // Función para extraer la img de un Element.
    private fun Element?.getImg(
        fallback: String = "https://sololatino.net/images/no-poster.jpg"
    ): String {
        if (this == null) return fallback
        return sequenceOf(
            "data-lazy-src",
            "data-poster",
            "data-src",
            "poster",
            "src"
        ).mapNotNull {
            this.attr(it).takeIf(String::isNotBlank)
        }.firstOrNull {
            it.lowercase().endsWith(".jpg") || it.lowercase().endsWith(".png")
        } ?: fallback
    }
    // Función para agregarle parametros a una URL
    private fun String.addParameter(key: String, value: String): String {
        if (key == "page" && value == "1") return this
        return try {
            val uri = Uri.parse(this)
            val builder = uri.buildUpon()
            builder.appendQueryParameter(key, value).build().toString()
        } catch (e: Exception) {
            Log.e("SoloLatino", "Error en agregar parametros a una URL: $e")
            this
        }
    }
    // Función para extraer una subcadena entre dos delimitadores
    private fun String.subStringBetween(start: String, end: String): String? {
        val startIndex = this.indexOf(start)
        if (startIndex == -1) return null
        val endIndex = this.indexOf(end, startIndex + start.length)
        if (endIndex == -1) return null
        return this.substring(startIndex + start.length, endIndex)
    }
    // Función para determinar el tipo de contenido.
    private fun getTvType(isType: String?): TvType {
        return when (isType) {
            "Serie", "series" -> TvType.TvSeries
            "Dorama", "dorama" -> TvType.AsianDrama
            "Dibujos", "toon" -> TvType.Cartoon
            "Anime", "anime" -> TvType.Anime
            else -> TvType.Movie
        }
    }
    // Función para determinar el estado de la serie.
    private fun getShowStatus(status: String?): ShowStatus {
        return when (status) {
            "Returning Series" -> ShowStatus.Ongoing
            "Released" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }
}