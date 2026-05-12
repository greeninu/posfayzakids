package com.fayzakids.pos

import android.content.Context

object PrinterSettings {
    private const val PREFS = "printer_settings"

    fun save(ctx: Context, s: Settings) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("store_name",  s.storeName)
            putString("header",      s.header)
            putString("footer",      s.footer)
            putInt("font_size",      s.fontSize)
            putBoolean("logo_en",    s.logoEnabled)
            putString("logo_path",   s.logoPath)
            apply()
        }
    }

    fun load(ctx: Context): Settings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            storeName   = p.getString("store_name", "")           ?: "",
            header      = p.getString("header",     "")           ?: "",
            footer      = p.getString("footer",     "")           ?: "",
            fontSize    = p.getInt("font_size", 0),
            logoEnabled = p.getBoolean("logo_en", false),
            logoPath    = p.getString("logo_path", "")            ?: ""
        )
    }

    data class Settings(
        val storeName:   String  = "",
        val header:      String  = "",
        val footer:      String  = "",
        val fontSize:    Int     = 0,   // 0=Normal, 1=Besar (double height)
        val logoEnabled: Boolean = false,
        val logoPath:    String  = ""
    )
}
