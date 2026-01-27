package com.hentaila

import android.util.Log
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import kotlin.random.Random

class HentaiLAProvider : MainAPI() {
    override var mainUrl        = "https://hentaila.com"
    override var name           = "HentaiLA+"
    override var lang           = "es"
    override val hasMainPage    = true
    override val hasQuickSearch = false
    //override val vpnStatus      = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)
    override val mainPage get() = mainPageOf(
        *(try {
            HentaiLASettings.getOrderedAndEnabledCategories().toTypedArray()
        } catch (e: Exception) {
            Log.e("HentaiLA", "No se pudieron cargar las categorías, usando la lista de respaldo: ${e.message}")
            arrayOf(
                "/hub" to "Episodios Actualizados",
                "/catalogo" to "Contenido Aleatorio",
                "/catalogo?status=emision&order=latest_released" to "Últimos Estrenados",
                "/catalogo?status=emision&order=latest_added" to "Últimos Añadidos"
            )
        })
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Determinar configuración basada en el tipo de solicitud (Hub vs Catálogo estándar)
        val isHub = request.data == "/hub"
        val cssSelector = if (isHub) "div.md\\:grid-cols-3 article" else "div.sm\\:grid-cols-3 > article"
        // Construir la URL final añadiendo paginación si es necesario
        val baseUrl = fixUrl(request.data).lowercase()
        var pageNum = if (request.data == "/catalogo") Random.nextInt(1, 50) else page 
        val url = baseUrl.addParameter("page", "$pageNum")
        val document = app.get(url).document
        // Verificar si existe una página siguiente buscando el enlace correspondiente
        val hasNextPage = document.selectFirst("a[href*='&page=${pageNum + 1}']") != null
        // Extraer los elementos de la lista usando el selector determinado
        val homeItems = document.select(cssSelector).mapNotNull {
            it.toMainPageResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = homeItems,
                isHorizontalImages = isHub
            ),
            hasNext = hasNextPage
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val baseUrl = "$mainUrl/catalogo?search=$query".addParameter("page", "$page")
        val document = app.get(baseUrl).document

        val hasNextPage = document.selectFirst("a[href*='&page=${page + 1}']") != null
        val homeSearch = document.select("div.sm\\:grid-cols-3 > article").mapNotNull {
            it.toMainPageResult()
        }

        return newSearchResponseList(homeSearch, hasNext = hasNextPage)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val scriptContent = document.select("script").html()

        // Recolectamos la Información de la Película/Anime/OVA
        val mediaInfo = getMediaInfo(scriptContent)
        val mediaID = mediaInfo?.mediaID
        val mediaSlug = mediaInfo?.slug
        val mediaTitle = mediaInfo?.title ?: "Unknown"
        val showStatus = getStatus(mediaInfo?.status)
        val animeType = getType(mediaInfo?.type)

        // Recolectamos los datos de Episodios.
        val episodes = mutableListOf<Episode>()
        if (mediaInfo != null && mediaInfo.episodesCount != null && mediaInfo.startEpisode != null) {
            val startEpisode = mediaInfo.startEpisode
            val totalEpisodes = mediaInfo.episodesCount
            for (i in startEpisode..totalEpisodes) {
                val epUrl = fixUrl("/media/$mediaSlug/$i")
                val epPoster = "https://cdn.hentaila.com/screenshots/$mediaID/$i.jpg"
                episodes.add(
                    newEpisode(epUrl) {
                        this.name = "Episodio $i"
                        this.episode = i
                        this.posterUrl = epPoster
                    }
                )
            }
        }

        return newAnimeLoadResponse(mediaTitle, url, animeType) {
            addMalId(mediaInfo?.malId)
            addEpisodes(DubStatus.Subbed, episodes)
            this.backgroundPosterUrl = "https://cdn.hentaila.com/backdrops/$mediaID.jpg"
            this.posterUrl = "https://cdn.hentaila.com/covers/$mediaID.jpg"
            this.plot = mediaInfo?.synopsis
            this.year = mediaInfo?.startDate?.substringBefore("-")?.toIntOrNull()
            this.score = Score.from10(mediaInfo?.score)
            this.showStatus = showStatus
            this.tags = mediaInfo?.genre
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val scriptContent = document.selectFirst("script:containsData(__sveltekit)")?.data() ?: return false
        val mediaInfo = getMediaInfo(scriptContent)

        mediaInfo?.embeds?.forEach { (type, embeds) ->
            embeds.forEach { embed ->
                when {
                    embed.url.contains("cdn.hvidserv.com/play/") -> {
                        val m3u8Url = embed.url.replace("/play/", "/m3u8/")
                        callback.invoke(
                            newExtractorLink(
                                source = "$type [HentaiLa]",
                                name = "$type [HentaiLa]",
                                url = m3u8Url,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.quality = Qualities.Unknown.value
                                this.referer = ""
                                this.headers = mapOf("sec-fetch-site" to "same-origin")
                            }
                        )
                    }
                    else -> loadSourceNameExtractor(type, embed.url, data, subtitleCallback, callback)
                }
            }
        }
        return true
    }

    // --------------------------------
    //       Funciónes auxiliares
    // --------------------------------
    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h3, div.text-subs.uppercase")?.text() ?: return null
        val href = this.selectFirst("a[href*='/media']")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img").getImgOrFallBack()
        val episodeNumber = this.selectFirst("span.font-bold.text-lead")?.text()?.toIntOrNull()
        val isType = getType(this.select("text-xs font-bold text-subs").text())
        return newAnimeSearchResponse(title, href, isType) {
            this.posterUrl = posterUrl
            episodeNumber?.let { addDubStatus(DubStatus.Subbed, it) }
        }
    }

