// app/src/main/java/com/oreodyssey/game/ai/AiContextPayload.kt
package com.oreodyssey.game.ai

/**
 * Data snapshot kondisi pemain yang dikirim sebagai konteks ke Gemini AI.
 * Semua field adalah tipe primitif / String agar mudah diserialisasi ke prompt teks.
 */
data class AiContextPayload(
    val playerHp: Int,
    val playerMaxHp: Int,
    val depthBelowSurface: Int,   // berapa tile pemain berada di bawah permukaan (negatif = di atas)
    val timeOfDay: String,        // "Pagi", "Siang", "Sore", "Malam"
    val isDaytime: Boolean,
    val inventoryItems: List<String>,   // nama item yang ada di inventaris (tanpa duplikat)
    val nearbyMonsters: List<String>,   // nama monster dalam jarak dekat
    val playerName: String
)
