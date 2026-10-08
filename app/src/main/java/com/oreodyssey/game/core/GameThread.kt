// app/src/main/java/com/oreodyssey/game/core/GameThread.kt
package com.oreodyssey.game.core

import android.graphics.Canvas
import android.view.SurfaceHolder
import kotlin.math.min

class GameThread(
    private val holder: SurfaceHolder,
    private val view: GameView
) : Thread("GameThread") {

    @Volatile var running = false

    override fun run() {
        val frameNs = 1_000_000_000L / 60        // target 60 FPS
        var last = System.nanoTime()

        while (running) {
            val start = System.nanoTime()
            // Delta time (detik), dibatasi 33ms agar pemain tidak "tembus" tile saat lag
            val dt = min((start - last) / 1_000_000_000f, 0.033f)
            last = start

            view.update(dt)

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) synchronized(holder) { view.drawGame(canvas) }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }

            val sleepMs = (frameNs - (System.nanoTime() - start)) / 1_000_000L
            if (sleepMs > 0) try { sleep(sleepMs) } catch (_: InterruptedException) {}
        }
    }
}

