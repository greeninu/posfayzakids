package com.fayzakids.pos

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user pilih allow/deny, lanjut saja */ }

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

        webView = findViewById(R.id.webView)

        NotificationHelper.createChannel(this)
        requestNotificationPermission()
        setupWebView()
        loadServerUrl()
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
        val bridge = WebAppInterface(webView, this)
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
        }
    }
}
