// app/src/main/java/com/oreodyssey/game/entities/Monster.kt
package com.oreodyssey.game.entities

import com.oreodyssey.game.world.TILE_SIZE
import com.oreodyssey.game.world.WorldMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

enum class MonsterType(val w: Float, val h: Float, val hp: Int, val dmg: Int, val speed: Float) {
    SLIME(56f, 44f, 30, 10, 240f),
    ZOMBIE(40f, 84f, 50, 15, 130f),
    SKELETON(36f, 84f, 40, 12, 170f),
    BAT(40f, 28f, 20, 8, 200f)
}

class Monster(val type: MonsterType, x: Float, y: Float) : GameObject(x, y, type.w, type.h) {

    var hp = type.hp
    var burn = 0f
    var facing = 1
    var flap = 0f
    private var timer = 0f
    private var stun = 0f

    init { if (type == MonsterType.BAT) gravityScale = 0f }

    fun hurt(dmg: Int, dir: Int) {
        hp -= dmg
        vy = if (type == MonsterType.BAT) -100f else -400f
        vx = dir * 300f
        stun = 0.35f
        onGround = false
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
                // Berjalan ke arah pemain, melompat jika menabrak tembok
                if (stun <= 0f) {
                    vx = if (abs(dx) < TILE_SIZE * 16f && abs(dx) > 8f) facing * type.speed else 0f
                    if (hitWall && onGround) vy = -720f
                } else if (onGround) vx *= 0.9f
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