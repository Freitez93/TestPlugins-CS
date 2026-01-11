package com.hdfull.settings

import android.util.Log
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.hdfull.BuildConfig
import com.hdfull.HDFullPlugin

class SettingsFragment(
    plugin: HDFullPlugin,
    private val sharedPref: SharedPreferences,
) : BottomSheetDialogFragment() {
    private val res = plugin.resources ?: throw Exception("Unable to read resources")

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val id = res.getIdentifier("settings_fragment", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = res.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = false // optional: prevent dragging at all
        }
    }


    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val GUID_Input = view.findView<EditText>("GUID_Input")
        val PHPSESSID_Input = view.findView<EditText>("PHPSESSID_Input")
        val addButton = view.findView<Button>("addButton")
        val resetButton = view.findView<Button>("resetButton")
        val loginButton = view.findView<Button>("loginButton")
        val webView = view.findView<WebView>("authWebView")
        val GUID_Data = sharedPref.getString("guid", null)
        val PHPSESSID_Data = sharedPref.getString("PHPSESSID", null)

        // Si tenemos guardados los datos los agregamos al input.
        GUID_Data?.let { GUID_Input.setText(GUID_Data) }
        PHPSESSID_Data?.let { PHPSESSID_Input.setText(PHPSESSID_Data) }

        setupWebView(webView)

        loginButton.setOnClickListener {
            webView.visibility = View.VISIBLE
            webView.loadUrl("https://hdfull.love/login")
        }

        addButton.setOnClickListener {
            val guidToken = GUID_Input.text.toString().trim()
            val sessidToken = PHPSESSID_Input.text.toString().trim()
            if (guidToken.isNotEmpty() && sessidToken.isNotEmpty()) {
                sharedPref.edit {
                    putString("guid", guidToken)
                    putString("PHPSESSID", sessidToken)
                }

                val ctx = context ?: run {
                    showToast("Error: el contexto es nulo")
                    return@setOnClickListener
                }

                AlertDialog.Builder(ctx)
                    .setTitle("Guardar y recargar")
                    .setMessage("Los cambios se han guardado. ¿Quieres reiniciar la aplicación para aplicarlos?")
                    .setPositiveButton("Sí") { _, _ ->
                        dismiss()
                        restartApp()
                    }
                    .setNegativeButton("No") { _, _ ->
                        dismiss()
                    }
                    .show()
            } else {
                showToast("Por favor ingresa los token válidos")
            }
        }

        resetButton.setOnClickListener {
            sharedPref.edit()?.apply {
                remove("guid")
                remove("PHPSESSID")
                apply()
            }
            GUID_Input.setText("")
            PHPSESSID_Input.setText("")
            showToast("Los datos se reiniciaron correctamente. Reinicie la aplicación.")
            dismiss()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Mobile Safari/537.36"

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // Resize WebView to content height
                view?.evaluateJavascript(
                    "(function() { return document.body.scrollHeight; })();"
                ) { value ->
                    val height = value.replace("\"", "").toFloatOrNull()
                    if (height != null) {
                        val density = resources.displayMetrics.density
                        val layoutParams = view.layoutParams
                        layoutParams.height = (height * density).toInt()
                        view.layoutParams = layoutParams
                    }
                }

                // Existing token scraping logic
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url ?: "")

                val PHPSESSID_Data = cookies?.split(";")
                    ?.map { it.trim() }
                    ?.find { it.startsWith("PHPSESSID=") }
                    ?.removePrefix("PHPSESSID=")
                val GUID_Data = cookies?.split(";")
                    ?.map { it.trim() }
                    ?.find { it.startsWith("guid=") }
                    ?.removePrefix("guid=")

                if (!GUID_Data.isNullOrEmpty() && view != null) {
                    activity?.runOnUiThread {
                        val GUID_Input = requireView().findViewById<EditText>(
                            res.getIdentifier("GUID_Input", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
                        )
                        val PHPSESSID_Input = requireView().findViewById<EditText>(
                            res.getIdentifier("PHPSESSID_Input", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
                        )

                        GUID_Input.setText(GUID_Data)
                        PHPSESSID_Input.setText(PHPSESSID_Data)

                        sharedPref.edit()?.apply {
                            putString("guid", GUID_Data)
                            putString("PHPSESSID", PHPSESSID_Data)
                            apply()
                        }

                        showToast("Inicio de sesión exitoso!")
                        webView.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun restartApp() {
        val context = requireContext().applicationContext
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component

        if (componentName != null) {
            val restartIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(restartIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
