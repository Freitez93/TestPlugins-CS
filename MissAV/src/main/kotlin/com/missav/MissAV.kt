package com.missav

import android.util.Log
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element
import kotlinx.coroutines.delay
import kotlin.text.substringBefore

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
        val document = safeAppGet(baseUrl, headers = mapOf("Referer" to mainUrl)).document
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
        val document = safeAppGet(baseUrl).document

        // Verificar si existe una página siguiente buscando el enlace correspondiente
        val hasNextPage = document.selectFirst("a[href*='page=${page + 1}']") != null
        val searchItems = document.select("div[class*='thumbnail']").mapNotNull { 
            it.toMainPageResult()
        }.distinctBy { it.url }
        return newSearchResponseList(searchItems, hasNext = hasNextPage)
    }

    override suspend fun load(url: String): LoadResponse? {
        val (href, time) = url.split("|")
        val baseCode     = href.substringAfterLast("/")
        val document     = safeAppGet(href).document
        val title        = document.select("h1[class*='text-base']").text().trim()
        val plot         = document.selectFirst("meta[property='og:description']")?.attr("content")?.trim()
        val year         = document.selectFirst("time[datetime]")?.text()?.substringBefore("-")?.toIntOrNull()
        val tags         = document.select("span:containsOwn(Genre) ~ a").map { it.text().trim() }
        val duration     = time.toIntOrNull()
        val actors       = document.select("span:containsOwn(Actress) ~ a").map { Actor(it.text()) }
        val m3u8         = """source\s*=\s*['"](.*?)['"]""".toRegex().find(getAndUnpack(document.html()))?.groupValues?.get(1)?.trim()
        val fixTitle     = getTitle(title, baseCode)
        return newMovieLoadResponse(fixTitle, url, TvType.NSFW, m3u8) {
            this.posterUrl = "https://fourhoi.com/$baseCode/cover-n.jpg"
            this.plot = plot
            this.year = year
            this.tags = tags
            this.duration = duration
            addActors(actors)
            if (showPreview) addTrailer("https://fourhoi.com/$baseCode/preview.mp4",addRaw = true)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = data,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://missav.ws"
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
    // --------------------------------
    //       Funciónes auxiliares
    // --------------------------------
    // Función para convertir un elemento HTML a SearchResponse
    private fun Element.toMainPageResult(): SearchResponse? {
        var tags = "" // Etiqueta vacia
        val time = fixTime(this.selectFirst("span.absolute")?.text()?.trim() ?: "")
        val code = this.selectFirst("a[alt*='-']")?.attr("alt") ?: return null
        val title = getTitle(this.selectFirst("img")?.attr("alt"), code)
        val posterUrl = this.selectFirst("img").getImg()
        listOf(
            "english" to "[SUB-ENGLISH]",
            "chinese" to "[SUB-CHINESE]",
            "uncensored" to "[UNCENSORED]"
        ).forEach { (text, tag) ->
            if (code.contains(text)) tags += tag
        }
        val fixTitle = if (tags.isNotBlank()) "$tags $title" else "$title"
        return newAnimeSearchResponse(fixTitle, "$code|$time", TvType.NSFW) {
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
    // Devuelve el tiempo en minutos.
    private fun fixTime(time: String): Int {
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] + parts[1] / 60  // MM:SS → minutos + segundos/60 (truncado)
            3 -> parts[0] * 60 + parts[1] + parts[2] / 60  // HH:MM:SS → horas*60 + minutos + segundos/60 (truncado)
            else -> 0
        }
    }
    // Función para peticiones seguras.
    private suspend fun safeAppGet(
        url: String,
        headers: Map<String, String>? = null,
        retries: Int = 5,
        initialDelayMs: Long = 5000L,
        timeoutMs: Long = 30000L
    ): NiceResponse {
        Log.d(name, "safeAppGet - Intento 1/$retries para URL: $url")
        val nHeaders = if (headers != null) {
            headers
        } else {
            mapOf("Referer" to mainUrl)
        }

        var response = app.get(url, timeout = timeoutMs, headers = nHeaders)
        if (response.isSuccessful) {
            return response
        } else {
            repeat(retries - 1) { attempt ->
                try {
                    Log.d(name, "safeAppGet - Intento ${attempt + 2}/$retries para URL: $url")
                    response = app.get(url, timeout = timeoutMs, headers = nHeaders)

                    if (response.isSuccessful) {
                        return response
                    } else if (response.code == 429) {
                        val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: 15000L
                        Log.w(name, "safeAppGet - Rate limit 429, esperando ${retryAfter}ms para: $url")
                        delay(retryAfter)
                    } else {
                        Log.w(name, "safeAppGet - HTTP ${response.code} no exitoso para: $url")
                    }
                } catch (e: Exception) {
                    Log.e(name, "safeAppGet - Error intento ${attempt + 2}: ${e.message}", e)
                    if (attempt < retries - 2) {
                        delay(initialDelayMs * (1L shl attempt))
                    }
                }
            }
        }
        Log.e(name, "safeAppGet - Fallaron todos los intentos para URL: $url")
        return response
    }
}
