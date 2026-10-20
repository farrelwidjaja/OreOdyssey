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

    const val COOLDOWN_MILLIS: Long = 6000L
    @Volatile private var lastAskTimestamp: Long = 0L

    /**
     * Menghitung sisa waktu cooldown dialog AI dalam milidetik.
     */
    fun remainingCooldownMillis(): Long {
        val elapsed = System.currentTimeMillis() - lastAskTimestamp
        return (COOLDOWN_MILLIS - elapsed).coerceAtLeast(0L)
    }

    /**
     * Reset timestamp cooldown (berguna untuk testing).
     */
    fun resetCooldown() {
        lastAskTimestamp = 0L
    }

    /**
     * Meminta saran dari AI berdasarkan konteks pemain.
     * Selalu dikembalikan dalam bahasa Indonesia (roleplay Crystal Sage).
     * @param force jika true, mengabaikan cooldown dialog.
     * @return String dialog dari Crystal Sage.
     */
    suspend fun ask(ctx: AiContextPayload, force: Boolean = false): String {
        val remaining = remainingCooldownMillis()
        if (!force && remaining > 0) {
            val detik = (remaining / 1000) + 1
            return "✦ Crystal Sage sedang memulihkan resonansi kristalnya... bersabarlah ($detik detik). ✦"
        }
        lastAskTimestamp = System.currentTimeMillis()

        return try {
            val m = model ?: return offlineFallback(ctx)
            val prompt = buildPrompt(ctx)
            withContext(Dispatchers.IO) {
                m.generateContent(prompt).text?.trim() ?: offlineFallback(ctx)
            }
        } catch (e: Exception) {
            offlineFallback(ctx)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Prompt builder
    // ─────────────────────────────────────────────────────────────────

    fun buildPrompt(ctx: AiContextPayload): String {
        val itemList = if (ctx.inventoryItems.isEmpty()) "tidak ada" else ctx.inventoryItems.joinToString(", ")
        val monsterList = if (ctx.nearbyMonsters.isEmpty()) "tidak ada" else ctx.nearbyMonsters.joinToString(", ")
        val hpPct = (ctx.playerHp * 100f / ctx.playerMaxHp).toInt()
        val depthDesc = when {
            ctx.depthBelowSurface <= 0 -> "di permukaan dunia"
            ctx.depthBelowSurface < 15 -> "di kedalaman dangkal (${ctx.depthBelowSurface} blok)"
            ctx.depthBelowSurface < 40 -> "di kedalaman sedang (${ctx.depthBelowSurface} blok)"
            else -> "di kedalaman gua gelap (${ctx.depthBelowSurface} blok)"
        }

        return """
            Kamu adalah Crystal Sage, sebuah entitas kristal purba yang bijaksana dan protektif dalam game 2D mining & survival bernama Ore Odyssey.
            Tugasmu: Berikan 1-2 kalimat petuah, taktik, atau petunjuk survival yang sangat relevan dengan situasi pemain saat ini.
            Gaya bicara: Bijaksana, tenang, sedikit mistis khas penjaga gua kuno, menggunakan Bahasa Indonesia yang fasih.
            Pantangan: JANGAN pernah menyebut bahwa kamu adalah AI/robot/LLM/Gemini. Sebut pemain dengan panggilan "${ctx.playerName}".
            
            Kondisi Real-Time Pemain:
            - HP: ${ctx.playerHp}/${ctx.playerMaxHp} ($hpPct%)
            - Bioma: ${ctx.currentBiome}
            - Posisi: $depthDesc
            - Waktu: ${ctx.timeOfDay} (${if (ctx.isDaytime) "siang hari" else "malam hari"})
            - Alat/Senjata yang dipegang: ${ctx.equippedItem}
            - Situasi sekitar: ${ctx.surroundingEvent}
            - Monster terdekat: $monsterList
            - Isi tas: $itemList
            
            Berikan 1-2 kalimat saran singkat dan padat untuk ${ctx.playerName}.
        """.trimIndent()
    }

    // ─────────────────────────────────────────────────────────────────
    // Offline fallback rule tree
    // ─────────────────────────────────────────────────────────────────

    fun offlineFallback(ctx: AiContextPayload): String {
        val hpPct = (ctx.playerHp * 100f / ctx.playerMaxHp).toInt()

        // 1. Prioritas Utama: HP Kritis (<= 25%)
        if (hpPct <= 25) {
            return when {
                ctx.inventoryItems.any { it.contains("Ramuan", ignoreCase = true) || it.contains("Apel", ignoreCase = true) } ->
                    "Detak jiwamu melemah, ${ctx.playerName}! Segera teguk ramuan atau makanan di tasmu sebelum kegelapan merenggutmu."
                ctx.nearbyMonsters.isNotEmpty() ->
                    "Lari dan pasang dinding blok di belakangmu, ${ctx.playerName}! Bertarung dengan luka parah melawan ${ctx.nearbyMonsters.first()} adalah kesia-siaan."
                else ->
                    "Darahmu terkuras parah. Carilah celah aman dan bertahanlah sejenak agar napasmu kembali pulih."
            }
        }

        // 2. Pertarungan Sengit (Banyak monster terdekat)
        if (ctx.nearbyMonsters.isNotEmpty()) {
            val hasWeapon = ctx.equippedItem.contains("Pedang", ignoreCase = true) ||
                    ctx.equippedItem.contains("Busur", ignoreCase = true) ||
                    ctx.inventoryItems.any { it.contains("Pedang", ignoreCase = true) || it.contains("Busur", ignoreCase = true) }

            if (!hasWeapon) {
                return "Kamu terkepung tanpa senjata tajam! Mundur dan buatlah pedang kayu atau tombak sebelum monster mendekat."
            }
            if (ctx.nearbyMonsters.size >= 2) {
                return "Waspadalah, ${ctx.nearbyMonsters.joinToString(" dan ")} bergerak mengepungmu. Gunakan ruang sempit lorong tambang untuk menghadapi mereka satu per satu."
            }
            return "Aura berbahaya terpancar dari ${ctx.nearbyMonsters.first()}. Jaga jarak ayunan senjatamu dan jangan biarkan mereka memojokkanmu!"
        }

        // 3. Bioma Khusus
        if (ctx.currentBiome.contains("Tundra", ignoreCase = true)) {
            return "Angin dingin Tundra menusuk tulang. Hati-hati melangkah di lapisan es licin dan waspadalah jika tergelincir ke jurang bersalju."
        }
        if (ctx.currentBiome.contains("Gurun", ignoreCase = true) || ctx.currentBiome.contains("Desert", ignoreCase = true)) {
            return "Gurun pasir ini gersang dan penuh duri kaktus, ${ctx.playerName}. Kumpulkan kayu secukupnya selagi masih ada sebelum malam tiba."
        }

        // 4. Kedalaman Ekstrem Bawah Tanah
        if (ctx.depthBelowSurface > 35) {
            val hasRare = ctx.inventoryItems.any { it.contains("Berlian", ignoreCase = true) || it.contains("Emas", ignoreCase = true) }
            return if (hasRare) {
                "Kilau permata purba di tasmu menandakan keberanianmu menaklukkan dasar bumi. Jangan lengah dalam perjalanan kembali ke atas!"
            } else {
                "Di kedalaman sunyi ini, dinding batu menyimpan mineral paling berharga. Teruslah menggali ke urat batu gelap di sekitarmu."
            }
        }

        // 5. Belum punya perlengkapan dasar (Logam/Bijih)
        if (!ctx.inventoryItems.any { it.contains("Besi", ignoreCase = true) || it.contains("Tembaga", ignoreCase = true) }) {
            return "Batu dan kayu hanyalah permulaan. Carilah urat bijih tembaga atau besi di dinding gua untuk memperkuat perlengkapanmu, ${ctx.playerName}."
        }

        // 6. Inventaris Penuh
        if (ctx.inventoryItems.size >= 7) {
            return "Beban tasmu kian berat dengan kekayaan alam. Gunakan bahan-bahan itu untuk meracik perlengkapan baru atau perisai pelindung."
        }

        // 7. Waktu Malam di Permukaan
        if (!ctx.isDaytime && ctx.depthBelowSurface <= 5) {
            return "Malam telah menyelimuti permukaan, membawa monster-monster bayangan keluar dari sarangnya. Tetaplah di dekat cahaya atau masuklah ke dalam tambang."
        }

        // 8. Default Suasana Siang / Eksplorasi
        return "Cahaya kristal merasakan ketenangan di sekitarmu, ${ctx.playerName}. Ini saat yang tepat untuk memperdalam galian dan menimbun cadangan material."
    }
}