    // Función para extraer la img de un Element.
    private fun Element?.getImgOrFallBack(
        fallback: String = "https://i.postimg.cc/tT28ZcZv/Poster-Not-Found.jpg"
    ): String {
        if (this == null) return fallback
        return sequenceOf(
            "data-wpfc-original-src",
            "data-lazy-src",
            "data-src",
            "src"
        ).mapNotNull {
            this.attr(it).takeIf(String::isNotBlank)
        }.firstOrNull {
            it.lowercase().endsWith(".jpg") || it.lowercase().endsWith(".png")
        } ?: fallback
    }

    // Función para obtener la información de la película/Anime/OVA
    private fun getMediaInfo(scriptContent: String): MediaDateJSON? {
        val dataJsonString = Regex("""data:\s*(\[.*\])""", RegexOption.DOT_MATCHES_ALL)
            .find(scriptContent)?.groupValues?.get(1) ?: return null

        return try {
            val cleanedJson = cleanJsToJson(dataJsonString)
            val dataArray = JSONArray(cleanedJson)

            var mediaData: MediaDateJSON? = null
            var embedsData: Map<String, List<EmbedInfo>>? = null

            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val dataObj = item.optJSONObject("data") ?: continue
                // Intentar extraer información de media
                val mediaObj = dataObj.optJSONObject("media")
                if (mediaObj != null) {
                    val genres = mutableListOf<String>()
                    val genresArray = mediaObj.optJSONArray("genres")
                    if (genresArray != null) {
                        for (j in 0 until genresArray.length()) {
                            val genreObj = genresArray.optJSONObject(j) ?: continue
                            genres.add(genreObj.optString("name"))
                        }
                    }
                    val episodesArray = mediaObj.optJSONArray("episodes")
                    val startEpisode = episodesArray?.optJSONObject(0)?.optInt("number", 1) ?: 1
                    val animeType = mediaObj.optJSONObject("category")?.optString("name")
                    val stringStatus = mediaObj.optInt("status", 0).let {
                        when (it) {
                            0 -> "Finalizado"
                            1 -> "Próximamente"
                            2 -> "En emisión"
                            else -> "Desconocido"
                        }
                    }
                    mediaData = MediaDateJSON(
                        mediaID = mediaObj.optInt("id"),
                        title = mediaObj.optString("title"),
                        slug = mediaObj.optString("slug"),
                        startDate = mediaObj.optString("startDate"),
                        status = stringStatus,
                        type = animeType,
                        genre = genres,
                        synopsis = mediaObj.optString("synopsis"),
                        score = mediaObj.optInt("score"),
                        malId = mediaObj.optInt("malId"),
                        episodesCount = mediaObj.optInt("episodesCount"),
                        startEpisode = startEpisode
                    )
                }
                // Intentar extraer embeds (puede estar en el mismo o diferente objeto)
                if (dataObj.has("embeds")) {
                    embedsData = extractEmbeds(dataObj)
                }
            }

            // Combinar los datos encontrados
            if (mediaData != null) {
                return mediaData.copy(embeds = embedsData)
            } else if (embedsData != null) {
                return MediaDateJSON(embeds = embedsData)
            }
            null
        } catch (e: Exception) {
            Log.e("AnimeAV1", "Error parsing DataJSON: ${e.message}", e)
            null
        }
    }
    // Función auxiliar para extraer embeds
    private fun extractEmbeds(dataObj: JSONObject): Map<String, List<EmbedInfo>>? {
        return try {
            if (!dataObj.has("embeds")) return null
        
            val embedsObj = dataObj.optJSONObject("embeds") ?: return null
            val embedsMap = mutableMapOf<String, List<EmbedInfo>>()
        
            fun extractEmbedList(key: String): List<EmbedInfo>? {
                return embedsObj.optJSONArray(key)?.let { array ->
                    List(array.length()) { i ->
                        array.optJSONObject(i)?.let { embedObj ->
                            EmbedInfo(
                                server = embedObj.optString("server", ""),
                                url = embedObj.optString("url", "")
                            )
                        }
                    }.filterNotNull().takeIf { it.isNotEmpty() }
                }
            }
        
            extractEmbedList("SUB")?.let { embedsMap["SUB"] = it }
            extractEmbedList("DUB")?.let { embedsMap["DUB"] = it }
        
            embedsMap.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.d("HentaiLA", "Error al extraer embeds: ${e.message}")
            null
        }
    }
    // Función auxiliar para limpiar el JSON
    private fun cleanJsToJson(js: String): String {
        var cleaned = js
        cleaned = cleaned.replace("void 0", "null")
        cleaned = Regex("""(?<=[{,])\s*(\w+)\s*:""").replace(cleaned) {
            "\"${it.groupValues[1]}\":"
        }
        return cleaned.trim()
    }
    // Función para obtener el tipo de contenido
    private fun getType(text: String?): TvType {
        if (text == null) return TvType.Anime
        return when {
            text.contains("OVA") || text.contains("Especial") -> TvType.OVA
            text.contains("Película") -> TvType.AnimeMovie
            else -> TvType.Anime
        }
    }
    // Función para obtener el estado del contenido
    private fun getStatus(text: String?): ShowStatus {
        if (text == null) return ShowStatus.Completed
        return when {
            text.contains("Finalizado") -> ShowStatus.Completed
            text.contains("En emisión") -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }
    // Función para agregarle paginacion a una URL
    private fun String.addParameter(key: String, value: String): String {
        if (key == "page" && value == "1") return this
        return try {
            val uri = Uri.parse(this)
            val builder = uri.buildUpon()
            builder.appendQueryParameter(
                key, value
            ).build().toString()
        } catch (e: Exception) {
            Log.e("HentaiLA", "Error en agregar parametros a una URL: $e")
            this
        }
    }

    //-------------------------------------//
    //              data class             //
    //-------------------------------------//
    // data class MediaDateJSON
    data class MediaDateJSON(
        val mediaID: Int? = null,
        val malId: Int? = null,
        val title: String? = null,
        val type: String? = null,
        val status: String? = null,
        val startDate: String? = null,
        val episodesCount: Int? = null,
        val startEpisode: Int? = null,
        val slug: String? = null,
        val score: Int? = null,
        val genre: List<String>? = null,
        val synopsis: String? = null,
        val embeds: Map<String, List<EmbedInfo>>? = null
    )

    // data class EmbedInfo
    data class EmbedInfo(
        val server: String,
        val url: String
    )
}