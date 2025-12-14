package com.animeav1

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeAV1 : MainAPI() {
    override var mainUrl = "https://animeav1.com"
    override var name = "AnimeAV1"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    override val mainPage = mainPageOf(
        "" to "Recientemente Actualizado",
        "catalogo?order=latest_added" to "Recientemente Agregados",
        "catalogo?category=tv-anime&order=latest_released" to "Anime - Últimos Estrenos",
        "catalogo?category=pelicula&order=latest_released" to "Películas - Últimos Estrenos",
        "catalogo?category=ova&category=especial&order=latest_released" to "OVA - Últimos Estrenos",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var isRecent = request.name == "Recientemente Actualizado"
        val addPageParam = if (isRecent) "" else "${request.data}&page=$page"
        val document = app.get("$mainUrl/$addPageParam").documentLarge

        val nextPage = document.select(".flex-wrap > a[href]").any { it.text().contains("»") }
        val homeList = document.select("main section:nth-child(1) article").mapNotNull { 
            it.toSearchResult(isRecent)
        }

        return newHomePageResponse(
            list = HomePageList(request.name, homeList, isRecent),
            hasNext = nextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/catalogo?search=$query").documentLarge
        val results = document.select("article").mapNotNull { it.toSearchResult() }
        return results
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
                val epUrl = "https://animeav1.com/media/$mediaSlug/$i"
                val epPoster = "https://cdn.animeav1.com/screenshots/$mediaID/$i.jpg"
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
            //this.backgroundPosterUrl = "https://cdn.animeav1.com/backdrops/$mediaID.jpg"
            this.posterUrl = "https://cdn.animeav1.com/covers/$mediaID.jpg"
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
        val document = app.get(data).documentLarge
        val scriptContent = document.select("script").html()
        val mediaInfo = getMediaInfo(scriptContent)
        
        mediaInfo?.embeds?.forEach { (type, embeds) ->
            embeds.forEach { embed ->
                loadCustomExtractor("AnimeAV1 [$type:${embed.server}]", embed.url, "", subtitleCallback, callback)
            }
        }
        return true
    }

    // --------------------------------
    //       Funciónes auxiliares
    // --------------------------------
    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toSearchResult(isRecents: Boolean = false): SearchResponse {
        val isType = getType(this.select("text-xs font-bold text-subs").text())
        val posterUrl = fixUrlNull(this.select("figure img").attr("src"))
        val name = this.select("h3, div.text-subs.uppercase").text()
        var href = this.select("a").attr("href")

        var episode: Int? = null
        if (isRecents) { // Si es Home Recientemente Actualizado
            episode = href.substringAfterLast("/").toIntOrNull()
            href = href.substringBeforeLast("/")
        }

        return newAnimeSearchResponse(name, href, isType) {
            episode?.let {
                addDubStatus(DubStatus.Subbed, episode)
            } ?: addDubStatus(DubStatus.Subbed)
            this.posterUrl = posterUrl
        }
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
            println("Error al extraer embeds: ${e.message}")
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