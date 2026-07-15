package com.example.sigapp.ui

import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sigapp.R
import com.example.sigapp.engine.NearbyEngine
import com.example.sigapp.model.DataPayload
import com.google.android.material.button.MaterialButton
import java.util.UUID
import android.widget.TextView
import android.view.ViewGroup
import android.graphics.Color
import android.view.Gravity

class ChatActivity : AppCompatActivity() {

    private val messagesList = mutableListOf<String>()
    private lateinit var engine: NearbyEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        engine = NearbyEngine.getInstance(this)
        val rvChat = findViewById<RecyclerView>(R.id.rv_chat)
        val etMessage = findViewById<EditText>(R.id.et_message)
        val btnSend = findViewById<MaterialButton>(R.id.btn_send)

        // Setup Adapter Kilat untuk malam ini
        val adapter = SimpleChatAdapter(messagesList)
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = adapter

        // Mendengarkan pesan yang masuk dari udara
        engine.onMessageReceived = { rawString ->
            runOnUiThread {
                val payload = DataPayload.fromProtocolString(rawString)
                if (payload != null) {
                    messagesList.add("${payload.senderName}: ${payload.messageText}")
                    adapter.notifyItemInserted(messagesList.size - 1)
                    rvChat.scrollToPosition(messagesList.size - 1)
                }
            }
        }

        // Mengirim Pesan
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                // Bungkus menggunakan struktur padat kita
                val payload = DataPayload(
                    messageId = UUID.randomUUID().toString(),
                    senderName = HubActivity.currentUserName,
                    messageText = text,
                    timestamp = System.currentTimeMillis()
                )

                // Lempar ke jaringan Mesh
                engine.broadcastMessage(payload.toProtocolString())

                // Tampilkan di layar sendiri
                messagesList.add("Saya: $text")
                adapter.notifyItemInserted(messagesList.size - 1)
                rvChat.scrollToPosition(messagesList.size - 1)
                etMessage.text.clear()
            }
        }
    }

    // Adapter RecyclerView Internal Super Ringkas
    class SimpleChatAdapter(private val dataSet: List<String>) : RecyclerView.Adapter<SimpleChatAdapter.ViewHolder>() {
        class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(16, 16, 16, 16) }
                textSize = 16f
                setTextColor(Color.WHITE)
            }
            return ViewHolder(tv)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = dataSet[position]
            holder.textView.text = msg
            holder.textView.gravity = if (msg.startsWith("Saya:")) Gravity.END else Gravity.START
            holder.textView.setTextColor(if (msg.startsWith("Saya:")) Color.parseColor("#00FF66") else Color.WHITE)
        }
        override fun getItemCount() = dataSet.size
    }
}