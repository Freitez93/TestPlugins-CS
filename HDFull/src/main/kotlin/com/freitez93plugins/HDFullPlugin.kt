package com.freitez93plugins

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.freitez93plugins.settings.SettingsFragment
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HDFullPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("HDFullProvider", Context.MODE_PRIVATE)
        registerMainAPI(HDFullProvider(sharedPref))

        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}