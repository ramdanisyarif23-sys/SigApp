package com.example.sigapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.sigapp.ui.HubActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Langsung lompat ke Pusat Komando, tutup layar ini
        startActivity(Intent(this, HubActivity::class.java))
        finish()
    }
}