// app/src/main/java/com/oreodyssey/game/world/Camera.kt
package com.oreodyssey.game.world

import com.oreodyssey.game.entities.GameObject
import kotlin.math.floor
import kotlin.math.max

class Camera {
    var x = 0f
    var y = 0f

    fun follow(target: GameObject, viewW: Int, viewH: Int, worldW: Float, worldH: Float) {
        x = (target.centerX - viewW / 2f).coerceIn(0f, max(0f, worldW - viewW))
        y = (target.centerY - viewH / 2f).coerceIn(0f, max(0f, worldH - viewH))
        x = floor(x); y = floor(y)   // dibulatkan agar tidak ada garis tipis antar tile
    }
}

