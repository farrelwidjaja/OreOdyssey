package com.oreodyssey.game.pool

import com.oreodyssey.game.entities.Arrow
import com.oreodyssey.game.ui.SpritePool
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ObjectPoolTest {

    @Before
    fun setUp() {
        Arrow.clearPool()
        SpritePool.clearParticles()
    }

    @Test
    fun testArrowObtainAndRecycle() {
        // Ambil objek panah pertama
        val arrow1 = Arrow.obtain(10f, 20f, 100f, -50f)
        assertEquals(10f, arrow1.x, 0.001f)
        assertEquals(20f, arrow1.y, 0.001f)
        assertEquals(100f, arrow1.vx, 0.001f)
        assertEquals(-50f, arrow1.vy, 0.001f)
        assertEquals(3f, arrow1.life, 0.001f)

        // Kembalikan ke pool
        Arrow.recycle(arrow1)

        // Objek berikutnya harus menggunakan ulang instance yang sama (object pooling)
        val arrow2 = Arrow.obtain(50f, 60f, 200f, 0f)
        assertSame("Arrow dari pool harus me-reuse instance yang sama", arrow1, arrow2)
        assertEquals(50f, arrow2.x, 0.001f)
        assertEquals(60f, arrow2.y, 0.001f)
        assertEquals(200f, arrow2.vx, 0.001f)
        assertEquals(3f, arrow2.life, 0.001f)
    }

    @Test
    fun testDustParticlePoolEmitAndClear() {
        // Awalnya semua partikel non-aktif
        val initialActiveCount = SpritePool.particles.count { it.active }
        assertEquals(0, initialActiveCount)

        // Emit 4 partikel debu
        SpritePool.emitDust(100f, 100f, 0xFF8B5A2B.toInt(), count = 4)
        val activeCount = SpritePool.particles.count { it.active }
        assertEquals(4, activeCount)

        // Update fisika partikel
        SpritePool.updateParticles(0.1f)
        val stillActive = SpritePool.particles.count { it.active }
        assertEquals(4, stillActive)

        // Clear partikel
        SpritePool.clearParticles()
        assertEquals(0, SpritePool.particles.count { it.active })
    }

    @Test
    fun testTapPointObtainAndRecycle() {
        SpritePool.TapPoint.clearPool()

        val tap1 = SpritePool.TapPoint.obtain(120f, 340f)
        assertEquals(120f, tap1.x, 0.001f)
        assertEquals(340f, tap1.y, 0.001f)

        SpritePool.TapPoint.recycle(tap1)

        val tap2 = SpritePool.TapPoint.obtain(450f, 200f)
        assertSame("TapPoint dari pool harus me-reuse instance yang sama", tap1, tap2)
        assertEquals(450f, tap2.x, 0.001f)
        assertEquals(200f, tap2.y, 0.001f)
    }
}
