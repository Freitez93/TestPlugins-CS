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
import kotlinx.coroutines.delay
import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class UqLoadCX : Uqload() {
    override var mainUrl = "https://uqload.bz"
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
        val embedResp = app.get(url)
        val pageHtml = embedResp.text
        val dataLinkJson = embedResp.document.select("script")
            .firstOrNull { it.html().contains("dataLink =") }
            ?.html()
            ?.substringAfter("dataLink =")
            ?.substringBefore(";")
            ?.trim()
            ?: run {
                Log.e("Embed69Extractor", "No se encontró dataLink en la página: $url")
                return
            }

        val challenge = Regex("""POW_CHALLENGE\s*=\s*['\"]([^'\"]+)['\"]""")
            .find(pageHtml)?.groupValues?.get(1)
        val salt = Regex("""POW_SALT\s*=\s*['\"]([^'\"]+)['\"]""")
            .find(pageHtml)?.groupValues?.get(1)

        if (challenge == null || salt == null) {
            Log.e("Embed69Extractor", "No se pudo extraer POW_CHALLENGE o POW_SALT de: $url")
            return
        }

        val serversByLang = AppUtils.tryParseJson<List<ServersByLang>>(dataLinkJson)
        if (serversByLang.isNullOrEmpty()) {
            Log.e("Embed69Extractor", "JSON de dataLink no se pudo parsear o está vacío")
            return
        }

        val aesKey = solveEmbed69PoW(challenge, salt)
        if (aesKey == null) {
            Log.e("Embed69Extractor", "PoW failed para: $url")
            return
        }

        serversByLang.amap { lang ->
            val encryptedLinks = lang.sortedEmbeds.mapNotNull { it.link }
            if (encryptedLinks.isEmpty()) return@amap
            encryptedLinks.forEach { encrypted ->
                val decryptedUrl = decryptAESLocal(encrypted, aesKey)
                if (!decryptedUrl.isNullOrBlank()) {
                    loadSourceNameExtractor(
                        lang.videoLanguage ?: "Unknown",
                        fixHostsLinks(decryptedUrl),
                        referer,
                        subtitleCallback,
                        callback
                    )
                } else {
                    Log.e("Embed69Extractor", "Link descifrado inválido para idioma ${lang.videoLanguage}")
                }
            }
        }
    }

    private suspend fun solveEmbed69PoW(challenge: String, salt: String): ByteArray? {
        val md = MessageDigest.getInstance("SHA-256")
        var nonce = 0L
        val maxAttempts = 500000L
        while (nonce < maxAttempts) {
            val input = "$challenge$nonce".toByteArray(Charsets.UTF_8)
            val hash = md.digest(input).joinToString("") { "%02x".format(it) }
            if (hash.startsWith("000")) {
                Log.d("Embed69Extractor", "PoW encontrado nonce=$nonce hash=${hash.take(8)}")
                return MessageDigest.getInstance("SHA-256")
                    .digest("$challenge$nonce$salt".toByteArray(Charsets.UTF_8))
            }
            nonce++
            if (nonce % 10000L == 0L) delay(1)
        }
        return null
    }

    private fun decryptAESLocal(encryptedBase64: String, aesKey: ByteArray): String? {
        return try {
            val raw = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (raw.size < 17) {
                Log.e("Embed69Extractor", "AES decrypt: raw data too short (${raw.size})")
                return null
            }
            val iv = raw.copyOfRange(0, 16)
            val ciphertext = raw.copyOfRange(16, raw.size)
            if (ciphertext.isEmpty()) {
                Log.e("Embed69Extractor", "AES decrypt: ciphertext vacío")
                return null
            }

            val keySpec = SecretKeySpec(aesKey.copyOfRange(0, 32), "AES")
            return try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w("Embed69Extractor", "PKCS5Padding failed: ${e.message}, intentando NoPadding")
                val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                val decrypted = cipher.doFinal(ciphertext)
                val padByte = decrypted.last().toInt() and 0xFF
                val padLen = if (padByte in 1..16 && decrypted.size >= padByte) padByte else 0
                val clean = if (padLen > 0) decrypted.copyOfRange(0, decrypted.size - padLen) else decrypted
                String(clean, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e("Embed69Extractor", "AES decrypt error: ${e.message}")
            null
        }
    }

    //-------------------------------------//
    //              data class             //
    //-------------------------------------//
    data class ServersByLang(
        @JsonProperty("file_id") val fileId: String? = null,
        @JsonProperty("video_language") val videoLanguage: String? = null,
        @JsonProperty("sortedEmbeds") val sortedEmbeds: List<Server> = emptyList(),
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