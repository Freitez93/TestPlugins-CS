package com.animeflv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnimeFLV : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("OVA") || t.contains("Especial") -> TvType.OVA
                t.contains("Película") -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }

        fun getDubStatus(title: String): DubStatus {
            return when {
                title.contains("Latino") || title.contains("Castellano") -> DubStatus.Dubbed
                else -> DubStatus.Subbed
            }
        }
    }

    override var mainUrl = "https://www3.animeflv.net"
    override var name = "AnimeFLV"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    override val mainPage = mainPageOf(
        Pair("", "Últimos episodios"),
        Pair("/browse?status[]=1&order=added", "En emisión"),
        Pair("/browse?type[]=movie&order=added", "Películas"),
        Pair("/browse?type[]=tv&type[]=special&type[]=ova&order=added", "Animes")
    )

    // Función auxiliar para crear un HomePageList
    private fun createHomePageList(
        name: String,
        items: List<SearchResponse>,
        isHorizontal: Boolean = false
    ): HomePageList {
        return HomePageList(name, items, isHorizontal)
    }

    // Función auxiliar para crear un SearchResponse
    private fun createAnimeSearchResponse(
        title: String,
        isType: String,
        url: String,
        posterUrl: String? = null,
        episodeNumber: Int? = null
    ): AnimeSearchResponse {
        return newAnimeSearchResponse(title, url, getType(isType)) {
            posterUrl?.let { this.posterUrl = fixUrl(it) }
            episodeNumber?.let { addDubStatus(getDubStatus(title), it) }
                ?: addDubStatus(getDubStatus(title))
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val addPageParam = if (request.name == "Últimos episodios") "" else "&page=$page"
        val url = "$mainUrl/${request.data}$addPageParam"
        val document = app.get(url).documentLarge
        val hasNextPage = document.selectFirst("a[rel=next]")?.attr("href") != null

        val home = if (request.name == "Últimos episodios") {
            document.select("main.Main ul.ListEpisodios li").mapNotNull { element ->
                val title = element.selectFirst("strong.Title")?.text() ?: return@mapNotNull null
                val poster = element.selectFirst("span img")?.attr("src") ?: return@mapNotNull null
                val episodeUrl = element.selectFirst("a")?.attr("href")?.let { href ->
                    val epRegex = Regex("(-(\\d+)\$)")
                    href.replace(epRegex, "").replace("ver/", "anime/")
                } ?: return@mapNotNull null
                val episodeNumber = element.selectFirst("span.Capi")?.text()
                    ?.replace("Episodio ", "")?.toIntOrNull()

                createAnimeSearchResponse(title, "Anime", episodeUrl, poster, episodeNumber)
            }
        } else {
            document.select("ul.ListAnimes li article").mapNotNull { element ->
                val title = element.selectFirst("h3.Title")?.text() ?: return@mapNotNull null
                val type = element.selectFirst("span.Type")?.text() ?: return@mapNotNull null
                val poster = element.selectFirst("figure img")?.attr("src") ?: return@mapNotNull null
                val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null

                createAnimeSearchResponse(title, type, fixUrl(href), poster)
            }
        }

        return newHomePageResponse(
            list = listOf(createHomePageList(request.name, home, request.name == "Últimos episodios")),
            hasNext = hasNextPage
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return try {
            val response = app.post(
                "$mainUrl/api/animes/search",
                data = mapOf("value" to query)
            ).text
            val json = parseJson<List<SearchObject>>(response)
            json.map { searchResult ->
                val title = searchResult.title
                val href = "$mainUrl/anime/${searchResult.slug}"
                val image = "$mainUrl/uploads/animes/covers/${searchResult.id}.jpg"
                createAnimeSearchResponse(title, searchResult.type, href, image)
            }
        } catch (e: Exception) {
            throw ErrorLoadingException("Error al realizar la búsqueda rápida: ${e.message}")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/browse?q=$query").document
        return document.select("ul.ListAnimes article").mapNotNull { element ->
            val title = element.selectFirst("h3")?.text() ?: return@mapNotNull null
            val type = element.selectFirst("span.Type")?.text() ?: return@mapNotNull null
            val image = element.selectFirst("figure img")?.attr("src") ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            createAnimeSearchResponse(title, type, fixUrl(href), image)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        val episodes = ArrayList<Episode>()
        val title = document.selectFirst("h1.Title")?.text() 
            ?: throw ErrorLoadingException("Título no encontrado")
        val poster = document.selectFirst("div.AnimeCover div.Image figure img")?.attr("src")
            ?: throw ErrorLoadingException("Póster no encontrado")
        val description = document.selectFirst("div.Description p")?.text()
        val type = document.selectFirst("span.Type")?.text() ?: ""
        val status = when (document.selectFirst("p.AnmStts span")?.text()) {
            "En emision" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val genre = document.select("nav.Nvgnrs a").map { it.text().trim() }

        document.select("script").forEach { script ->
            if (script.data().contains("var episodes = [")) {
                val data = script.data().substringAfter("var episodes = [").substringBefore("];")
                data.split("],").forEach { episodeData ->
                    val episodeNumber = episodeData.removePrefix("[").substringBefore(",")
                    val animeId = document.selectFirst("div.Strs.RateIt")?.attr("data-id")
                    val episodeThumbnail = "https://cdn.animeflv.net/screenshots/$animeId/$episodeNumber/th_3.jpg"
                    val episodeUrl = url.replace("/anime/", "/ver/") + "-$episodeNumber"

                    episodes.add(
                        newEpisode(episodeUrl) {
                            this.name = "Episodio $episodeNumber"
                            this.episode = episodeNumber.toIntOrNull()
                            this.posterUrl = episodeThumbnail
                        }
                    )
                }
            }
        }

        return newAnimeLoadResponse(title, url, getType(type)) {
            this.posterUrl = fixUrl(poster)
            addEpisodes(DubStatus.Subbed, episodes.reversed())
            this.showStatus = status
            this.plot = description
            this.tags = genre
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            app.get(data).documentLarge.select("script").amap { script ->
                if (script.data().contains("var videos = {") ||
                    script.data().contains("var anime_id =") ||
                    script.data().contains("server")
                ) {
                    val serversRegex = Regex("var videos = (\\{\"SUB\":\\[\\{.*?\\}\\]\\});")
                    val serversJson = serversRegex.find(script.data())?.destructured?.component1()
                        ?: return@amap
                    val servers = parseJson<MainServers>(serversJson)
                    // Processa los enlaces SUB si existen
                    servers.sub?.forEach { server ->
                        loadSourceNameExtractor("SUB", server.code, data, subtitleCallback, callback)
                    }
                    // Processa los enlaces LAT si existen
                    servers.lat?.forEach { server ->
                        loadSourceNameExtractor("LAT", server.code, data, subtitleCallback, callback)
                    }
                }
            }
            true
        } catch (e: Exception) {
            throw ErrorLoadingException("Error al cargar los enlaces: ${e.message}")
        }
    }
}

// Data class
data class Sub(val code: String)
data class MainServers(
    @JsonProperty("SUB") val sub: List<Sub>? = null,
    @JsonProperty("LAT") val lat: List<Sub>? = null,
)
data class SearchObject(
    @JsonProperty("id") val id: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("last_id") val lastId: String,
    @JsonProperty("slug") val slug: String
)

// Funcion Auxiliar para Servers con Lenguages.
suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    "$source [${link.source}]",
                    "$source [${link.source}]",
                    link.url,
                ) {
                    this.quality = link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}