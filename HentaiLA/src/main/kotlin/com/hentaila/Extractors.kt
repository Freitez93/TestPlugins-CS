package com.hentaila

import android.annotation.SuppressLint
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// --------------------------------
//           VidHidePro
// --------------------------------
class VidHideHub : VidHidePro() {
    override val mainUrl = "https://vidhidehub.com"
}

class Movearnpre : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://movearnpre.com"
}

class Dintezuvio : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://dintezuvio.com"
}

class Riderjet: VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://riderjet.com"
}

// --------------------------------
//          PlayerZilla
// --------------------------------
class PlayerZilla : ExtractorApi() {
    override var name = "PlayerZilla"
    override var mainUrl = "https://player.zilla-networks.com"
    override val requiresReferer = false

    @SuppressLint("SuspiciousIndentation")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val video = "$mainUrl/m3u8/${url.substringAfterLast("/")}"
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = video,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P1080.value
            }
        )
    }
}

// --------------------------------
//              Utils
// --------------------------------
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