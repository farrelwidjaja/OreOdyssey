// app/src/main/java/com/oreodyssey/game/world/WorldMap.kt
package com.oreodyssey.game.world

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

const val TILE_SIZE = 64f

object Tiles {
    const val AIR     = 0
    const val DIRT    = 1
    const val STONE   = 2
    const val COPPER  = 3
    const val GRASS   = 4
    const val WOOD    = 5   // batang pohon (tidak padat)
    const val LEAVES  = 6   // daun (tidak padat)
    const val PLANK   = 7
    const val COAL    = 8
    const val IRON    = 9
    const val GOLD    = 10
    const val DIAMOND = 11
    // Biome tiles baru
    const val SAND    = 12  // pasir gurun
    const val SNOW    = 13  // salju tundra
    const val ICE     = 14  // es (solid, licin)
    const val WATER   = 15  // air (tidak padat, bahaya)
    const val CACTUS  = 16  // kaktus gurun (tidak padat, damage)
    const val DEAD_WOOD = 17 // batang pohon mati gurun
    const val CHEST   = 18  // peti harta karun
    const val MUSHROOM = 19 // jamur goa (tidak padat, bisa dimakan)
}

/** Jenis biome untuk tiap kolom */
enum class Biome { FOREST, DESERT, TUNDRA }

class WorldMap(val width: Int = 720, val height: Int = 200) {

    private val tiles   = Array(width) { ByteArray(height) }
    private val surface = IntArray(width)
    val explored = Array(width) { ByteArray(height) }
    /** Biome per kolom x */
    val biome = Array(width) { Biome.FOREST }

    companion object { const val BASE_SURFACE = 50 }

