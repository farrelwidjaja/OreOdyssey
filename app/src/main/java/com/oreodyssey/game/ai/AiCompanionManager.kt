// app/src/main/java/com/oreodyssey/game/ai/AiCompanionManager.kt
package com.oreodyssey.game.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.oreodyssey.game.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mengelola interaksi dengan Google Gemini AI untuk Crystal Sage companion.
 * Jika jaringan gagal atau API Key kosong, otomatis menggunakan fallback rule-based offline.
 */
object AiCompanionManager {

    private val model: GenerativeModel? by lazy {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || key == "MASUKKAN_API_KEY_DISINI") null
        else GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = key
        )
    }

    /**
     * Meminta saran dari AI berdasarkan konteks pemain.
     * Selalu dikembalikan dalam bahasa Indonesia (roleplay Crystal Sage).
     * @return String dialog dari Crystal Sage.
     */
    suspend fun ask(ctx: AiContextPayload): String {
        return try {
            val m = model ?: return offlineFallback(ctx)
            val prompt = buildPrompt(ctx)
            withContext(Dispatchers.IO) {
                m.generateContent(prompt).text ?: offlineFallback(ctx)
            }
        } catch (e: Exception) {
            offlineFallback(ctx)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Prompt builder
    // ─────────────────────────────────────────────────────────────────

    private fun buildPrompt(ctx: AiContextPayload): String {
        val itemList = if (ctx.inventoryItems.isEmpty()) "tidak ada" else ctx.inventoryItems.joinToString(", ")
        val monsterList = if (ctx.nearbyMonsters.isEmpty()) "tidak ada" else ctx.nearbyMonsters.joinToString(", ")
        val hpPct = (ctx.playerHp * 100f / ctx.playerMaxHp).toInt()
        val depthDesc = when {
            ctx.depthBelowSurface <= 0 -> "di permukaan"
            ctx.depthBelowSurface < 15 -> "di kedalaman dangkal (${ctx.depthBelowSurface} blok)"
            ctx.depthBelowSurface < 40 -> "di kedalaman sedang (${ctx.depthBelowSurface} blok)"
            else -> "di kedalaman gelap (${ctx.depthBelowSurface} blok)"
        }

        return """
            Kamu adalah Crystal Sage, sebuah kristal purba bijaksana dalam game 2D mining & survival bernama Ore Odyssey.
            Berikan 1-2 kalimat saran/petunjuk survival yang relevan dengan kondisi pemain saat ini.
            Gunakan bahasa Indonesia yang ramah, sedikit misterius, dan penuh hikmat.
            Jangan menyebut nama AI atau Gemini. Sebut pemain sebagai "${ctx.playerName}".
            
            Kondisi pemain saat ini:
            - HP: ${ctx.playerHp}/${ctx.playerMaxHp} ($hpPct%)
            - Lokasi: $depthDesc
            - Waktu: ${ctx.timeOfDay} (${if (ctx.isDaytime) "siang hari" else "malam hari"})
            - Item di inventaris: $itemList
            - Monster terdekat: $monsterList
            
            Berikan saran singkat yang relevan.
        """.trimIndent()
    }

    // ─────────────────────────────────────────────────────────────────
    // Offline fallback rule tree
    // ─────────────────────────────────────────────────────────────────

    private fun offlineFallback(ctx: AiContextPayload): String {
        val hpPct = ctx.playerHp * 100 / ctx.playerMaxHp

        // Prioritas: HP kritis dulu
        if (hpPct <= 20) {
            return if (ctx.inventoryItems.any { it.contains("Ramuan") || it.contains("Apel") })
                "Jiwamu hampir padam, ${ctx.playerName}... gunakan ramuan yang kamu bawa sebelum terlambat."
            else
                "Kamu terluka parah! Carilah tempat aman dan istirahat sejenak agar tubuhmu memulih."
        }

        // Monster malam banyak
        if (!ctx.isDaytime && ctx.nearbyMonsters.isNotEmpty()) {
            return "Malam telah tiba dan kegelapan membawa bahaya. Tetap waspada, ${ctx.playerName}."
        }

        // Tidak punya senjata dan ada monster
        if (ctx.nearbyMonsters.isNotEmpty() && ctx.inventoryItems.none { it.contains("Pedang") || it.contains("Busur") }) {
            return "Monster mengintaimu tanpa senjata. Crafting pedang kayu dari pohon adalah langkah pertamamu."
        }

        // Di bawah tanah dan gelap
        if (ctx.depthBelowSurface > 20 && !ctx.inventoryItems.any { it.contains("Berlian") || it.contains("Emas") }) {
            return "Semakin dalam kamu menggali, semakin berharga yang akan kamu temukan. Tetapi bahayanya pun berlipat."
        }

        // Belum punya bijih besi
        if (!ctx.inventoryItems.any { it.contains("Besi") || it.contains("Emas") || it.contains("Berlian") }) {
            return "Gali lebih dalam, ${ctx.playerName}. Di bawah lapisan batu tersembunyi bijih yang akan mengubah nasibmu."
        }

        // Inventaris penuh / banyak item
        if (ctx.inventoryItems.size >= 8) {
            return "Tasmu penuh dengan hasil tambang. Gunakan meja crafting untuk mengolah bijih menjadi senjata dan perisai."
        }

        // Default berdasarkan waktu
        return if (ctx.isDaytime)
            "Manfaatkan cahaya siang untuk menjelajah dan menambang, ${ctx.playerName}. Malam akan datang lebih cepat dari yang kamu kira."
        else
            "Kristal kuno berbisik padamu: di bawah akar bumi tersimpan permata yang menanti tangan yang berani."
    }
}
