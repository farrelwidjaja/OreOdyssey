// app/src/main/java/com/oreodyssey/game/world/WorldMap.kt
package com.oreodyssey.game.world

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

const val TILE_SIZE = 64f

object Tiles {
    const val AIR = 0
    const val DIRT = 1
    const val STONE = 2
    const val COPPER = 3
    const val GRASS = 4
    const val WOOD = 5     // batang pohon (tidak padat, bisa dilewati)
    const val LEAVES = 6   // daun (tidak padat)
    const val PLANK = 7    // papan kayu buatan pemain (padat)
}

class WorldMap(val width: Int = 150, val height: Int = 80) {

    private val tiles = Array(width) { ByteArray(height) }
    private val surface = IntArray(width)

    companion object { private const val BASE_SURFACE = 25 }

    fun generate(seed: Long) {
        val rnd = Random(seed)
        val p1 = rnd.nextFloat() * 6.28f
        val p2 = rnd.nextFloat() * 6.28f

        for (x in 0 until width) {
            val h = (BASE_SURFACE + sin(x * 0.06f + p1) * 4f + sin(x * 0.17f + p2) * 2f).toInt()
            surface[x] = h
            for (y in 0 until height) {
                tiles[x][y] = when {
                    y < h -> Tiles.AIR
                    y == h -> Tiles.GRASS
                    y < h + 6 -> Tiles.DIRT
                    y == height - 1 -> Tiles.STONE
                    rnd.nextFloat() < 0.025f -> Tiles.COPPER
                    else -> Tiles.STONE
                }.toByte()
            }
        }

        // Gua: "cacing" acak yang melubangi blok
        repeat(25) {
            var cx = rnd.nextInt(width)
            var cy = BASE_SURFACE + 12 + rnd.nextInt(height - BASE_SURFACE - 16)
            repeat(80) {
                for (dx in -1..1) for (dy in -1..1) {
                    val x = cx + dx; val y = cy + dy
                    if (x in 0 until width && y in 0 until height - 1) tiles[x][y] = Tiles.AIR.toByte()
                }
                cx = (cx + rnd.nextInt(3) - 1).coerceIn(1, width - 2)
                cy = (cy + rnd.nextInt(3) - 1).coerceIn(BASE_SURFACE + 8, height - 3)
            }
        }

        growTrees(rnd)
    }

    /** Menanam pohon di permukaan: batang 4-6 blok + mahkota daun 5x3. */
    private fun growTrees(rnd: Random) {
        var x = 3
        while (x < width - 3) {
            if (rnd.nextFloat() < 0.18f) {
                val ground = surface[x]
                val h = 4 + rnd.nextInt(3)
                for (i in 1..h) tiles[x][ground - i] = Tiles.WOOD.toByte()

                val crownY = ground - h               // blok batang paling atas
                for (dx in -2..2) for (dy in -2..0) {
                    if (abs(dx) == 2 && dy == -2) continue   // potong sudut agar bulat
                    val lx = x + dx; val ly = crownY + dy
                    if (tiles[lx][ly].toInt() == Tiles.AIR) tiles[lx][ly] = Tiles.LEAVES.toByte()
                }
                x += 5                                // jarak antar pohon
            } else x++
        }
    }

    fun get(x: Int, y: Int): Int = when {
        x < 0 || x >= width -> Tiles.STONE
        y < 0 -> Tiles.AIR
        y >= height -> Tiles.STONE
        else -> tiles[x][y].toInt()
    }

    fun set(x: Int, y: Int, type: Int) {
        if (x in 0 until width && y in 0 until height) tiles[x][y] = type.toByte()
    }

    /** Batang & daun tidak padat, jadi pemain bisa berjalan melewatinya. */
    fun isSolid(x: Int, y: Int): Boolean {
        val t = get(x, y)
        return t != Tiles.AIR && t != Tiles.WOOD && t != Tiles.LEAVES
    }

    fun surfaceY(x: Int) = surface[x.coerceIn(0, width - 1)]
}