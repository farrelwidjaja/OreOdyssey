// app/src/main/java/com/oreodyssey/game/entities/Player.kt
package com.oreodyssey.game.entities

import com.oreodyssey.game.audio.SoundManager
import com.oreodyssey.game.world.TILE_SIZE
import com.oreodyssey.game.world.WorldMap

class Player : GameObject(0f, 0f, 40f, 88f) {

    companion object {
        const val MAX_HEALTH = 100
        private const val MOVE_SPEED = 400f
        private const val JUMP_VELOCITY = -950f
        private const val FALL_DAMAGE_SPEED = 1300f
    }

    var health = MAX_HEALTH
    var facing = 1

    fun respawn(world: WorldMap) {
        val tx = world.width / 2
        x = tx * TILE_SIZE + (TILE_SIZE - width) / 2f
        y = world.surfaceY(tx) * TILE_SIZE - height - 1f
        vx = 0f; vy = 0f
        health = MAX_HEALTH
    }

    fun update(dt: Float, world: WorldMap, moveDir: Int, jump: Boolean) {
        vx = moveDir * MOVE_SPEED
        if (moveDir != 0) facing = moveDir
        if (jump && onGround) {
            vy = JUMP_VELOCITY
            onGround = false
            SoundManager.play(SoundManager.SFX_JUMP)
        }

        val impact = physics(dt, world)
        if (impact > FALL_DAMAGE_SPEED) health -= 20      // jatuh terlalu tinggi
    }

    fun overlapsTile(tx: Int, ty: Int): Boolean =
        x < (tx + 1) * TILE_SIZE && x + width > tx * TILE_SIZE &&
                y < (ty + 1) * TILE_SIZE && y + height > ty * TILE_SIZE
}