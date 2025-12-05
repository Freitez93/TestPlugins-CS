package com.srnovelas

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element

class SrNovelas : MainAPI() {
    override var mainUrl = "https://srnovelas.com"
    override var name = "SrNovelas"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        Pair("$mainUrl/novelas-americanas", "Novelas Americanas"),
        Pair("$mainUrl/novelas-colombianas", "Novelas Colombianas"),
        Pair("$mainUrl/novelas-mexicanas", "Novelas Mexicanas"),
        Pair("$mainUrl/novelas-chilenas", "Novelas Chilenas"),
        Pair("$mainUrl/novelas-turcas", "Novelas Turcas")
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val searchURL = if (page > 1) "${request.data}/page/$page" else request.data
        val document = app.get(searchURL).documentLarge
        val hasNextPage = document.selectFirst(".next.page-numbers") != null
        val home = document.select(".content-cluster > article").filter { it -> 
            !it.attr("target").equals("_blank", ignoreCase = true)
        }.mapNotNull { 
            it.toSearchResult()
        }

        return newHomePageResponse(
            list = listOf(HomePageList(request.name, home, true)),
            hasNext = hasNextPage
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchURL = if (page > 1) "/page/$page/?s=$query" else "$mainUrl/?s=$query"
        val searchDOC = app.get(searchURL).documentLarge
        val searchRES = searchDOC.select(".content-area > article").filter { it ->
            !it.select("p[class=entry-title]").text().contains("Capitulo")
        }.mapNotNull { it.toSearchResult() }

        val hasNextPage = searchDOC.selectFirst(".next.page-numbers") != null
        return newSearchResponseList(
            searchRES,
            hasNextPage
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).documentLarge
        val episodes = ArrayList<Episode>()
        val isType = TvType.TvSeries

        // Recolectamos la Información de la Película/Serie/Dorama/Anime
        val title = document.selectFirst("article h1")?.text()
            ?.replace(" Serie", "")
            ?: "Titulo no Encontrado"
        val description = document.selectFirst(".the-content p")?.text()
            ?.replace("&nbsp;", " ")
            ?.replace(Regex("\\s+"), " ")?.trim()
            ?: "Trama No Encontrada"
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")

        // Si es una Serie, Dorama o Anime
        if (isType != TvType.Movie) {
            var seasonNumber = 1  // Usamos var para poder incrementarla
            document.select("ul.su-posts-list-loop").forEach { seasonElement ->
                seasonElement.select("li.su-post").forEachIndexed { index, episode ->
                    val name = episode.select("a").text().split("Capitulo")[1].trim()
                    val href = episode.select("a").attr("href")
                    episodes.add(
                        newEpisode(href) {
                            this.name = "Capitulo $name"
                            this.season = seasonNumber
                            this.episode = index + 1  // Numeración de episodios comenzando desde 1
                        }
                    )
                }
                // Incrementamos el número de temporada.
                seasonNumber++
            }
        }

        return newTvSeriesLoadResponse(title, url, isType, episodes) {
            this.backgroundPosterUrl = poster
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false

        try {
            val document = app.get(data).documentLarge
            // Extraer todas las URLs de 'iframes'
            val videoUrls = document.select("iframe[src]").amap { iframe -> iframe.attr("src") }
            // Procesar cada URL
            videoUrls.forEach { url ->
                val success = loadExtractor(url, data, subtitleCallback, callback)
                if (success) {
                    foundLinks = true
                }
            }
        } catch (e: Exception) {
            Log.d("SrNovelas", "Error: ${e.message}")
        }
        return foundLinks
    }

    // --------------------------------
    //       Funciónes auxiliares
    // --------------------------------
    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select("p[class=entry-title]").text()
        val href = this.select("a").attr("href")
        val posterUrl = fixUrl(this.select("img").attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { 
            this.posterUrl = posterUrl 
        }
    }
}
