// app/src/main/java/com/oreodyssey/game/entities/Monster.kt
package com.oreodyssey.game.entities

import com.oreodyssey.game.world.TILE_SIZE
import com.oreodyssey.game.world.WorldMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

enum class MonsterType(
    val w: Float,
    val h: Float,
    val hp: Int,
    val dmg: Int,
    val speed: Float,
    val displayName: String
) {
    SLIME(56f, 44f, 30, 10, 240f, "Slime"),
    ZOMBIE(40f, 84f, 50, 15, 130f, "Zombie"),
    SKELETON(36f, 84f, 40, 12, 170f, "Skeleton"),
    BAT(40f, 28f, 20, 8, 200f, "Kelelawar"),
    SPIDER(48f, 32f, 35, 12, 210f, "Laba-laba"),
    GOLEM(56f, 92f, 120, 25, 85f, "Golem Batu")
}

class Monster(val type: MonsterType, x: Float, y: Float) : GameObject(x, y, type.w, type.h) {

    var hp = type.hp
    var burn = 0f
    var facing = 1
    var flap = 0f
    private var timer = 0f
    private var stun = 0f

    init { if (type == MonsterType.BAT) gravityScale = 0f }

    /** Knockback saat terkena serangan. Golem lebih kebal dorongan, laba-laba lebih terpental. */
    fun hurt(dmg: Int, dir: Int) {
        hp -= dmg
        val knockbackScale = when (type) {
            MonsterType.GOLEM -> 0.35f
            MonsterType.SPIDER -> 1.25f
            MonsterType.BAT -> 0.8f
            else -> 1.0f
        }
        vy = if (type == MonsterType.BAT) -100f else -380f * knockbackScale
        vx = dir * 300f * knockbackScale
        stun = 0.35f
        onGround = false
    }

    /** Memeriksa apakah koordinat tap mengenai monster ini */
    fun containsPoint(px: Float, py: Float, padding: Float = 16f): Boolean {
        return px >= x - padding && px <= x + width + padding &&
                py >= y - padding && py <= y + height + padding
    }

    /**
     * AI Deteksi Rintangan: memeriksa balok 1 tile di depan langkah monster.
     * Jika di depan adalah balok solid dan ada ruang lompat di atas, monster melompat.
     */
    private fun shouldJumpOverObstacle(world: WorldMap): Boolean {
        if (!onGround) return false
        val checkX = if (facing > 0) x + width + 8f else x - 8f
        val tx = (checkX / TILE_SIZE).toInt()
        val footTy = ((y + height - 8f) / TILE_SIZE).toInt()
        val headTy = (y / TILE_SIZE).toInt()

        if (tx !in 0 until world.width) return false
        val obstacleInFront = world.isSolid(tx, footTy)
        val spaceAbove = !world.isSolid((centerX / TILE_SIZE).toInt(), headTy - 1)
        return obstacleInFront && spaceAbove
    }

    fun update(dt: Float, world: WorldMap, player: GameObject) {
        timer -= dt; stun -= dt; flap += dt
        val dx = player.centerX - centerX
        val dy = player.centerY - centerY
        facing = if (dx >= 0f) 1 else -1

        when (type) {
            MonsterType.SLIME -> {
                // Melompat ke arah pemain jika jaraknya < 9 tile
                if (onGround && stun <= 0f) {
                    vx = 0f
                    if (timer <= 0f && abs(dx) < TILE_SIZE * 9f) {
                        vy = -720f; vx = facing * 240f; timer = 1.1f; onGround = false
                    }
                }
            }
            MonsterType.ZOMBIE, MonsterType.SKELETON -> {
                // Berjalan ke arah pemain, melompat jika ada rintangan atau menabrak tembok
                if (stun <= 0f) {
                    vx = if (abs(dx) < TILE_SIZE * 16f && abs(dx) > 8f) facing * type.speed else 0f
                    if (onGround && (hitWall || shouldJumpOverObstacle(world))) {
                        vy = -720f
                        onGround = false
                    }
                } else if (onGround) vx *= 0.9f
            }
            MonsterType.SPIDER -> {
                // Laba-laba lincah: melompat menerkam jika dekat, atau melompati balok
                if (stun <= 0f) {
                    vx = if (abs(dx) < TILE_SIZE * 16f && abs(dx) > 8f) facing * type.speed else 0f
                    if (onGround) {
                        if (abs(dx) < TILE_SIZE * 6f && timer <= 0f) {
                            vy = -850f; vx = facing * 290f; timer = 1.5f; onGround = false
                        } else if (hitWall || shouldJumpOverObstacle(world)) {
                            vy = -740f; onGround = false
                        }
                    }
                } else if (onGround) vx *= 0.9f
            }
            MonsterType.GOLEM -> {
                // Golem batu: lambat, melangkah berat, melompati undakan tanah
                if (stun <= 0f) {
                    vx = if (abs(dx) < TILE_SIZE * 14f && abs(dx) > 10f) facing * type.speed else 0f
                    if (onGround && (hitWall || shouldJumpOverObstacle(world))) {
                        vy = -680f; onGround = false
                    }
                } else if (onGround) vx *= 0.85f
            }
            MonsterType.BAT -> {
                // Terbang lurus ke pemain + gerak naik-turun (sinus)
                if (stun <= 0f) {
                    val d = hypot(dx, dy)
                    if (d < TILE_SIZE * 14f && d > 1f) {
                        vx = dx / d * type.speed
                        vy = dy / d * type.speed + sin(flap * 6f) * 90f
                    } else { vx = 0f; vy = sin(flap * 3f) * 40f }
                }
            }
        }
        physics(dt, world)
    }
}