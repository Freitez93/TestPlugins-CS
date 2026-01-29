package com.hentaila

import android.util.Log
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import kotlin.random.Random

class HentaiLAProvider : MainAPI() {
    override var mainUrl        = "https://hentaila.com"
    private var mediaUrl        = "https://cdn.hentaila.com"
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
        val scriptContent = document.selectFirst("script:containsData(__sveltekit)")?.data() ?: ""

        // Recolectamos la Información de la Película/Anime/OVA
        val mediaInfo = getMediaInfo(scriptContent)
        val mediaID = mediaInfo?.mediaID
        val mediaSlug = mediaInfo?.slug
        val mediaTitle = mediaInfo?.title ?: "Unknown"
        val mediaBackDrops = document.selectFirst("img[src*='/backdrops/']").getImg()
        val showStatus = getStatus(mediaInfo?.status)

        // Recolectamos los datos de Episodios.
        val episodes = mutableListOf<Episode>()
        if (mediaInfo != null && mediaInfo.episodesCount != null && mediaInfo.startEpisode != null) {
            val startEpisode = mediaInfo.startEpisode
            val totalEpisodes = mediaInfo.episodesCount
            for (i in startEpisode..totalEpisodes) {
                val epUrl = fixUrl("/media/$mediaSlug/$i")
                episodes.add(
                    newEpisode(epUrl) {
                        this.name = "Episodio $i"
                        this.episode = i
                        this.posterUrl = "$mediaUrl/screenshots/$mediaID/$i.jpg"
                    }
                )
            }
        }

