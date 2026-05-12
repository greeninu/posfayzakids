package com.fayzakids.pos

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class PrinterSettingsActivity : AppCompatActivity() {

    private lateinit var swLogo:       Switch
    private lateinit var imgPreview:   android.widget.ImageView
    private lateinit var btnPickLogo:  Button
    private lateinit var etStoreName:  EditText
    private lateinit var etHeader:     EditText
    private lateinit var etFooter:     EditText
    private lateinit var rgFontSize:   RadioGroup
    private lateinit var rbNormal:     RadioButton
    private lateinit var rbBesar:      RadioButton
    private lateinit var btnSave:      Button

    private var logoPath: String = ""

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { copyLogoToInternal(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer_settings)

        supportActionBar?.apply {
            title = "⚙️ Pengaturan Struk"
            setDisplayHomeAsUpEnabled(true)
        }

        bindViews()
        loadSettings()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun bindViews() {
        swLogo      = findViewById(R.id.sw_logo)
        imgPreview  = findViewById(R.id.img_logo_preview)
        btnPickLogo = findViewById(R.id.btn_pick_logo)
        etStoreName = findViewById(R.id.et_store_name)
        etHeader    = findViewById(R.id.et_header)
        etFooter    = findViewById(R.id.et_footer)
        rgFontSize  = findViewById(R.id.rg_font_size)
        rbNormal    = findViewById(R.id.rb_normal)
        rbBesar     = findViewById(R.id.rb_besar)
        btnSave     = findViewById(R.id.btn_save)
    }

    private fun loadSettings() {
        val s = PrinterSettings.load(this)
        swLogo.isChecked       = s.logoEnabled
        etStoreName.setText(s.storeName)
        etHeader.setText(s.header)
        etFooter.setText(s.footer)
        logoPath = s.logoPath
        if (s.fontSize == 1) rbBesar.isChecked = true else rbNormal.isChecked = true
        updateLogoPreview()
    }

    private fun setupListeners() {
        swLogo.setOnCheckedChangeListener { _, _ -> updateLogoPreview() }

        btnPickLogo.setOnClickListener {
            imagePicker.launch("image/*")
        }

        btnSave.setOnClickListener { saveSettings() }
    }

    private fun copyLogoToInternal(uri: Uri) {
        try {
            val dest = File(filesDir, "printer_logo.png")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            logoPath = dest.absolutePath
            updateLogoPreview()
            Toast.makeText(this, "Logo berhasil dipilih", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memuat gambar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLogoPreview() {
        val show = swLogo.isChecked && logoPath.isNotEmpty()
        imgPreview.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        if (show) {
            val bmp = BitmapFactory.decodeFile(logoPath)
            if (bmp != null) imgPreview.setImageBitmap(bmp)
        }
    }

    private fun saveSettings() {
        val fontSize = if (rbBesar.isChecked) 1 else 0
        val s = PrinterSettings.Settings(
            storeName   = etStoreName.text.toString().trim(),
            header      = etHeader.text.toString().trim(),
            footer      = etFooter.text.toString().trim(),
            fontSize    = fontSize,
            logoEnabled = swLogo.isChecked,
            logoPath    = logoPath
        )
        PrinterSettings.save(this, s)
        Toast.makeText(this, "✅ Pengaturan disimpan", Toast.LENGTH_SHORT).show()
        finish()
    }
}
