// app/src/main/java/com/oreodyssey/game/combat/LootTable.kt
package com.oreodyssey.game.combat

import com.oreodyssey.game.entities.Inventory
import com.oreodyssey.game.entities.Items
import com.oreodyssey.game.entities.Monster
import com.oreodyssey.game.entities.MonsterType
import kotlin.random.Random

/**
 * Pengatur reward drop item & koin saat monster berhasil dikalahkan.
 * Logika sederhana, rapi, dan mudah disesuaikan untuk balancing game.
 */
object LootTable {

    fun dropRewards(monster: Monster, inventory: Inventory, addCoins: (Int) -> Unit) {
        val rnd = Random.Default
        fun chance(p: Float) = rnd.nextFloat() < p

        // Reward koin berdasarkan jenis monster
        val coinsEarned = when (monster.type) {
            MonsterType.SLIME -> 3
            MonsterType.BAT -> 4
            MonsterType.ZOMBIE -> 6
            MonsterType.SPIDER -> 8
            MonsterType.SKELETON -> 10
            MonsterType.GOLEM -> 25
        }
        addCoins(coinsEarned)

        // Drop item
        when (monster.type) {
            MonsterType.SLIME -> {
                if (chance(0.6f)) inventory.add(Items.GEL, 1 + rnd.nextInt(2))
                if (chance(0.04f)) inventory.add(Items.GOLD_ORE)
            }
            MonsterType.BAT -> {
                if (chance(0.4f)) inventory.add(Items.COAL)
                if (chance(0.02f)) inventory.add(Items.DIAMOND)
            }
            MonsterType.ZOMBIE -> {
                if (chance(0.5f)) inventory.add(Items.COAL)
                if (chance(0.1f)) inventory.add(Items.IRON_ORE)
                if (chance(0.03f)) inventory.add(Items.GOLD_ORE)
            }
            MonsterType.SKELETON -> {
                if (chance(0.6f)) inventory.add(Items.ARROW, 2 + rnd.nextInt(3))
                if (chance(0.12f)) inventory.add(Items.IRON_ORE)
                if (chance(0.04f)) inventory.add(Items.GOLD_ORE)
            }
            MonsterType.SPIDER -> {
                if (chance(0.7f)) inventory.add(Items.GEL, 1 + rnd.nextInt(3))
                if (chance(0.15f)) inventory.add(Items.POTION)
            }
            MonsterType.GOLEM -> {
                inventory.add(Items.STONE, 3 + rnd.nextInt(3))
                if (chance(0.5f)) inventory.add(Items.IRON_ORE, 1 + rnd.nextInt(2))
                if (chance(0.25f)) inventory.add(Items.GOLD_ORE)
                if (chance(0.08f)) inventory.add(Items.DIAMOND)
            }
        }
    }
}
