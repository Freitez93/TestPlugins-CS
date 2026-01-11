package com.hdfull

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Base64
import android.util.Log

object HDFullExtractor {
    suspend fun load(
        dataHash: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val json = decodeHash(dataHash)
        //Log.d("HDFull", "json: $json")

        json.amap {
            val url = getUrlByProvider(it.provider, it.code)
            if (url.isNotEmpty()) {
                loadSourceNameExtractor(fixLang(it.lang), url, referer, subtitleCallback, callback)
            }
        }
    }

    // Arreglar url de HDFull
    private fun getUrlByProvider(providerIdx: String, id: String): String {
        val fixID = id.replace(" ", "")
        return when (providerIdx) {
            "1" -> "https://powvideo.org/$fixID"
            "2" -> "https://streamplay.to/$fixID"
            "6" -> "https://streamtape.com/v/$fixID"
            "12" -> "https://gamovideo.com/$fixID"
            "15" -> "https://mixdrop.bz/f/$fixID"
            "40" -> "https://vidmoly.me/w/$fixID"
            else -> ""
        }
    }

    // Arreglar idioma en HDFull
    private fun fixLang(land: String): String {
        return when (land) {
            "ESPSUB" -> "SUB"
            else -> land
        }
    }

    // Función para decodificar Base64 y devolver un JSON
    private fun decodeHash(str: String): List<ProviderCode> {
        val decodedBytes = Base64.decode(str, Base64.DEFAULT)
        val decodedString = String(decodedBytes)
        val jsonString = decodedString.substrings(14)
        return AppUtils.parseJson<List<ProviderCode>>(jsonString)
    }

    private fun String.obfs(key: Int, n: Int = 126): String {
        if (key % 1 != 0 || n % 1 != 0) {
            return this
        }
        val chars = this.toCharArray()
        for (i in chars.indices) {
            val c = chars[i].code
            if (c <= n) {
                chars[i] = ((chars[i].code + key) % n).toChar()
            }
        }
        return chars.concatToString()
    }

    private fun String.substrings(key: Int, n: Int = 126): String {
        if (key % 1 != 0 || n % 1 != 0) {
            return this
        }
        return this.obfs(n - key)
    }

    // --------------------------------
    //            Data class
    // --------------------------------
    data class ProviderCode(
        val id: String,
        val provider: String,
        val code: String,
        val lang: String,
        val quality: String
    )
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