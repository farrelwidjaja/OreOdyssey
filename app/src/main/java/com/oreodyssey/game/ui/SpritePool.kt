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

    /**
     * Objek partikel debu galian yang di-pool secara statis (zero-allocation).
     */
    class DustParticle {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var life = 0f
        var maxLife = 0.4f
        var color = 0
        var size = 6f
        var active = false

        fun reset(x: Float, y: Float, vx: Float, vy: Float, color: Int, size: Float, maxLife: Float) {
            this.x = x
            this.y = y
            this.vx = vx
            this.vy = vy
            this.color = color
            this.size = size
            this.maxLife = maxLife
            this.life = maxLife
            this.active = true
        }

        fun update(dt: Float) {
            if (!active) return
            x += vx * dt
            y += vy * dt
            vy += 350f * dt // efek gravitasi partikel
            life -= dt
            if (life <= 0f) active = false
        }
    }

    private const val MAX_PARTICLES = 64
    val particles = Array(MAX_PARTICLES) { DustParticle() }

    /**
     * Memunculkan partikel debu galian tanpa alokasi memori heap baru.
     */
    fun emitDust(centerX: Float, centerY: Float, color: Int, count: Int = 4) {
        var spawned = 0
        for (p in particles) {
            if (!p.active) {
                val angle = (kotlin.random.Random.nextFloat() * kotlin.math.PI * 2).toFloat()
                val speed = 60f + kotlin.random.Random.nextFloat() * 120f
                val vx = kotlin.math.cos(angle) * speed
                val vy = kotlin.math.sin(angle) * speed - 50f
                val sz = 3f + kotlin.random.Random.nextFloat() * 4f
                val life = 0.25f + kotlin.random.Random.nextFloat() * 0.25f
                p.reset(centerX, centerY, vx, vy, color, sz, life)
                spawned++
                if (spawned >= count) break
            }
        }
    }

    fun updateParticles(dt: Float) {
        for (p in particles) {
            if (p.active) p.update(dt)
        }
    }

    fun clearParticles() {
        for (p in particles) p.active = false
        clearDamageTexts()
    }

    /**
     * Angka damage melayang saat monster atau pemain terkena serangan.
     */
    class DamageText {
        var x = 0f
        var y = 0f
        var text = ""
        var color = 0
        var life = 0f
        var active = false

        fun reset(x: Float, y: Float, text: String, color: Int) {
            this.x = x
            this.y = y
            this.text = text
            this.color = color
            this.life = 0.65f
            this.active = true
        }

        fun update(dt: Float) {
            if (!active) return
            y -= 45f * dt
            life -= dt
            if (life <= 0f) active = false
        }
    }

    private const val MAX_DAMAGE_TEXTS = 16
    val damageTexts = Array(MAX_DAMAGE_TEXTS) { DamageText() }

    fun emitDamage(x: Float, y: Float, text: String, color: Int) {
        for (dt in damageTexts) {
            if (!dt.active) {
                dt.reset(x, y, text, color)
                break
            }
        }
    }

    fun updateDamageTexts(dt: Float) {
        for (d in damageTexts) if (d.active) d.update(dt)
    }

    fun clearDamageTexts() {
        for (d in damageTexts) d.active = false
    }

    /**
     * Objek titik sentuh yang di-pool untuk zero-allocation touch input.
     */
    class TapPoint(var x: Float = 0f, var y: Float = 0f) {
        companion object {
            private const val MAX_TAPS = 16
            private val pool = ArrayList<TapPoint>(MAX_TAPS)

            fun obtain(x: Float, y: Float): TapPoint {
                val p = synchronized(pool) {
                    if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else null
                } ?: TapPoint()
                p.x = x
                p.y = y
                return p
            }

            fun recycle(p: TapPoint) {
                synchronized(pool) {
                    if (pool.size < MAX_TAPS) pool.add(p)
                }
            }

            fun clearPool() {
                synchronized(pool) {
                    pool.clear()
                }
            }
        }
    }
}
