// app/src/main/java/com/oreodyssey/game/world/WorldMap.kt
package com.oreodyssey.game.world

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Collections
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

const val TILE_SIZE = 64f

object Tiles {
    const val AIR       = 0
    const val DIRT      = 1
    const val STONE     = 2
    const val COPPER    = 3
    const val GRASS     = 4
    const val WOOD      = 5   // batang pohon (tidak padat)
    const val LEAVES    = 6   // daun (tidak padat)
    const val PLANK     = 7
    const val COAL      = 8
    const val IRON      = 9
    const val GOLD      = 10
    const val DIAMOND   = 11
    // Biome tiles
    const val SAND      = 12  // pasir gurun
    const val SNOW      = 13  // salju tundra
    const val ICE       = 14  // es (solid, licin)
    const val WATER     = 15  // air (tidak padat, bahaya)
    const val CACTUS    = 16  // kaktus gurun (tidak padat, damage)
    const val DEAD_WOOD = 17  // batang pohon mati gurun
    const val CHEST     = 18  // peti harta karun
    const val MUSHROOM  = 19  // jamur goa (tidak padat, bisa dimakan)
    // Underground biome tiles
    const val LAVA      = 20  // cairan lava pijar (tidak padat, damage)
    const val CRYSTAL   = 21  // kristal ungu bercahaya (padat, drop koin/permata)
    const val MOSS_STONE = 22 // batu gua berlumut (padat)
}

/** Jenis biome permukaan untuk tiap kolom */
enum class Biome { FOREST, DESERT, TUNDRA }

/** Jenis bioma bawah tanah untuk area gua */
enum class UndergroundBiome {
    STANDARD_CAVE,   // Gua batu standar (besi, batu bara, tembaga)
    MUSHROOM_GROTTO, // Gua jamur (jamur berlimpah, batu berlumut)
    CRYSTAL_CAVE,    // Gua kristal (kristal bercahaya, emas & berlian)
    LAVA_CAVERN      // Gua magma (kantung lava, batuan panas dasar bumi)
}

class WorldMap(val width: Int = 720, val height: Int = 200) {

    private val tiles   = Array(width) { ByteArray(height) }
    private val surface = IntArray(width)
    val explored = Array(width) { ByteArray(height) }
    val biome = Array(width) { Biome.FOREST }

    // Sistem Grid Chunk 16x16
    val chunkCols = (width + Chunk.SIZE - 1) / Chunk.SIZE
    val chunkRows = (height + Chunk.SIZE - 1) / Chunk.SIZE
    val chunkCache = ChunkLruCache(maxSize = 160)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pendingChunks = Collections.synchronizedSet(HashSet<Long>())
    var currentSeed: Long = 0L
        private set

    companion object { const val BASE_SURFACE = 50 }

    /**
     * Menentukan bioma bawah tanah menggunakan sampling Simplex Noise 2D.
     */
    fun getUndergroundBiome(x: Int, y: Int): UndergroundBiome {
        val s = surfaceY(x)
        val depth = y - s
        if (depth <= 10) return UndergroundBiome.STANDARD_CAVE

        val nx = x.toDouble() / 45.0
        val ny = y.toDouble() / 35.0
        val noiseVal = SimplexNoise.noise2(currentSeed + 777L, nx, ny)

        // Gua Lava di kedalaman ekstrem (> 38 blok)
        if (depth > 38 && noiseVal > 0.12f) {
            return UndergroundBiome.LAVA_CAVERN
        }

        // Variasi bioma sekunder (Kristal vs Jamur)
        val bNoise = SimplexNoise.noise2(currentSeed + 333L, nx * 0.8, ny * 0.8)
        return when {
            bNoise > 0.22f -> UndergroundBiome.CRYSTAL_CAVE
            bNoise < -0.22f -> UndergroundBiome.MUSHROOM_GROTTO
            else -> UndergroundBiome.STANDARD_CAVE
        }
    }

