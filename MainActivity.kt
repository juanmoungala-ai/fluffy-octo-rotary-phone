package com.nucleus.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingExportData: String? = null

    // ── Launchers — tous déclarés AVANT onCreate ──

    /** Import de config JSON */
    private val importConfigLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val text = readText(uri)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.onImportReceived(${jsonStringLiteral(text)})", null
                            )
                        }
                    } catch (e: Exception) {
                        toast("Erreur lecture fichier")
                    }
                }
            }
        }

    /** Export de config JSON */
    private val exportConfigLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    pendingExportData?.let { data ->
                        try {
                            contentResolver.openOutputStream(uri)?.use {
                                it.write(data.toByteArray(Charsets.UTF_8))
                            }
                            toast("Configuration exportée ✓")
                        } catch (e: Exception) {
                            toast("Erreur export")
                        }
                        pendingExportData = null
                    }
                }
            }
        }

    /** Sélecteur PDF pour le bouton 📎 (joindre à une question) */
    private val chatPdfLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val name   = fileName(uri)
                        val b64    = pdfToBase64(uri)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.onChatPDFReceived(${jsonStringLiteral(name)}, '$b64')",
                                null
                            )
                        }
                    } catch (e: Exception) {
                        toast("Erreur chargement PDF : ${e.message}")
                    }
                }
            }
        }

    /** Sélecteur PDF pour les données de contexte permanent */
    private val dataPdfLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val name = fileName(uri)
                        val size = fileSize(uri)
                        val b64  = pdfToBase64(uri)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.onDataPDFReceived(${jsonStringLiteral(name)}, '$b64', $size)",
                                null
                            )
                        }
                    } catch (e: Exception) {
                        toast("Erreur chargement PDF : ${e.message}")
                    }
                }
            }
        }

    /** Sélecteur PDF pour extraction d'axiomes */
    private val axiomPdfLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val b64 = pdfToBase64(uri)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.onAxiomPDFReceived('$b64')", null
                            )
                        }
                    } catch (e: Exception) {
                        toast("Erreur chargement PDF : ${e.message}")
                    }
                }
            }
        }

    // ── onCreate ──

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }

        webView = WebView(this)
        setContentView(webView)
        setupWebView()
        webView.loadUrl("file:///android_asset/app.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled        = true
            domStorageEnabled        = true   // localStorage
            allowFileAccess          = true
            allowContentAccess       = true
            databaseEnabled          = true
            cacheMode                = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls      = false
            displayZoomControls      = false
            useWideViewPort          = true
            loadWithOverviewMode     = true
            mixedContentMode         = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val host = request.url.host ?: return true
                // Autoriser Anthropic API + Google Fonts
                return host !in listOf(
                    "api.anthropic.com",
                    "fonts.googleapis.com",
                    "fonts.gstatic.com"
                )
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean = false
        }
    }

    // ── Pont JS ↔ Android ──

    inner class Bridge {

        @JavascriptInterface
        fun isAndroid(): Boolean = true

        /** Bouton 📎 — joindre un PDF à la question en cours */
        @JavascriptInterface
        fun openPdfPicker() {
            runOnUiThread { launchPdfPicker(chatPdfLauncher) }
        }

        /** Onglet FICHIERS — ajouter des données de contexte permanent */
        @JavascriptInterface
        fun openDataPdfPicker() {
            runOnUiThread { launchPdfPicker(dataPdfLauncher) }
        }

        /** Onglet PRINCIPES — extraire axiomes d'un PDF */
        @JavascriptInterface
        fun openAxiomPdfPicker() {
            runOnUiThread { launchPdfPicker(axiomPdfLauncher) }
        }

        /** Export config JSON */
        @JavascriptInterface
        fun saveExportFile(jsonData: String) {
            pendingExportData = jsonData
            runOnUiThread {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "nucleus-config.json")
                }
                exportConfigLauncher.launch(intent)
            }
        }

        /** Import config JSON */
        @JavascriptInterface
        fun openImportPicker() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                importConfigLauncher.launch(intent)
            }
        }
    }

    // ── Utilitaires ──

    private fun launchPdfPicker(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        launcher.launch(intent)
    }

    private fun readText(uri: Uri): String =
        contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        } ?: ""

    private fun pdfToBase64(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Impossible de lire le fichier")
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun fileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (idx >= 0) it.getString(idx) else "document.pdf"
        } ?: "document.pdf"
    }

    private fun fileSize(uri: Uri): Long {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            it.moveToFirst()
            if (idx >= 0) it.getLong(idx) else 0L
        } ?: 0L
    }

    private fun jsonStringLiteral(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun toast(msg: String) =
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
