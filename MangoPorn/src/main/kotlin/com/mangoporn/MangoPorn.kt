package com.mangoporn

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import kotlin.random.Random

class MangoPorn : MainAPI() {
    override var mainUrl              = "https://mangoporn.net"
    override var name                 = "MangoPorn+"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    // Lista de Palabras para excluir contenido no deseado.
    private val wordsToExclude = listOf(
        "gay", "homosexual", "queer", "homo", "androphile", "femboy", "feminine boy", "effeminate", "trap",
        "scat", "coprophilia", "coprophagia", "fecal", "poo", "shit", "crap", "bm play", "trans", "Gagged",
        "Trade", "Vers", "Twink", "Otter", "Bear", "Femme", "Masc", "No fats, no fems", "Serving", "crossdress", 
        "Receipts", "Kiki", "Kai Kai", "Werk", "Realness", "Hunty", "Snatched", "Beat", "Bisexual", "tranny",
        "Clocked", "Shade", "Zaddy", "Closet case", "Henny", "Queening out", "Slay", "Camp", "Fishy", "futa", 
        "Cruising", "Bathhouse", "Power bottom", "Situationship", "Pegging", "Femdom", 
    ).joinToString("|") { Regex.escape(it) }

    // Regex Para usar en la Exclucion de contenido no deseado.
    private val REGEX_EXCLUDE = Regex("""\b(?:$wordsToExclude)\w*\b""", RegexOption.IGNORE_CASE)

    override val mainPage get() = mainPageOf(
        *(try {
            MangoSettings.getOrderedAndEnabledCategories().toTypedArray()
        } catch (e: Exception) {
            Log.e("MangoPorn", "No se pudieron cargar las categorías, usando la lista de respaldo: ${e.message}")
            arrayOf(
                "xxxclips" to "XXXClips",
                "trending" to "Trending",
                "genres/porn-movies" to "Recently added",
                "genres/porn-movies/random" to "Random Contents"
            )
        })
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.endsWith("/random")) {
            val randomPageNumber = Random.nextInt(1, 3587)
            "$mainUrl/genres/porn-movies/page/$randomPageNumber"
        } else {
            fixUrl("${request.data}/page/$page")
        }

        val document = app.get(url).document
        val hasNextPage = document.selectFirst(".pagination > a[href*='/page/${page + 1}/']") != null
        val home = document.select("div.items > article").mapNotNull {
            it.toSearchResult() 
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = request.data.contains("xxxclips")
            ),
            hasNext = hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=$query").document
        val results = document.select(".result-item > article").mapNotNull {
            it.toSearchingResult()
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("div.data > h1")?.text().toString()
        val poster = document.selectFirst("div.poster > img")?.attr("data-wpfc-original-src")?.trim().toString()
        val year = document.selectFirst("span.textco a[rel=tag]")?.text()?.trim()?.toIntOrNull()
        val duration = document.selectFirst("span.duration")?.text()?.let {
            val hours = Regex("""(\d+)\s*hrs""").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val minutes = Regex("""(\d+)\s*mins""").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            (hours * 60) + minutes
        }
        val description = document.selectFirst("div.wp-content > p")?.text()
        val actors = document.select("div.persons a[href*=/pornstar/]").map { Actor(it.text()) }
        val tags = document.select("span.valors a[href*=/genre/]").map { it.text() }
        val score = document.select("span[itemprop=ratingValue]").text()

        if (tags.any { it.contains(REGEX_EXCLUDE) }) {
            val blockedTitle = "Contenido bloqueado"
            val blockedDescription = "Este contenido ha sido bloqueado debido a tus filtros."
            val blockedPoster = "https://i.postimg.cc/tT28ZcZv/Poster-Not-Found.jpg"
            val urlBos = ""
            return newMovieLoadResponse(blockedTitle, urlBos, TvType.NSFW, urlBos) {
                this.posterUrl = blockedPoster
                this.plot = blockedDescription
                this.tags = tags
                this.score = Score.from10(score)
                addActors(actors)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.NSFW, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.duration = duration
                this.tags = tags
                this.score = Score.from10(score)
                addActors(actors)
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
        document.select("div#pettabs > ul a").map {
            val link=it.attr("href")
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }

    // --------------------------------
    //       Funciónes auxiliares
    // --------------------------------
    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select("div h3").text()
        if (title.contains(REGEX_EXCLUDE)) {
            return null
        }
        val href = fixUrl(this.select("a[href]").attr("href"))
        val posterUrl = getImgOrFallBack(this.selectFirst("div.poster > img"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    // Función para convertir un elemento HTML a SearchResponse especial para la fun search
    private fun Element.toSearchingResult(): SearchResponse? {
        val title = this.select(".title > a").text()
        if (title.contains(REGEX_EXCLUDE)) {
            return null
        }
        val href = fixUrl(this.select(".title > a").attr("href"))
        val posterUrl = getImgOrFallBack(this.selectFirst("img"))
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    // Función para extraer la img de un Element.
    private fun getImgOrFallBack(
        element: Element?,
        fallback: String = "https://i.postimg.cc/tT28ZcZv/Poster-Not-Found.jpg"
    ): String {
        if (element == null) return fallback
        return sequenceOf(
            "data-wpfc-original-src",
            "data-lazy-src",
            "data-src",
            "src"
        ).mapNotNull {
            element.attr(it).takeIf(String::isNotBlank)
        }.firstOrNull {
            it.lowercase().endsWith(".jpg") || it.lowercase().endsWith(".png")
        } ?: fallback
    }
}