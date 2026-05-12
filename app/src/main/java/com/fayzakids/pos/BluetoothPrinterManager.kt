package com.fayzakids.pos

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import java.io.OutputStream
import java.util.UUID

/**
 * BluetoothPrinterManager — singleton untuk mengelola koneksi BT SPP
 */
object BluetoothPrinterManager {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    var connectedDeviceName: String = ""
        private set

    val isConnected: Boolean
        get() = socket?.isConnected == true && outputStream != null

    // ── Connect ───────────────────────────────────────────────────────────
    /**
     * Hubungkan ke device. Harus dipanggil dari background thread.
     * @return Pair(ok, message)
     */
    fun connect(device: BluetoothDevice): Pair<Boolean, String> {
        disconnect()
        return try {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            socket = s
            outputStream = s.outputStream
            connectedDeviceName = device.name ?: device.address
            Pair(true, "Terhubung ke ${connectedDeviceName}")
        } catch (e: Exception) {
            disconnect()
            Pair(false, "Gagal: ${e.message}")
        }
    }

    // ── Print ─────────────────────────────────────────────────────────────
    /**
     * Kirim byte array ke printer. Harus dipanggil dari background thread.
     * @return Pair(ok, message)
     */
    fun print(data: ByteArray): Pair<Boolean, String> {
        val out = outputStream
            ?: return Pair(false, "Printer belum terhubung")
        return try {
            // Chunk 200 byte agar tidak overflow buffer printer murah
            data.toList().chunked(200).forEach { chunk ->
                out.write(chunk.toByteArray())
                out.flush()
                Thread.sleep(50)
            }
            Pair(true, "Cetak berhasil")
        } catch (e: Exception) {
            disconnect()
            Pair(false, "Error cetak: ${e.message}")
        }
    }

    // ── Disconnect ────────────────────────────────────────────────────────
    fun disconnect() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        socket = null
        connectedDeviceName = ""
    }

    // ── Helper — daftar device paired ────────────────────────────────────
    fun getPairedDevices(context: Context): List<BluetoothDevice> {
        if (!hasPermission(context)) return emptyList()
        val adapter = getAdapter(context) ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    fun getAdapter(context: Context): BluetoothAdapter? {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bm.adapter
    }

    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }
}
