// app/src/main/java/com/oreodyssey/game/entities/Inventory.kt
package com.oreodyssey.game.entities

import com.oreodyssey.game.world.Tiles

class Recipe(val name: String, val wood: Int, val stone: Int)

class Inventory {

    companion object {
        const val STONE_SLOT = 1
        const val WOOD_SLOT = 3
        val recipes = listOf(
            Recipe("Pedang Kayu", 5, 0),
            Recipe("Pedang Batu", 2, 4),
            Recipe("Perisai Kayu", 8, 0),
            Recipe("Perisai Batu", 3, 8)
        )
    }

    // Slot ke-4 (kayu) saat dipasang menjadi blok PLANK (papan)
    val slotTypes = intArrayOf(Tiles.DIRT, Tiles.STONE, Tiles.COPPER, Tiles.PLANK)
    val counts = IntArray(slotTypes.size)
    @Volatile var selected = 0

    // Perlengkapan: 0 = tidak punya, 1 = kayu, 2 = batu
    var swordLevel = 0
    var shieldLevel = 0
    var shieldDur = 0          // sisa serangan yang bisa diserap perisai

    fun add(type: Int) {
        val t = if (type == Tiles.WOOD) Tiles.PLANK else type
        val i = slotTypes.indexOf(t)
        if (i >= 0) counts[i]++
    }

    fun takeSelected(): Int {
        if (counts[selected] <= 0) return Tiles.AIR
        counts[selected]--
        return slotTypes[selected]
    }

    /** Buat item resep ke-i. Return false jika bahan kurang atau sudah punya yang lebih bagus. */
    fun craft(i: Int): Boolean {
        val r = recipes[i]
        if (counts[WOOD_SLOT] < r.wood || counts[STONE_SLOT] < r.stone) return false
        when (i) {
            0, 1 -> {
                val lvl = i + 1
                if (swordLevel >= lvl) return false
                swordLevel = lvl
            }
            else -> {
                val lvl = i - 1                     // 2 -> level 1, 3 -> level 2
                if (shieldLevel > lvl) return false
                shieldLevel = lvl
                shieldDur = if (lvl == 1) 3 else 6  // perisai batu lebih awet
            }
        }
        counts[WOOD_SLOT] -= r.wood
        counts[STONE_SLOT] -= r.stone
        return true
    }

    fun clear() {
        counts.fill(0); selected = 0
        swordLevel = 0; shieldLevel = 0; shieldDur = 0
    }
}