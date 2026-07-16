package com.example.sigapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pesan_lokal")
data class PesanLokal(
    @PrimaryKey val id_pesan: String, // UUID sebagai Primary Key
    val pengirim: String,
    val teks_pesan: String,
    val waktu: Long,
    val lat: Double,
    val lon: Double,
    val sumber: String, // "LORA" atau "WIFI"
    val is_sync: Boolean = false // Penanda status sinkronisasi ke VPS
)