package com.pelisplushd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Uqload
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.amap
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Base64
import android.util.Log

class UqLoadCX : Uqload() {
    override val mainUrl = "https://uqload.bz"
}

class waaw : StreamSB() {
    override var mainUrl = "https://waaw.to"
}

// Extractor para Embed69
object Embed69Extractor {
    suspend fun load(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url).document.select("script")
            .firstOrNull { it.html().contains("dataLink = [") }?.html()
            ?.substringAfter("dataLink = ")
            ?.substringBefore(";")?.let {
                AppUtils.tryParseJson<List<ServersByLang>>(it)?.amap { lang ->
                    val jsonData = LinksRequest(lang.sortedEmbeds.amap { it.link!! })
                    val body = jsonData.toJson().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                    val decrypted = app.post(
                        "https://embed69.org/api/decrypt",
                        requestBody = body
                    ).parsedSafe<Loadlinks>()

                    if (decrypted?.success == true) {
                        decrypted.links.amap {
                            loadSourceNameExtractor(
                                lang.videoLanguage!!,
                                fixHostsLinks(it.link),
                                referer,
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                }
            }
    }

    //-------------------------------------//
    //              data class             //
    //-------------------------------------//
    data class ServersByLang(
        @JsonProperty("file_id") val fileId: String? = null,
        @JsonProperty("video_language") val videoLanguage: String? = null,
        @JsonProperty("sortedEmbeds") val sortedEmbeds: List<Server> = emptyList<Server>(),
    ) {
        data class Server(
            @JsonProperty("servername") val servername: String? = null,
            @JsonProperty("link") val link: String? = null,
        )
    }

    data class LinksRequest(
        val links: List<String>,
    )

    data class Loadlinks(
        val success: Boolean,
        val links: List<Link>,
    ) {
        data class Link(
            val index: Long,
            val link: String,
        )
    }
}

// Extractor para Xupalace
object XupaLaceExtractor {
    suspend fun load(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).documentLarge
        val mapUrl = mapOf(
            ".OD_LAT > li" to "LAT",
            ".OD_ES > li" to "ESP",
            ".OD_EN > li" to "SUB",
            "li[data-lang='0']" to "LAT",
            "li[data-lang='1']" to "ESP",
            "li[data-lang='2']" to "SUB"
        )

        mapUrl.forEach { (selector, language) ->
            val langLinks = document.select(selector)
                .mapNotNull { element ->
                    runCatching {
                        val onclick = element.attr("onclick").takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        extractUrlFromOnclick(onclick)
                    }.onFailure { error ->
                        Log.e("PelisPlusHD", "Error al procesar onclick: ${error.message}", error)
                    }.getOrNull()
                }
                .filterNotNull()

            if (langLinks.isNotEmpty()) {
                langLinks.amap {
                    Log.d("PelisPlusHD", "Host: $it")
                    loadSourceNameExtractor(
                        language,
                        fixHostsLinks(it),
                        referer,
                        subtitleCallback,
                        callback
                    )
                }
            }
        }
    }

    private fun extractUrlFromOnclick(onclick: String): String? {
        // Patrón 1: Match genérico URL (captura lo que esté entre comillas)
        getFirstMatch("""['"](https?:\/\/[^'"]+)['"]""", onclick)?.let { return it }

        // Patrón 2: .php?link=BASE64&servidor=...
        getFirstMatch("""\.php\?link=([^&]+)&servidor=""", onclick)?.let { encoded ->
            return try {
                String(Base64.decode(encoded, Base64.DEFAULT))
            } catch (e: IllegalArgumentException) {
                Log.w("XupaLaceExtractor", "Base64 inválido: $encoded", e)
                null
            }
        }

        return null
    }

    private fun getFirstMatch(pattern: String, input: String): String? {
        return Regex(pattern).find(input)?.groupValues?.get(1)
    }
}

// Funcion Auxiliar para arreglar los URL de Embed69 y XupaLace
private fun fixHostsLinks(url: String): String {
    val replacements = mapOf(
        "hglink.to" to "streamwish.to",
        "swdyu.com" to "streamwish.to",
        "cybervynx.com" to "streamwish.to",
        "dumbalag.com" to "streamwish.to",
        "mivalyo.com" to "vidhidepro.com",
        "dinisglows.com" to "vidhidepro.com",
        "dhtpre.com" to "vidhidepro.com",
        "filemoon.link" to "filemoon.sx",
        "bysedikamoum.com" to "filemoon.sx",
        "sblona.com" to "watchsb.com",
        "lulu.st" to "lulustream.com",
        "uqload.io" to "uqload.com",
        "do7go.com" to "dood.la"
    )

    var result = url
    for ((from, to) in replacements) {
        if (result.contains(from)) {
            result = result.replaceFirst(from, to)
            break // Si solo se espera un dominio por URL, rompemos tras el primer match
        }
    }
    return result
}

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