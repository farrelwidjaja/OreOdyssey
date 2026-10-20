// app/src/test/java/com/oreodyssey/game/world/WorldGenTest.kt
package com.oreodyssey.game.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldGenTest {

    @Test
    fun testChunkIndexingAndKeyPacking() {
        assertEquals(16, Chunk.SIZE)
        assertEquals(256, Chunk.TILE_COUNT)

        val cx = 12
        val cy = 7
        val key = Chunk.chunkKey(cx, cy)
        assertEquals(cx, Chunk.keyToCx(key))
        assertEquals(cy, Chunk.keyToCy(key))

        val chunk = Chunk(cx, cy)
        chunk.setLocal(5, 5, Tiles.DIAMOND.toByte())
        assertEquals(Tiles.DIAMOND, chunk.getLocal(5, 5).toInt())
        assertEquals(0, chunk.getLocal(0, 0).toInt())
    }

    @Test
    fun testChunkLruCacheEviction() {
        val cache = ChunkLruCache(maxSize = 3)
        val c1 = Chunk(0, 0)
        val c2 = Chunk(1, 0)
        val c3 = Chunk(2, 0)
        val c4 = Chunk(3, 0)

        cache.put(Chunk.chunkKey(0, 0), c1)
        cache.put(Chunk.chunkKey(1, 0), c2)
        cache.put(Chunk.chunkKey(2, 0), c3)
        assertEquals(3, cache.size())

        // Akses c1 agar menjadi yang paling baru digunakan (MRU)
        assertNotNull(cache.get(Chunk.chunkKey(0, 0)))

        // Tambah c4 -> harus mengeluarkan c2 (elemen paling lama tidak diakses)
        cache.put(Chunk.chunkKey(3, 0), c4)
        assertEquals(3, cache.size())

        assertNotNull(cache.get(Chunk.chunkKey(0, 0)))
        assertNull(cache.get(Chunk.chunkKey(1, 0))) // c2 terevicted!
        assertNotNull(cache.get(Chunk.chunkKey(2, 0)))
        assertNotNull(cache.get(Chunk.chunkKey(3, 0)))
    }

    @Test
    fun testUndergroundBiomesClassification() {
        val world = WorldMap(320, 160)
        world.generate(12345L)

        // Di dekat permukaan (kedalaman dangkal) selalu STANDARD_CAVE
        val shallowBiome = world.getUndergroundBiome(100, world.surfaceY(100) + 5)
        assertEquals(UndergroundBiome.STANDARD_CAVE, shallowBiome)

        // Verifikasi semua tipe bioma bawah tanah dapat terbentuk
        val biomesFound = mutableSetOf<UndergroundBiome>()
        for (x in 20..300 step 10) {
            for (y in (world.surfaceY(x) + 15) until 150 step 5) {
                biomesFound.add(world.getUndergroundBiome(x, y))
            }
        }

        assertTrue("Harus mengandung gua standar", biomesFound.contains(UndergroundBiome.STANDARD_CAVE))
        assertTrue("Harus ada variasi bioma gua lain (jamur/kristal/lava)", biomesFound.size >= 2)
    }

    @Test
    fun testNewTilesSolidAndDamageProperties() {
        val world = WorldMap(100, 100)

        // Tiles.LAVA adalah cairan berbahaya (tidak solid, memberi damage)
        world.set(10, 10, Tiles.LAVA)
        assertFalse("Lava tidak boleh solid", world.isSolid(10, 10))
        assertTrue("Lava harus memberikan damage", world.isDamaging(10, 10))

        // Tiles.CRYSTAL adalah padatan berharga (solid, tidak damage)
        world.set(10, 11, Tiles.CRYSTAL)
        assertTrue("Kristal harus padat / solid", world.isSolid(10, 11))
        assertFalse("Kristal tidak melukai pemain", world.isDamaging(10, 11))

        // Tiles.MOSS_STONE adalah batu solid
        world.set(10, 12, Tiles.MOSS_STONE)
        assertTrue("Batu lumut harus solid", world.isSolid(10, 12))
    }

    @Test
    fun testChunkGenerationAndCacheIntegration() {
        val world = WorldMap(320, 160)
        world.generate(99999L)

        // Pre-warm spawn area harus ada di cache
        val spawnCx = (world.width / 2) / Chunk.SIZE
        val spawnChunk = world.getChunk(spawnCx, 2)
        assertNotNull("Spawn chunk harus langsung tergenerasi", spawnChunk)
        assertTrue(spawnChunk!!.isGenerated)

        // Request chunks di viewport lain
        world.requestChunks(2, 4, 1, 3)
        // Tunggu sedikit agar coroutine Dispatchers.Default selesai
        Thread.sleep(150)

        val loadedChunk = world.getChunk(2, 2)
        assertNotNull("Chunk hasil request harus tersimpan di LRU cache", loadedChunk)
        assertTrue(loadedChunk!!.isGenerated)
    }
}