    fun generate(seed: Long) {
        val rnd = Random(seed)
        for (row in explored) row.fill(0)

        // ── Tentukan biome per kolom ──────────────────────────────────────────
        // Kiri ~25% = Desert, Tengah ~50% = Forest, Kanan ~25% = Tundra
        for (x in 0 until width) {
            biome[x] = when {
                x < width / 4              -> Biome.DESERT
                x > width * 3 / 4          -> Biome.TUNDRA
                else                       -> Biome.FOREST
            }
        }

        // ── 1) Terrain multi-octave Simplex ───────────────────────────────────
        for (x in 0 until width) {
            val nx = x.toDouble() / width.toDouble()
            val n1 = SimplexNoise.noise2(seed,      nx * 3.0,  0.0).toDouble()
            val n2 = SimplexNoise.noise2(seed + 1L, nx * 8.0,  0.0).toDouble()
            val n3 = SimplexNoise.noise2(seed + 2L, nx * 20.0, 0.0).toDouble()
            val noiseVal = n1 * 0.6 + n2 * 0.3 + n3 * 0.1
            // Desert sedikit lebih rata, Tundra sedikit lebih berbukit
            val amplitude = when (biome[x]) { Biome.DESERT -> 8.0; Biome.TUNDRA -> 18.0; else -> 14.0 }
            val h = (BASE_SURFACE + noiseVal * amplitude).roundToInt()
                .coerceIn(BASE_SURFACE - 16, BASE_SURFACE + 20)
            surface[x] = h

            val surfaceTile = when (biome[x]) {
                Biome.DESERT -> Tiles.SAND
                Biome.TUNDRA -> Tiles.SNOW
                Biome.FOREST -> Tiles.GRASS
            }
            val subSurfaceTile = when (biome[x]) {
                Biome.DESERT -> Tiles.SAND
                Biome.TUNDRA -> Tiles.DIRT
                Biome.FOREST -> Tiles.DIRT
            }

            for (y in 0 until height) {
                tiles[x][y] = when {
                    y < h       -> Tiles.AIR
                    y == h      -> surfaceTile
                    y < h + 5   -> subSurfaceTile
                    y < h + 8   -> if (biome[x] == Biome.TUNDRA) Tiles.ICE else Tiles.STONE
                    else        -> Tiles.STONE
                }.toByte()
            }
        }

        // ── 2) Genangan air di cekungan ───────────────────────────────────────
        for (x in width / 4 until width * 3 / 4) {
            val s = surface[x]
            // Jika lebih rendah dari dua tetangga = cekungan → air
            val leftH  = if (x > 0) surface[x - 1] else s
            val rightH = if (x < width - 1) surface[x + 1] else s
            if (s > leftH + 2 && s > rightH + 2) {
                // bukan cekungan
            } else if (s >= leftH && s >= rightH && biome[x] == Biome.FOREST) {
                // titik rendah di hutan → air
                for (depth in 0..2) {
                    if (s + depth < height) tiles[x][s + depth] = Tiles.WATER.toByte()
                }
            }
        }

        // ── 3) Bijih ──────────────────────────────────────────────────────────
        for (x in 0 until width) for (y in 0 until height - 1) {
            if (tiles[x][y].toInt() != Tiles.STONE) continue
            val d = y - surface[x]
            val ore = when {
                d > 110 && rnd.nextFloat() < 0.002f  -> Tiles.DIAMOND
                d > 70  && rnd.nextFloat() < 0.007f  -> Tiles.GOLD
                d > 35  && rnd.nextFloat() < 0.015f  -> Tiles.IRON
                d > 12  && rnd.nextFloat() < 0.022f  -> Tiles.COAL
                d > 8   && rnd.nextFloat() < 0.020f  -> Tiles.COPPER
                else -> 0
            }
            if (ore == 0) continue
            tiles[x][y] = ore.toByte()
            if (ore == Tiles.COAL || ore == Tiles.COPPER || ore == Tiles.IRON) {
                if (x + 1 < width && tiles[x + 1][y].toInt() == Tiles.STONE && rnd.nextFloat() < 0.5f)
                    tiles[x + 1][y] = ore.toByte()
                if (y + 1 < height - 1 && tiles[x][y + 1].toInt() == Tiles.STONE && rnd.nextFloat() < 0.5f)
                    tiles[x][y + 1] = ore.toByte()
            }
        }

        // ── 4) Gua Simplex noise ──────────────────────────────────────────────
        val caveThreshold = 0.20f
        for (x in 0 until width) {
            val surfY = surface[x]
            for (y in surfY + 10 until height - 3) {
                val nx = x.toDouble() / 80.0
                val ny = y.toDouble() / 50.0
                val cn = SimplexNoise.noise2(seed + 999L, nx, ny)
                if (cn > caveThreshold) tiles[x][y] = Tiles.AIR.toByte()
            }
        }

        // ── 5) Worm caves tambahan ────────────────────────────────────────────
        repeat(50) {
            var cx = rnd.nextInt(width)
            var cy = BASE_SURFACE + 18 + rnd.nextInt(height - BASE_SURFACE - 30)
            repeat(100) {
                for (dx in -1..1) for (dy in -1..1) {
                    val wx = cx + dx; val wy = cy + dy
                    if (wx in 0 until width && wy in 0 until height - 1)
                        tiles[wx][wy] = Tiles.AIR.toByte()
                }
                cx = (cx + rnd.nextInt(3) - 1).coerceIn(1, width - 2)
                cy = (cy + rnd.nextInt(3) - 1).coerceIn(BASE_SURFACE + 12, height - 4)
            }
        }

        // ── 6) Jamur di goa ───────────────────────────────────────────────────
        for (x in 1 until width - 1) {
            for (y in BASE_SURFACE + 15 until height - 2) {
                if (tiles[x][y].toInt() == Tiles.AIR &&
                    tiles[x][y + 1].toInt() == Tiles.STONE &&
                    rnd.nextFloat() < 0.012f) {
                    tiles[x][y] = Tiles.MUSHROOM.toByte()
                }
            }
        }

        // ── 7) Peti harta karun di goa ────────────────────────────────────────
        var chestsPlaced = 0
        for (attempt in 0 until 500) {
            if (chestsPlaced >= 30) break
            val cx = rnd.nextInt(10, width - 10)
            val cy = rnd.nextInt(BASE_SURFACE + 20, height - 5)
            if (tiles[cx][cy].toInt() == Tiles.AIR &&
                isSolid(cx, cy + 1) &&
                tiles[cx - 1][cy].toInt() == Tiles.AIR) {
                tiles[cx][cy] = Tiles.CHEST.toByte()
                chestsPlaced++
            }
        }

        // ── 8) Vegetasi per biome ─────────────────────────────────────────────
        growVegetation(rnd)
    }

