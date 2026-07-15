package com.example.sigapp.engine

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyEngine private constructor(private val context: Context) {

    private val SERVICE_ID = "com.example.sigapp.MESH_NETWORK"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val STRATEGY = Strategy.P2P_CLUSTER

    private val connectedEndpoints = mutableListOf<String>()
    var onMessageReceived: ((String) -> Unit)? = null

    // Menyimpan nama pengguna untuk identitas salaman
    private var myName: String = Build.MODEL

    companion object {
        private var instance: NearbyEngine? = null
        fun getInstance(context: Context): NearbyEngine {
            if (instance == null) instance = NearbyEngine(context.applicationContext)
            return instance!!
        }
    }

    // Fungsi pengaman agar pesan visual (Toast) tidak crash di background thread
    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // BAGIAN 1: PEMANCAR & PENCARI
    // ==========================================
    fun startHosting(userName: String) {
        this.myName = userName
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(userName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener { showToast("Pemancar Aktif. Menunggu anggota...") }
            .addOnFailureListener { e -> showToast("Gagal memancarkan: ${e.message}") }
    }

    fun startJoining(userName: String) {
        this.myName = userName
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener { showToast("Pemindai Aktif. Mencari jaringan sekitar...") }
            .addOnFailureListener { e -> showToast("Gagal memindai: ${e.message}") }
    }

    // ==========================================
    // BAGIAN 2: PROTOKOL BERSALAMAN (Cerewet & Aman)
    // ==========================================
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            showToast("Melihat [${info.endpointName}]. Meminta koneksi...")
            // Menggunakan myName alih-alih "Guest" agar tidak tabrakan identitas
            connectionsClient.requestConnection(myName, endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {
            showToast("Sinyal hilang di udara.")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            showToast("Menyinkronkan kunci dengan [${info.endpointName}]...")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                showToast("KONEKSI AMAN TERBENTUK!")
            } else {
                showToast("Gagal menjalin koneksi.")
            }
        }
        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            showToast("Node terputus.")
        }
    }

    // ==========================================
    // BAGIAN 3: PENERIMA & PENGIRIM PESAN
    // ==========================================
    fun broadcastMessage(payloadStr: String) {
        if (connectedEndpoints.isNotEmpty()) {
            val bytesPayload = Payload.fromBytes(payloadStr.toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(connectedEndpoints, bytesPayload)
        } else {
            showToast("Belum ada yang terhubung!")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val text = String(payload.asBytes()!!)
                onMessageReceived?.invoke(text)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }
}