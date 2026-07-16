package com.example.sigapp.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.sigapp.R
import com.example.sigapp.engine.BleEngine
import com.example.sigapp.engine.NearbyEngine
import com.google.android.material.button.MaterialButton

class HubActivity : AppCompatActivity() {

    companion object { var currentUserName = "User" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        // 1. MINTA IZIN OTOMATIS SAAT LOBI DIBUKA
        requestNetworkPermissions()

        val btnHost = findViewById<MaterialButton>(R.id.btn_host)
        val btnJoin = findViewById<MaterialButton>(R.id.btn_join)
        val btnLoraSetup = findViewById<ImageButton>(R.id.btn_lora_setup)

        btnHost.setOnClickListener { showNameDialog(isHosting = true) }
        btnJoin.setOnClickListener { showNameDialog(isHosting = false) }

        // MENGHUBUNGKAN KE JEMBATAN LORA
        btnLoraSetup.setOnClickListener {
            val options = arrayOf("Hubungkan ke Node A", "Hubungkan ke Node B")
            AlertDialog.Builder(this)
                .setTitle("Setup Jembatan LoRa")
                .setItems(options) { _, which ->
                    val macAddress = if (which == 0) BleEngine.MAC_NODE_A else BleEngine.MAC_NODE_B
                    val btManager = getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                    val btAdapter = btManager.adapter

                    Toast.makeText(this, "Menyambungkan...", Toast.LENGTH_SHORT).show()

                    BleEngine.connect(macAddress, btAdapter) { sukses, pesan ->
                        runOnUiThread {
                            Toast.makeText(this, pesan, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
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

    private fun showNameDialog(isHosting: Boolean) {
        val input = EditText(this).apply {
            hint = "Masukkan Nama Panggilan"
            setPadding(50, 50, 50, 50)
        }

        AlertDialog.Builder(this)
            .setTitle("Identitas Jaringan")
            .setView(input)
            .setPositiveButton("Mulai") { _, _ ->
                val name = input.text.toString().trim()
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
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}