    /**
     * Meminta / memuat sekumpulan chunk di viewport kamera + buffer margin
     * secara asynchronous menggunakan Dispatchers.Default (background thread).
     */
    fun requestChunks(minCx: Int, maxCx: Int, minCy: Int, maxCy: Int) {
        val cMinX = minCx.coerceIn(0, chunkCols - 1)
        val cMaxX = maxCx.coerceIn(0, chunkCols - 1)
        val cMinY = minCy.coerceIn(0, chunkRows - 1)
        val cMaxY = maxCy.coerceIn(0, chunkRows - 1)

        for (cx in cMinX..cMaxX) {
            for (cy in cMinY..cMaxY) {
                val key = Chunk.chunkKey(cx, cy)
                if (chunkCache.get(key) == null && !pendingChunks.contains(key)) {
                    pendingChunks.add(key)
                    scope.launch(Dispatchers.Default) {
                        try {
                            val chunk = Chunk(cx, cy)
                            generateChunkData(chunk, currentSeed)
                            chunkCache.put(key, chunk)
                        } finally {
                            pendingChunks.remove(key)
                        }
                    }
                }
            }
        }
    }

    /**
     * Komputasi prosedural data tile untuk satu unit Chunk (16x16 blok).
     * Menerapkan Simplex Noise, sebaran bijih, gua, dan variasi bioma bawah tanah.
     */
    fun generateChunkData(chunk: Chunk, seed: Long) {
        val rnd = Random(seed xor ((chunk.cx.toLong() shl 16) or (chunk.cy.toLong() and 0xFFFFL)))
        val startWx = chunk.cx * Chunk.SIZE
        val startWy = chunk.cy * Chunk.SIZE

        for (lx in 0 until Chunk.SIZE) {
            val wx = startWx + lx
            if (wx !in 0 until width) continue
            val surfY = surface[wx]
            val b = biome[wx]

            val surfaceTile = when (b) {
                Biome.DESERT -> Tiles.SAND
                Biome.TUNDRA -> Tiles.SNOW
                Biome.FOREST -> Tiles.GRASS
            }
            val subSurfaceTile = when (b) {
                Biome.DESERT -> Tiles.SAND
                Biome.TUNDRA -> Tiles.DIRT
                Biome.FOREST -> Tiles.DIRT
            }

            for (ly in 0 until Chunk.SIZE) {
                val wy = startWy + ly
                if (wy !in 0 until height) continue

                // 1) Base Terrain
                var tile = when {
                    wy < surfY     -> Tiles.AIR
                    wy == surfY    -> surfaceTile
                    wy < surfY + 5 -> subSurfaceTile
                    wy < surfY + 8 -> if (b == Biome.TUNDRA) Tiles.ICE else Tiles.STONE
                    else           -> Tiles.STONE
                }

                // 2) Underground Biome Tile Adjustments
                if (tile == Tiles.STONE) {
                    val uBiome = getUndergroundBiome(wx, wy)
                    when (uBiome) {
                        UndergroundBiome.MUSHROOM_GROTTO -> {
                            if (rnd.nextFloat() < 0.22f) tile = Tiles.MOSS_STONE
                        }
                        UndergroundBiome.CRYSTAL_CAVE -> {
                            if (rnd.nextFloat() < 0.045f) tile = Tiles.CRYSTAL
                        }
                        UndergroundBiome.LAVA_CAVERN -> {
                            if (rnd.nextFloat() < 0.03f) tile = Tiles.COAL
                        }
                        else -> {}
                    }
                }

                // 3) Sebaran Bijih Berdasarkan Kedalaman
                if (tile == Tiles.STONE || tile == Tiles.MOSS_STONE) {
                    val d = wy - surfY
                    val uBiome = getUndergroundBiome(wx, wy)
                    val diamondChance = if (uBiome == UndergroundBiome.CRYSTAL_CAVE) 0.005f else 0.002f
                    val goldChance = if (uBiome == UndergroundBiome.CRYSTAL_CAVE) 0.015f else 0.007f

                    val ore = when {
                        d > 105 && rnd.nextFloat() < diamondChance -> Tiles.DIAMOND
                        d > 65  && rnd.nextFloat() < goldChance    -> Tiles.GOLD
                        d > 35  && rnd.nextFloat() < 0.015f        -> Tiles.IRON
                        d > 12  && rnd.nextFloat() < 0.022f        -> Tiles.COAL
                        d > 8   && rnd.nextFloat() < 0.020f        -> Tiles.COPPER
                        else -> 0
                    }
                    if (ore != 0) tile = ore
                }

                // 4) Cave Carving (Simplex Noise 2D)
                if (wy > surfY + 9 && wy < height - 2) {
                    val nx = wx.toDouble() / 80.0
                    val ny = wy.toDouble() / 50.0
                    val cn = SimplexNoise.noise2(seed + 999L, nx, ny)
                    if (cn > 0.20f) {
                        val uBiome = getUndergroundBiome(wx, wy)
                        tile = when {
                            // Kantung lava di dasar gua lava
                            uBiome == UndergroundBiome.LAVA_CAVERN && wy > surfY + 40 && wy > height - 18 && cn > 0.32f ->
                                Tiles.LAVA
                            else -> Tiles.AIR
                        }
                    }
                }

                chunk.setLocal(lx, ly, tile.toByte())
                // Sinkronkan ke backing array dunia
                if (tiles[wx][wy].toInt() == 0 || tiles[wx][wy].toInt() == Tiles.STONE) {
                    tiles[wx][wy] = tile.toByte()
                }
            }
        }
        chunk.isGenerated = true
    }

