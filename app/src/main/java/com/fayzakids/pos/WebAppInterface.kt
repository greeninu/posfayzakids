package com.fayzakids.pos

import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * WebAppInterface — bridge antara JavaScript di WebView dan Android native.
 * Terdaftar sebagai window.AndroidPrinter di sisi JavaScript.
 */
class WebAppInterface(
    private val webView: WebView,
    private val onStatusChange: ((connected: Boolean, name: String) -> Unit)? = null
) {

    /** Cek apakah printer sedang terhubung */
    @JavascriptInterface
    fun isConnected(): Boolean = BluetoothPrinterManager.isConnected

    /** Nama device yang sedang terhubung */
    @JavascriptInterface
    fun getStatus(): String {
        return if (BluetoothPrinterManager.isConnected)
            "connected:${BluetoothPrinterManager.connectedDeviceName}"
        else "disconnected"
    }

    /** Apakah berjalan di dalam Android app */
    @JavascriptInterface
    fun isAndroidApp(): Boolean = true

    /**
     * Cetak struk dari JSON.
     * JSON format sama persis dengan yang dikirim printer.js.
     * Dipanggil dari background thread (JavascriptInterface runs on a separate thread).
     */
    @JavascriptInterface
    fun printReceipt(json: String) {
        Thread {
            try {
                val bytes = EscPosHelper.buildReceipt(json)
                val (ok, msg) = BluetoothPrinterManager.print(bytes)
                notifyWeb(ok, msg)
            } catch (e: Exception) {
                notifyWeb(false, "Error: ${e.message}")
            }
        }.start()
    }

    /** Test print untuk verifikasi koneksi */
    @JavascriptInterface
    fun testPrint() {
        Thread {
            try {
                val bytes = EscPosHelper.buildTestPrint()
                val (ok, msg) = BluetoothPrinterManager.print(bytes)
                notifyWeb(ok, msg)
            } catch (e: Exception) {
                notifyWeb(false, "Error: ${e.message}")
            }
        }.start()
    }

    private fun notifyWeb(ok: Boolean, msg: String) {
        val safeMsg = msg.replace("'", "\\'")
        webView.post {
            webView.evaluateJavascript(
                "if(window.onAndroidPrinterResult) window.onAndroidPrinterResult($ok,'$safeMsg')", null
            )
        }
        onStatusChange?.invoke(BluetoothPrinterManager.isConnected, BluetoothPrinterManager.connectedDeviceName)
    }
}
