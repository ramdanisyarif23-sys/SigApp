package com.example.sigapp.ui


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sigapp.R
import com.example.sigapp.engine.BleEngine
import com.example.sigapp.engine.NearbyEngine
import com.google.android.material.button.MaterialButton
import android.annotation.SuppressLint // Pastikan ini ada di deretan atas

@SuppressLint("MissingPermission")
class HubActivity : AppCompatActivity() {

    companion object { var currentUserName = "User" }

    // Variabel untuk Scanner LoRa
    private val listNodeLora = mutableListOf<BluetoothDevice>()
    private lateinit var loraAdapter: ArrayAdapter<String>
    private val listNamaNode = mutableListOf<String>()

    // Penangkap Sinyal Bluetooth (Receiver)
    // Penangkap Sinyal Bluetooth (Receiver)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {

                // 1. SOLUSI DEPRECATED: Penyesuaian cara baca untuk Android baru vs lama
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                }

                // 2. SOLUSI NULL SAFETY: Jika device kosong, langsung hentikan proses ini
                if (device == null) return

                // Cek Izin Nama Device
                val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return
                } else {
                    device.name // Hapus tanda tanya, karena device sudah dipastikan tidak null
                }

                // FILTER: Hanya tangkap alat berawalan "SIG_NODE_"
                if (deviceName != null && deviceName.startsWith("SIG_NODE_")) {
                    if (!listNodeLora.any { it.address == device.address }) {
                        listNodeLora.add(device) // Error listNodeLora.add hilang di sini!
                        loraAdapter.add(deviceName)
                        loraAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        requestNetworkPermissions()

        val btnScanLora = findViewById<MaterialButton>(R.id.btn_scan_lora)
        val btnSetupNearby = findViewById<MaterialButton>(R.id.btn_setup_nearby)
        val btnMasukChat = findViewById<MaterialButton>(R.id.btn_masuk_chat)

        // 1. TOMBOL NEARBY
        btnSetupNearby.setOnClickListener {
            showNameAndRoleDialog()
        }

        // 2. TOMBOL SCAN LORA (Dinamis)
        btnScanLora.setOnClickListener {
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val btAdapter = btManager.adapter

            if (btAdapter == null || !btAdapter.isEnabled) {
                Toast.makeText(this, "Nyalakan Bluetooth terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Bersihkan daftar lama setiap kali tombol ditekan
            listNodeLora.clear()
            listNamaNode.clear()
            loraAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listNamaNode)

            val dialog = AlertDialog.Builder(this)
                .setTitle("Mencari Jembatan LoRa...")
                .setAdapter(loraAdapter) { _, which ->
                    val deviceTerpilih = listNodeLora[which]
                    btAdapter.cancelDiscovery() // Hentikan scan jika sudah memilih
                    Toast.makeText(this, "Menyambungkan ke ${deviceTerpilih.name}...", Toast.LENGTH_SHORT).show()

                    BleEngine.connect(deviceTerpilih.address, btAdapter) { sukses, pesan ->
                        runOnUiThread {
                            Toast.makeText(this, pesan, Toast.LENGTH_SHORT).show()
                            if (sukses) {
                                findViewById<TextView>(R.id.tv_status_lora).text = "Terhubung: ${deviceTerpilih.name}"
                            }
                        }
                    }
                }
                .setNegativeButton("Batal") { _, _ -> btAdapter.cancelDiscovery() }
                .create()

            dialog.show()
            btAdapter.startDiscovery()
        }

        // 3. TOMBOL MASUK CHAT
        btnMasukChat.setOnClickListener {
            if (currentUserName == "User") {
                val input = EditText(this).apply { hint = "Nama Anda"; setPadding(50,50,50,50) }
                AlertDialog.Builder(this).setTitle("Identitas Dasar").setView(input)
                    .setPositiveButton("Masuk") { _, _ ->
                        if (input.text.isNotBlank()) {
                            currentUserName = input.text.toString().trim()
                            startActivity(Intent(this, ChatActivity::class.java))
                        }
                    }.show()
            } else {
                startActivity(Intent(this, ChatActivity::class.java))
            }
        }
    }

    private fun requestNetworkPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        requestPermissions(perms.toTypedArray(), 1)
    }

    private fun showNameAndRoleDialog() {
        val input = EditText(this).apply {
            hint = "Masukkan Nama Panggilan"
            setPadding(50, 50, 50, 50)
        }

        AlertDialog.Builder(this)
            .setTitle("Identitas Jaringan P2P")
            .setView(input)
            .setPositiveButton("Buat Jaringan (Host)") { _, _ ->
                mulaiJaringan(input.text.toString().trim(), isHosting = true)
            }
            .setNeutralButton("Cari Jaringan (Join)") { _, _ ->
                mulaiJaringan(input.text.toString().trim(), isHosting = false)
            }
            .show()
    }

    private fun mulaiJaringan(name: String, isHosting: Boolean) {
        if (name.isNotEmpty()) {
            currentUserName = name
            val engine = NearbyEngine.getInstance(this)

            if (isHosting) {
                engine.startHosting(name)
                Toast.makeText(this, "Membuat Jaringan... Menunggu anggota.", Toast.LENGTH_LONG).show()
            } else {
                engine.startJoining(name)
                Toast.makeText(this, "Mencari Jaringan di sekitar...", Toast.LENGTH_LONG).show()
            }

            startActivity(Intent(this, ChatActivity::class.java))
        } else {
            Toast.makeText(this, "Nama tidak boleh kosong!", Toast.LENGTH_SHORT).show()
        }
    }

    // Daftarkan dan matikan Receiver sesuai siklus hidup aplikasi
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}