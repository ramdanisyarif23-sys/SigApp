package com.example.sigapp.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sigapp.R
import com.example.sigapp.engine.BleEngine
import com.example.sigapp.engine.NearbyEngine
import com.example.sigapp.model.DataPayload
import com.google.android.material.button.MaterialButton
import com.example.sigapp.model.AppDatabase
import com.example.sigapp.model.PesanLokal
import java.util.UUID
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices

class ChatActivity : AppCompatActivity() {

    private val messagesList = mutableListOf<String>()
    private lateinit var engine: NearbyEngine
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val dbDao = AppDatabase.getDatabase(this).pesanDao()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        engine = NearbyEngine.getInstance(this)
        val rvChat = findViewById<RecyclerView>(R.id.rv_chat)
        val etMessage = findViewById<EditText>(R.id.et_message)
        val btnSend = findViewById<MaterialButton>(R.id.btn_send)

        val adapter = SimpleChatAdapter(messagesList)
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = adapter

        // Tarik riwayat dari database saat aplikasi dibuka
        Thread {
            val riwayat = dbDao.getAllPesan()
            if (riwayat.isNotEmpty()) {
                runOnUiThread {
                    for (pesan in riwayat) {
                        val teksTampil = if (pesan.sumber == "SAYA") {
                            "Saya: ${pesan.teks_pesan}"
                        } else {
                            "${pesan.pengirim} (${pesan.sumber}): ${pesan.teks_pesan}"
                        }
                        messagesList.add(teksTampil)
                    }
                    adapter.notifyDataSetChanged()
                    rvChat.scrollToPosition(messagesList.size - 1)
                }
            }
        }.start()

        // Mendengarkan pesan dari Wi-Fi P2P
        engine.onMessageReceived = { rawString ->
            runOnUiThread {
                val payload = DataPayload.fromProtocolString(rawString)
                if (payload != null) {
                    Thread {
                        dbDao.insertPesan(PesanLokal(
                            id_pesan = payload.messageId, pengirim = payload.senderName,
                            teks_pesan = payload.messageText, waktu = payload.timestamp,
                            lat = payload.lat, lon = payload.lon, sumber = "WIFI"
                        ))
                    }.start()
                    tambahKeLayar("${payload.senderName} (WiFi): ${payload.messageText}", adapter, rvChat)
                }
            }
        }

        // Mendengarkan pesan dari LoRa ESP32
        BleEngine.onMessageReceived = { rawString ->
            runOnUiThread {
                // Mencoba parse sebagai DataPayload, jika gagal tampilkan teks mentah
                val payload = DataPayload.fromProtocolString(rawString)
                if (payload != null) {
                    Thread {
                        dbDao.insertPesan(PesanLokal(
                            id_pesan = payload.messageId, pengirim = payload.senderName,
                            teks_pesan = payload.messageText, waktu = payload.timestamp,
                            lat = payload.lat, lon = payload.lon, sumber = "LORA"
                        ))
                    }.start()
                    tambahKeLayar("${payload.senderName} (LoRa): ${payload.messageText}", adapter, rvChat)
                } else {
                    tambahKeLayar("LoRa: $rawString", adapter, rvChat)
                }
            }
        }

        // Mengirim Pesan
        // Mengirim Pesan
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {

                // 1. Cek apakah izin GPS sudah diberikan
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                    return@setOnClickListener // Hentikan proses jika belum ada izin
                }

                // 2. Tarik lokasi secara asinkron
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    val currentLat = location?.latitude ?: 0.0
                    val currentLon = location?.longitude ?: 0.0

                    val payload = DataPayload(
                        messageId = UUID.randomUUID().toString(),
                        senderName = HubActivity.currentUserName,
                        messageText = text,
                        timestamp = System.currentTimeMillis(),
                        lat = currentLat, // <-- Koordinat asli disuntikkan di sini
                        lon = currentLon  // <-- Koordinat asli disuntikkan di sini
                    )

                    val stringPayload = payload.toProtocolString()

                    // Tembakan Ganda
                    BleEngine.sendMessage(stringPayload)
                    engine.broadcastMessage(stringPayload)

                    // Simpan ke SQLite Lokal
                    Thread {
                        val simpanPesan = PesanLokal(
                            id_pesan = payload.messageId, pengirim = payload.senderName,
                            teks_pesan = payload.messageText, waktu = payload.timestamp,
                            lat = payload.lat, lon = payload.lon, sumber = "SAYA", is_sync = false
                        )
                        dbDao.insertPesan(simpanPesan)
                    }.start()

                    // Tampilkan di layar
                    tambahKeLayar("Saya: $text", adapter, rvChat)
                    etMessage.text.clear()
                }
            }
        }
    }

    private fun tambahKeLayar(teks: String, adapter: SimpleChatAdapter, rv: RecyclerView) {
        messagesList.add(teks)
        adapter.notifyItemInserted(messagesList.size - 1)
        rv.scrollToPosition(messagesList.size - 1)
    }

    class SimpleChatAdapter(private val dataSet: List<String>) : RecyclerView.Adapter<SimpleChatAdapter.ViewHolder>() {
        class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(16, 16, 16, 16) }
                textSize = 16f
                setTextColor(Color.WHITE)
            }
            return ViewHolder(tv)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = dataSet[position]
            holder.textView.text = msg
            holder.textView.gravity = if (msg.startsWith("Saya:")) Gravity.END else Gravity.START
            holder.textView.setTextColor(if (msg.startsWith("Saya:")) android.graphics.Color.parseColor("#00FF66") else android.graphics.Color.WHITE)
        }
        override fun getItemCount() = dataSet.size
    }
}