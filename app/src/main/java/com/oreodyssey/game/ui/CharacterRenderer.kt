// app/src/main/java/com/oreodyssey/game/ui/CharacterRenderer.kt
package com.oreodyssey.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.oreodyssey.game.data.Palettes
import com.oreodyssey.game.data.Profile

/** Menggambar karakter (dipakai di game dan di layar kustomisasi). Ukuran dasar 40 x 88. */
object CharacterRenderer {

    private fun r(c: Canvas, p: Paint, col: Int, x: Float, y: Float, w: Float, h: Float) {
        p.color = col
        c.drawRect(x, y, x + w, y + h, p)
    }

    fun draw(c: Canvas, p: Paint, pr: Profile, px: Float, py: Float, facing: Int) {
        val skin = Palettes.skin[pr.skin.coerceIn(0, Palettes.skin.size - 1)]
        val hair = Palettes.hair[pr.hairColor.coerceIn(0, Palettes.hair.size - 1)]
        val shirt = Palettes.shirt[pr.shirt.coerceIn(0, Palettes.shirt.size - 1)]
        val pants = Palettes.pants[pr.pants.coerceIn(0, Palettes.pants.size - 1)]
        val w = 40f
        val shoe = 0xFF3E2723.toInt()

        // Kaki + celana (cowok) / rok (cewek)
        if (pr.female) {
            r(c, p, skin, px + 8f, py + 66f, 8f, 20f)
            r(c, p, skin, px + 24f, py + 66f, 8f, 20f)
            r(c, p, pants, px - 3f, py + 54f, w + 6f, 16f)
        } else {
            r(c, p, pants, px + 4f, py + 62f, 14f, 22f)
            r(c, p, pants, px + 22f, py + 62f, 14f, 22f)
        }
        r(c, p, shoe, px + 4f, py + 83f, 14f, 5f)
        r(c, p, shoe, px + 22f, py + 83f, 14f, 5f)

        // Badan + lengan + tangan
        r(c, p, shirt, px, py + 28f, w, if (pr.female) 28f else 36f)
        r(c, p, shirt, px - 5f, py + 30f, 7f, 20f)
        r(c, p, shirt, px + w - 2f, py + 30f, 7f, 20f)
        r(c, p, skin, px - 5f, py + 48f, 7f, 8f)
        r(c, p, skin, px + w - 2f, py + 48f, 7f, 8f)

        // Kepala + mata (di sisi arah hadap)
        val hx = px + 4f
        val hw = 32f
        r(c, p, skin, hx, py, hw, 28f)
        val ex = if (facing > 0) px + w - 14f else px + 6f
        r(c, p, 0xFF000000.toInt(), ex, py + 12f, 8f, 8f)
        if (pr.female) r(c, p, 0xFF000000.toInt(), ex - 1f, py + 10f, 10f, 2f)   // bulu mata

        // Rambut. "back" = sisi belakang kepala (kebalikan arah hadap)
        val backX = if (facing > 0) hx else hx + hw - 8f
        val backOut = if (facing > 0) hx - 8f else hx + hw
        val backLong = if (facing > 0) hx - 8f else hx + hw - 4f
        when (pr.hairStyle) {
            1 -> r(c, p, hair, hx - 1f, py - 3f, hw + 2f, 11f)
            2 -> { r(c, p, hair, hx - 1f, py - 3f, hw + 2f, 12f); r(c, p, hair, backX, py, 8f, 20f) }
            3 -> {
                r(c, p, hair, hx, py - 3f, hw, 8f)
                for (i in 0..3) r(c, p, hair, hx + 2f + i * 8f, py - 13f, 6f, 11f)
            }
            4 -> { r(c, p, hair, hx - 1f, py - 3f, hw + 2f, 11f); r(c, p, hair, backLong, py, 12f, 56f) }
            5 -> { r(c, p, hair, hx - 1f, py - 3f, hw + 2f, 11f); r(c, p, hair, backOut, py + 4f, 8f, 32f) }
            6 -> r(c, p, hair, hx + 10f, py - 13f, 12f, 24f)
            7 -> r(c, p, hair, hx - 7f, py - 13f, hw + 14f, 24f)
            8 -> { r(c, p, hair, hx - 1f, py - 3f, hw + 2f, 11f); r(c, p, hair, backLong, py, 10f, 34f) }
            9 -> {
                r(c, p, hair, hx - 1f, py - 3f, hw + 2f, 11f)
                r(c, p, hair, hx - 8f, py + 8f, 8f, 34f)
                r(c, p, hair, hx + hw, py + 8f, 8f, 34f)
            }
        }
    }
}