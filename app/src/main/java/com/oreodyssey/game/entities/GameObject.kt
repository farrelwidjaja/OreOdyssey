// app/src/main/java/com/oreodyssey/game/entities/GameObject.kt
package com.oreodyssey.game.entities

import com.oreodyssey.game.world.TILE_SIZE
import com.oreodyssey.game.world.WorldMap
import kotlin.math.floor
import kotlin.math.min

open class GameObject(var x: Float, var y: Float, val width: Float, val height: Float) {
    var vx = 0f
    var vy = 0f
    var onGround = false
    var hitWall = false
    var gravityScale = 1f          // 0 = melayang (kelelawar)
    val centerX get() = x + width / 2f
    val centerY get() = y + height / 2f

    companion object {
        const val GRAVITY = 2200f
        const val MAX_FALL = 1800f
    }

    /** Gravitasi + gerak + tabrakan AABB vs grid. Return kecepatan jatuh saat mendarat (0 jika tidak). */
    fun physics(dt: Float, world: WorldMap): Float {
        vy = min(vy + GRAVITY * gravityScale * dt, MAX_FALL)
        x += vx * dt
        hitWall = collideX(world)
        val impact = vy
        y += vy * dt
        val landed = collideY(world)
        return if (landed) impact else 0f
    }

    // AABB vs Grid: kotak objek diubah jadi rentang indeks tile (floor(x / TILE_SIZE)),
    // lalu hanya tile di rentang itu yang dicek, bukan seluruh dunia.
    private fun collideX(world: WorldMap): Boolean {
        val left = floor(x / TILE_SIZE).toInt()
        val right = floor((x + width - 0.01f) / TILE_SIZE).toInt()
        val top = floor(y / TILE_SIZE).toInt()
        val bottom = floor((y + height - 0.01f) / TILE_SIZE).toInt()
        for (ty in top..bottom) for (tx in left..right) {
            if (!world.isSolid(tx, ty)) continue
            if (vx > 0f) x = tx * TILE_SIZE - width
            else if (vx < 0f) x = (tx + 1) * TILE_SIZE
            vx = 0f
            return true
        }
        return false
    }

    private fun collideY(world: WorldMap): Boolean {
        onGround = false
        val left = floor(x / TILE_SIZE).toInt()
        val right = floor((x + width - 0.01f) / TILE_SIZE).toInt()
        val top = floor(y / TILE_SIZE).toInt()
        val bottom = floor((y + height - 0.01f) / TILE_SIZE).toInt()
        for (ty in top..bottom) for (tx in left..right) {
            if (!world.isSolid(tx, ty)) continue
            val falling = vy > 0f
            if (falling) { y = ty * TILE_SIZE - height; onGround = true }
            else if (vy < 0f) y = (ty + 1) * TILE_SIZE
            vy = 0f
            return falling
        }
        return false
    }

    fun overlaps(o: GameObject): Boolean =
        x < o.x + o.width && x + width > o.x && y < o.y + o.height && y + height > o.y
}