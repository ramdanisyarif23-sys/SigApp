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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val dbDao = AppDatabase.getDatabase(this).pesanDao()
        engine = NearbyEngine.getInstance(this)
        val rvChat = findViewById<RecyclerView>(R.id.rv_chat)
        val etMessage = findViewById<EditText>(R.id.et_message)
        val btnSend = findViewById<MaterialButton>(R.id.btn_send)

        val adapter = SimpleChatAdapter(messagesList)
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = adapter

        // Tampilkan data lokal dulu, lalu sedot data tertinggal dari awan
        muatUlangLayar()
        tarikPesanDariAwan()

        // Mendengarkan pesan dari Wi-Fi P2P
        engine.onMessageReceived = { rawString ->
            runOnUiThread {
                val payload = DataPayload.fromProtocolString(rawString)
                if (payload != null) {
                    // CEK ANTI-PANTULAN
                    if (payload.senderName == HubActivity.currentUserName) return@runOnUiThread

                    Thread {
                        dbDao.insertPesan(PesanLokal(
                            id_pesan = payload.messageId, pengirim = payload.senderName,
                            teks_pesan = payload.messageText, waktu = payload.timestamp,
                            lat = payload.lat, lon = payload.lon, sumber = "WIFI", is_sync = false
                        ))
                        picuSinkronisasiLatarBelakang()
                    }.start()
                    tambahKeLayar("${payload.senderName} (WiFi): ${payload.messageText}", adapter, rvChat)
                }
            }
        }

        // Mendengarkan pesan dari LoRa ESP32
        BleEngine.onMessageReceived = { rawString ->
            runOnUiThread {
                android.util.Log.d("MESH_DEBUG", "TANGKAPAN LORA: $rawString")
                val payload = DataPayload.fromProtocolString(rawString)
                if (payload != null) {
                    // CEK ANTI-PANTULAN
                    if (payload.senderName == HubActivity.currentUserName) return@runOnUiThread

                    Thread {
                        dbDao.insertPesan(PesanLokal(
                            id_pesan = payload.messageId, pengirim = payload.senderName,
                            teks_pesan = payload.messageText, waktu = payload.timestamp,
                            lat = payload.lat, lon = payload.lon, sumber = "LORA", is_sync = false
                        ))
                        picuSinkronisasiLatarBelakang()
                    }.start()
                    tambahKeLayar("${payload.senderName} (LoRa): ${payload.messageText}", adapter, rvChat)
                } else {
                    tambahKeLayar("LoRa: $rawString", adapter, rvChat)
                }
            }
        }

        // Mengirim Pesan (Optimasi Kecepatan & GPS)
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

                // Tarik Koordinat Asli
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

                    // Tampilkan di layar
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

    private fun tarikPesanDariAwan() {
        Thread {
            try {
                val url = java.net.URL("https://api.entahlah831.uk/tarik-riwayat")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val stream = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(stream)

                    // --- Inisialisasi DB di dalam Thread yang aman ---
                    val dbDao = AppDatabase.getDatabase(this@ChatActivity).pesanDao()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val simpanPesan = PesanLokal(
                            id_pesan = obj.getString("id_pesan"), pengirim = obj.getString("pengirim"),
                            teks_pesan = obj.getString("teks_pesan"), waktu = obj.getLong("waktu"),
                            lat = obj.getDouble("lat"), lon = obj.getDouble("lon"),
                            sumber = "AWAN", is_sync = true
                        )
                        dbDao.insertPesan(simpanPesan)
                    }

                    muatUlangLayar()
                }
            } catch (e: Exception) {
                android.util.Log.e("SINKRON_AWAN", "Gagal narik dari awan: ${e.message}")
            }
        }.start()
    }

    private fun muatUlangLayar() {
        Thread {
            val dbDao = AppDatabase.getDatabase(this).pesanDao()
            val riwayat = dbDao.getAllPesan()
            runOnUiThread {
                messagesList.clear()
                for (pesan in riwayat) {
                    val teksTampil = if (pesan.sumber == "SAYA") {
                        "Saya: ${pesan.teks_pesan}"
                    } else {
                        "${pesan.pengirim} (${pesan.sumber}): ${pesan.teks_pesan}"
                    }
                    messagesList.add(teksTampil)
                }
                val rvChat = findViewById<RecyclerView>(R.id.rv_chat)
                rvChat.adapter?.notifyDataSetChanged()
                if (messagesList.isNotEmpty()) {
                    rvChat.scrollToPosition(messagesList.size - 1)
                }
            }
        }.start()
    }

    private fun tambahKeLayar(teks: String, adapter: SimpleChatAdapter, rv: RecyclerView) {
        messagesList.add(teks)
        adapter.notifyItemInserted(messagesList.size - 1)
        rv.scrollToPosition(messagesList.size - 1)
    }

    class SimpleChatAdapter(private val dataSet: List<String>) : RecyclerView.Adapter<SimpleChatAdapter.ViewHolder>() {
        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val root: android.widget.LinearLayout = view.findViewById(R.id.root_chat)
            val bubble: android.widget.LinearLayout = view.findViewById(R.id.bubble_chat)
            val tvNama: TextView = view.findViewById(R.id.tv_nama)
            val tvTeks: TextView = view.findViewById(R.id.tv_teks)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = dataSet[position]
            val isSaya = msg.startsWith("Saya:")

            // Memisahkan nama dan isi pesan
            holder.tvTeks.text = if (isSaya) msg.removePrefix("Saya:").trim() else msg.substringAfter("):").trim()
            holder.tvNama.text = if (isSaya) "Saya" else msg.substringBefore("):") + ")"

            // Melempar balon ke Kanan atau Kiri
            holder.root.gravity = if (isSaya) Gravity.END else Gravity.START

            // Memasang warna balon yang dibuat sebelumnya
            holder.bubble.setBackgroundResource(if (isSaya) R.drawable.bg_chat_saya else R.drawable.bg_chat_dia)

            // Mengatur warna teks (Putih untuk balon biru, Hitam untuk balon putih)
            holder.tvTeks.setTextColor(if (isSaya) Color.WHITE else Color.parseColor("#1E1E1E"))
            holder.tvNama.setTextColor(if (isSaya) Color.parseColor("#E0E0E0") else Color.GRAY)
        }
        override fun getItemCount() = dataSet.size
    }
}