        return newAnimeLoadResponse(mediaTitle, url, TvType.NSFW) {
            addMalId(mediaInfo?.malId)
            addEpisodes(DubStatus.Subbed, episodes)
            this.backgroundPosterUrl = mediaBackDrops
            this.posterUrl = "$mediaUrl/covers/$mediaID.jpg"
            this.plot = mediaInfo?.synopsis
            this.year = mediaInfo?.startDate?.substringBefore("-")?.toIntOrNull()
            this.score = Score.from10(mediaInfo?.score)
            this.showStatus = showStatus
            this.tags = mediaInfo?.genre
            this.recommendations = mediaInfo?.recommendations?.map {
                newAnimeSearchResponse(it.title, it.href, TvType.NSFW) {
                    this.posterUrl = it.posterUrl
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
                                source = "$type [HentaiLA]",
                                name = "$type [HentaiLA]",
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
        val posterUrl = this.selectFirst("img").getImg()
        val episodeNumber = this.selectFirst("span.font-bold.text-lead")?.text()?.toIntOrNull()
        return newAnimeSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
            episodeNumber?.let { addDubStatus(DubStatus.Subbed, it) }
        }
    }
    // Función para extraer la img de un Element.
    private fun Element?.getImg(
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
    // Función para agregarle parametros a una URL
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
    // Función para obtener la información de la película/Anime/OVA
    private fun getMediaInfo(scriptContent: String): MediaDateJSON? {
        // Buscar el JSON dentro del script
        val dataJsonString = Regex("""data:\s*(\[.*\])""", RegexOption.DOT_MATCHES_ALL)
            .find(scriptContent)?.groupValues?.get(1) ?: return null
        return try {
            val cleanedJson = cleanJsToJson(dataJsonString)
            val dataArray = JSONArray(cleanedJson)
            var mediaData: MediaDateJSON? = null
            // Usamos un mapa mutable para acumular embeds y downloads sin sobrescribir
            val allEmbeds = mutableMapOf<String, List<EmbedInfo>>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val dataObj = item.optJSONObject("data") ?: continue
                // 1. Extraer información de media (si existe y no se ha extraído ya)
                if (mediaData == null) {
                    dataObj.optJSONObject("media")?.let { mediaObj ->
                        mediaData = parseMediaObject(mediaObj)
                    }
                }
                // 2. Extraer urls de 'embeds' y 'downloads' dinámicamente
                listOf("embeds", "downloads").forEach { key ->
                    extractUrls(dataObj, key)?.forEach { (category, items) ->
                        val currentList = allEmbeds[category] ?: emptyList()
                        allEmbeds[category] = currentList + items
                    }
                }
            }
            // Construir el objeto final combinando info + embeds
            mediaData?.copy(
                embeds = allEmbeds.takeIf { it.isNotEmpty() }
            ) ?: allEmbeds.takeIf { it.isNotEmpty() }?.let {
                MediaDateJSON(embeds = it) 
            }
        } catch (e: Exception) {
            Log.e("HentaiLA", "Error al procesar JSON en getMediaInfo: ${e.message}", e)
            null
        }
    }

    // Parsea el objeto 'media' del JSON a nuestra data class MediaDateJSON.
    private fun parseMediaObject(mediaObj: JSONObject): MediaDateJSON {
        // Extraer géneros, manejando nulos
        val genres = mediaObj.optJSONArray("genres")?.let { arr ->
            List(arr.length()) { i ->
                arr.optJSONObject(i)?.optString("name")
            }.filterNotNull()
        } ?: emptyList()
        // Determinar episodio inicial (default 1)
        val startEpisode = mediaObj.optJSONArray("episodes")
            ?.optJSONObject(0)?.optInt("number", 1) ?: 1
        // Convertir estado numérico a texto legible
        val statusText = when (mediaObj.optInt("status", 0)) {
            0 -> "Finalizado"
            1 -> "Próximamente"
            2 -> "En emisión"
            else -> "Desconocido"
        }
        return MediaDateJSON(
            mediaID = mediaObj.optInt("id"),
            title = mediaObj.optString("title"),
            slug = mediaObj.optString("slug"),
            startDate = mediaObj.optString("startDate"),
            status = statusText,
            type = mediaObj.optJSONObject("category")?.optString("name"),
            genre = genres,
            synopsis = mediaObj.optString("synopsis"),
            score = mediaObj.optInt("score"),
            malId = mediaObj.optInt("malId"),
            episodesCount = mediaObj.optInt("episodesCount"),
            startEpisode = startEpisode,
            recommendations = parseRecommendations(mediaObj.optJSONArray("relations"))
        )
    }

    // Función auxiliar para extraer y modelar las recomendaciones (relations).
    private fun parseRecommendations(relationsArray: JSONArray?): List<MediaRelation>? {
        if (relationsArray == null) return null
        return List(relationsArray.length()) { i ->
            val item = relationsArray.optJSONObject(i)
            val dest = item?.optJSONObject("destination")
            if (dest != null) {
                MediaRelation(
                    type = item.optInt("type", 0),
                    title = dest.optString("title"),
                    href = "/media/${dest.optString("slug")}",
                    posterUrl = "$mediaUrl/covers/${dest.optInt("id")}.jpg"
                )
            } else null
        }.filterNotNull()
    }

    // Función auxiliar para extraer URLs (embeds, downloads).
    private fun extractUrls(dataObj: JSONObject, key: String): Map<String, List<EmbedInfo>>? {
        val containerObj = dataObj.optJSONObject(key) ?: return null
        return try {
            val resultMap = mutableMapOf<String, List<EmbedInfo>>()
            val keysIterator = containerObj.keys()
            // Iterar dinámicamente sobre todas las categorías de servidores (SUB, MEGA, etc.)
            while (keysIterator.hasNext()) {
                val category = keysIterator.next()
                val itemsArray = containerObj.optJSONArray(category) ?: continue
                val validItems = List(itemsArray.length()) { i ->
                    itemsArray.optJSONObject(i)?.let { item ->
                        val url = item.optString("url")
                        // Solo incluimos si tiene URL válida
                        if (url.isNotBlank()) {
                            EmbedInfo(
                                server = item.optString("server", ""),
                                url = url
                            )
                        } else null
                    }
                }.filterNotNull()
                if (validItems.isNotEmpty()) {
                    resultMap[category] = validItems
                }
            }
            resultMap.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("HentaiLA", "Error al extraer '$key': ${e.message}")
            null
        }
    }
    // Limpia un string de JS para convertirlo en JSON válido.
    private fun cleanJsToJson(js: String): String {
        return js.replace("void 0", "null")
            .replace(Regex("""(?<=[{,])\s*(\w+)\s*:""")) {
                "\"${it.groupValues[1]}\":"
            }.trim()
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
        val embeds: Map<String, List<EmbedInfo>>? = null,
        val recommendations: List<MediaRelation>? = null
    )
    // data class MediaRelation
    data class MediaRelation(
        val type: Int,
        val title: String,
        val href: String,
        val posterUrl: String
    )
    // data class EmbedInfo
    data class EmbedInfo(
        val server: String,
        val url: String
    )
}