// app/src/main/java/com/oreodyssey/game/entities/Slime.kt
package com.oreodyssey.game.entities

import com.oreodyssey.game.world.TILE_SIZE
import com.oreodyssey.game.world.WorldMap
import kotlin.math.abs

class Slime(x: Float, y: Float) : GameObject(x, y, 56f, 44f) {

    companion object { const val MAX_HP = 3 }

    var hp = MAX_HP
    private var hopTimer = 0f
    private var stun = 0f

    /** Kena serangan: kurangi nyawa dan terpental menjauh. dir = +1 (ke kanan) / -1 (ke kiri). */
    fun hurt(dmg: Int, dir: Int) {
        hp -= dmg
        vy = -450f
        vx = dir * 300f
        stun = 0.4f
        onGround = false
    }

    fun update(dt: Float, world: WorldMap, player: GameObject) {
        hopTimer -= dt
        stun -= dt
        if (onGround && stun <= 0f) {
            vx = 0f
            val dx = player.centerX - centerX
            // Melompat ke arah pemain jika jaraknya < 8 tile
            if (hopTimer <= 0f && abs(dx) < TILE_SIZE * 8f) {
                vy = -720f
                vx = if (dx > 0f) 240f else -240f
                hopTimer = 1.1f
                onGround = false
            }
        }
        physics(dt, world)
    }
}