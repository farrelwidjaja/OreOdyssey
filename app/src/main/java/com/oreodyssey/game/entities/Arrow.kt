// app/src/main/java/com/oreodyssey/game/entities/Arrow.kt
package com.oreodyssey.game.entities

class Arrow(var x: Float = 0f, var y: Float = 0f, var vx: Float = 0f, var vy: Float = 0f) {
    var life = 3f

    fun reset(x: Float, y: Float, vx: Float, vy: Float) {
        this.x = x
        this.y = y
        this.vx = vx
        this.vy = vy
        this.life = 3f
    }

    companion object {
        private const val MAX_POOL_SIZE = 32
        private val pool = ArrayList<Arrow>(MAX_POOL_SIZE)

        /**
         * Mengambil objek Arrow dari pool (atau membuat baru jika pool kosong).
         * Mencegah alokasi memori berulang saat menembakkan panah.
         */
        fun obtain(x: Float, y: Float, vx: Float, vy: Float): Arrow {
            val arrow = synchronized(pool) {
                if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else null
            } ?: Arrow()
            arrow.reset(x, y, vx, vy)
            return arrow
        }

        /**
         * Mengembalikan objek Arrow ke pool setelah selesai digunakan (life habis / mengenai target).
         */
        fun recycle(arrow: Arrow) {
            synchronized(pool) {
                if (pool.size < MAX_POOL_SIZE) {
                    pool.add(arrow)
                }
            }
        }

        /**
         * Membersihkan semua objek di pool saat game selesai.
         */
        fun clearPool() {
            synchronized(pool) {
                pool.clear()
            }
        }
    }
}