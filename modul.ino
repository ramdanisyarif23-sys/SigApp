#include "BluetoothSerial.h"
#include <SPI.h>
#include <LoRa.h>

BluetoothSerial SerialBT;

// Definisi Pin SPI untuk LoRa ke ESP32
// (Sesuaikan angka pin ini jika Anda menggunakan modul ESP32+LoRa bawaan seperti TTGO)
#define ss 18
#define rst 14
#define dio0 26

void setup() {
  Serial.begin(115200);

  // 1. Inisialisasi Bluetooth Low Energy (Classic/BLE hybrid)
  // Pastikan prefix SIG_NODE_ sama dengan algoritma scanner di Kotlin Anda
  SerialBT.begin("SIG_NODE_Pos_1"); 
  Serial.println("Bluetooth Aktif, siap dipindai oleh Android!");

  // 2. Inisialisasi Modul LoRa
  LoRa.setPins(ss, rst, dio0);
  
  // Menggunakan frekuensi 915 MHz (Bisa disesuaikan ke 920E6 - 923E6 sesuai regulasi)
  if (!LoRa.begin(915E6)) {
    Serial.println("Gagal menyalakan modul LoRa! Cek kabel jumper.");
    while (1); // Berhenti di sini jika LoRa tidak terdeteksi
  }
  Serial.println("Modul LoRa Aktif dan siap memancar!");
}

void loop() {
  // --- SKENARIO 1: MENGIRIM PESAN ---
  // Jika ada pesan masuk dari Aplikasi Android (via Bluetooth) -> Tembakkan ke LoRa
  if (SerialBT.available()) {
    String pesanKeluar = SerialBT.readString();
    
    LoRa.beginPacket();
    LoRa.print(pesanKeluar);
    LoRa.endPacket();
    
    Serial.println("Memancarkan via LoRa: " + pesanKeluar);
  }

  // --- SKENARIO 2: MENERIMA PESAN ---
  // Jika antena LoRa menangkap sinyal masuk -> Lempar ke Aplikasi Android (via Bluetooth)
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    String pesanMasuk = "";
    while (LoRa.available()) {
      pesanMasuk += (char)LoRa.read();
    }
    
    SerialBT.print(pesanMasuk);
    Serial.println("Menerima dari LoRa: " + pesanMasuk);
  }
}
