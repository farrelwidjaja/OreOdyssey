// app/src/test/java/com/oreodyssey/game/audio/AudioSystemTest.kt
package com.oreodyssey.game.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSystemTest {

    @Test
    fun testBgmTrackForDepth() {
        // Zona Permukaan (0 - 14 blok)
        assertEquals(SoundManager.BGM_SURFACE, SoundManager.trackForDepth(0))
        assertEquals(SoundManager.BGM_SURFACE, SoundManager.trackForDepth(5))
        assertEquals(SoundManager.BGM_SURFACE, SoundManager.trackForDepth(14))

        // Zona Gua Dangkal (15 - 35 blok)
        assertEquals(SoundManager.BGM_CAVE, SoundManager.trackForDepth(15))
        assertEquals(SoundManager.BGM_CAVE, SoundManager.trackForDepth(25))
        assertEquals(SoundManager.BGM_CAVE, SoundManager.trackForDepth(35))

        // Zona Gua Dalam / Dasar Bumi (> 35 blok)
        assertEquals(SoundManager.BGM_DEEP, SoundManager.trackForDepth(36))
        assertEquals(SoundManager.BGM_DEEP, SoundManager.trackForDepth(50))
        assertEquals(SoundManager.BGM_DEEP, SoundManager.trackForDepth(100))
    }

    @Test
    fun testComputeVolumeFormula() {
        // Volume penuh 100%
        val fullVol = SoundManager.computeVolume(1.0f, 1.0f, 1.0f)
        assertEquals(1.0f, fullVol, 0.001f)

        // Master 50%, SFX 100%
        val halfMaster = SoundManager.computeVolume(1.0f, 0.5f, 1.0f)
        assertEquals(0.5f, halfMaster, 0.001f)

        // Master 80%, SFX 50% -> 0.40
        val mixed = SoundManager.computeVolume(1.0f, 0.8f, 0.5f)
        assertEquals(0.4f, mixed, 0.001f)

        // Master Mute (0%) -> 0.0
        val muted = SoundManager.computeVolume(1.0f, 0.0f, 1.0f)
        assertEquals(0.0f, muted, 0.001f)

        // Clamp batas luar (< 0 atau > 1)
        val clampedLow = SoundManager.computeVolume(-0.5f, 1.0f, 1.0f)
        assertEquals(0.0f, clampedLow, 0.001f)

        val clampedHigh = SoundManager.computeVolume(2.0f, 2.0f, 2.0f)
        assertEquals(1.0f, clampedHigh, 0.001f)
    }

    @Test
    fun testDefaultVolumeRanges() {
        assertTrue("Master volume should be in range 0..1", SoundManager.masterVolume in 0f..1f)
        assertTrue("BGM volume should be in range 0..1", SoundManager.bgmVolume in 0f..1f)
        assertTrue("SFX volume should be in range 0..1", SoundManager.sfxVolume in 0f..1f)
    }
}
