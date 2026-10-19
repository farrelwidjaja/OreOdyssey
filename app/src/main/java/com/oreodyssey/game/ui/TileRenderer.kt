// app/src/main/java/com/oreodyssey/game/ui/TileRenderer.kt
package com.oreodyssey.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.oreodyssey.game.R
import com.oreodyssey.game.world.Tiles
import com.oreodyssey.game.world.TILE_SIZE

/**
 * Renderer tile berbasis spritesheet Kenney Pixel Platformer.
 * Sheet: tilemap_packed.png — 360x162 px, setiap tile 18x18 px (20 col x 9 row).
 *
 * Koordinat tile di sheet (col, row) — 0-indexed:
 * Row 0: terrain atas (rumput, tanah, batu, dll)
 * Row 1: terrain tengah
 * Row 2: terrain bawah
 * dst.
 *
 * PENTING: Tidak thread-safe — hanya dipanggil dari render thread.
 */
object TileRenderer {

    private const val SPRITE_SIZE = 18          // ukuran 1 tile di spritesheet (px)
    private const val SHEET_COLS = 20           // jumlah kolom di sheet

    private var sheet: Bitmap? = null
    private var initialized = false

    // Rect permanen untuk zero-allocation blitting
    private val src = Rect()
    private val dst = RectF()

    // Paint filter untuk scaling pixel art (matikan filter agar tetap crisp)
    private val paint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    fun init(context: Context) {
        if (initialized) return
        val opts = BitmapFactory.Options().apply {
            inScaled = false    // jangan auto-scale berdasarkan DPI
        }
        sheet = BitmapFactory.decodeResource(context.resources, R.drawable.tilemap_packed, opts)
        initialized = true
    }

    fun release() {
        sheet?.recycle()
        sheet = null
        initialized = false
    }

    /**
     * Gambar tile di posisi dunia (worldX, worldY) dengan ukuran TILE_SIZE x TILE_SIZE.
     * @param col kolom di spritesheet (0-indexed)
     * @param row baris di spritesheet (0-indexed)
     */
    fun drawSprite(canvas: Canvas, col: Int, row: Int, worldX: Float, worldY: Float) {
        val bmp = sheet ?: return
        src.set(
            col * SPRITE_SIZE,
            row * SPRITE_SIZE,
            col * SPRITE_SIZE + SPRITE_SIZE,
            row * SPRITE_SIZE + SPRITE_SIZE
        )
        dst.set(worldX, worldY, worldX + TILE_SIZE, worldY + TILE_SIZE)
        canvas.drawBitmap(bmp, src, dst, paint)
    }

    private const val MAX_TILE_ID = 32
    private val tileCols = IntArray(MAX_TILE_ID) { -1 }
    private val tileRows = IntArray(MAX_TILE_ID) { -1 }

    init {
        fun map(id: Int, col: Int, row: Int) {
            if (id in 0 until MAX_TILE_ID) {
                tileCols[id] = col
                tileRows[id] = row
            }
        }
        map(Tiles.GRASS, 0, 0)    // tanah + rumput atas
        map(Tiles.DIRT, 0, 1)     // tanah coklat polos
        map(Tiles.STONE, 0, 2)    // batu abu-abu
        map(Tiles.PLANK, 0, 3)    // papan kayu
        map(Tiles.WOOD, 3, 1)     // batang pohon coklat
        map(Tiles.LEAVES, 3, 0)   // daun hijau
        map(Tiles.COAL, 1, 2)     // batu + bercak hitam
        map(Tiles.COPPER, 2, 2)   // batu + bercak oranye
        map(Tiles.IRON, 3, 2)     // batu + bercak abu
        map(Tiles.GOLD, 4, 2)     // batu + bercak kuning
        map(Tiles.DIAMOND, 5, 2)  // batu + bercak biru
    }

    /**
     * Gambar tile berdasarkan tile ID dari WorldMap.
     * Menggunakan zero-allocation direct array lookup untuk 60 FPS stabil.
     */
    fun drawTile(canvas: Canvas, tileId: Int, worldX: Float, worldY: Float,
                 hasGrassAbove: Boolean = false) {
        if (tileId !in 0 until MAX_TILE_ID) return
        val col = tileCols[tileId]
        if (col < 0) return
        val row = tileRows[tileId]
        drawSprite(canvas, col, row, worldX, worldY)
    }

    val isReady get() = initialized && sheet != null
}
