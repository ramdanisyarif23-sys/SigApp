package com.example.sigapp.model // Sesuaikan nama package Anda

data class DataPayload(
    val messageId: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    // Membungkus data sebelum ditembakkan ke udara
    fun toProtocolString(): String {
        return "$messageId|$senderName|$messageText|$timestamp|$lat|$lon"
    }

    companion object {
        // Membongkar data yang ditangkap dari udara
        fun fromProtocolString(raw: String): DataPayload? {
            val parts = raw.split("|")

            // Cek jika format baru (6 bagian: UUID|Nama|Pesan|Waktu|Lat|Lon)
            if (parts.size >= 6) {
                return try {
                    DataPayload(
                        messageId = parts[0],
                        senderName = parts[1],
                        messageText = parts[2],
                        timestamp = parts[3].toLong(),
                        lat = parts[4].toDouble(),
                        lon = parts[5].toDouble()
                    )
                } catch (e: Exception) { null }
            }

            // Cek jika format lama (4 bagian) - Opsional agar tidak crash kalau terima dari versi lama
            if (parts.size == 4) {
                return try {
                    DataPayload(parts[0], parts[1], parts[2], parts[3].toLong(), 0.0, 0.0)
                } catch (e: Exception) { null }
            }

            return null
        }
    }
}