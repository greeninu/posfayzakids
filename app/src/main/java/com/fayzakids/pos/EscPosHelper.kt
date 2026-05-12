package com.fayzakids.pos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/**
 * EscPosHelper — membangun byte stream ESC/POS untuk printer thermal 58mm
 */
object EscPosHelper {

    private const val WIDTH = 32   // karakter per baris printer 58mm

    // ESC/POS commands
    private val ESC_INIT         = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT   = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_BOLD_ON      = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF     = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_SIZE  = byteArrayOf(0x1D, 0x21, 0x11)
    private val ESC_DOUBLE_H     = byteArrayOf(0x1D, 0x21, 0x10)  // double height only
    private val ESC_NORMAL_SIZE  = byteArrayOf(0x1D, 0x21, 0x00)
    private val ESC_CUT          = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    private val LF               = byteArrayOf(0x0A)

    private fun text(s: String): ByteArray = s.toByteArray(Charsets.ISO_8859_1)
    private fun line(s: String = ""): ByteArray = text(s) + LF
    private fun sep(ch: Char = '-'): ByteArray = line(ch.toString().repeat(WIDTH))

    private fun cols(left: String, right: String): ByteArray {
        val r   = right.take(WIDTH / 2)
        val l   = left.take(WIDTH - r.length - 1)
        val pad = WIDTH - l.length - r.length
        return line(l + " ".repeat(maxOf(1, pad)) + r)
    }

    private fun rupiah(n: Double): String {
        val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
        return "Rp" + fmt.format(n.toLong())
    }

