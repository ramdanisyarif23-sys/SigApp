package com.example.sigapp.model

data class DataPayload(
    val messageId: String,      // ID unik untuk mencegah duplikasi di jaringan P2P
    val senderName: String,     // Nama pengirim
    val messageText: String,    // Isi pesan
    val timestamp: Long         // Waktu pengiriman (penting untuk pembuktian DTN)
) {
    // Memadatkan objek menjadi string lurus untuk ditembakkan via LoRa/Bluetooth
    fun toProtocolString(): String {
        return "$messageId|$senderName|$messageText|$timestamp"
    }

    companion object {
        // Mengurai kembali string yang ditangkap dari udara menjadi objek
        fun fromProtocolString(rawString: String): DataPayload? {
            val parts = rawString.split("|")
            if (parts.size >= 4) {
                return DataPayload(
                    messageId = parts[0],
                    senderName = parts[1],
                    messageText = parts[2], // Jika pesan mengandung spasi, tidak akan terpotong
                    timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                )
            }
            return null
        }
    }
}