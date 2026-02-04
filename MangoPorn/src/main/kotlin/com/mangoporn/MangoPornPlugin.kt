package com.mangoporn

import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.mangoporn.MangoPorn
import androidx.appcompat.app.AppCompatActivity
import android.content.Context

@CloudstreamPlugin
class MangoPornPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MangoPorn())
        registerExtractorAPI(DoodDoply())
        registerExtractorAPI(MixDropMy())
        registerExtractorAPI(LuluVid())
        registerExtractorAPI(LuluVdoo())
        registerExtractorAPI(LuluPvp())
        registerExtractorAPI(VidNest())
        registerExtractorAPI(Vip4me())
        registerExtractorAPI(Player4Me())

        this.openSettings = { ctx: Context ->
            MangoPornSettings.showSettingsDialog(ctx as AppCompatActivity) {
                MainActivity.reloadHomeEvent.invoke(true)
            }
        }
    }
}