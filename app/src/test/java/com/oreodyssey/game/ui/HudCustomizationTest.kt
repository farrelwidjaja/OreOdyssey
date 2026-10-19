package com.oreodyssey.game.ui

import com.oreodyssey.game.data.Profile
import com.oreodyssey.game.entities.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudCustomizationTest {

    @Test
    fun testProfileDefaultCoins() {
        val profile = Profile(nick = "Hero")
        assertEquals(100, profile.coins)
        profile.coins += 25
        assertEquals(125, profile.coins)
    }

    @Test
    fun testItemStatsForTooltip() {
        val sword = Items.info(Items.SWORD_DIAMOND)
        assertTrue("Diamond sword should have high damage", sword.damage >= 25)

        val shield = Items.info(Items.SHIELD_GOLD)
        assertTrue("Gold shield should provide damage reduction", shield.shield > 0)

        val potion = Items.info(Items.POTION)
        assertTrue("Potion should restore health", potion.heal > 0)
    }

    @Test
    fun testTooltipSummaryFormat() {
        val sword = Items.info(Items.SWORD_IRON)
        val stats = mutableListOf<String>()
        if (sword.damage > 0) stats.add("⚔️ Serangan +${sword.damage}")
        if (sword.shield > 0) stats.add("🛡️ Pertahanan +${sword.shield}%")
        if (sword.heal > 0) stats.add("❤️ Pulihkan +${sword.heal} HP")
        if (sword.placeable) stats.add("🧱 Dapat diletakkan")
        if (stats.isEmpty()) stats.add("📦 Bahan Kerajinan")

        val summary = stats.joinToString("  •  ")
        assertTrue("Summary should contain attack stat", summary.contains("⚔️ Serangan +"))
    }
}
