// app/src/main/java/com/oreodyssey/game/world/SimplexNoise.kt
package com.oreodyssey.game.world

import kotlin.math.floor

/**
 * Implementasi OpenSimplex2 2D dalam Kotlin murni.
 * Diadaptasi dari KdotJPG/OpenSimplex2 (Public Domain).
 * Hanya fungsi noise2 yang diport karena game hanya butuh noise 2D.
 */
object SimplexNoise {

    private const val PRIME_X = 0x5205402B9270C86FL
    private const val PRIME_Y = 0x598CD327003817B5L
    private const val HASH_MULTIPLIER = 0x53A3F72DEEC546F5L
    private const val RSQUARED_2D = 2.0f / 3.0f
    private const val SKEW_2D = 0.366025403784439
    private const val UNSKEW_2D = -0.21132486540518713
    private const val N_GRADS_2D_EXPONENT = 7
    private const val N_GRADS_2D = 1 shl N_GRADS_2D_EXPONENT
    private const val NORMALIZER_2D = 0.05481866495625118

    private val GRADIENTS_2D: FloatArray

    init {
        val grad2 = floatArrayOf(
            0.38268343236509f,   0.923879532511287f,
            0.923879532511287f,  0.38268343236509f,
            0.923879532511287f, -0.38268343236509f,
            0.38268343236509f,  -0.923879532511287f,
            -0.38268343236509f,  -0.923879532511287f,
            -0.923879532511287f, -0.38268343236509f,
            -0.923879532511287f,  0.38268343236509f,
            -0.38268343236509f,   0.923879532511287f,
            0.130526192220052f,  0.99144486137381f,
            0.608761429008721f,  0.793353340291235f,
            0.793353340291235f,  0.608761429008721f,
            0.99144486137381f,   0.130526192220051f,
            0.99144486137381f,  -0.130526192220051f,
            0.793353340291235f, -0.60876142900872f,
            0.608761429008721f, -0.793353340291235f,
            0.130526192220052f, -0.99144486137381f,
            -0.130526192220052f, -0.99144486137381f,
            -0.608761429008721f, -0.793353340291235f,
            -0.793353340291235f, -0.608761429008721f,
            -0.99144486137381f,  -0.130526192220052f,
            -0.99144486137381f,   0.130526192220051f,
            -0.793353340291235f,  0.608761429008721f,
            -0.608761429008721f,  0.793353340291235f,
            -0.130526192220052f,  0.99144486137381f,
        )
        for (i in grad2.indices) grad2[i] = (grad2[i] / NORMALIZER_2D).toFloat()

        GRADIENTS_2D = FloatArray(N_GRADS_2D * 2)
        var j = 0
        for (i in GRADIENTS_2D.indices) {
            if (j == grad2.size) j = 0
            GRADIENTS_2D[i] = grad2[j++]
        }
    }

    /** Evaluasi noise 2D pada koordinat (x, y) dengan seed tertentu. Hasil: -1..1 */
    fun noise2(seed: Long, x: Double, y: Double): Float {
        val s = SKEW_2D * (x + y)
        val xs = x + s
        val ys = y + s
        return noise2Base(seed, xs, ys)
    }

    private fun noise2Base(seed: Long, xs: Double, ys: Double): Float {
        val xsb = fastFloor(xs)
        val ysb = fastFloor(ys)
        val xi = (xs - xsb).toFloat()
        val yi = (ys - ysb).toFloat()

        val xsbp = xsb.toLong() * PRIME_X
        val ysbp = ysb.toLong() * PRIME_Y

        val t = (xi + yi) * UNSKEW_2D.toFloat()
        val dx0 = xi + t
        val dy0 = yi + t

        val a0 = RSQUARED_2D - dx0 * dx0 - dy0 * dy0
        var value = if (a0 > 0) (a0 * a0) * (a0 * a0) * grad(seed, xsbp, ysbp, dx0, dy0) else 0f

        val a1 = (2f * (1f + 2f * UNSKEW_2D.toFloat()) * (1f / UNSKEW_2D.toFloat() + 2f)) * t +
                ((-2f * (1f + 2f * UNSKEW_2D.toFloat()) * (1f + 2f * UNSKEW_2D.toFloat())) + a0)
        val dx1 = dx0 - (1f + 2f * UNSKEW_2D.toFloat())
        val dy1 = dy0 - (1f + 2f * UNSKEW_2D.toFloat())
        if (a1 > 0) value += (a1 * a1) * (a1 * a1) * grad(seed, xsbp + PRIME_X, ysbp + PRIME_Y, dx1, dy1)

        val xmyi = xi - yi
        if (t < UNSKEW_2D.toFloat()) {
            if (xi + xmyi > 1f) {
                val dx2 = dx0 - (3f * UNSKEW_2D.toFloat() + 2f)
                val dy2 = dy0 - (3f * UNSKEW_2D.toFloat() + 1f)
                val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
                if (a2 > 0) value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + (PRIME_X shl 1), ysbp + PRIME_Y, dx2, dy2)
            } else {
                val dx2 = dx0 - UNSKEW_2D.toFloat()
                val dy2 = dy0 - (UNSKEW_2D.toFloat() + 1f)
                val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
                if (a2 > 0) value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp + PRIME_Y, dx2, dy2)
            }
            if (yi - xmyi > 1f) {
                val dx2 = dx0 - (3f * UNSKEW_2D.toFloat() + 1f)
                val dy2 = dy0 - (3f * UNSKEW_2D.toFloat() + 2f)
                val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
                if (a2 > 0) value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + PRIME_X, ysbp + (PRIME_Y shl 1), dx2, dy2)
            } else {
                val dx2 = dx0 - (UNSKEW_2D.toFloat() + 1f)
                val dy2 = dy0 - UNSKEW_2D.toFloat()
                val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
                if (a2 > 0) value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + PRIME_X, ysbp, dx2, dy2)
            }
        } else {
            if (xi + xmyi < 0f) {
                val dx2 = dx0 + 1f + UNSKEW_2D.toFloat()
                val dy2 = dy0 + UNSKEW_2D.toFloat()
                val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
                if (a2 > 0) value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp - PRIME_X, ysbp, dx2, dy2)
            } else {
                val dx2 = dx0 - (UNSKEW_2D.toFloat() + 1f)
                val dy2 = dy0 - UNSKEW_2D.toFloat()
                val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
                if (a2 > 0) value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + PRIME_X, ysbp, dx2, dy2)
            }
            if (yi < xmyi) {
                val dx2 = dx0 + UNSKEW_2D.toFloat()
                val dy2 = dy0 + 1f + UNSKEW_2D.toFloat()
                val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
                if (a2 > 0) value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp - PRIME_Y, dx2, dy2)
            } else {
                val dx2 = dx0 - UNSKEW_2D.toFloat()
                val dy2 = dy0 - (UNSKEW_2D.toFloat() + 1f)
                val a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2
                if (a2 > 0) value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp + PRIME_Y, dx2, dy2)
            }
        }
        return value
    }

    private fun grad(seed: Long, xsvp: Long, ysvp: Long, dx: Float, dy: Float): Float {
        var hash = seed xor xsvp xor ysvp
        hash *= HASH_MULTIPLIER
        hash = hash xor (hash ushr (64 - N_GRADS_2D_EXPONENT + 1))
        val gi = (hash.toInt() and ((N_GRADS_2D - 1) shl 1))
        return GRADIENTS_2D[gi] * dx + GRADIENTS_2D[gi or 1] * dy
    }

    private fun fastFloor(x: Double): Int {
        val xi = x.toInt()
        return if (x < xi) xi - 1 else xi
    }
}
