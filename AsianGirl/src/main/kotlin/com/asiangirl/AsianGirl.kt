package com.asiangirl

import android.util.Log
import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import kotlin.random.Random

class AsianGirlProvider : MainAPI() {
    // Variables en Settings.
    private val showPreview         = AsianGirlSettings.getShowTrailer()

    // Variables en MainAPI.
    override var mainUrl            = "https://asiangirl.porn"
    override var name               = "AsianGirl+"
    override var lang               = "en"
    override val hasMainPage        = true
    override val hasQuickSearch     = true
    override val hasDownloadSupport = true
    override val supportedTypes     = setOf(TvType.NSFW)
    override val mainPage get()     = mainPageOf(
        *(try {
            AsianGirlSettings.getOrderedAndEnabledCategories().toTypedArray()
        } catch (e: Exception) {
            Log.e("AsianGirl", "No se pudieron cargar las categorías, usando la lista de respaldo: ${e.message}")
            arrayOf(
                "/new" to "Latest",
                "/hot" to "Most Viewed",
                "/categories/amateur" to "Series: Amateur",
                "/categories/china-av" to "Series: China AV",
                "/categories/japan-producer" to "Series: Japan Producers"
            )
        })
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Construir la URL final añadiendo paginación si es necesario
        val baseUrl = fixUrl(request.data).lowercase()
        val url = if (page == 1) "$baseUrl/" else "$baseUrl/$page/"

        val document = app.get(url).document
        // Verificar si existe una página siguiente buscando el enlace correspondiente
        val hasNextPage = document.selectFirst("option[data-parameters*=';from:${page + 1}']") != null
        // Extraer los elementos de la lista usando el selector determinado
        val homeItems = document.select("figure[class*='aspect-ratio']").mapNotNull {
            it.toMainPageResult()
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = homeItems,
                isHorizontalImages = true
            ),
            hasNext = hasNextPage
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)
    override suspend fun search(query: String, page: Int): SearchResponseList {
        val baseUrl = if (page == 1) "$mainUrl/search/$query/" else "$mainUrl/search/$query/$page/"
        val document = app.get(baseUrl).document
        val hasNextPage = document.selectFirst("option[data-parameters*=';from:${page + 1}']") != null
        val homeSearch = document.select("figure[class*='aspect-ratio']").mapNotNull {
            it.toMainPageResult()
        }
        return newSearchResponseList(homeSearch, hasNext = hasNextPage)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge

        // Recolectamos la Información de la Película/Anime/OVA
        val mediaTitle = document.selectFirst("meta[property='og:title']")!!.attr("content")
        val mediaPoster = document.selectFirst("meta[property='og:image']")!!.attr("content")
        val mediaYear = document.selectFirst("meta[property='video:release_date']")!!.attr("content")
        val mediaUploaded = document.selectFirst("div[class*='details__meta'] > time")?.text()
        val mediaLikes = document.selectFirst("span[class*='menu-bar__counter']")?.text() ?: "0"
        val mediaViews = document.selectFirst("span:contains(Views)")?.text()?.replace("Views", "")?.trim() ?: "0"
        val mediaDuration = document.selectFirst("meta[property='video:duration']")!!.attr("content").toInt()
        val mediaActors = document.select("a[href*='/s/']").map { Actor(it.text()) }
        val mediaGenre = document.select("div[class*='content-details__meta'] > a").map { it.text() }
        val mediaVideo = document.selectFirst("script:containsData(var stream =)")?.let { script ->
            val match = Regex("""var stream = '(https:.+\.m3u8)';""").find(script.data())
            if (match != null) {
                match.groupValues[1]
            } else {
                Log.e("AsianGirl", "No se pudo extraer la URL del video en el script.")
                null
            }
        }
        return newMovieLoadResponse(mediaTitle, url, TvType.NSFW, mediaVideo) {
            //this.backgroundPosterUrl = mediaBackDrops
            this.posterUrl = mediaPoster
            this.logoUrl = "https://asiangirl.porn/a/i/letter-no-bg.png"
            this.plot = """
                🎥 $mediaTitle.<br><br>
                ${colorText("🔹 Views", "#218b7b")}: ${mediaViews}<br>
                ${colorText("🔹 Likes", "#218b7b")}: ${mediaLikes}<br>
                ${colorText("🔹 Uploaded", "#218b7b")}: ${mediaUploaded}.
            """
            this.year = mediaYear?.substringBefore("-")?.toIntOrNull()
            //this.score = Score.from10(mediaInfo?.score)
            this.duration = (mediaDuration / 60) // Convertir a minutos
            this.tags = mediaGenre
            this.recommendations = document.select("figure[class*='aspect-ratio']").mapNotNull {
                it.toMainPageResult()
            }
            addActors(mediaActors)
            if (showPreview) addTrailer(imgToPreview(mediaPoster),addRaw = true)
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
                this.referer = "https://asiangirl.porn/"
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
        val title = this.selectFirst("img")?.attr("alt") ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img").getImg()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }
    // Función para extraer la img de un Element.
    private fun Element?.getImg(
        fallback: String = "https://asiangirl.porn/a/i/video-placeholder.png"
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
    // Función para corregir URLs de preview.
    private fun imgToPreview(urlImagen: String): String {
        // Dividir la URL en partes usando "/"
        val partes = urlImagen.split("/")
        // Obtener el número de la carpeta y el número del video
        val numeroVideo = partes[partes.size - 2]   // En este caso, es el mismo que la carpeta
        val nuevaUrl = urlImagen.replace("preview.m3u8.jpg", "${numeroVideo}_preview.mp4")
        return nuevaUrl
    }
    // Función para colorear texto en el plot.
    private fun colorText(text: String, color: String?): String {
        if (color != null) {
            return "<span style='color:$color;'>$text</span>"
        } else {
            return "<span>$text</span>"
        }
    }
}