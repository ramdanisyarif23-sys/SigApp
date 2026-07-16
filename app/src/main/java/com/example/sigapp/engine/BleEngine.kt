package com.example.sigapp.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
object BleEngine {

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isListening = false

    // UUID standar untuk SPP (Serial Port Profile) Bluetooth Classic
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Konstanta MAC Address ESP32 Anda
    val MAC_NODE_A = "88:57:21:94:78:96"
    val MAC_NODE_B = "6C:C8:40:4F:8C:32"

    // Callback untuk melempar pesan yang diterima ke layar UI (ChatActivity)
    var onMessageReceived: ((String) -> Unit)? = null

    fun connect(macAddress: String, adapter: BluetoothAdapter?, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val device = adapter?.getRemoteDevice(macAddress)
                socket = device?.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()

                outputStream = socket?.outputStream
                inputStream = socket?.inputStream

                onResult(true, "Tersambung ke LoRa ($macAddress)")

                // Mulai dengarkan pesan masuk dari ESP32
                startListening()
            } catch (e: Exception) {
                onResult(false, "Gagal konek: ${e.message}")
                disconnect()
            }
        }.start()
    }

    fun sendMessage(pesan: String) {
        if (socket?.isConnected == true) {
            Thread {
                try {
                    // \n penting agar Arduino Serial.readString() tahu kapan berhenti membaca
                    outputStream?.write((pesan + "\n").toByteArray())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    private fun startListening() {
        isListening = true
        Thread {
            try {
                // Gunakan BufferedReader agar sistem menahan napas (menunggu)
                // sampai kalimat dari ESP32 utuh diakhiri enter (\n)
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))

                while (isListening) {
                    val incomingMessage = reader.readLine()

                    if (!incomingMessage.isNullOrEmpty()) {
                        onMessageReceived?.invoke(incomingMessage.trim())
                    }
                }
            } catch (e: Exception) {
                isListening = false
                disconnect()
            }
        }.start()
    }

    fun disconnect() {
        isListening = false
        try { inputStream?.close() } catch (e: Exception) {}
        try { outputStream?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
    }
}