package com.pelisplushd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.Uqload
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.*

class FileMoonlink : FilemoonV2() {
    override var mainUrl = "https://filemoon.link"
    override var name = "FileMoon"
}

class Mivalyo : VidHidePro() {
    override var name = "VidHide"
    override var mainUrl = "https://mivalyo.com"
}

class StreamwishHG : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to"
}

class UqLoadCX : Uqload() {
    override val mainUrl = "https://uqload.cx"
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

suspend fun decryptLinks(url: String): Map<String, List<String>> {
    val mainUrl = "https://embed69.org"
    val res = app.get(url).documentLarge
    val jsonString = res.selectFirst("script:containsData(dataLink)")?.data()
        ?.substringAfter("dataLink = ")
        ?.substringBefore(";")

    val allLinksByLanguage = mutableMapOf<String, MutableList<String>>()
    if (jsonString != null) {
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val fileObject = jsonArray.getJSONObject(i)
                val language = fileObject.getString("video_language")
                val embeds = fileObject.getJSONArray("sortedEmbeds")
                val serverLinks = mutableListOf<String>()
                for (j in 0 until embeds.length()) {
                    val embedObj = embeds.getJSONObject(j)
                    embedObj.optString("link").let { link ->
                        if (link.isNotBlank()) serverLinks.add("\"$link\"")
                    }
                }
                val json = """ {"links":$serverLinks} """.trimIndent()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val decrypted = app.post("$mainUrl/api/decrypt", requestBody = json).parsedSafe<Loadlinks>()
                if (decrypted?.success == true) {
                    val links = decrypted.links.map { it.link }
                    val listForLang = allLinksByLanguage.getOrPut(language) { mutableListOf() }
                    listForLang.addAll(links)
                }
            }
        } catch (e: Exception) {
            throw ErrorLoadingException("Error al cargar los enlaces: ${e.message}")
        }
    } else {
        println("dataLink not found in response")
    }
    return allLinksByLanguage
}

data class Link(val index: Long, val link: String)
data class Loadlinks(val success: Boolean, val links: List<Link>)