package com.animeav1

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class Zilla : ExtractorApi() {
    override var name = "HLS"
    override var mainUrl = "https://player.zilla-networks.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
            val mp4 = "$mainUrl/m3u8/${url.substringAfterLast("/")}"
            return listOf(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = mp4,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer ?: ""
                    this.quality = Qualities.P1080.value
                }
            )
    }
}

class Animeav1upn : VidStack() {
    override var mainUrl = "https://animeav1.uns.bio"
}

suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(newExtractorLink(
                name ?: link.source,
                name ?: link.name,
                link.url,
            ) {
                this.quality = when {
                    else -> quality ?: link.quality
                }
                this.type = link.type
                this.referer = link.referer
                this.headers = link.headers
                this.extractorData = link.extractorData
            })
        }
    }
}