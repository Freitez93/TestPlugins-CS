package com.sololatino

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SoloLatinoPlugin: Plugin() {
    override fun load() {
        registerExtractorAPI(ByseExtractor())
        registerMainAPI(SoloLatinoProvider())

        this.openSettings = { ctx: Context ->
            SoloLatinoSettings.showSettingsDialog(ctx as AppCompatActivity) {
                MainActivity.reloadHomeEvent.invoke(true)
            }
        }
    }
}