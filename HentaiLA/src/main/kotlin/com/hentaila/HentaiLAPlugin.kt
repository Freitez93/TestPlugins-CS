package com.hentaila

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HentaiLaPlugin: Plugin() {
    override fun load() {
        registerMainAPI(HentaiLAProvider())
        registerExtractorAPI(VidHideHub())
        registerExtractorAPI(Movearnpre())
        registerExtractorAPI(Dintezuvio())
        registerExtractorAPI(Riderjet())
        registerExtractorAPI(PlayerZilla())

        this.openSettings = { ctx: Context ->
            HentaiLASettings.showSettingsDialog(ctx as AppCompatActivity) {
                MainActivity.reloadHomeEvent.invoke(true)
            }
        }
    }
}