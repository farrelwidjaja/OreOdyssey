// app/src/test/java/com/oreodyssey/game/combat/CombatSystemTest.kt
package com.oreodyssey.game.combat

import com.oreodyssey.game.entities.Inventory
import com.oreodyssey.game.entities.Monster
import com.oreodyssey.game.entities.MonsterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatSystemTest {

    @Test
    fun testMonsterVariantsStats() {
        val spider = Monster(MonsterType.SPIDER, 100f, 100f)
        assertEquals(35, spider.hp)
        assertEquals("Laba-laba", spider.type.displayName)
        assertTrue("Spider should be agile", spider.type.speed >= 200f)

        val golem = Monster(MonsterType.GOLEM, 200f, 200f)
        assertEquals(120, golem.hp)
        assertEquals(25, golem.type.dmg)
        assertEquals("Golem Batu", golem.type.displayName)
    }

    @Test
    fun testMonsterHitboxDetection() {
        val golem = Monster(MonsterType.GOLEM, 100f, 100f)
        // Koordinat tepat di tengah golem
        assertTrue(golem.containsPoint(128f, 140f))
        // Koordinat jauh di luar
        assertTrue(!golem.containsPoint(300f, 300f))
    }

    @Test
    fun testLootTableRewards() {
        val inv = Inventory()
        var coinsEarned = 0

        val golem = Monster(MonsterType.GOLEM, 0f, 0f)
        LootTable.dropRewards(golem, inv) { coinsEarned += it }

        assertEquals(25, coinsEarned)
        // Golem setidaknya mendrop batu
        val totalStone = (0 until Inventory.SIZE).sumOf { if (inv.ids[it] == com.oreodyssey.game.entities.Items.STONE) inv.counts[it] else 0 }
        assertTrue("Golem should drop stone", totalStone > 0)
    }

    @Test
    fun testMonsterKnockbackDampening() {
        val golem = Monster(MonsterType.GOLEM, 0f, 0f)
        golem.hurt(10, 1)
        // Golem memiliki knockbackScale 0.35f, sehingga vx = 1 * 300 * 0.35 = 105f
        assertEquals(105f, golem.vx, 1f)

        val spider = Monster(MonsterType.SPIDER, 0f, 0f)
        spider.hurt(10, 1)
        // Spider memiliki knockbackScale 1.25f, sehingga vx = 1 * 300 * 1.25 = 375f
        assertEquals(375f, spider.vx, 1f)
    }
}
