package com.sololatino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.ByseSX
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class ByseExtractor : ByseSX() {
    override var mainUrl = "https://bysedikamoum.com"
    override var name = "Byse"
}

// Extractor para Embed69
object Embed69Extractor {
    suspend fun load(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var aesKey: ByteArray
        app.get(url).document.select("script")
            .firstOrNull { it.html().contains("dataLink = [") }?.html()?.let {
                val powChallenge = it.substringAfter("const POW_CHALLENGE = '").substringBefore("';")
                val powDifficulty = it.substringAfter("const POW_DIFFICULTY = ").substringBefore(";").toInt()
                val powSalt = it.substringAfter("const POW_SALT = '").substringBefore("';")
                aesKey = deriveAesKey(powChallenge, powDifficulty, powSalt)
                it
            }?.substringAfter("dataLink = ")
            ?.substringBefore(";")?.let {
                AppUtils.tryParseJson<List<ServersByLang>>(it)?.amap { lang ->
                    val links = lang.sortedEmbeds.amap { embed ->
                        decryptAES(embed.link!!, aesKey)
                    }
                    links.filterNotNull().amap { link ->
                        loadSourceNameExtractor(
                            lang.videoLanguage!!,
                            link,
                            referer,
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
    }

    fun deriveAesKey(challenge: String, difficulty: Int, salt: String): ByteArray {
        val prefix = "0".repeat(difficulty)
        var nonce: Long = 0
        val batchSize = 5000

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }

        fun sha256Bytes(input: String): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
        }

        while (true) {
            for (i in 0 until batchSize) {
                val hashHex = sha256Hex(challenge + nonce)
                if (hashHex.startsWith(prefix)) {
                    return sha256Bytes(challenge + nonce + salt)
                }
                nonce++
            }
        }
    }
    fun decryptAES(encryptedBase64: String, aesKey: ByteArray): String? {
        return try {
            val raw = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = raw.copyOfRange(0, 16)
            val ciphertext = raw.copyOfRange(16, raw.size)
            val keyBytes = aesKey.copyOfRange(0, 32)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
    data class ServersByLang(
        @JsonProperty("file_id") val fileId: String? = null,
        @JsonProperty("video_language") val videoLanguage: String? = null,
        @JsonProperty("sortedEmbeds") val sortedEmbeds: List<Server> = emptyList(),
        @JsonProperty("downloadEmbeds") val downloadEmbeds: List<Server> = emptyList(),
    )
    data class Server(
        @JsonProperty("servername") val servername: String? = null,
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("type") val type: String? = null,
    )
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
                        Log.e("XupaLaceExtractor", "Error al procesar onclick: ${error.message}", error)
                    }.getOrNull()
                }

            if (langLinks.isNotEmpty()) {
                langLinks.amap {
                    loadSourceNameExtractor(
                        language,
                        it,
                        referer,
                        subtitleCallback,
                        callback
                    )
                }
            }
        }
    }

    private fun extractUrlFromOnclick(onclick: String): String? {
        // Patrón 1: .php?link=BASE64&servidor=...
        getFirstMatch("""\.php\?link=([^&]+)&servidor=""", onclick)?.let { encoded ->
            return try {
                String(Base64.decode(encoded, Base64.DEFAULT))
            } catch (e: IllegalArgumentException) {
                Log.w("XupaLaceExtractor", "Base64 inválido: $encoded", e)
                null
            }
        }
        // Patrón 2: Match genérico URL (captura lo que esté entre comillas)
        getFirstMatch("""['"](https?:\/\/[^'"]+)['"]""", onclick)?.let { return it }
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
        "minochinos.com" to "vidhidepro.com",
        "filemoon.link" to "bysedikamoum.com",
        "filemoon.sx" to "bysedikamoum.com",
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
private val extractorCallbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
suspend fun loadSourceNameExtractor(
    prefix: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
) {
    val fixUrl = fixHostsLinks(url)
    Log.d("loadExtractor", "Ori: $url -> Fix: $fixUrl")
    loadExtractor(fixUrl, referer, subtitleCallback) { link ->
        extractorCallbackScope.launch {
            callback.invoke(
                newExtractorLink(
                    "$prefix [${link.source}]",
                    "$prefix [${link.source}]",
                    link.url,
                ) {
                    this.quality = link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                    Log.d("loadExtractor", "Link: $link")
                }
            )
        }
    }
}