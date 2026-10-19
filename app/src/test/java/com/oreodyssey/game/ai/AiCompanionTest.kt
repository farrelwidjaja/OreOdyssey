package com.oreodyssey.game.ai

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiCompanionTest {

    @Before
    fun setUp() {
        AiCompanionManager.resetCooldown()
    }

    @Test
    fun testAiContextPayloadCreation() {
        val payload = AiContextPayload(
            playerHp = 80,
            playerMaxHp = 100,
            depthBelowSurface = 10,
            timeOfDay = "Siang",
            isDaytime = true,
            inventoryItems = listOf("Batu", "Kayu"),
            nearbyMonsters = listOf("Slime"),
            playerName = "Farrel",
            currentBiome = "Tundra Salju (Tundra)",
            equippedItem = "Pedang Kayu",
            surroundingEvent = "Eksplorasi dan menambang dengan aman"
        )

        assertEquals(80, payload.playerHp)
        assertEquals(100, payload.playerMaxHp)
        assertEquals("Farrel", payload.playerName)
        assertEquals("Tundra Salju (Tundra)", payload.currentBiome)
        assertEquals("Pedang Kayu", payload.equippedItem)
    }

    @Test
    fun testBuildPromptIncludesContextualInformation() {
        val payload = AiContextPayload(
            playerHp = 50,
            playerMaxHp = 100,
            depthBelowSurface = 25,
            timeOfDay = "Malam",
            isDaytime = false,
            inventoryItems = listOf("Besi", "Obor"),
            nearbyMonsters = listOf("Zombie"),
            playerName = "Alex",
            currentBiome = "Gurun Pasir (Desert)",
            equippedItem = "Busur",
            surroundingEvent = "Bertahan dari dinginnya malam di permukaan"
        )

        val prompt = AiCompanionManager.buildPrompt(payload)

        assertTrue("Prompt harus menyertakan nama pemain", prompt.contains("Alex"))
        assertTrue("Prompt harus menyertakan bioma pemain", prompt.contains("Gurun Pasir (Desert)"))
        assertTrue("Prompt harus menyertakan senjata aktif", prompt.contains("Busur"))
        assertTrue("Prompt harus menyertakan situasi sekitar", prompt.contains("Bertahan dari dinginnya malam di permukaan"))
        assertTrue("Prompt harus menyertakan nama Crystal Sage", prompt.contains("Crystal Sage"))
    }

    @Test
    fun testOfflineFallback_CriticalHp() {
        val criticalPayloadWithPotion = AiContextPayload(
            playerHp = 15,
            playerMaxHp = 100,
            depthBelowSurface = 5,
            timeOfDay = "Siang",
            isDaytime = true,
            inventoryItems = listOf("Ramuan HP", "Kayu"),
            nearbyMonsters = emptyList(),
            playerName = "Alex"
        )
        val response = AiCompanionManager.offlineFallback(criticalPayloadWithPotion)
        assertTrue("Harus menyarankan minum ramuan saat HP kritis", response.contains("ramuan", ignoreCase = true))

        val criticalPayloadNoPotion = criticalPayloadWithPotion.copy(inventoryItems = listOf("Kayu"))
        val responseNoPotion = AiCompanionManager.offlineFallback(criticalPayloadNoPotion)
        assertTrue("Harus menyarankan istirahat saat HP kritis tanpa ramuan", responseNoPotion.contains("terkuras", ignoreCase = true) || responseNoPotion.contains("aman", ignoreCase = true))
    }

    @Test
    fun testOfflineFallback_TundraBiome() {
        val tundraPayload = AiContextPayload(
            playerHp = 100,
            playerMaxHp = 100,
            depthBelowSurface = 0,
            timeOfDay = "Siang",
            isDaytime = true,
            inventoryItems = listOf("Batu"),
            nearbyMonsters = emptyList(),
            playerName = "Alex",
            currentBiome = "Tundra Salju"
        )
        val response = AiCompanionManager.offlineFallback(tundraPayload)
        assertTrue("Harus memberikan nasihat spesifik Tundra", response.contains("Tundra", ignoreCase = true) || response.contains("es", ignoreCase = true))
    }

    @Test
    fun testOfflineFallback_UnarmedMonsterEncounter() {
        val unarmedPayload = AiContextPayload(
            playerHp = 90,
            playerMaxHp = 100,
            depthBelowSurface = 5,
            timeOfDay = "Malam",
            isDaytime = false,
            inventoryItems = listOf("Tanah"),
            nearbyMonsters = listOf("Skeleton"),
            playerName = "Alex",
            equippedItem = "Tangan Kosong"
        )
        val response = AiCompanionManager.offlineFallback(unarmedPayload)
        assertTrue("Harus memperingatkan pemain tanpa senjata", response.contains("senjata", ignoreCase = true))
    }
}
