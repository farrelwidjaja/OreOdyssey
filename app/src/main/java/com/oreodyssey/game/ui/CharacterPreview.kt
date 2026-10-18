// app/src/main/java/com/oreodyssey/game/ui/CharacterPreview.kt
package com.oreodyssey.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.oreodyssey.game.data.Profile

/** Pratinjau karakter. Tap untuk memutar arah hadap. */
class CharacterPreview @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    var profile = Profile()
    private var facing = 1
    private val paint = Paint()

    init { setOnClickListener { facing = -facing; invalidate() } }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF87CEEB.toInt())
        paint.color = 0xFF3FA535.toInt()
        c.drawRect(0f, height * 0.93f, width.toFloat(), height.toFloat(), paint)
        val s = (height * 0.8f) / 100f
        c.save()
        c.translate(width / 2f - 20f * s, height * 0.12f + 14f * s)
        c.scale(s, s)
        CharacterRenderer.draw(c, paint, profile, 0f, 0f, facing)
        c.restore()
    }
}