package com.missav

import android.util.Log
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import kotlin.text.replace

class MissAV : MainAPI() {
    // Variables en Settings.
    private val langInPage      = MissAVSettings.getLangInPage()
    private val titleInCode     = MissAVSettings.getTitleInCode()
    private val showPreview     = MissAVSettings.getShowTrailer()

    // Variables en MainAPI.
    override var mainUrl        = "https://missav.ws/$langInPage"
    override var name           = "MissAV+"
    override var lang           = "ja"
    override val hasMainPage    = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.NSFW)
    override val mainPage get() = mainPageOf(
        *(try {
            MissAVSettings.getOrderedAndEnabledCategories().toTypedArray()
        } catch (e: Exception) {
            Log.e("MissAV", "No se pudieron cargar las categorías, usando la lista de respaldo: ${e.message}")
            arrayOf(
                "/new?sort=published_at" to "Recent Update",
                "/release?sort=released_at" to "New Releases",
                "/uncensored-leak?sort=monthly_views" to "Uncensored Leak",
                "/monthly-hot?sort=views" to "Most Viewed - Month",
                "/weekly-hot?sort=weekly_views" to "Most Viewed - Week"
            )
        })
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val baseUrl = fixUrl(request.data).addParameter("page", "$page")
        val document = app.get(baseUrl).document
        // Verificar si existe una página siguiente buscando el enlace correspondiente
        val hasNextPage = document.selectFirst("a[href*='page=${page + 1}']") != null
        val home = document.select("div[class*='thumbnail']").mapNotNull {
            it.toMainPageResult()
        }.distinctBy { it.url }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = hasNextPage
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val baseUrl = fixUrl("/search/$query").addParameter("page", "$page")
        val document = app.get(baseUrl).document
        // Verificar si existe una página siguiente buscando el enlace correspondiente
        val hasNextPage = document.selectFirst("a[href*='page=${page + 1}']") != null
        val searchItems = document.select("div[class*='thumbnail']").mapNotNull { 
            it.toMainPageResult()
        }.distinctBy { it.url }
        return newSearchResponseList(searchItems, hasNext = hasNextPage)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val code = url.substringAfterLast("/")
        val name = document.select("h1[class*='text-base']").text().trim()
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property='og:description']")?.attr("content")?.trim()
        val year = document.selectFirst("div.extra span.C a")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("span:containsOwn(Genre) ~ a").map { it.text().trim() }
        val duration = document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val actors = document.select("span:containsOwn(Actress) ~ a").map { Actor(it.text()) }

        val title = getTitle(name, code)
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.duration = duration
            addActors(actors)
            if (showPreview) addTrailer("https://fourhoi.com/$code/preview.mp4",addRaw = true)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        getAndUnpack(app.get(data).text).let { unpacked ->
            """source=['"](.*?)['"]""".toRegex().find(unpacked)?.groupValues?.get(1)?.let { url ->
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://missav.ws"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }
        return true
    }
    // --------------------------------
    //       Funciónes auxiliares
    // --------------------------------
    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toMainPageResult(): SearchResponse? {
        var tags = "" // Etiqueta vacia
        val code = this.selectFirst("a[alt*='-']")?.attr("alt") ?: return null
        val name = getTitle(this.selectFirst("img")?.attr("alt"), code)
        val posterUrl = this.selectFirst("img").getImg()
        listOf(
            "english" to "[SUB-ENGLISH]",
            "chinese" to "[SUB-CHINESE]",
            "uncensored" to "[UNCENSORED]"
        ).forEach { (text, tag) ->
            if (code.contains(text)) tags += tag
        }
        val title = if (tags.isNotBlank()) "$tags $name" else "$name"
        return newAnimeSearchResponse(title.trim(), code, TvType.NSFW) {
            this.posterUrl = posterUrl.replace("cover-t", "cover")
            if (code.contains("-subtitle")) addDubStatus(DubStatus.Subbed)
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
            Log.e("MissAV", "Error en agregar parametros a una URL: $e")
            this
        }
    }
    // Arrelga el Titulo de la pelicula
    private fun getTitle(name: String?, code: String): String {
        return if (titleInCode || name == null) {
            code.replace("-uncensored-leak", "", true)
                .replace("-english-subtitle", "", true)
                .replace("-chinese-subtitle", "", true)
                .uppercase()
        } else name
    }
}
