package com.fayzakids.pos

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fabPrinter: ImageButton

    companion object {
        const val PREFS_NAME   = "pos_prefs"
        const val KEY_URL      = "server_url"
        const val DEFAULT_URL  = "https://pos.fayzakids.com"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.apply {
            title = "POS Fayzakids"
        }

        webView    = findViewById(R.id.webView)
        fabPrinter = findViewById(R.id.fab_printer)

        setupWebView()
        setupFab()

        loadServerUrl()
    }

    override fun onResume() {
        super.onResume()
        updateFabColor()
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothPrinterManager.disconnect()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    // ── Options Menu ──────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "🖨️ Printer")
        menu.add(0, 2, 1, "🔄 Reload")
        menu.add(0, 3, 2, "🌐 Ganti URL Server")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> { openPrinterActivity(); true }
            2 -> { webView.reload(); true }
            3 -> { showUrlDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── FAB ───────────────────────────────────────────────────────────────

    private fun setupFab() {
        updateFabColor()

        fabPrinter.setOnClickListener {
            openPrinterActivity()
        }

        fabPrinter.setOnLongClickListener {
            showUrlDialog()
            true
        }
    }

    private fun updateFabColor() {
        val color = if (BluetoothPrinterManager.isConnected)
            getColor(android.R.color.holo_green_dark)
        else
            0xFF7B1FA2.toInt()   // Material Purple 700
        fabPrinter.setColorFilter(color)
    }

    private fun openPrinterActivity() {
        startActivity(Intent(this, PrinterActivity::class.java))
    }

    // ── Server URL ────────────────────────────────────────────────────────

    private fun getServerUrl(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    private fun saveServerUrl(url: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, url).apply()
    }

    private fun loadServerUrl() {
        webView.loadUrl(getServerUrl())
    }

    private fun showUrlDialog() {
        val current = getServerUrl()
        val et = EditText(this).apply {
            setText(current)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("🌐 URL Server")
            .setMessage("Masukkan alamat URL server POS:")
            .setView(et)
            .setPositiveButton("Simpan & Muat") { _, _ ->
                var url = et.text.toString().trim()
                if (url.isEmpty()) url = DEFAULT_URL
                if (!url.startsWith("http")) url = "https://$url"
                saveServerUrl(url)
                webView.loadUrl(url)
                Toast.makeText(this, "URL disimpan: $url", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ── WebView Setup ─────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled      = true
            domStorageEnabled      = true
            allowFileAccess        = true
            setSupportZoom(false)
            useWideViewPort        = true
            loadWithOverviewMode   = true
            mixedContentMode       = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.loadUrl("file:///android_asset/offline.html")
                }
            }
        }
        webView.webChromeClient = WebChromeClient()

        // Bridge baru — diakses dari JS sebagai window.AndroidPrinter
        val bridge = WebAppInterface(webView) { connected, name ->
            runOnUiThread { updateFabColor() }
        }
        webView.addJavascriptInterface(bridge, "AndroidPrinter")

        // Legacy bridge — window.AndroidBluetooth (backward compat)
        webView.addJavascriptInterface(LegacyBridge(), "AndroidBluetooth")
    }

    // ── Legacy Bridge (AndroidBluetooth) — backward compat ───────────────
    inner class LegacyBridge {

        @JavascriptInterface
        fun isConnected(): Boolean = BluetoothPrinterManager.isConnected

        @JavascriptInterface
        fun showDevicePicker() {
            openPrinterActivity()
        }

        @JavascriptInterface
        fun getPairedDevices(): String {
            val devices = BluetoothPrinterManager.getPairedDevices(this@MainActivity)
            val sb = StringBuilder("[")
            devices.forEachIndexed { i, d ->
                if (i > 0) sb.append(",")
                val name = (d.name ?: "Unknown").replace("\"", "\\\"")
                sb.append("""{"name":"$name","address":"${d.address}"}""")
            }
            sb.append("]")
            return sb.toString()
        }

        @JavascriptInterface
        fun disconnect() {
            BluetoothPrinterManager.disconnect()
            runOnUiThread { updateFabColor() }
        }
    }
}
