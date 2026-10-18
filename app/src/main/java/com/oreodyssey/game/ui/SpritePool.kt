// app/src/main/java/com/oreodyssey/game/ui/SpritePool.kt
package com.oreodyssey.game.ui

import android.graphics.Rect
import android.graphics.RectF

/**
 * Pool permanen pasangan Rect sumber (srcRect) dan RectF tujuan (dstRect)
 * untuk blitting tekstur bitmap.
 *
 * Tujuan: menghindari alokasi runtime di render loop agar tidak memicu GC spike.
 * Gunakan instance ini saat memanggil canvas.drawBitmap(bitmap, srcRect, dstRect, paint).
 *
 * PENTING: Tidak thread-safe — hanya boleh digunakan dari game thread (render thread).
 */
object SpritePool {
    /** Rect sumber integer (koordinat piksel di spritesheet). */
    val srcRect = Rect()

    /** RectF tujuan float (koordinat di layar / canvas dunia). */
    val dstRect = RectF()

    /** RectF tambahan untuk keperluan UI overlay (mis. HP bar monster). */
    val uiRect = RectF()

    /** RectF tambahan untuk clipping atau efek khusus. */
    val clipRect = RectF()

    /**
     * Set srcRect sekaligus.
     * @param sheetX kolom piksel di spritesheet
     * @param sheetY baris piksel di spritesheet
     * @param w lebar sprite dalam piksel
     * @param h tinggi sprite dalam piksel
     */
    fun src(sheetX: Int, sheetY: Int, w: Int, h: Int) {
        srcRect.set(sheetX, sheetY, sheetX + w, sheetY + h)
    }

    /**
     * Set dstRect sekaligus (koordinat dunia float).
     */
    fun dst(x: Float, y: Float, w: Float, h: Float) {
        dstRect.set(x, y, x + w, y + h)
    }
}
