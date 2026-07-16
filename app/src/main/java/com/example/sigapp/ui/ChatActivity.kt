package com.example.sigapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.sigapp.R
import com.example.sigapp.engine.BleEngine
import com.example.sigapp.engine.NearbyEngine
import com.example.sigapp.engine.SyncWorker
import com.example.sigapp.model.AppDatabase
import com.example.sigapp.model.DataPayload
import com.example.sigapp.model.PesanLokal
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import java.util.UUID

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

        // 1. Tarik Riwayat (Load History)
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

        // 2. Mendengarkan pesan dari Wi-Fi P2P
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
                        picuSinkronisasiLatarBelakang()
                    }.start()
                    tambahKeLayar("${payload.senderName} (WiFi): ${payload.messageText}", adapter, rvChat)
                }
            }
        }

        // 3. Mendengarkan pesan dari LoRa ESP32
        BleEngine.onMessageReceived = { rawString ->
            runOnUiThread {
                android.util.Log.d("MESH_DEBUG", "TANGKAPAN LORA: $rawString")
                val payload = DataPayload.fromProtocolString(rawString)
                if (payload != null) {
                    Thread {
                        dbDao.insertPesan(PesanLokal(
                            id_pesan = payload.messageId, pengirim = payload.senderName,
                            teks_pesan = payload.messageText, waktu = payload.timestamp,
                            lat = payload.lat, lon = payload.lon, sumber = "LORA"
                        ))
                        picuSinkronisasiLatarBelakang()
                    }.start()
                    tambahKeLayar("${payload.senderName} (LoRa): ${payload.messageText}", adapter, rvChat)
                } else {
                    tambahKeLayar("LoRa: $rawString", adapter, rvChat)
                }
            }
        }

        // 4. Mengirim Pesan (Dengan GPS)
        // 4. Mengirim Pesan (Optimasi Kecepatan & GPS)
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {

                // LANGSUNG kosongkan input agar aplikasi terasa cepat
                etMessage.text.clear()

                // Cek Izin GPS
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                    return@setOnClickListener
                }

                // Tarik Koordinat Asli (Gunakan CompleteListener agar tidak lag)
                fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    val location = if (task.isSuccessful) task.result else null
                    val currentLat = location?.latitude ?: 0.0
                    val currentLon = location?.longitude ?: 0.0

                    val payload = DataPayload(
                        messageId = UUID.randomUUID().toString(),
                        senderName = HubActivity.currentUserName,
                        messageText = text,
                        timestamp = System.currentTimeMillis(),
                        lat = currentLat,
                        lon = currentLon
                    )

                    val stringPayload = payload.toProtocolString()

                    // Tembakan Ganda (Smart Routing)
                    BleEngine.sendMessage(stringPayload)
                    engine.broadcastMessage(stringPayload)

                    // Simpan ke SQLite Lokal & Picu Sinkronisasi
                    Thread {
                        val simpanPesan = PesanLokal(
                            id_pesan = payload.messageId, pengirim = payload.senderName,
                            teks_pesan = payload.messageText, waktu = payload.timestamp,
                            lat = payload.lat, lon = payload.lon, sumber = "SAYA", is_sync = false
                        )
                        dbDao.insertPesan(simpanPesan)
                        picuSinkronisasiLatarBelakang()
                    }.start()

                    // Tampilkan di layar dengan segera
                    tambahKeLayar("Saya: $text", adapter, rvChat)
                }
            }
        }
    }

    private fun picuSinkronisasiLatarBelakang() {
        val syaratInternet = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val tugasSinkronisasi = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(syaratInternet)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(tugasSinkronisasi)
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