    private fun growVegetation(rnd: Random) {
        var x = 3
        while (x < width - 3) {
            val ground = surface[x]
            when (biome[x]) {
                Biome.FOREST -> {
                    if (rnd.nextFloat() < 0.18f) {
                        val h = 4 + rnd.nextInt(3)
                        for (i in 1..h) {
                            if (ground - i >= 0) tiles[x][ground - i] = Tiles.WOOD.toByte()
                        }
                        val crownY = ground - h
                        for (dx in -2..2) for (dy in -2..0) {
                            if (abs(dx) == 2 && dy == -2) continue
                            val lx = x + dx; val ly = crownY + dy
                            if (lx in 0 until width && ly in 0 until height &&
                                tiles[lx][ly].toInt() == Tiles.AIR)
                                tiles[lx][ly] = Tiles.LEAVES.toByte()
                        }
                        x += 5; return@while
                    }
                }
                Biome.DESERT -> {
                    if (rnd.nextFloat() < 0.06f && ground > 1) {
                        // Kaktus 2-3 tinggi
                        val h = 2 + rnd.nextInt(2)
                        for (i in 1..h) {
                            if (ground - i >= 0) tiles[x][ground - i] = Tiles.CACTUS.toByte()
                        }
                        x += 4; return@while
                    }
                    if (rnd.nextFloat() < 0.03f && ground > 1) {
                        // Pohon mati
                        val h = 3 + rnd.nextInt(2)
                        for (i in 1..h) {
                            if (ground - i >= 0) tiles[x][ground - i] = Tiles.DEAD_WOOD.toByte()
                        }
                        x += 5; return@while
                    }
                }
                Biome.TUNDRA -> {
                    if (rnd.nextFloat() < 0.07f && ground > 1) {
                        // Pohon berdaun putih/beku
                        val h = 3 + rnd.nextInt(2)
                        for (i in 1..h) {
                            if (ground - i >= 0) tiles[x][ground - i] = Tiles.WOOD.toByte()
                        }
                        val crownY = ground - h
                        for (dx in -1..1) for (dy in -2..0) {
                            val lx = x + dx; val ly = crownY + dy
                            if (lx in 0 until width && ly in 0 until height &&
                                tiles[lx][ly].toInt() == Tiles.AIR)
                                tiles[lx][ly] = Tiles.SNOW.toByte()
                        }
                        x += 4; return@while
                    }
                }
            }
            x++
        }
    }

    fun get(x: Int, y: Int): Int = when {
        x < 0 || x >= width -> Tiles.STONE
        y < 0               -> Tiles.AIR
        y >= height         -> Tiles.STONE
        else                -> tiles[x][y].toInt() and 0xFF
    }

    fun set(x: Int, y: Int, type: Int) {
        if (x in 0 until width && y in 0 until height) tiles[x][y] = type.toByte()
    }

    fun isSolid(x: Int, y: Int): Boolean {
        val t = get(x, y)
        return t != Tiles.AIR && t != Tiles.WOOD && t != Tiles.LEAVES &&
               t != Tiles.WATER && t != Tiles.CACTUS && t != Tiles.DEAD_WOOD &&
               t != Tiles.MUSHROOM
    }

    /** Tile yang melukai saat disentuh */
    fun isDamaging(x: Int, y: Int): Boolean {
        val t = get(x, y)
        return t == Tiles.CACTUS || t == Tiles.WATER
    }

    fun surfaceY(x: Int) = surface[x.coerceIn(0, width - 1)]
    fun getBiome(x: Int) = biome[x.coerceIn(0, width - 1)]

    fun tileCopy(x: Int): ByteArray = tiles[x].copyOf()
    fun exploredCopy(x: Int): ByteArray = explored[x].copyOf()
    fun surfaceCopy(): IntArray = surface.copyOf()

    fun write(out: DataOutputStream) {
        out.writeInt(width); out.writeInt(height)
        for (x in 0 until width) { out.write(tiles[x]); out.write(explored[x]) }
        for (x in 0 until width) out.writeInt(surface[x])
    }

    fun read(inp: DataInputStream): Boolean {
        if (inp.readInt() != width || inp.readInt() != height) return false
        for (x in 0 until width) { inp.readFully(tiles[x]); inp.readFully(explored[x]) }
        for (x in 0 until width) surface[x] = inp.readInt()
        return true
    }
}
