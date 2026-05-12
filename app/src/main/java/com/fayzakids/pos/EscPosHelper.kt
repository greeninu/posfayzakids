package com.fayzakids.pos

import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/**
 * EscPosHelper — membangun byte stream ESC/POS untuk printer thermal 58mm
 */
object EscPosHelper {

    private const val WIDTH = 32   // karakter per baris printer 58mm

    // ESC/POS commands
    private val ESC_INIT        = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT  = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER= byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON     = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF    = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11)
    private val ESC_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    private val ESC_CUT         = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    private val LF              = byteArrayOf(0x0A)

    private fun text(s: String): ByteArray = s.toByteArray(Charsets.ISO_8859_1)
    private fun line(s: String = ""): ByteArray = text(s) + LF
    private fun sep(ch: Char = '-'): ByteArray = line(ch.toString().repeat(WIDTH))

    private fun cols(left: String, right: String): ByteArray {
        val r = right.take(WIDTH / 2)
        val l = left.take(WIDTH - r.length - 1)
        val pad = WIDTH - l.length - r.length
        return line(l + " ".repeat(maxOf(1, pad)) + r)
    }

    private fun rupiah(n: Double): String {
        val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
        return "Rp" + fmt.format(n.toLong())
    }

    // ── Build struk dari JSON ─────────────────────────────────────────────
    fun buildReceipt(json: String): ByteArray {
        val obj = try { JSONObject(json) } catch (_: Exception) { JSONObject() }

        val namaApp  = obj.optString("appName",      "POS SYSTEM")
        val header   = obj.optString("header",       "")
        val footer   = obj.optString("footer",       "Terima kasih!")
        val faktur   = obj.optString("faktur",       "-")
        val tanggal  = obj.optString("tanggal",      "")
        val waktu    = obj.optString("waktu",        "")
        val kasir    = obj.optString("kasir",        "")
        val member   = obj.optString("member",       "")
        val metode   = obj.optString("metode",       "Tunai")
        val items    = obj.optJSONArray("items")
        val subtotal = obj.optDouble("subtotal", 0.0)
        val diskon   = obj.optDouble("diskon",   0.0)
        val total    = obj.optDouble("total",    0.0)
        val bayar    = obj.optDouble("bayar",    0.0)
        val kembali  = obj.optDouble("kembali",  0.0)
        val poin     = obj.optInt("poinEarned",  0)

        val buf = mutableListOf<Byte>()
        fun add(b: ByteArray) = b.forEach { buf.add(it) }

        add(ESC_INIT)

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
        add(cols("Tanggal", tgl))
        if (kasir.isNotBlank())  add(cols("Kasir",    kasir))
        if (member.isNotBlank()) add(cols("Member",   member))
        add(sep())

        // Items
        if (items != null) {
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val nama     = it.optString("nama", "-")
                val qty      = it.optDouble("qty",  1.0)
                val harga    = it.optDouble("harga", 0.0)
                val sub      = it.optDouble("subtotal", qty * harga)
                add(line(nama.take(WIDTH)))
                add(cols("  ${qty.toInt()} x ${rupiah(harga)}", rupiah(sub)))
            }
        }

        add(sep())

        // Ringkasan
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
            add(line("⭐ +$poin poin earned"))
            add(ESC_ALIGN_LEFT)
        }

        // Footer
        add(sep())
        add(ESC_ALIGN_CENTER)
        if (footer.isNotBlank()) footer.split("\n").forEach { add(line(it.trim())) }
        add(LF); add(LF); add(LF)
        add(ESC_CUT)

        return buf.toByteArray()
    }

    // ── Test print ───────────────────────────────────────────────────────
    fun buildTestPrint(): ByteArray {
        val buf = mutableListOf<Byte>()
        fun add(b: ByteArray) = b.forEach { buf.add(it) }

        add(ESC_INIT)
        add(ESC_ALIGN_CENTER)
        add(ESC_DOUBLE_SIZE); add(ESC_BOLD_ON)
        add(line("TEST PRINT"))
        add(ESC_NORMAL_SIZE); add(ESC_BOLD_OFF)
        add(line("POS Fayzakids"))
        add(sep())
        add(ESC_ALIGN_LEFT)
        add(line("Printer OK!"))
        add(line("Koneksi Bluetooth berhasil"))
        add(line("Siap untuk mencetak struk"))
        add(sep())
        add(ESC_ALIGN_CENTER)
        add(line("============================"))
        add(cols("Item",         "Harga"))
        add(cols("Bakso 2x",     "Rp10.000"))
        add(cols("Es Teh",       "Rp5.000"))
        add(sep('-'))
        add(ESC_BOLD_ON)
        add(cols("TOTAL",        "Rp15.000"))
        add(ESC_BOLD_OFF)
        add(cols("Bayar",        "Rp20.000"))
        add(cols("Kembali",      "Rp5.000"))
        add(sep())
        add(ESC_ALIGN_CENTER)
        add(line("Terima kasih!"))
        add(LF); add(LF); add(LF)
        add(ESC_CUT)

        return buf.toByteArray()
    }
}
