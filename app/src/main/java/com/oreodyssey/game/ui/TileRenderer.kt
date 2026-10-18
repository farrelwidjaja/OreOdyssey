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

    /**
     * Gambar tile berdasarkan tile ID dari WorldMap.
     * Mapping tile ID → posisi sprite di sheet.
     *
     * Layout tilemap_packed.png (Kenney Pixel Platformer):
     * Baris 0: terrain center/mid variants
     * Baris 1: terrain with grass top
     * Baris 2: slopes dan corners
     * dst...
     *
     * Kita pakai mapping manual berdasarkan visual sheet yang sudah dicek.
     */
    fun drawTile(canvas: Canvas, tileId: Int, worldX: Float, worldY: Float,
                 hasGrassAbove: Boolean = false) {
        val (col, row) = when (tileId) {
            Tiles.GRASS  -> Pair(0, 0)   // tanah + rumput atas
            Tiles.DIRT   -> Pair(0, 1)   // tanah coklat polos
            Tiles.STONE  -> Pair(0, 2)   // batu abu-abu
            Tiles.PLANK  -> Pair(0, 3)   // papan kayu
            Tiles.WOOD   -> Pair(3, 1)   // batang pohon coklat
            Tiles.LEAVES -> Pair(3, 0)   // daun hijau
            Tiles.COAL   -> Pair(1, 2)   // batu + bercak hitam
            Tiles.COPPER -> Pair(2, 2)   // batu + bercak oranye
            Tiles.IRON   -> Pair(3, 2)   // batu + bercak abu
            Tiles.GOLD   -> Pair(4, 2)   // batu + bercak kuning
            Tiles.DIAMOND-> Pair(5, 2)   // batu + bercak biru
            else         -> Pair(-1, -1) // tidak digambar
        }
        if (col < 0) return
        drawSprite(canvas, col, row, worldX, worldY)
    }

    val isReady get() = initialized && sheet != null
}
