package com.example.sigapp.engine

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.sigapp.model.AppDatabase
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val dbDao = AppDatabase.getDatabase(applicationContext).pesanDao()
        val pesanTertunda = dbDao.getPesanBelumSinkron()

        if (pesanTertunda.isEmpty()) return Result.success()

        for (pesan in pesanTertunda) {
            try {
                // TODO: Ganti URL ini dengan alamat peladen Cloudflare Tunnels Anda
                val url = URL("https://api.entahlah831.uk/sinkron")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                // Merakit Data Menjadi JSON
                val jsonParam = JSONObject().apply {
                    put("id_pesan", pesan.id_pesan)
                    put("pengirim", pesan.pengirim)
                    put("teks_pesan", pesan.teks_pesan)
                    put("waktu", pesan.waktu)
                    put("lat", pesan.lat)
                    put("lon", pesan.lon)
                    put("sumber", pesan.sumber)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(jsonParam.toString()) }

                // Jika server merespons sukses (200 OK), tandai sinkron
                if (conn.responseCode == 200 || conn.responseCode == 201) {
                    dbDao.tandaiSudahSinkron(pesan.id_pesan)
                } else {
                    return Result.retry()
                }
                conn.disconnect()
            } catch (e: Exception) {
                // Gagal (tidak ada sinyal internet / server mati), coba lagi nanti otomatis
                return Result.retry()
            }
        }
        return Result.success()
    }
}