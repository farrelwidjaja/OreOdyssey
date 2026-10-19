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
            return "Tunggu sebentar... Sage sedang mengamati situasi ($detik detik)."
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
            Kamu adalah Crystal Sage, pemandu petualangan dalam game 2D mining bernama Ore Odyssey.
            Tugas: Berikan 1 saran singkat (maksimal 2 kalimat) yang relevan dan praktis untuk pemain.
            Gaya: Langsung ke intinya, santai dan membantu selayaknya tips game survival.
            Gunakan Bahasa Indonesia dan sebut pemain dengan "${ctx.playerName}".
            
            Kondisi Pemain:
            - HP: ${ctx.playerHp}/${ctx.playerMaxHp} ($hpPct%)
            - Bioma: ${ctx.currentBiome}
            - Kedalaman: $depthDesc
            - Waktu: ${ctx.timeOfDay} (${if (ctx.isDaytime) "siang" else "malam"})
            - Item aktif: ${ctx.equippedItem}
            - Situasi: ${ctx.surroundingEvent}
            - Monster terdekat: $monsterList
            - Isi tas: $itemList
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
                    "Darahmu menipis, ${ctx.playerName}! Segera makan apel atau minum ramuan di tasmu."
                ctx.nearbyMonsters.isNotEmpty() ->
                    "Lari dan tutup jalan dengan balok, ${ctx.playerName}! Mundur dulu dari ${ctx.nearbyMonsters.first()} sampai HP pulih."
                else ->
                    "Darahmu terkuras parah. Cari tempat aman dan tunggu beberapa detik agar HP beregenerasi."
            }
        }

        // 2. Pertarungan Sengit (Banyak monster terdekat)
        if (ctx.nearbyMonsters.isNotEmpty()) {
            val hasWeapon = ctx.equippedItem.contains("Pedang", ignoreCase = true) ||
                    ctx.equippedItem.contains("Busur", ignoreCase = true) ||
                    ctx.inventoryItems.any { it.contains("Pedang", ignoreCase = true) || it.contains("Busur", ignoreCase = true) }

            if (!hasWeapon) {
                return "Kamu belum pegang senjata! Buka menu tas dan buat pedang kayu atau busur untuk melawan monster."
            }
            if (ctx.nearbyMonsters.size >= 2) {
                return "Ada ${ctx.nearbyMonsters.joinToString(" dan ")} di dekatmu! Pancing mereka ke lorong sempit agar bisa dilawan satu per satu."
            }
            return "Ada ${ctx.nearbyMonsters.first()} mendekat. Jaga jarak ayunan senjatamu agar tidak terkena serangan."
        }

        // 3. Bioma Khusus
        if (ctx.currentBiome.contains("Tundra", ignoreCase = true)) {
            return "Di bioma Tundra, lapisan es licin saat dipijak. Hati-hati jangan sampai tergelincir ke jurang."
        }
        if (ctx.currentBiome.contains("Gurun", ignoreCase = true) || ctx.currentBiome.contains("Desert", ignoreCase = true)) {
            return "Di bioma Gurun jarang ada pohon, ${ctx.playerName}. Kumpulkan kayu secukupnya selagi ada sebelum malam tiba."
        }

        // 4. Kedalaman Ekstrem Bawah Tanah
        if (ctx.depthBelowSurface > 35) {
            val hasRare = ctx.inventoryItems.any { it.contains("Berlian", ignoreCase = true) || it.contains("Emas", ignoreCase = true) }
            return if (hasRare) {
                "Bagus, kamu sudah dapat ore langka di kedalaman ini! Jangan lupa simpan progresmu dan hati-hati saat naik."
            } else {
                "Di kedalaman ${ctx.depthBelowSurface}m banyak bijih emas dan berlian. Waspada terhadap Golem batu saat menggali."
            }
        }

        // 5. Belum punya perlengkapan dasar (Logam/Bijih)
        if (!ctx.inventoryItems.any { it.contains("Besi", ignoreCase = true) || it.contains("Tembaga", ignoreCase = true) }) {
            return "Cari urat bijih tembaga atau besi di dinding gua untuk membuat pedang dan perisai yang lebih kuat, ${ctx.playerName}."
        }

        // 6. Inventaris Penuh
        if (ctx.inventoryItems.size >= 7) {
            return "Tasmu hampir penuh. Manfaatkan bahan mentah untuk crafting item atau letakkan balok yang tidak terpakai."
        }

        // 7. Waktu Malam di Permukaan
        if (!ctx.isDaytime && ctx.depthBelowSurface <= 5) {
            return "Hari sudah malam di permukaan dan banyak zombie berkeliaran. Bertahanlah di dalam gua atau buat rumah kecil."
        }

        // 8. Default Suasana Siang / Eksplorasi
        return "Area sekitar tampak aman, ${ctx.playerName}. Waktu yang pas untuk menggali lebih dalam dan mengumpulkan material."
    }
}
