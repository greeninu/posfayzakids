package com.fayzakids.pos

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * WebAppInterface — bridge antara JavaScript di WebView dan Android native.
 * Terdaftar sebagai window.AndroidPrinter di sisi JavaScript.
 */
class WebAppInterface(
    private val webView: WebView,
    private val context: Context,
    private val onStatusChange: ((connected: Boolean, name: String) -> Unit)? = null
) {

    @JavascriptInterface
    fun isConnected(): Boolean = BluetoothPrinterManager.isConnected

    @JavascriptInterface
    fun getStatus(): String =
        if (BluetoothPrinterManager.isConnected)
            "connected:${BluetoothPrinterManager.connectedDeviceName}"
        else "disconnected"

    @JavascriptInterface
    fun isAndroidApp(): Boolean = true

    /** Cetak struk dari JSON */
    @JavascriptInterface
    fun printReceipt(json: String) {
        Thread {
            try {
                val settings  = PrinterSettings.load(context)
                val bytes     = EscPosHelper.buildReceipt(json, settings)
                val (ok, msg) = BluetoothPrinterManager.print(bytes)
                notifyWeb(ok, msg)
            } catch (e: Exception) {
                notifyWeb(false, "Error: ${e.message}")
            }
        }.start()
    }

    /** Test print */
    @JavascriptInterface
    fun testPrint() {
        Thread {
            try {
                val settings  = PrinterSettings.load(context)
                val bytes     = EscPosHelper.buildTestPrint(settings)
                val (ok, msg) = BluetoothPrinterManager.print(bytes)
                notifyWeb(ok, msg)
            } catch (e: Exception) {
                notifyWeb(false, "Error: ${e.message}")
            }
        }.start()
    }

    /**
     * Tampilkan notifikasi sistem Android.
     * Dipanggil dari JavaScript setelah transaksi berhasil:
     *   window.AndroidPrinter.showNotification("Transaksi Baru", "Total: Rp50.000")
     */
    @JavascriptInterface
    fun showNotification(title: String, body: String) {
        NotificationHelper.createChannel(context)
        NotificationHelper.show(context, title, body)
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
