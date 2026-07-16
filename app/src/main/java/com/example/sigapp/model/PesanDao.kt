package com.example.sigapp.model // Sesuaikan jika Anda menaruhnya di folder 'db'

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PesanDao {
    // Memasukkan pesan baru (Jika UUID sama, abaikan agar tidak duplikat)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPesan(pesan: PesanLokal)

    // Mengambil seluruh riwayat obrolan untuk ditampilkan di layar
    @Query("SELECT * FROM pesan_lokal ORDER BY waktu ASC")
    fun getAllPesan(): List<PesanLokal>

    // Kunci untuk VPS: Mengambil pesan yang belum terkirim ke internet
    @Query("SELECT * FROM pesan_lokal WHERE is_sync = 0")
    fun getPesanBelumSinkron(): List<PesanLokal>

    // Menandai pesan jika sudah berhasil ditarik oleh VPS
    @Query("UPDATE pesan_lokal SET is_sync = 1 WHERE id_pesan = :id")
    fun tandaiSudahSinkron(id: String)
}