    fun generate(seed: Long) {
        currentSeed = seed
        val rnd = Random(seed)
        for (row in explored) row.fill(0)
        chunkCache.clear()
        pendingChunks.clear()

        // ── 1) Tentukan Biome Permukaan per Kolom ─────────────────────────────
        for (x in 0 until width) {
            biome[x] = when {
                x < width / 4     -> Biome.DESERT
                x > width * 3 / 4 -> Biome.TUNDRA
                else              -> Biome.FOREST
            }
        }

        // ── 2) Heightmap Permukaan (Multi-Octave Simplex Noise) ────────────────
        for (x in 0 until width) {
            val nx = x.toDouble() / width.toDouble()
            val n1 = SimplexNoise.noise2(seed,      nx * 3.0,  0.0).toDouble()
            val n2 = SimplexNoise.noise2(seed + 1L, nx * 8.0,  0.0).toDouble()
            val n3 = SimplexNoise.noise2(seed + 2L, nx * 20.0, 0.0).toDouble()
            val noiseVal = n1 * 0.6 + n2 * 0.3 + n3 * 0.1
            val amplitude = when (biome[x]) { Biome.DESERT -> 8.0; Biome.TUNDRA -> 18.0; else -> 14.0 }
            val h = (BASE_SURFACE + noiseVal * amplitude).roundToInt()
                .coerceIn(BASE_SURFACE - 16, BASE_SURFACE + 20)
            surface[x] = h
        }

        // ── 3) Pre-generate area spawn pemain secara synchronous ──────────────
        // Area tengah (width / 2) agar pemain langsung berdiri di atas tanah solid
        val spawnCx = (width / 2) / Chunk.SIZE
        for (cx in (spawnCx - 4)..(spawnCx + 4)) {
            for (cy in 0..7) {
                if (cx in 0 until chunkCols && cy in 0 until chunkRows) {
                    val key = Chunk.chunkKey(cx, cy)
                    val chunk = Chunk(cx, cy)
                    generateChunkData(chunk, seed)
                    chunkCache.put(key, chunk)
                }
            }
        }

        // ── 4) Genangan Air Cekungan di Hutan ──────────────────────────────────
        for (x in width / 4 until width * 3 / 4) {
            val s = surface[x]
            val leftH  = if (x > 0) surface[x - 1] else s
            val rightH = if (x < width - 1) surface[x + 1] else s
            if (s >= leftH && s >= rightH && biome[x] == Biome.FOREST) {
                for (depth in 0..2) {
                    if (s + depth < height) set(x, s + depth, Tiles.WATER)
                }
            }
        }

        // ── 5) Worm Caves Tambahan ───────────────────────────────────────────
        repeat(50) {
            var cx = rnd.nextInt(width)
            var cy = BASE_SURFACE + 18 + rnd.nextInt(height - BASE_SURFACE - 30)
            repeat(100) {
                for (dx in -1..1) for (dy in -1..1) {
                    val wx = cx + dx; val wy = cy + dy
                    if (wx in 0 until width && wy in 0 until height - 1)
                        set(wx, wy, Tiles.AIR)
                }
                cx = (cx + rnd.nextInt(3) - 1).coerceIn(1, width - 2)
                cy = (cy + rnd.nextInt(3) - 1).coerceIn(BASE_SURFACE + 12, height - 4)
            }
        }

        // ── 6) Peti Harta Karun di Gua ────────────────────────────────────────
        var chestsPlaced = 0
        for (attempt in 0 until 500) {
            if (chestsPlaced >= 30) break
            val cx = rnd.nextInt(10, width - 10)
            val cy = rnd.nextInt(BASE_SURFACE + 20, height - 5)
            if (get(cx, cy) == Tiles.AIR && isSolid(cx, cy + 1) && get(cx - 1, cy) == Tiles.AIR) {
                set(cx, cy, Tiles.CHEST)
                chestsPlaced++
            }
        }

        // ── 7) Vegetasi Permukaan (Pohon, Kaktus) ─────────────────────────────
        growVegetation(rnd)

        // ── 8) Background Asynchronous Generation untuk Sisa Dunia ────────────
        scope.launch(Dispatchers.Default) {
            for (cx in 0 until chunkCols) {
                for (cy in 0 until chunkRows) {
                    val key = Chunk.chunkKey(cx, cy)
                    if (chunkCache.get(key) == null) {
                        val chunk = Chunk(cx, cy)
                        generateChunkData(chunk, seed)
                        chunkCache.put(key, chunk)
                    }
                }
            }
        }
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
                            if (ground - i >= 0) set(x, ground - i, Tiles.WOOD)
                        }
                        val crownY = ground - h
                        for (dx in -2..2) for (dy in -2..0) {
                            if (abs(dx) == 2 && dy == -2) continue
                            val lx = x + dx; val ly = crownY + dy
                            if (lx in 0 until width && ly in 0 until height && get(lx, ly) == Tiles.AIR)
                                set(lx, ly, Tiles.LEAVES)
                        }
                        x += 5; continue
                    }
                }
                Biome.DESERT -> {
                    if (rnd.nextFloat() < 0.06f && ground > 1) {
                        val h = 2 + rnd.nextInt(2)
                        for (i in 1..h) {
                            if (ground - i >= 0) set(x, ground - i, Tiles.CACTUS)
                        }
                        x += 4; continue
                    }
                    if (rnd.nextFloat() < 0.03f && ground > 1) {
                        val h = 3 + rnd.nextInt(2)
                        for (i in 1..h) {
                            if (ground - i >= 0) set(x, ground - i, Tiles.DEAD_WOOD)
                        }
                        x += 5; continue
                    }
                }
                Biome.TUNDRA -> {
                    if (rnd.nextFloat() < 0.07f && ground > 1) {
                        val h = 3 + rnd.nextInt(2)
                        for (i in 1..h) {
                            if (ground - i >= 0) set(x, ground - i, Tiles.WOOD)
                        }
                        val crownY = ground - h
                        for (dx in -1..1) for (dy in -2..0) {
                            val lx = x + dx; val ly = crownY + dy
                            if (lx in 0 until width && ly in 0 until height && get(lx, ly) == Tiles.AIR)
                                set(lx, ly, Tiles.SNOW)
                        }
                        x += 4; continue
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
        if (x in 0 until width && y in 0 until height) {
            tiles[x][y] = type.toByte()
            val cx = x / Chunk.SIZE
            val cy = y / Chunk.SIZE
            val key = Chunk.chunkKey(cx, cy)
            chunkCache.get(key)?.setLocal(x % Chunk.SIZE, y % Chunk.SIZE, type.toByte())
        }
    }

    fun getChunk(cx: Int, cy: Int): Chunk? = chunkCache.get(Chunk.chunkKey(cx, cy))

    fun isSolid(x: Int, y: Int): Boolean {
        val t = get(x, y)
        return t != Tiles.AIR && t != Tiles.WOOD && t != Tiles.LEAVES &&
               t != Tiles.WATER && t != Tiles.CACTUS && t != Tiles.DEAD_WOOD &&
               t != Tiles.MUSHROOM && t != Tiles.LAVA
    }

    /** Tile yang melukai saat disentuh */
    fun isDamaging(x: Int, y: Int): Boolean {
        val t = get(x, y)
        return t == Tiles.CACTUS || t == Tiles.WATER || t == Tiles.LAVA
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
        chunkCache.clear()
        return true
    }
}
