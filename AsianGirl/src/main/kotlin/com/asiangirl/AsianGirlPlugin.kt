package com.asiangirl

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AsianGirlPlugin: Plugin() {
    override fun load() {
        registerMainAPI(AsianGirlProvider())

        this.openSettings = { ctx: Context ->
            AsianGirlSettings.showSettingsDialog(ctx as AppCompatActivity) {
                MainActivity.reloadHomeEvent.invoke(true)
            }
        }
    }
}