    // ── Logo -> ESC/POS raster (GS v 0) ─────────────────────────────────
    private fun logoBytes(path: String): ByteArray {
        if (path.isBlank()) return ByteArray(0)
        val raw = BitmapFactory.decodeFile(path) ?: return ByteArray(0)

        val maxW  = 384
        val scale = if (raw.width > maxW) maxW.toFloat() / raw.width else 1f
        val w     = ((raw.width * scale).toInt()).let { v -> if (v % 8 == 0) v else (v / 8 + 1) * 8 }
        val h     = (raw.height * scale).toInt()
        val bmp   = Bitmap.createScaledBitmap(raw, w, h, true)

        val bytesPerRow = w / 8
        val imgData     = ByteArray(bytesPerRow * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val px   = bmp.getPixel(x, y)
                val gray = (0.299 * Color.red(px) + 0.587 * Color.green(px) + 0.114 * Color.blue(px)).toInt()
                if (gray < 128) {
                    val idx = y * bytesPerRow + x / 8
                    imgData[idx] = (imgData[idx].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }

        val buf = mutableListOf<Byte>()
        ESC_ALIGN_CENTER.forEach { buf.add(it) }
        // GS v 0 normal density
        listOf(0x1D, 0x76, 0x30, 0x00).forEach { buf.add(it.toByte()) }
        buf.add((bytesPerRow and 0xFF).toByte()); buf.add((bytesPerRow shr 8).toByte())
        buf.add((h and 0xFF).toByte());            buf.add((h shr 8).toByte())
        imgData.forEach { buf.add(it) }
        LF.forEach { buf.add(it) }
        return buf.toByteArray()
    }

    // ── Build struk dari JSON + settings ─────────────────────────────────
    fun buildReceipt(
        json: String,
        settings: PrinterSettings.Settings = PrinterSettings.Settings()
    ): ByteArray {
        val obj = try { JSONObject(json) } catch (_: Exception) { JSONObject() }

        // Android settings override web values ketika tidak kosong
        val namaApp = settings.storeName.ifBlank { obj.optString("appName", "POS SYSTEM") }
        val header  = settings.header.ifBlank     { obj.optString("header",  "") }
        val footer  = settings.footer.ifBlank     { obj.optString("footer",  "Terima kasih!") }

        val faktur  = obj.optString("faktur",  "-")
        val tanggal = obj.optString("tanggal", "")
        val waktu   = obj.optString("waktu",   "")
        val kasir   = obj.optString("kasir",   "")
        val member  = obj.optString("member",  "")
        val metode  = obj.optString("metode",  "Tunai")
        val items   = obj.optJSONArray("items")
        val subtotal= obj.optDouble("subtotal", 0.0)
        val diskon  = obj.optDouble("diskon",   0.0)
        val total   = obj.optDouble("total",    0.0)
        val bayar   = obj.optDouble("bayar",    0.0)
        val kembali = obj.optDouble("kembali",  0.0)
        val poin    = obj.optInt("poinEarned",  0)
        val bigItem = settings.fontSize == 1

        val buf = mutableListOf<Byte>()
        fun add(b: ByteArray) = b.forEach { buf.add(it) }

        add(ESC_INIT)

        // Logo
        if (settings.logoEnabled && settings.logoPath.isNotBlank())
            add(logoBytes(settings.logoPath))

        // Header toko
        add(ESC_ALIGN_CENTER)
        add(ESC_DOUBLE_SIZE); add(ESC_BOLD_ON)
        add(line(namaApp))
        add(ESC_NORMAL_SIZE); add(ESC_BOLD_OFF)
        if (header.isNotBlank()) header.split("\n").forEach { add(line(it.trim())) }
        add(LF)

        // Info transaksi
        add(ESC_ALIGN_LEFT)
        add(cols("No Faktur", faktur))
        val tgl = if (waktu.isNotBlank()) "$tanggal $waktu" else tanggal
        add(cols("Tanggal",  tgl))
        if (kasir.isNotBlank())  add(cols("Kasir",  kasir))
        if (member.isNotBlank()) add(cols("Member", member))
        add(sep())

        // Items
        if (items != null) {
            for (i in 0 until items.length()) {
                val it    = items.getJSONObject(i)
                val nama  = it.optString("nama", "-")
                val qty   = it.optDouble("qty",  1.0)
                val harga = it.optDouble("harga", 0.0)
                val sub   = it.optDouble("subtotal", qty * harga)

                if (bigItem) add(ESC_DOUBLE_H)
                add(line(nama.take(WIDTH)))
                if (bigItem) add(ESC_NORMAL_SIZE)
                add(cols("  ${qty.toInt()} x ${rupiah(harga)}", rupiah(sub)))
            }
        }

        add(sep())
        add(cols("Subtotal", rupiah(subtotal)))
        if (diskon > 0) add(cols("Diskon", "- ${rupiah(diskon)}"))
        add(ESC_BOLD_ON)
        add(cols("TOTAL", rupiah(total)))
        add(ESC_BOLD_OFF)
        add(cols("Bayar ($metode)", rupiah(bayar)))
        if (kembali > 0) add(cols("Kembali", rupiah(kembali)))

        if (poin > 0) {
            add(sep())
            add(ESC_ALIGN_CENTER)
            add(line("* +$poin poin earned"))
        }

        add(sep())
        add(ESC_ALIGN_CENTER)
        if (footer.isNotBlank()) footer.split("\n").forEach { add(line(it.trim())) }
        add(LF); add(LF); add(LF)
        add(ESC_CUT)

        return buf.toByteArray()
    }

    // ── Test print ────────────────────────────────────────────────────────
    fun buildTestPrint(
        settings: PrinterSettings.Settings = PrinterSettings.Settings()
    ): ByteArray {
        val storeName = settings.storeName.ifBlank { "POS Fayzakids" }
        val footer    = settings.footer.ifBlank    { "Terima kasih!" }
        val bigItem   = settings.fontSize == 1

        val buf = mutableListOf<Byte>()
        fun add(b: ByteArray) = b.forEach { buf.add(it) }

        add(ESC_INIT)

        if (settings.logoEnabled && settings.logoPath.isNotBlank())
            add(logoBytes(settings.logoPath))

        add(ESC_ALIGN_CENTER)
        add(ESC_DOUBLE_SIZE); add(ESC_BOLD_ON)
        add(line("TEST PRINT"))
        add(ESC_NORMAL_SIZE); add(ESC_BOLD_OFF)
        add(line(storeName))
        add(sep())
        add(ESC_ALIGN_LEFT)
        add(line("Printer OK!"))
        add(line("Koneksi Bluetooth berhasil"))
        add(line("Siap untuk mencetak struk"))
        add(sep())

        listOf("Bakso Malang" to ("2 x Rp10.000" to "Rp20.000"),
               "Es Teh Manis" to ("1 x Rp5.000"  to "Rp5.000")).forEach { (nama, p) ->
            if (bigItem) add(ESC_DOUBLE_H)
            add(line(nama))
            if (bigItem) add(ESC_NORMAL_SIZE)
            add(cols("  ${p.first}", p.second))
        }

        add(sep())
        add(ESC_BOLD_ON);  add(cols("TOTAL",   "Rp25.000")); add(ESC_BOLD_OFF)
        add(cols("Bayar",   "Rp30.000"))
        add(cols("Kembali", "Rp5.000"))
        add(sep())
        add(ESC_ALIGN_CENTER)
        if (footer.isNotBlank()) footer.split("\n").forEach { add(line(it.trim())) }
        add(LF); add(LF); add(LF)
        add(ESC_CUT)

        return buf.toByteArray()
    }
}
