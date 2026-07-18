package com.example.sigapp.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sigapp.R
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Cek Memori: Apakah nama sudah pernah disimpan?
        val prefs = getSharedPreferences("SIGAPP_PREFS", Context.MODE_PRIVATE)
        val savedName = prefs.getString("USER_NAME", null)

        if (savedName != null) {
            HubActivity.currentUserName = savedName
            startActivity(Intent(this, HubActivity::class.java))
            finish() // Tutup layar Welcome agar tidak bisa di-back
            return
        }

        // 2. Jika belum ada nama, tampilkan layar Welcome
        setContentView(R.layout.activity_welcome)

        findViewById<MaterialButton>(R.id.btn_masuk).setOnClickListener {
            val inputName = findViewById<EditText>(R.id.et_nama).text.toString().trim()
            if (inputName.isNotEmpty()) {
                // Simpan nama ke memori HP
                prefs.edit().putString("USER_NAME", inputName).apply()
                HubActivity.currentUserName = inputName

                startActivity(Intent(this, HubActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Nama tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}