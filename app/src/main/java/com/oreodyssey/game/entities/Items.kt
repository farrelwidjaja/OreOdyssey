// app/src/main/java/com/oreodyssey/game/entities/Items.kt
package com.oreodyssey.game.entities

import com.oreodyssey.game.world.Tiles

/** short = nama pendek untuk kotak inventory. shield = persen pengurang damage. */
class ItemInfo(
    val name: String, val short: String, val color: Int,
    val placeable: Boolean = false, val damage: Int = 0, val shield: Int = 0, val heal: Int = 0
)

class Recipe(val result: Int, val count: Int, val needs: List<Pair<Int, Int>>)

object Items {
    // ID blok sama dengan ID tile di dunia
    const val DIRT = Tiles.DIRT
    const val STONE = Tiles.STONE
    const val COPPER_ORE = Tiles.COPPER
    const val WOOD = Tiles.WOOD
    const val PLANK = Tiles.PLANK
    const val COAL = Tiles.COAL
    const val IRON_ORE = Tiles.IRON
    const val GOLD_ORE = Tiles.GOLD
    const val DIAMOND = Tiles.DIAMOND

    const val COPPER_BAR = 20
    const val IRON_BAR = 21
    const val GOLD_BAR = 22
    const val ARROW = 23
    const val GEL = 24
    const val APPLE = 25
    const val POTION = 26

    const val SWORD_WOOD = 30
    const val SWORD_STONE = 31
    const val SWORD_COPPER = 32
    const val SWORD_IRON = 33
    const val SWORD_GOLD = 34
    const val SWORD_DIAMOND = 35
    const val BOW = 40
    const val SHIELD_WOOD = 50
    const val SHIELD_STONE = 51
    const val SHIELD_COPPER = 52
    const val SHIELD_IRON = 53
    const val SHIELD_GOLD = 54
    const val SHIELD_DIAMOND = 55

    private val table = HashMap<Int, ItemInfo>()
    private val unknown = ItemInfo("?", "?", 0xFFFF00FF.toInt())

    private fun reg(id: Int, name: String, short: String, color: Long,
                    placeable: Boolean = false, damage: Int = 0, shield: Int = 0, heal: Int = 0) {
        table[id] = ItemInfo(name, short, color.toInt(), placeable, damage, shield, heal)
    }

    init {
        reg(DIRT, "Tanah", "Tanah", 0xFF8B5A2B, placeable = true)
        reg(STONE, "Batu", "Batu", 0xFF808080, placeable = true)
        reg(PLANK, "Papan", "Papan", 0xFFC8924F, placeable = true)
        reg(WOOD, "Kayu", "Kayu", 0xFF7A5230)
        reg(COPPER_ORE, "Bijih Tembaga", "B.Tmbg", 0xFFE07B39)
        reg(COAL, "Batubara", "Arang", 0xFF2B2B2B)
        reg(IRON_ORE, "Bijih Besi", "B.Besi", 0xFFC9A58B)
        reg(GOLD_ORE, "Bijih Emas", "B.Emas", 0xFFFFC107)
        reg(DIAMOND, "Berlian", "Berlian", 0xFF26C6DA)
        reg(COPPER_BAR, "Batang Tembaga", "Bt.Tmbg", 0xFFFF8F3F)
        reg(IRON_BAR, "Batang Besi", "Bt.Besi", 0xFFB0BEC5)
        reg(GOLD_BAR, "Batang Emas", "Bt.Emas", 0xFFFFD54F)
        reg(ARROW, "Panah", "Panah", 0xFFD7CCC8)
        reg(GEL, "Gel Slime", "Gel", 0xFF3FA84A)
        reg(APPLE, "Apel (+20 HP)", "Apel", 0xFFE53935, heal = 20)
        reg(POTION, "Ramuan (+60 HP)", "Ramuan", 0xFFD81B60, heal = 60)

        reg(SWORD_WOOD, "Pedang Kayu", "P.Kayu", 0xFFB8895A, damage = 10)
        reg(SWORD_STONE, "Pedang Batu", "P.Batu", 0xFF9E9E9E, damage = 14)
        reg(SWORD_COPPER, "Pedang Tembaga", "P.Tmbg", 0xFFE07B39, damage = 18)
        reg(SWORD_IRON, "Pedang Besi", "P.Besi", 0xFFCFD8DC, damage = 24)
        reg(SWORD_GOLD, "Pedang Emas", "P.Emas", 0xFFFFD700, damage = 30)
        reg(SWORD_DIAMOND, "Pedang Berlian", "P.Brln", 0xFF4DD0E1, damage = 42)
        reg(BOW, "Busur (butuh Panah)", "Busur", 0xFF8D6E63)

        reg(SHIELD_WOOD, "Perisai Kayu (-15%)", "S.Kayu", 0xFFB8895A, shield = 15)
        reg(SHIELD_STONE, "Perisai Batu (-30%)", "S.Batu", 0xFF9E9E9E, shield = 30)
        reg(SHIELD_COPPER, "Perisai Tembaga (-40%)", "S.Tmbg", 0xFFE07B39, shield = 40)
        reg(SHIELD_IRON, "Perisai Besi (-55%)", "S.Besi", 0xFFCFD8DC, shield = 55)
        reg(SHIELD_GOLD, "Perisai Emas (-65%)", "S.Emas", 0xFFFFD700, shield = 65)
        reg(SHIELD_DIAMOND, "Perisai Berlian (-80%)", "S.Brln", 0xFF4DD0E1, shield = 80)
    }

    fun info(id: Int): ItemInfo = table[id] ?: unknown

    val recipes: List<Recipe> = listOf(
        Recipe(PLANK, 4, listOf(WOOD to 1)),
        Recipe(COPPER_BAR, 1, listOf(COPPER_ORE to 2, COAL to 1)),
        Recipe(IRON_BAR, 1, listOf(IRON_ORE to 2, COAL to 1)),
        Recipe(GOLD_BAR, 1, listOf(GOLD_ORE to 2, COAL to 1)),
        Recipe(SWORD_WOOD, 1, listOf(WOOD to 5)),
        Recipe(SWORD_STONE, 1, listOf(WOOD to 2, STONE to 5)),
        Recipe(SWORD_COPPER, 1, listOf(WOOD to 2, COPPER_BAR to 4)),
        Recipe(SWORD_IRON, 1, listOf(WOOD to 2, IRON_BAR to 5)),
        Recipe(SWORD_GOLD, 1, listOf(WOOD to 2, GOLD_BAR to 5)),
        Recipe(SWORD_DIAMOND, 1, listOf(IRON_BAR to 2, DIAMOND to 3)),
        Recipe(BOW, 1, listOf(WOOD to 5, GEL to 3)),
        Recipe(ARROW, 10, listOf(WOOD to 1, STONE to 1)),
        Recipe(SHIELD_WOOD, 1, listOf(WOOD to 8)),
        Recipe(SHIELD_STONE, 1, listOf(WOOD to 4, STONE to 8)),
        Recipe(SHIELD_COPPER, 1, listOf(COPPER_BAR to 6)),
        Recipe(SHIELD_IRON, 1, listOf(IRON_BAR to 8)),
        Recipe(SHIELD_GOLD, 1, listOf(GOLD_BAR to 8)),
        Recipe(SHIELD_DIAMOND, 1, listOf(DIAMOND to 8)),
        Recipe(POTION, 1, listOf(GEL to 3, APPLE to 1))
    )
}