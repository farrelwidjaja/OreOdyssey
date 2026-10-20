// app/src/test/java/com/oreodyssey/game/data/CloudSaveTest.kt
package com.oreodyssey.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CloudSaveTest {

    @Test
    fun testPayloadSerializationAndDeserialization() {
        val originalProfile = Profile(
            nick = "AlexMiner",
            female = false,
            hairStyle = 3,
            hairColor = 5,
            shirt = 4,
            pants = 2,
            skin = 1,
            coins = 350
        )

        val invIds = IntArray(24) { 0 }
        val invCounts = IntArray(24) { 0 }
        invIds[0] = 3 // Copper
        invCounts[0] = 15
        invIds[1] = 11 // Diamond
        invCounts[1] = 2

        val originalPayload = CloudSavePayload(
            accountId = "u:alex",
            timestamp = 1698000000000L,
            profile = originalProfile,
            playerX = 120.5f,
            playerY = 450.0f,
            playerHealth = 80,
            invIds = invIds,
            invCounts = invCounts,
            invSelected = 1,
            dayTime = 125.0f
        )

        val jsonStr = originalPayload.toJson()
        val parsedPayload = CloudSavePayload.fromJson(jsonStr)

        assertNotNull("Payload hasil parse tidak boleh null", parsedPayload)
        assertEquals(originalPayload.accountId, parsedPayload!!.accountId)
        assertEquals(originalPayload.timestamp, parsedPayload.timestamp)
        assertEquals(originalPayload.profile.nick, parsedPayload.profile.nick)
        assertEquals(originalPayload.profile.coins, parsedPayload.profile.coins)
        assertEquals(originalPayload.playerHealth, parsedPayload.playerHealth)
        assertEquals(originalPayload.invSelected, parsedPayload.invSelected)
        assertEquals(15, parsedPayload.invCounts[0])
        assertEquals(2, parsedPayload.invCounts[1])
        assertEquals(125.0f, parsedPayload.dayTime, 0.001f)
    }

    @Test
    fun testConflictResolutionRule() {
        val oldTime = 1000L
        val newTime = 2000L

        // Lokal lebih baru dari Cloud -> Upload
        assertEquals(
            SyncResolution.UPLOADED_LOCAL_TO_CLOUD,
            CloudSaveManager.resolveConflict(localTimestamp = newTime, cloudTimestamp = oldTime)
        )

        // Cloud lebih baru dari Lokal -> Download / Restore
        assertEquals(
            SyncResolution.DOWNLOADED_CLOUD_TO_LOCAL,
            CloudSaveManager.resolveConflict(localTimestamp = oldTime, cloudTimestamp = newTime)
        )

        // Timestamp sama persis -> Up to date
        assertEquals(
            SyncResolution.UP_TO_DATE,
            CloudSaveManager.resolveConflict(localTimestamp = newTime, cloudTimestamp = newTime)
        )
    }

    @Test
    fun testCorruptedJsonReturnsNullSafely() {
        val corrupted = "{\"accountId\": \"test\", \"invalidJson\": true"
        val result = CloudSavePayload.fromJson(corrupted)
        assertNull("Corrupted JSON harus mengembalikan null tanpa crash", result)
    }
}
