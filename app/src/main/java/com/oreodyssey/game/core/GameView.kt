// app/src/main/java/com/oreodyssey/game/core/GameView.kt
package com.oreodyssey.game.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.oreodyssey.game.entities.Inventory
import com.oreodyssey.game.entities.Player
import com.oreodyssey.game.entities.Slime
import com.oreodyssey.game.world.Camera
import com.oreodyssey.game.world.TILE_SIZE
import com.oreodyssey.game.world.Tiles
import com.oreodyssey.game.world.WorldMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    interface Listener {
        fun onStateChanged(state: GameState)
        fun onHudChanged(health: Int, counts: IntArray, sword: Int, shield: Int, shieldDur: Int)
    }

    companion object {
        private const val REACH = TILE_SIZE * 5f
        private const val MAX_SLIMES = 8
    }

    var listener: Listener? = null
    @Volatile var state = GameState.MENU
        private set

    val inventory = Inventory()
    private val world = WorldMap()
    private val camera = Camera()
    private val player = Player()
    private val slimes = ArrayList<Slime>()
    private var thread: GameThread? = null
    private val main = Handler(Looper.getMainLooper())
    private val rnd = Random.Default

    private var invuln = 0f          // waktu kebal setelah dipukul
    private var spawnTimer = 0f
    @Volatile private var pendingReset = false

    // Input dari tombol XML (UI thread) dibaca game thread
    @Volatile private var leftHeld = false
    @Volatile private var rightHeld = false
    @Volatile private var wantJump = false
    private val taps = ConcurrentLinkedQueue<FloatArray>()

    private val paint = Paint()
    private val rectF = RectF()

    init {
        holder.addCallback(this)
        resetWorld()
    }

    // ===================== Kontrol dari Activity =====================
    fun setLeft(b: Boolean) { leftHeld = b }
    fun setRight(b: Boolean) { rightHeld = b }
    fun setJump(b: Boolean) { wantJump = b }

    fun newGame() { pendingReset = true; state = GameState.PLAYING; notifyState() }
    fun pauseGame() { if (state == GameState.PLAYING) { state = GameState.PAUSE; notifyState() } }
    fun openCraft() { if (state == GameState.PLAYING) { state = GameState.CRAFTING; notifyState() } }
    fun resumeGame() {
        if (state == GameState.PAUSE || state == GameState.CRAFTING) { state = GameState.PLAYING; notifyState() }
    }
    fun selectSlot(i: Int) { inventory.selected = i; notifyHud() }
    fun craft(i: Int): Boolean { val ok = inventory.craft(i); if (ok) notifyHud(); return ok }

    private fun notifyState() { val s = state; main.post { listener?.onStateChanged(s) } }
    private fun notifyHud() {
        val h = player.health; val c = inventory.counts.copyOf()
        val sw = inventory.swordLevel; val sh = inventory.shieldLevel; val d = inventory.shieldDur
        main.post { listener?.onHudChanged(h, c, sw, sh, d) }
    }

    // ===================== Dunia =====================
    private fun resetWorld() {
        world.generate(System.nanoTime())
        inventory.clear()
        taps.clear()
        slimes.clear()
        invuln = 0f; spawnTimer = 0f
        player.respawn(world)
        repeat(MAX_SLIMES) {
            var tx = rnd.nextInt(5, world.width - 5)
            if (kotlin.math.abs(tx - world.width / 2) < 8) tx += 12
            spawnSlime(tx.coerceIn(2, world.width - 3))
        }
    }

    private fun spawnSlime(tx: Int) {
        slimes.add(Slime(tx * TILE_SIZE + 4f, world.surfaceY(tx) * TILE_SIZE - 45f))
    }

    private fun spawnNearPlayer() {
        val side = if (rnd.nextBoolean()) 1 else -1
        val tx = (player.centerX / TILE_SIZE).toInt() + side * (10 + rnd.nextInt(5))
        spawnSlime(tx.coerceIn(2, world.width - 3))
    }

    private fun hurtPlayer() {
        if (invuln > 0f) return
        invuln = 1f
        if (inventory.shieldDur > 0) inventory.shieldDur--   // perisai menyerap serangan
        else player.health--
        player.vy = -500f
        player.onGround = false
        notifyHud()
    }

    private fun swordDamage() = when (inventory.swordLevel) { 0 -> 1; 1 -> 2; else -> 3 }

    // ===================== Update (game thread) =====================
    fun update(dt: Float) {
        if (pendingReset) { pendingReset = false; resetWorld(); notifyHud() }
        if (state != GameState.PLAYING) { taps.clear(); return }

        processTaps()

        val before = player.health
        val dir = (if (rightHeld) 1 else 0) - (if (leftHeld) 1 else 0)
        player.update(dt, world, dir, wantJump)

        if (invuln > 0f) invuln -= dt
        for (i in slimes.indices) {
            val s = slimes[i]
            s.update(dt, world, player)
            if (s.overlaps(player)) hurtPlayer()
        }

        spawnTimer += dt
        if (spawnTimer > 15f) { spawnTimer = 0f; if (slimes.size < MAX_SLIMES) spawnNearPlayer() }

        if (player.health != before) notifyHud()
        if (player.health <= 0) { state = GameState.GAME_OVER; notifyState() }
    }

    private fun processTaps() {
        var changed = false
        while (true) {
            val t = taps.poll() ?: break
            // Layar -> dunia: tambah offset kamera
            val wx = t[0] + camera.x
            val wy = t[1] + camera.y
            if (hypot(wx - player.centerX, wy - player.centerY) > REACH) continue

            // 1) Tap pada slime = serang
            var hit = false
            for (i in slimes.indices) {
                val s = slimes[i]
                if (wx >= s.x - 16f && wx <= s.x + s.width + 16f &&
                    wy >= s.y - 16f && wy <= s.y + s.height + 16f) {
                    s.hurt(swordDamage(), if (s.centerX > player.centerX) 1 else -1)
                    hit = true
                    break
                }
            }
            if (hit) { slimes.removeAll { it.hp <= 0 }; continue }

            // 2) Selain itu: hancurkan / pasang blok
            val tx = floor(wx / TILE_SIZE).toInt()
            val ty = floor(wy / TILE_SIZE).toInt()
            if (tx !in 0 until world.width || ty !in 0 until world.height) continue

            val cur = world.get(tx, ty)
            if (cur != Tiles.AIR) {
                if (ty == world.height - 1) continue
                val drop = when (cur) {
                    Tiles.GRASS -> Tiles.DIRT
                    Tiles.LEAVES -> Tiles.AIR     // daun tidak menjatuhkan apa-apa
                    else -> cur                   // WOOD & PLANK jadi kayu
                }
                if (drop != Tiles.AIR) inventory.add(drop)
                world.set(tx, ty, Tiles.AIR)
                changed = true
            } else {
                val adjacent = world.get(tx - 1, ty) != Tiles.AIR || world.get(tx + 1, ty) != Tiles.AIR ||
                        world.get(tx, ty - 1) != Tiles.AIR || world.get(tx, ty + 1) != Tiles.AIR
                if (adjacent && !player.overlapsTile(tx, ty)) {
                    val type = inventory.takeSelected()
                    if (type != Tiles.AIR) { world.set(tx, ty, type); changed = true }
                }
            }
        }
        if (changed) notifyHud()
    }

    // ===================== Draw (game thread) =====================
    private fun rect(c: Canvas, color: Long, x: Float, y: Float, w: Float, h: Float) {
        paint.color = color.toInt()
        c.drawRect(x, y, x + w, y + h, paint)
    }

    fun drawGame(canvas: Canvas) {
        val vw = width; val vh = height
        if (vw == 0 || vh == 0) return
        camera.follow(player, vw, vh, world.width * TILE_SIZE, world.height * TILE_SIZE)
        canvas.drawColor(0xFF87CEEB.toInt())

        // Matahari (tetap di layar)
        paint.color = 0xFFFFE066.toInt()
        canvas.drawCircle(vw - 140f, 110f, 55f, paint)

        // Viewport culling
        val startX = max(0, floor(camera.x / TILE_SIZE).toInt())
        val endX = min(world.width - 1, floor((camera.x + vw) / TILE_SIZE).toInt())
        val startY = max(0, floor(camera.y / TILE_SIZE).toInt())
        val endY = min(world.height - 1, floor((camera.y + vh) / TILE_SIZE).toInt())

        canvas.save()
        canvas.translate(-camera.x, -camera.y)

        for (tx in startX..endX) for (ty in startY..endY) {
            val t = world.get(tx, ty)
            val l = tx * TILE_SIZE; val tp = ty * TILE_SIZE
            if (t == Tiles.AIR) {
                if (ty > world.surfaceY(tx) + 1) rect(canvas, 0xFF3B2A1E, l, tp, TILE_SIZE, TILE_SIZE)
            } else drawTile(canvas, t, l, tp, tx, ty)
        }

        for (i in slimes.indices) drawSlime(canvas, slimes[i])
        // Berkedip saat kebal
        if (invuln <= 0f || (invuln * 10f).toInt() % 2 == 0) drawPlayer(canvas)

        canvas.restore()
    }

    // Tekstur tile: variasi dari hash posisi (tx, ty), tanpa alokasi objek
    private fun drawTile(c: Canvas, t: Int, l: Float, tp: Float, tx: Int, ty: Int) {
        val h = (tx * 73856093) xor (ty * 19349663)
        val a = ((h ushr 4) and 3) * 8f
        val b = ((h ushr 9) and 3) * 8f
        val s = TILE_SIZE
        when (t) {
            Tiles.DIRT, Tiles.GRASS -> {
                rect(c, 0xFF8B5A2B, l, tp, s, s)
                rect(c, 0xFF6B4220, l + 6f + a, tp + 10f + b, 10f, 8f)
                rect(c, 0xFF6B4220, l + 40f - a, tp + 40f - b / 2f, 8f, 8f)
                rect(c, 0xFFA06A35, l + 12f + b / 2f, tp + 44f - a / 2f, 6f, 6f)
                if (t == Tiles.GRASS) {
                    rect(c, 0xFF2E7D32, l, tp + 12f, s, 6f)
                    rect(c, 0xFF3FA535, l, tp, s, 14f)
                    rect(c, 0xFF3FA535, l + 4f + a, tp + 14f, 8f, 8f)
                }
            }
            Tiles.STONE, Tiles.COPPER -> {
                rect(c, 0xFF808080, l, tp, s, s)
                rect(c, 0xFF666666, l + 8f + a, tp + 10f + b, 18f, 12f)
                rect(c, 0xFF9A9A9A, l + 30f - a, tp + 34f - b / 2f, 16f, 10f)
                if (t == Tiles.COPPER) {
                    rect(c, 0xFFE07B39, l + 14f, tp + 14f, 12f, 12f)
                    rect(c, 0xFFE07B39, l + 36f, tp + 22f, 12f, 12f)
                    rect(c, 0xFFE07B39, l + 20f, tp + 40f, 12f, 12f)
                    rect(c, 0xFFF5B27A, l + 16f, tp + 16f, 4f, 4f)
                    rect(c, 0xFFF5B27A, l + 38f, tp + 24f, 4f, 4f)
                }
            }
            Tiles.WOOD -> {
                rect(c, 0xFF7A5230, l, tp, s, s)
                rect(c, 0xFF4E3419, l + 10f, tp, 6f, s)
                rect(c, 0xFF4E3419, l + 44f, tp, 6f, s)
                rect(c, 0xFF93653A, l + 26f, tp, 5f, s)
                if (((h ushr 12) and 3) == 0) rect(c, 0xFF4E3419, l + 20f, tp + 24f, 16f, 14f)
            }
            Tiles.LEAVES -> {
                rect(c, 0xFF2F8F3A, l, tp, s, s)
                rect(c, 0xFF1F6B2A, l + 8f + a, tp + 8f + b, 14f, 14f)
                rect(c, 0xFF1F6B2A, l + 38f - a, tp + 36f - b / 2f, 14f, 12f)
                rect(c, 0xFF52B857, l + 30f - b, tp + 6f + a, 10f, 10f)
            }
            Tiles.PLANK -> {
                rect(c, 0xFFC8924F, l, tp, s, s)
                rect(c, 0xFF8F6428, l, tp + 20f, s, 4f)
                rect(c, 0xFF8F6428, l, tp + 42f, s, 4f)
                rect(c, 0xFF8F6428, l + 32f, tp, 4f, 20f)
                rect(c, 0xFF5A3F1A, l + 6f, tp + 8f, 4f, 4f)
                rect(c, 0xFF5A3F1A, l + 54f, tp + 8f, 4f, 4f)
            }
        }
    }

    private fun drawSlime(c: Canvas, s: Slime) {
        paint.color = 0xFF3FA84A.toInt()
        rectF.set(s.x, s.y + 6f, s.x + s.width, s.y + s.height)
        c.drawRoundRect(rectF, 18f, 18f, paint)
        rect(c, 0xFF8BE594, s.x + 10f, s.y + 12f, 14f, 8f)
        rect(c, 0xFF000000, s.x + 14f, s.y + 22f, 8f, 10f)
        rect(c, 0xFF000000, s.x + 36f, s.y + 22f, 8f, 10f)
        // Bar nyawa
        rect(c, 0xFF550000, s.x, s.y - 8f, s.width, 5f)
        rect(c, 0xFFE53935, s.x, s.y - 8f, s.width * s.hp / Slime.MAX_HP.toFloat(), 5f)
    }

    private fun drawPlayer(c: Canvas) {
        val px = player.x; val py = player.y; val w = player.width
        val d = player.facing

        // Perisai di punggung (digambar sebelum badan)
        if (inventory.shieldLevel > 0) {
            val sx = if (d > 0) px - 14f else px + w - 2f
            val col = if (inventory.shieldLevel == 1) 0xFFC8924F else 0xFF9E9E9E
            rect(c, 0xFF3E2A12, sx - 2f, py + 34f, 20f, 40f)
            rect(c, col, sx, py + 36f, 16f, 36f)
        }
        // Kaki, badan, kepala, rambut, mata
        rect(c, 0xFF2B2B6B, px + 4f, py + 64f, 14f, 24f)
        rect(c, 0xFF2B2B6B, px + w - 18f, py + 64f, 14f, 24f)
        rect(c, 0xFF2F5BD8, px, py + 28f, w, 38f)
        rect(c, 0xFFF2C79B, px + 4f, py, w - 8f, 28f)
        rect(c, 0xFF5B3A1E, px + 4f, py, w - 8f, 8f)
        val ex = if (d > 0) px + w - 14f else px + 6f
        rect(c, 0xFF000000, ex, py + 12f, 8f, 8f)

        // Pedang di tangan depan
        if (inventory.swordLevel > 0) {
            val blade = if (inventory.swordLevel == 1) 0xFFD9B26F else 0xFFB8BEC4
            if (d > 0) {
                rect(c, 0xFF5B3A1E, px + w - 4f, py + 44f, 10f, 8f)
                rect(c, blade, px + w + 6f, py + 42f, 34f, 6f)
            } else {
                rect(c, 0xFF5B3A1E, px - 6f, py + 44f, 10f, 8f)
                rect(c, blade, px - 40f, py + 42f, 34f, 6f)
            }
        }
    }

    // ===================== Input (UI thread) =====================
    // Tombol gerak ada di XML. Di sini hanya tap di dunia: serang / hancurkan / pasang.
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            taps.add(floatArrayOf(e.getX(i), e.getY(i)))
        }
        return true
    }

    // ===================== Lifecycle Surface =====================
    override fun surfaceCreated(holder: SurfaceHolder) {
        thread = GameThread(holder, this).also { it.running = true; it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val t = thread ?: return
        t.running = false
        var retry = true
        while (retry) {
            try { t.join(); retry = false } catch (_: InterruptedException) {}
        }
        thread = null
    }
}