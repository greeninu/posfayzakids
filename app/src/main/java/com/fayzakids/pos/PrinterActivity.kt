package com.fayzakids.pos

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PrinterActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var btnDisconnect: Button
    private lateinit var btnTestPrint: Button
    private lateinit var btnEnableBt: Button
    private lateinit var rvDevices: RecyclerView
    private lateinit var tvNoDevices: TextView
    private lateinit var progressBar: ProgressBar

    private val deviceAdapter = DeviceAdapter { device -> connectDevice(device) }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { loadPairedDevices() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) loadPairedDevices()
        else Toast.makeText(this, "Izin Bluetooth diperlukan", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer)

        supportActionBar?.apply {
            title = "🖨️ Pengaturan Printer"
            setDisplayHomeAsUpEnabled(true)
        }

        // Bind views
        tvStatus     = findViewById(R.id.tv_status)
        tvDeviceName = findViewById(R.id.tv_device_name)
        btnDisconnect= findViewById(R.id.btn_disconnect)
        btnTestPrint = findViewById(R.id.btn_test_print)
        btnEnableBt  = findViewById(R.id.btn_enable_bt)
        rvDevices    = findViewById(R.id.rv_devices)
        tvNoDevices  = findViewById(R.id.tv_no_devices)
        progressBar  = findViewById(R.id.progress_bar)

        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = deviceAdapter

        btnDisconnect.setOnClickListener {
            BluetoothPrinterManager.disconnect()
            updateStatusUI()
            Toast.makeText(this, "Printer diputus", Toast.LENGTH_SHORT).show()
        }

        btnTestPrint.setOnClickListener {
            if (!BluetoothPrinterManager.isConnected) {
                Toast.makeText(this, "Printer belum terhubung", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnTestPrint.isEnabled = false
            btnTestPrint.text = "Mencetak..."
            Thread {
                val bytes = EscPosHelper.buildTestPrint()
                val (ok, msg) = BluetoothPrinterManager.print(bytes)
                runOnUiThread {
                    btnTestPrint.isEnabled = true
                    btnTestPrint.text = "🖨️ Test Print"
                    Toast.makeText(this, if (ok) "✅ Test print berhasil!" else "❌ $msg", Toast.LENGTH_LONG).show()
                }
            }.start()
        }

        btnEnableBt.setOnClickListener {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        updateStatusUI()
        requestPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun requestPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
                return
            }
        }
        loadPairedDevices()
    }

    private fun loadPairedDevices() {
        val adapter = BluetoothPrinterManager.getAdapter(this)
        if (adapter == null || !adapter.isEnabled) {
            btnEnableBt.visibility = View.VISIBLE
            tvNoDevices.visibility = View.VISIBLE
            tvNoDevices.text = "Bluetooth tidak aktif.\nAktifkan Bluetooth terlebih dahulu."
            rvDevices.visibility = View.GONE
            return
        }
        btnEnableBt.visibility = View.GONE

        progressBar.visibility = View.VISIBLE
        Thread {
            val devices = BluetoothPrinterManager.getPairedDevices(this)
            runOnUiThread {
                progressBar.visibility = View.GONE
                if (devices.isEmpty()) {
                    tvNoDevices.visibility = View.VISIBLE
                    tvNoDevices.text = "Tidak ada device Bluetooth yang sudah di-pair.\n\nPair printer di Pengaturan → Bluetooth HP terlebih dahulu."
                    rvDevices.visibility = View.GONE
                } else {
                    tvNoDevices.visibility = View.GONE
                    rvDevices.visibility = View.VISIBLE
                    deviceAdapter.setDevices(devices)
                }
            }
        }.start()
    }

    private fun connectDevice(device: BluetoothDevice) {
        progressBar.visibility = View.VISIBLE
        deviceAdapter.setConnecting(device.address)

        Thread {
            val (ok, msg) = BluetoothPrinterManager.connect(device)
            runOnUiThread {
                progressBar.visibility = View.GONE
                deviceAdapter.clearConnecting()
                Toast.makeText(this, if (ok) "✅ $msg" else "❌ $msg", Toast.LENGTH_LONG).show()
                updateStatusUI()
                deviceAdapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun updateStatusUI() {
        val connected = BluetoothPrinterManager.isConnected
        val name = BluetoothPrinterManager.connectedDeviceName

        if (connected) {
            tvStatus.text     = "● TERHUBUNG"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            tvDeviceName.text = name
            tvDeviceName.visibility = View.VISIBLE
            btnDisconnect.isEnabled = true
            btnTestPrint.isEnabled  = true
        } else {
            tvStatus.text     = "○ Tidak Terhubung"
            tvStatus.setTextColor(getColor(android.R.color.darker_gray))
            tvDeviceName.visibility = View.GONE
            btnDisconnect.isEnabled = false
            btnTestPrint.isEnabled  = false
        }
    }

    // ── RecyclerView Adapter ──────────────────────────────────────────────
    class DeviceAdapter(
        private val onClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.VH>() {

        private var devices = listOf<BluetoothDevice>()
        private var connectingAddress: String? = null

        fun setDevices(list: List<BluetoothDevice>) { devices = list; notifyDataSetChanged() }
        fun setConnecting(addr: String) { connectingAddress = addr; notifyDataSetChanged() }
        fun clearConnecting() { connectingAddress = null; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val dev = devices[pos]
            val isConnected  = BluetoothPrinterManager.isConnected &&
                    BluetoothPrinterManager.connectedDeviceName == (dev.name ?: dev.address)
            val isConnecting = connectingAddress == dev.address

            h.tvName.text    = dev.name ?: "Unknown"
            h.tvAddr.text    = dev.address
            h.btnConnect.text = when {
                isConnected  -> "✓ Terhubung"
                isConnecting -> "Menghubungkan..."
                else         -> "Hubungkan"
            }
            h.btnConnect.isEnabled = !isConnecting
            h.btnConnect.setBackgroundColor(
                h.itemView.context.getColor(
                    if (isConnected) android.R.color.holo_green_dark else android.R.color.holo_purple
                )
            )
            h.btnConnect.setOnClickListener {
                if (!isConnected) onClick(dev)
            }
        }

        override fun getItemCount() = devices.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView   = v.findViewById(R.id.tv_device_name)
            val tvAddr: TextView   = v.findViewById(R.id.tv_device_addr)
            val btnConnect: Button = v.findViewById(R.id.btn_connect)
        }
    }
}
