// app/src/main/java/com/oreodyssey/game/world/Chunk.kt
package com.oreodyssey.game.world

import java.util.LinkedHashMap

/**
 * Representasi satu unit chunk 16x16 blok dalam dunia game.
 * Menyimpan data tile secara kompak dalam 1D ByteArray ukuran 256.
 */
class Chunk(val cx: Int, val cy: Int) {
    companion object {
        const val SIZE = 16
        const val TILE_COUNT = SIZE * SIZE

        fun chunkKey(cx: Int, cy: Int): Long =
            (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

        fun keyToCx(key: Long): Int = (key ushr 32).toInt()
        fun keyToCy(key: Long): Int = key.toInt()
    }

    val tiles = ByteArray(TILE_COUNT)
    @Volatile var isGenerated: Boolean = false

    fun getLocal(lx: Int, ly: Int): Byte {
        if (lx !in 0 until SIZE || ly !in 0 until SIZE) return 0
        return tiles[ly * SIZE + lx]
    }

    fun setLocal(lx: Int, ly: Int, tile: Byte) {
        if (lx in 0 until SIZE && ly in 0 until SIZE) {
            tiles[ly * SIZE + lx] = tile
        }
    }
}

/**
 * Cache LRU (Least Recently Used) thread-safe untuk menyimpan chunk aktif di memori.
 * Menghindari overhead komputasi ulang Simplex Noise ketika pemain bolak-balik di area tambang.
 * Kompatibel untuk Android runtime dan JVM Unit Tests tanpa dependensi eksternal.
 */
class ChunkLruCache(val maxSize: Int = 160) {
    private val map = object : LinkedHashMap<Long, Chunk>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Chunk>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: Long): Chunk? = map[key]

    @Synchronized
    fun put(key: Long, chunk: Chunk): Chunk? = map.put(key, chunk)

    @Synchronized
    fun contains(key: Long): Boolean = map.containsKey(key)

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun remove(key: Long): Chunk? = map.remove(key)

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun snapshot(): List<Chunk> = map.values.toList()
}
