// app/src/main/java/com/oreodyssey/game/core/GameView.kt
package com.oreodyssey.game.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.oreodyssey.game.combat.LootTable
import com.oreodyssey.game.data.CloudSavePayload
import com.oreodyssey.game.data.Profile
import com.oreodyssey.game.entities.Arrow
import com.oreodyssey.game.entities.Inventory
import com.oreodyssey.game.entities.Items
import com.oreodyssey.game.entities.Monster
import com.oreodyssey.game.entities.MonsterType
import com.oreodyssey.game.entities.Player
import com.oreodyssey.game.audio.SoundManager
import com.oreodyssey.game.ui.CharacterRenderer
import com.oreodyssey.game.ui.SpritePool
import com.oreodyssey.game.ui.TileRenderer
import com.oreodyssey.game.world.Biome
import com.oreodyssey.game.world.Camera
import com.oreodyssey.game.world.TILE_SIZE
import com.oreodyssey.game.world.Tiles
import com.oreodyssey.game.world.WorldMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    interface Listener {
        fun onStateChanged(state: GameState)
        fun onHudChanged(health: Int, timeText: String)
        fun onInventoryChanged()
    }

    companion object {
        private const val REACH = TILE_SIZE * 5f
        private const val DAY_LEN = 300f            // 1 hari game = 5 menit
        private const val TWO_PI = 6.2831855f
        private const val ARROW_SPEED = 1500f
        private const val MINI_W = 60               // minimap menampilkan 60 x 36 tile
        private const val MINI_H = 36
        private const val FOG = 0xFF05060A.toInt()
    }

    var listener: Listener? = null
    @Volatile var state = GameState.MENU
        private set
    @Volatile var started = false
        private set
    var profile = Profile()
    var saveFile: File? = null            // null = tamu (tidak menyimpan)

    val inventory = Inventory()

    // ── Property untuk AI Companion payload ──────────────────────────────
    val playerHealth: Int get() = synchronized(lock) { player.health }

    val playerDepth: Int get() = synchronized(lock) {
        val ptx = (player.centerX / TILE_SIZE).toInt()
        val surfY = world.surfaceY(ptx)
        ((player.centerY / TILE_SIZE).toInt() - surfY).coerceAtLeast(0)
    }

    val isDaytime: Boolean get() = daylight() >= 0.4f

    val currentTimeLabel: String get() {
        val t = timeText()
        return t.split(" ").lastOrNull() ?: "Siang"
    }

    val nearbyMonsterNames: List<String> get() = synchronized(lock) {
        monsters
            .filter { kotlin.math.abs(it.centerX - player.centerX) < TILE_SIZE * 12 }
            .map { it.type.displayName }
            .distinct()
            .take(4)
    }

    val currentBiome: String get() = synchronized(lock) {
        val ptx = (player.centerX / TILE_SIZE).toInt().coerceIn(0, world.width - 1)
        when (world.biome[ptx]) {
            Biome.DESERT -> "Gurun Pasir (Desert)"
            Biome.TUNDRA -> "Tundra Salju (Tundra)"
            Biome.FOREST -> "Hutan Hijau (Forest)"
        }
    }

    val currentEquippedItemName: String get() = synchronized(lock) {
        val sel = inventory.selectedId()
        if (sel == 0) "Tangan Kosong" else Items.info(sel).name
    }

    val currentSurroundingEvent: String get() = synchronized(lock) {
        val monstersNear = monsters.filter { kotlin.math.abs(it.centerX - player.centerX) < TILE_SIZE * 8 }
        val hpPct = player.health * 100 / Player.MAX_HEALTH
        val depth = playerDepth
        when {
            hpPct <= 25 -> "Kritis! HP sangat rendah"
            monstersNear.size >= 2 -> "Dikepung oleh ${monstersNear.size} monster"
            monstersNear.size == 1 -> "Berhadapan dengan ${monstersNear.first().type.name}"
            depth > 35 -> "Menjelajah di kedalaman gua ekstrem"
            !isDaytime && depth <= 5 -> "Bertahan dari dinginnya malam di permukaan"
            else -> "Eksplorasi dan menambang dengan aman"
        }
    }
    // ─────────────────────────────────────────────────────────────────────

    private val world = WorldMap()
    private val camera = Camera()
    private val player = Player()
    private val monsters = ArrayList<Monster>()
    private val arrows = ArrayList<Arrow>()
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rnd = Random.Default
    private var thread: GameThread? = null

    private var dayTime = DAY_LEN * 0.05f
    private var invuln = 0f
    private var attackCd = 0f
    private var bowCd = 0f
    private var spawnTimer = 0f
    private var regenTimer = 0f
    private var hudTimer = 0f
    private var saveTimer = 0f
    private var lastHealth = -1

    // Input (tombol XML / keyboard di UI thread, dibaca game thread)
    @Volatile private var leftHeld = false
    @Volatile private var rightHeld = false
    @Volatile private var wantJump = false
    private val taps = ConcurrentLinkedQueue<SpritePool.TapPoint>()

    // Peta: 1 piksel bitmap = 1 tile. Diperbarui per tile, bukan digambar ulang tiap frame.
    private val mapBmp: Bitmap = Bitmap.createBitmap(world.width, world.height, Bitmap.Config.ARGB_8888)
    private val mapPixels = IntArray(world.width * world.height)
    private val mapPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val mmRect = RectF()
    private val tmpRect = RectF()
    @Volatile private var mapZoom = 1
    @Volatile private var panX = 0f
    @Volatile private var panY = 0f
    private var mapScale = 1f
    private var lastRx = -1
    private var lastRy = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val paint = Paint().apply { strokeWidth = 5f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val borderPaint = Paint().apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 3f }
    private val stars = FloatArray(80) { rnd.nextFloat() }
    private val density get() = resources.displayMetrics.density

    init {
        holder.addCallback(this)
        newWorld()
    }

    // ===================== Kontrol dari Activity =====================
    fun setLeft(b: Boolean) { leftHeld = b }
    fun setRight(b: Boolean) { rightHeld = b }
    fun setJump(b: Boolean) { wantJump = b }

    private fun setState(s: GameState) { state = s; notifyState() }

    private fun clearArrows() {
        for (i in arrows.indices) {
            Arrow.recycle(arrows[i])
        }
        arrows.clear()
    }

    private fun clearTaps() {
        while (true) {
            val t = taps.poll() ?: break
            SpritePool.TapPoint.recycle(t)
        }
    }

    fun startGame(loadSave: Boolean) {
        synchronized(lock) {
            var loaded = false
            val f = saveFile
            if (loadSave && f != null && f.exists()) loaded = loadFrom(f)
            if (!loaded) newWorld()
            else { monsters.clear(); clearArrows(); clearTaps(); rebuildMap() }
            reveal()
            started = true
            state = GameState.PLAYING
            val ptx = (player.centerX / TILE_SIZE).toInt()
            val pty = (player.centerY / TILE_SIZE).toInt()
            SoundManager.updateBgmForDepth((pty - world.surfaceY(ptx)).coerceAtLeast(0))
        }
        notifyState(); notifyHud(); notifyInv()
    }

    fun respawn() {
        synchronized(lock) {
            monsters.clear(); clearArrows()
            player.respawn(world)
            invuln = 1.5f
            state = GameState.PLAYING
            SoundManager.updateBgmForDepth(0)
        }
        notifyState(); notifyHud()
    }

    fun pauseGame() {
        if (state == GameState.PLAYING || state == GameState.BAG || state == GameState.MAP) setState(GameState.PAUSE)
    }
    fun resumeGame() { if (state == GameState.PAUSE) setState(GameState.PLAYING) }

    fun applyCloudSave(payload: CloudSavePayload) {
        synchronized(lock) {
            profile = payload.profile
            for (i in 0 until Inventory.SIZE) {
                if (i < payload.invIds.size) {
                    inventory.ids[i] = payload.invIds[i]
                    inventory.counts[i] = payload.invCounts[i]
                }
            }
            inventory.selected = payload.invSelected.coerceIn(0, Inventory.HOTBAR - 1)
            if (payload.playerHealth > 0) {
                player.health = payload.playerHealth
            }
        }
        notifyState(); notifyHud(); notifyInv()
    }
    fun toggleBag() {
        if (state == GameState.PLAYING) setState(GameState.BAG)
        else if (state == GameState.BAG) setState(GameState.PLAYING)
    }
    fun toggleMap() {
        if (state == GameState.PLAYING) { mapZoom = 1; panX = 0f; panY = 0f; setState(GameState.MAP) }
        else if (state == GameState.MAP) setState(GameState.PLAYING)
    }
    fun mapZoomIn() { mapZoom = min(8, mapZoom * 2) }
    fun mapZoomOut() { mapZoom = max(1, mapZoom / 2); if (mapZoom == 1) { panX = 0f; panY = 0f } }

    fun selectSlot(i: Int) { inventory.selected = i; notifyInv() }
    fun craftRecipe(i: Int): Boolean {
        val ok = inventory.craft(Items.recipes[i])
        if (ok) notifyInv()
        return ok
    }

    private fun notifyState() { val s = state; main.post { listener?.onStateChanged(s) } }
    private fun notifyInv() { main.post { listener?.onInventoryChanged() } }
    private fun notifyHud() {
        val h = player.health; val t = timeText()
        main.post { listener?.onHudChanged(h, t) }
    }

    // ===================== Siang / malam =====================
    private fun daylight(): Float {
        // sin positif = siang, negatif = malam. Diperbesar agar siang & malam terasa panjang,
        // dan transisi (senja/fajar) singkat. Hasil 0 (gelap) .. 1 (terang)
        val l = sin(dayTime / DAY_LEN * TWO_PI)
        return (l * 2f + 0.5f).coerceIn(0f, 1f)
    }

    private fun timeText(): String {
        val hrs = ((dayTime / DAY_LEN) * 24f + 6f) % 24f     // 0 detik = 06:00
        val hh = hrs.toInt()
        val mm = ((hrs - hh) * 60f).toInt()
        val label = when (hh) { in 6..9 -> "Pagi"; in 10..14 -> "Siang"; in 15..17 -> "Sore"; else -> "Malam" }
        return "%02d:%02d %s".format(hh, mm, label)
    }

    // ===================== Dunia, peta, save =====================
    private fun newWorld() {
        world.generate(System.nanoTime())
        inventory.clear()
        clearTaps(); monsters.clear(); clearArrows()
        dayTime = DAY_LEN * 0.05f
        invuln = 0f; spawnTimer = 0f; saveTimer = 0f
        player.respawn(world)
        rebuildMap()
        repeat(8) {
            var tx = rnd.nextInt(5, world.width - 5)
            if (abs(tx - world.width / 2) < 8) tx += 14
            tx = tx.coerceIn(2, world.width - 3)
            monsters.add(Monster(MonsterType.SLIME, tx * TILE_SIZE + 4f,
                world.surfaceY(tx) * TILE_SIZE - MonsterType.SLIME.h - 1f))
        }
    }

    private fun tileMapColor(t: Int, x: Int, y: Int): Int = when (t) {
        Tiles.AIR -> if (y > world.surfaceY(x) + 1) 0xFF2A1E16.toInt() else 0xFFA9D8F0.toInt()
        Tiles.DIRT -> 0xFF8B5A2B.toInt()
        Tiles.GRASS -> 0xFF3FA535.toInt()
        Tiles.STONE -> 0xFF808080.toInt()
        Tiles.COPPER -> 0xFFFF8F3F.toInt()
        Tiles.WOOD -> 0xFF5A3A1A.toInt()
        Tiles.LEAVES -> 0xFF2F8F3A.toInt()
        Tiles.PLANK -> 0xFFC8924F.toInt()
        Tiles.COAL -> 0xFF000000.toInt()
        Tiles.IRON -> 0xFFE8C8B0.toInt()
        Tiles.GOLD -> 0xFFFFD700.toInt()
        Tiles.DIAMOND -> 0xFF26E0F0.toInt()
        Tiles.SAND -> 0xFFE0C870.toInt()
        Tiles.SNOW, Tiles.ICE -> 0xFFD8F0F8.toInt()
        Tiles.WATER -> 0xFF1976D2.toInt()
        Tiles.LAVA -> 0xFFFF3D00.toInt()
        Tiles.CRYSTAL -> 0xFFAB47BC.toInt()
        Tiles.MOSS_STONE -> 0xFF4E7D53.toInt()
        Tiles.MUSHROOM -> 0xFFE53935.toInt()
        else -> FOG
    }

    private fun rebuildMap() {
        val w = world.width
        for (x in 0 until w) for (y in 0 until world.height)
            mapPixels[y * w + x] = if (world.explored[x][y].toInt() != 0) tileMapColor(world.get(x, y), x, y) else FOG
        mapBmp.setPixels(mapPixels, 0, w, 0, 0, w, world.height)
        lastRx = -1; lastRy = -1
    }

    private fun refreshPixel(x: Int, y: Int) {
        if (x in 0 until world.width && y in 0 until world.height && world.explored[x][y].toInt() != 0)
            mapBmp.setPixel(x, y, tileMapColor(world.get(x, y), x, y))
    }

    /** Membuka kabut di sekitar pemain (elips 33 x 21 tile). Hanya dihitung saat pemain pindah tile. */
    private fun reveal() {
        val ptx = (player.centerX / TILE_SIZE).toInt()
        val pty = (player.centerY / TILE_SIZE).toInt()
        if (ptx == lastRx && pty == lastRy) return
        lastRx = ptx; lastRy = pty
        for (dx in -16..16) for (dy in -10..10) {
            if (dx * dx / 256f + dy * dy / 100f > 1f) continue
            val x = ptx + dx; val y = pty + dy
            if (x !in 0 until world.width || y !in 0 until world.height) continue
            if (world.explored[x][y].toInt() == 0) {
                world.explored[x][y] = 1
                mapBmp.setPixel(x, y, tileMapColor(world.get(x, y), x, y))
            }
        }
    }

    /** Data class snapshot immutable — dibuat cepat di dalam lock, ditulis ke disk di IO thread */
    private data class SaveSnapshot(
        val tilesCopy: Array<ByteArray>,
        val exploredCopy: Array<ByteArray>,
        val surfaceCopy: IntArray,
        val px: Float, val py: Float, val health: Int,
        val invIds: IntArray, val invCounts: IntArray, val invSelected: Int,
        val dayTime: Float
    )

    /** Ambil snapshot cepat di dalam lock (<0.5ms), lalu tulis ke disk di IO thread (non-blocking). */
    fun saveNow(): Boolean {
        val f = saveFile ?: return false
        if (!started) return false
        // Buat snapshot di dalam lock — hanya copy data primitif, sangat cepat
        val snap = synchronized(lock) {
            SaveSnapshot(
                tilesCopy = Array(world.width) { world.tileCopy(it) },
                exploredCopy = Array(world.width) { world.exploredCopy(it) },
                surfaceCopy = world.surfaceCopy(),
                px = player.x, py = player.y, health = player.health,
                invIds = inventory.ids.copyOf(),
                invCounts = inventory.counts.copyOf(),
                invSelected = inventory.selected,
                dayTime = dayTime
            )
        }
        // Tulis ke disk di background — tidak memblokir game thread
        ioScope.launch {
            try {
                val tmp = File(f.parentFile, f.name + ".tmp")
                DataOutputStream(BufferedOutputStream(GZIPOutputStream(FileOutputStream(tmp)))).use { out ->
                    out.writeInt(1)
                    out.writeInt(world.width); out.writeInt(world.height)
                    for (x in 0 until world.width) { out.write(snap.tilesCopy[x]); out.write(snap.exploredCopy[x]) }
                    for (x in 0 until world.width) out.writeInt(snap.surfaceCopy[x])
                    out.writeFloat(snap.px); out.writeFloat(snap.py); out.writeInt(snap.health)
                    for (i in 0 until Inventory.SIZE) { out.writeInt(snap.invIds[i]); out.writeInt(snap.invCounts[i]) }
                    out.writeInt(snap.invSelected)
                    out.writeFloat(snap.dayTime)
                }
                tmp.renameTo(f)
            } catch (_: Exception) {}
        }
        return true
    }

    private fun loadFrom(f: File): Boolean = try {
        DataInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(f)))).use { inp ->
            if (inp.readInt() != 1) return@use false
            if (!world.read(inp)) return@use false
            player.x = inp.readFloat(); player.y = inp.readFloat(); player.health = inp.readInt()
            player.vx = 0f; player.vy = 0f
            for (i in 0 until Inventory.SIZE) { inventory.ids[i] = inp.readInt(); inventory.counts[i] = inp.readInt() }
            inventory.selected = inp.readInt().coerceIn(0, Inventory.HOTBAR - 1)
            dayTime = inp.readFloat()
            invuln = 1f
            true
        }
    } catch (e: Exception) { false }

    // ===================== Update (game thread) =====================
    fun update(dt: Float) { synchronized(lock) { step(dt) } }

    private fun step(dt: Float) {
        if (state != GameState.PLAYING) { clearTaps(); return }
        dayTime = (dayTime + dt) % DAY_LEN
        invuln -= dt; attackCd -= dt; bowCd -= dt
        processTaps()

        val dir = (if (rightHeld) 1 else 0) - (if (leftHeld) 1 else 0)
        player.update(dt, world, dir, wantJump)
        reveal()

        // Cek bahaya lingkungan (kaktus, lava, air)
        val minTx = (player.x / TILE_SIZE).toInt()
        val maxTx = ((player.x + player.width) / TILE_SIZE).toInt()
        val minTy = (player.y / TILE_SIZE).toInt()
        val maxTy = ((player.y + player.height) / TILE_SIZE).toInt()
        for (ty in minTy..maxTy) {
            for (tx in minTx..maxTx) {
                if (tx in 0 until world.width && ty in 0 until world.height &&
                    player.overlapsTile(tx, ty) && world.isDamaging(tx, ty)) {
                    val tile = world.get(tx, ty)
                    val hazardDmg = when (tile) {
                        Tiles.LAVA -> 25
                        Tiles.CACTUS -> 8
                        else -> 4
                    }
                    hurtPlayer(hazardDmg)
                }
            }
        }

        val light = daylight()
        val night = light < 0.4f
        val ptx = (player.centerX / TILE_SIZE).toInt()
        val pty = (player.centerY / TILE_SIZE).toInt()
        val currentDepth = (pty - world.surfaceY(ptx)).coerceAtLeast(0)
        SoundManager.updateBgmForDepth(currentDepth)

        // Monster
        for (i in monsters.indices) {
            val m = monsters[i]
            m.update(dt, world, player)
            if (m.overlaps(player)) hurtPlayer(m.type.dmg)
            // Zombie terbakar jika terkena matahari di permukaan
            if (m.type == MonsterType.ZOMBIE && light > 0.75f &&
                m.centerY < (world.surfaceY((m.centerX / TILE_SIZE).toInt()) + 1) * TILE_SIZE) {
                m.burn += dt * 20f
                if (m.burn >= 1f) { val b = m.burn.toInt(); m.hp -= b; m.burn -= b }
            }
        }

        // Panah: gravitasi ringan, berhenti jika kena blok atau monster
        for (i in arrows.indices.reversed()) {
            val a = arrows[i]
            a.vy += 500f * dt
            a.x += a.vx * dt; a.y += a.vy * dt; a.life -= dt
            var dead = a.life <= 0f
            if (!dead && world.isSolid(floor(a.x / TILE_SIZE).toInt(), floor(a.y / TILE_SIZE).toInt())) dead = true
            if (!dead) for (m in monsters) {
                if (m.containsPoint(a.x, a.y, 4f)) {
                    m.hurt(22, if (a.vx > 0f) 1 else -1)
                    SpritePool.emitDamage(m.centerX, m.y - 12f, "-22", 0xFF80D8FF.toInt())
                    dead = true; break
                }
            }
            if (dead) {
                Arrow.recycle(arrows.removeAt(i))
            }
        }

        // Partikel debu galian & angka damage melayang
        SpritePool.updateParticles(dt)
        SpritePool.updateDamageTexts(dt)

        // Monster mati -> efek partikel kekalahan & loot
        var killed = false
        for (i in monsters.indices.reversed()) {
            val m = monsters[i]
            if (m.hp <= 0) {
                val deathColor = when (m.type) {
                    MonsterType.SLIME -> 0xFF8BE594.toInt()
                    MonsterType.ZOMBIE -> 0xFF6FAF5A.toInt()
                    MonsterType.SKELETON -> 0xFFE8E8E0.toInt()
                    MonsterType.BAT -> 0xFF9C27B0.toInt()
                    MonsterType.SPIDER -> 0xFFE53935.toInt()
                    MonsterType.GOLEM -> 0xFF9E9E9E.toInt()
                }
                SpritePool.emitDust(m.centerX, m.centerY, deathColor, 10)
                LootTable.dropRewards(m, inventory) { earned ->
                    profile.coins += earned
                    notifyHud()
                }
                monsters.removeAt(i)
                killed = true
                SoundManager.play(SoundManager.SFX_MONSTER_DIE)
            }
        }
        if (killed) notifyInv()
        monsters.removeAll {
            abs(it.centerX - player.centerX) > TILE_SIZE * 45f || abs(it.centerY - player.centerY) > TILE_SIZE * 35f
        }

        // Spawn: malam lebih sering dan lebih banyak
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            val underground = pty > world.surfaceY(ptx) + 10
            spawnTimer = if (night) 1.5f else if (underground) 3f else 6f
            val cap = if (night) 30 else if (underground) 20 else 14
            if (monsters.size < cap) trySpawn(night, underground, ptx, pty)
        }

        // Regenerasi 1 HP / 2 detik
        regenTimer += dt
        if (regenTimer >= 2f) { regenTimer = 0f; if (player.health < Player.MAX_HEALTH) player.health++ }

        hudTimer += dt
        if (player.health != lastHealth || hudTimer >= 1f) { lastHealth = player.health; hudTimer = 0f; notifyHud() }

        saveTimer += dt
        if (saveTimer >= 30f) { saveTimer = 0f; saveNow() }     // autosave tiap 30 detik

        if (player.health <= 0) { player.health = 0; setState(GameState.GAME_OVER); notifyHud() }
    }

    private fun trySpawn(night: Boolean, underground: Boolean, ptx: Int, pty: Int) {
        for (attempt in 0 until 10) {
            val side = if (rnd.nextBoolean()) 1 else -1
            val tx = (ptx + side * (16 + rnd.nextInt(12))).coerceIn(2, world.width - 3)
            var ty: Int
            val type: MonsterType
            if (underground) {
                ty = pty + rnd.nextInt(-8, 9)
                if (ty <= world.surfaceY(tx) + 8 || ty !in 2 until world.height - 2) continue
                if (world.get(tx, ty) != Tiles.AIR || world.get(tx, ty - 1) != Tiles.AIR || !world.isSolid(tx, ty + 1)) continue
                val depth = ty - world.surfaceY(tx)
                type = when {
                    depth > 35 && rnd.nextInt(10) < 3 -> MonsterType.GOLEM
                    rnd.nextInt(10) in 0..2 -> MonsterType.SPIDER
                    rnd.nextInt(10) in 3..5 -> MonsterType.SKELETON
                    rnd.nextInt(10) in 6..7 -> MonsterType.BAT
                    else -> MonsterType.ZOMBIE
                }
            } else {
                ty = world.surfaceY(tx) - 1
                type = if (!night) MonsterType.SLIME else when (rnd.nextInt(10)) {
                    in 0..3 -> MonsterType.ZOMBIE
                    in 4..5 -> MonsterType.SPIDER
                    in 6..7 -> MonsterType.SLIME
                    in 8..9 -> MonsterType.SKELETON
                    else -> MonsterType.BAT
                }
            }
            monsters.add(Monster(type, tx * TILE_SIZE + 4f, (ty + 1) * TILE_SIZE - type.h - 1f))
            return
        }
    }

    private fun chance(p: Float) = rnd.nextFloat() < p

    /** Perisai terbaik yang ada di tas / hotbar dipakai otomatis. */
    private fun bestShield(): Int {
        var best = 0; var pct = 0
        for (i in 0 until Inventory.SIZE) {
            if (inventory.counts[i] <= 0) continue
            val s = Items.info(inventory.ids[i]).shield
            if (s > pct) { pct = s; best = inventory.ids[i] }
        }
        return best
    }

    private fun hurtPlayer(dmg: Int) {
        if (invuln > 0f) return
        invuln = 0.8f
        val red = Items.info(bestShield()).shield
        val finalDmg = max(1, dmg * (100 - red) / 100)
        player.health -= finalDmg
        player.vy = -450f
        player.onGround = false
        SpritePool.emitDamage(player.centerX, player.y - 12f, "-$finalDmg", 0xFFFF1744.toInt())
        SoundManager.play(SoundManager.SFX_HURT)
    }

    private fun shoot(wx: Float, wy: Float) {
        if (bowCd > 0f) return
        if (!inventory.remove(Items.ARROW, 1)) return
        val sy = player.y + 30f
        val dx = wx - player.centerX; val dy = wy - sy
        val d = max(1f, hypot(dx, dy))
        arrows.add(Arrow.obtain(player.centerX, sy, dx / d * ARROW_SPEED, dy / d * ARROW_SPEED))
        player.facing = if (dx >= 0f) 1 else -1
        bowCd = 0.4f
        SoundManager.play(SoundManager.SFX_ARROW)
        notifyInv()
    }

    private fun processTaps() {
        var changed = false
        while (true) {
            val t = taps.poll() ?: break
            // Layar -> dunia: tambah offset kamera
            val wx = t.x + camera.x
            val wy = t.y + camera.y
            SpritePool.TapPoint.recycle(t)
            val selId = inventory.selectedId()
            val info = Items.info(selId)

            // 1) Makan / minum ramuan
            if (info.heal > 0) {
                if (player.health < Player.MAX_HEALTH) {
                    player.health = min(Player.MAX_HEALTH, player.health + info.heal)
                    inventory.consumeSelected(); changed = true
                }
                continue
            }
            // 2) Busur: tembak ke arah tap (tidak perlu dekat)
            if (selId == Items.BOW) { shoot(wx, wy); continue }

            if (hypot(wx - player.centerX, wy - player.centerY) > REACH) continue

            // 3) Tap monster = serang (damage dari pedang yang dipegang, tangan kosong = 5)
            var hit = false
            for (m in monsters) {
                if (m.containsPoint(wx, wy)) {
                    if (attackCd <= 0f) {
                        val dmg = if (info.damage > 0) info.damage else 5
                        m.hurt(dmg, if (m.centerX > player.centerX) 1 else -1)
                        SpritePool.emitDamage(m.centerX, m.y - 12f, "-$dmg", 0xFFFFD54F.toInt())
                        attackCd = 0.25f
                        SoundManager.play(SoundManager.SFX_SWING)
                    }
                    hit = true; break
                }
            }
            if (hit) continue

            // 4) Hancurkan / pasang blok
            val tx = floor(wx / TILE_SIZE).toInt()
            val ty = floor(wy / TILE_SIZE).toInt()
            if (tx !in 0 until world.width || ty !in 0 until world.height) continue
            val cur = world.get(tx, ty)
            if (cur != Tiles.AIR) {
                if (ty == world.height - 1) continue
                val drop = when (cur) {
                    Tiles.GRASS -> Tiles.DIRT
                    Tiles.LEAVES -> if (chance(0.08f)) Items.APPLE else 0
                    Tiles.LAVA -> 0
                    else -> cur
                }
                if (drop != 0) { inventory.add(drop); SoundManager.play(SoundManager.SFX_PICKUP) }
                val oreCoin = when (cur.toInt()) {
                    Tiles.COPPER -> 1
                    Tiles.IRON -> 2
                    Tiles.GOLD -> 5
                    Tiles.DIAMOND -> 10
                    Tiles.CRYSTAL -> 8
                    else -> 0
                }
                if (oreCoin > 0) {
                    profile.coins += oreCoin
                    notifyHud()
                }
                val dustColor = when (cur.toInt()) {
                    Tiles.GRASS, Tiles.DIRT -> 0xFF8B5A2B.toInt()
                    Tiles.STONE, Tiles.COAL, Tiles.IRON -> 0xFF808080.toInt()
                    Tiles.COPPER -> 0xFFD2691E.toInt()
                    Tiles.GOLD -> 0xFFFFD700.toInt()
                    Tiles.DIAMOND -> 0xFF00E5FF.toInt()
                    Tiles.CRYSTAL -> 0xFFBA68C8.toInt()
                    Tiles.LAVA -> 0xFFFF3D00.toInt()
                    Tiles.MOSS_STONE -> 0xFF4E7D53.toInt()
                    Tiles.WOOD, Tiles.PLANK -> 0xFF7A5230.toInt()
                    Tiles.LEAVES -> 0xFF2E7D32.toInt()
                    Tiles.SAND -> 0xFFE0C068.toInt()
                    Tiles.SNOW, Tiles.ICE -> 0xFFE0F7FA.toInt()
                    else -> 0xFF808080.toInt()
                }
                SpritePool.emitDust(tx * TILE_SIZE + TILE_SIZE / 2f, ty * TILE_SIZE + TILE_SIZE / 2f, dustColor, 5)
                world.set(tx, ty, Tiles.AIR); refreshPixel(tx, ty)
                SoundManager.play(SoundManager.SFX_MINE)
                changed = true
            } else if (info.placeable) {
                val adjacent = world.get(tx - 1, ty) != Tiles.AIR || world.get(tx + 1, ty) != Tiles.AIR ||
                        world.get(tx, ty - 1) != Tiles.AIR || world.get(tx, ty + 1) != Tiles.AIR
                if (adjacent && !player.overlapsTile(tx, ty)) {
                    world.set(tx, ty, selId); refreshPixel(tx, ty)
                    inventory.consumeSelected()
                    SoundManager.play(SoundManager.SFX_PLACE)
                    changed = true
                }
            }
        }
        if (changed) notifyInv()
    }

    // ===================== Draw (game thread) =====================
    private fun rect(c: Canvas, color: Long, x: Float, y: Float, w: Float, h: Float) {
        paint.color = color.toInt()
        c.drawRect(x, y, x + w, y + h, paint)
    }

    private fun rectI(c: Canvas, color: Int, x: Float, y: Float, w: Float, h: Float) {
        paint.color = color
        c.drawRect(x, y, x + w, y + h, paint)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    fun drawGame(canvas: Canvas) { synchronized(lock) { render(canvas) } }

    private fun render(canvas: Canvas) {
        val vw = width; val vh = height
        if (vw == 0 || vh == 0) return
        if (state == GameState.MAP) { drawFullMap(canvas, vw, vh); return }

        camera.follow(player, vw, vh, world.width * TILE_SIZE, world.height * TILE_SIZE)
        val light = daylight()
        canvas.drawColor(lerpColor(0xFF0B1030.toInt(), 0xFF87CEEB.toInt(), light))

        // Bintang (malam)
        if (light < 0.8f) {
            val a = ((1f - light) * 255f).toInt().coerceIn(0, 255)
            paint.color = Color.argb(a, 255, 255, 255)
            var i = 0
            while (i < stars.size) {
                val sx = stars[i] * vw; val sy = stars[i + 1] * vh * 0.6f
                canvas.drawRect(sx, sy, sx + 3f, sy + 3f, paint)
                i += 2
            }
        }
        // Matahari (0..0.5 hari) dan bulan (0.5..1) bergerak melengkung dari kiri ke kanan
        val sp = dayTime / DAY_LEN * 2f
        val isSun = sp <= 1f
        val q = if (isSun) sp else sp - 1f
        paint.color = if (isSun) 0xFFFFE066.toInt() else 0xFFEFEFF5.toInt()
        canvas.drawCircle(vw * q, vh * 0.55f - sin(q * 3.1415927f) * vh * 0.45f, if (isSun) 55f else 40f, paint)

        // Viewport culling & chunk loading: minta chunk yang terlihat di kamera + buffer margin
        val startX = max(0, floor(camera.x / TILE_SIZE).toInt())
        val endX = min(world.width - 1, floor((camera.x + vw) / TILE_SIZE).toInt())
        val startY = max(0, floor(camera.y / TILE_SIZE).toInt())
        val endY = min(world.height - 1, floor((camera.y + vh) / TILE_SIZE).toInt())

        val minCx = (startX / Chunk.SIZE - 1).coerceAtLeast(0)
        val maxCx = (endX / Chunk.SIZE + 1).coerceAtMost(world.chunkCols - 1)
        val minCy = (startY / Chunk.SIZE - 1).coerceAtLeast(0)
        val maxCy = (endY / Chunk.SIZE + 1).coerceAtMost(world.chunkRows - 1)
        world.requestChunks(minCx, maxCx, minCy, maxCy)

        canvas.save()
        canvas.translate(-camera.x, -camera.y)

        for (tx in startX..endX) for (ty in startY..endY) {
            val t = world.get(tx, ty)
            val l = tx * TILE_SIZE; val tp = ty * TILE_SIZE
            if (t == Tiles.AIR) {
                if (ty > world.surfaceY(tx) + 1) rect(canvas, 0xFF3B2A1E, l, tp, TILE_SIZE, TILE_SIZE)
            } else drawTile(canvas, t, l, tp, tx, ty)
        }

        for (i in monsters.indices) drawMonster(canvas, monsters[i])
        for (i in arrows.indices) {
            val a = arrows[i]
            paint.color = 0xFFEEEEEE.toInt()
            canvas.drawLine(a.x - a.vx * 0.03f, a.y - a.vy * 0.03f, a.x, a.y, paint)
        }
        for (p in SpritePool.particles) {
            if (p.active) {
                paint.color = p.color
                canvas.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, paint)
            }
        }
        for (d in SpritePool.damageTexts) {
            if (d.active) {
                textPaint.color = d.color
                textPaint.textSize = 14f * density
                textPaint.alpha = ((d.life / 0.65f).coerceIn(0f, 1f) * 255).toInt()
                canvas.drawText(d.text, d.x, d.y, textPaint)
            }
        }
        if (invuln <= 0f || (invuln * 10f).toInt() % 2 == 0) drawPlayer(canvas)
        canvas.restore()

        // Gelap: malam, dan makin dalam di bawah tanah
        val nightA = ((1f - light) * 150f).toInt()
        val ptx = (player.centerX / TILE_SIZE).toInt()
        val depth = max(0f, player.centerY / TILE_SIZE - world.surfaceY(ptx) - 4f)
        val depthA = min(110, (depth * 1.2f).toInt())
        val a = max(nightA, depthA)
        if (a > 0) {
            paint.color = Color.argb(a, 5, 8, 30)
            canvas.drawRect(0f, 0f, vw.toFloat(), vh.toFloat(), paint)
        }

        drawMiniMap(canvas)
    }

    private fun drawMiniMap(c: Canvas) {
        val d = density
        val mw = 150f * d; val mh = 90f * d; val left = 8f * d; val top = 8f * d
        mmRect.set(left, top, left + mw, top + mh)
        val ptx = (player.centerX / TILE_SIZE).toInt()
        val pty = (player.centerY / TILE_SIZE).toInt()
        val sl = (ptx - MINI_W / 2).coerceIn(0, world.width - MINI_W)
        val st = (pty - MINI_H / 2).coerceIn(0, world.height - MINI_H)
        srcRect.set(sl, st, sl + MINI_W, st + MINI_H)

        paint.color = Color.BLACK
        c.drawRect(left - 3f, top - 3f, left + mw + 3f, top + mh + 3f, paint)
        c.drawBitmap(mapBmp, srcRect, mmRect, mapPaint)

        val s = mw / MINI_W
        paint.color = 0xFFFF3B30.toInt()                       // monster = titik merah
        for (i in monsters.indices) {
            val m = monsters[i]
            val mx = m.centerX / TILE_SIZE - sl; val my = m.centerY / TILE_SIZE - st
            if (mx >= 0f && mx <= MINI_W && my >= 0f && my <= MINI_H) c.drawCircle(left + mx * s, top + my * s, 2.5f * d, paint)
        }
        paint.color = Color.WHITE                              // pemain = titik putih
        c.drawCircle(left + (player.centerX / TILE_SIZE - sl) * s, top + (player.centerY / TILE_SIZE - st) * s, 4f * d, paint)
    }

    private fun drawFullMap(c: Canvas, vw: Int, vh: Int) {
        val d = density
        c.drawColor(0xFF0B0E14.toInt())
        val s0 = min((vw - 40f * d) / world.width, (vh - 40f * d) / world.height)   // skala agar muat layar
        val scale = s0 * mapZoom
        mapScale = scale
        val ptx = player.centerX / TILE_SIZE
        val pty = player.centerY / TILE_SIZE
        // Zoom 1x = seluruh dunia. Zoom lebih besar = berpusat di pemain (+ geser).
        val cx = if (mapZoom == 1) world.width / 2f else (ptx + panX).coerceIn(0f, world.width.toFloat())
        val cy = if (mapZoom == 1) world.height / 2f else (pty + panY).coerceIn(0f, world.height.toFloat())
        val left = vw / 2f - cx * scale
        val top = vh / 2f - cy * scale
        dstRect.set(left, top, left + world.width * scale, top + world.height * scale)
        c.drawBitmap(mapBmp, null, dstRect, mapPaint)
        c.drawRect(dstRect, borderPaint)

        // Penanda pemain berkedip
        paint.color = if ((System.currentTimeMillis() / 400) % 2L == 0L) 0xFFFF3B30.toInt() else 0xFFFFEB3B.toInt()
        c.drawCircle(left + ptx * scale, top + pty * scale, max(5f * d, scale * 1.6f), paint)

        textPaint.textSize = 14f * d
        val depth = (pty - world.surfaceY(ptx.toInt())).toInt()
        c.drawText("Posisi X:${ptx.toInt()} Y:${pty.toInt()}  |  Kedalaman: ${max(0, depth)}  |  Zoom ${mapZoom}x" +
                (if (mapZoom > 1) "  |  geser layar untuk menggeser peta" else ""), 16f * d, vh - 14f * d, textPaint)
    }

    // Tekstur tile: pakai sprite sheet jika tersedia, fallback ke rect primitif
    private fun drawTile(c: Canvas, t: Int, l: Float, tp: Float, tx: Int, ty: Int) {
        // Coba render pakai spritesheet Kenney jika didukung
        if (TileRenderer.isReady && TileRenderer.drawTile(c, t, l, tp)) {
            return
        }
        // Fallback: rect primitif (zero-allocation)
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
            Tiles.STONE -> {
                rect(c, 0xFF808080, l, tp, s, s)
                rect(c, 0xFF666666, l + 8f + a, tp + 10f + b, 18f, 12f)
                rect(c, 0xFF9A9A9A, l + 30f - a, tp + 34f - b / 2f, 16f, 10f)
            }
            Tiles.COPPER -> drawOre(c, l, tp, a, b, 0xFFE07B39, 0xFFF5B27A)
            Tiles.COAL -> drawOre(c, l, tp, a, b, 0xFF1A1A1A, 0xFF444444)
            Tiles.IRON -> drawOre(c, l, tp, a, b, 0xFFD2A98C, 0xFFF0D5C0)
            Tiles.GOLD -> drawOre(c, l, tp, a, b, 0xFFFFC107, 0xFFFFF176)
            Tiles.DIAMOND -> drawOre(c, l, tp, a, b, 0xFF26C6DA, 0xFFB2EBF2)
            Tiles.WOOD -> {
                rect(c, 0xFF7A5230, l, tp, s, s)
                rect(c, 0xFF4E3419, l + 10f, tp, 6f, s)
                rect(c, 0xFF4E3419, l + 44f, tp, 6f, s)
                rect(c, 0xFF93653A, l + 26f, tp, 5f, s)
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
            Tiles.SAND -> rect(c, 0xFFE0C870, l, tp, s, s)
            Tiles.SNOW -> rect(c, 0xFFF0F4F8, l, tp, s, s)
            Tiles.ICE -> {
                rect(c, 0xFFB0E0E6, l, tp, s, s)
                rect(c, 0xFFE0FFFF, l + 4f + a, tp + 4f + b, 12f, 8f)
            }
            Tiles.WATER -> rect(c, 0xFF1976D2, l, tp, s, s)
            Tiles.CACTUS -> {
                rect(c, 0xFF2E7D32, l + 12f, tp, 40f, s)
                rect(c, 0xFF1B5E20, l + 2f, tp + 16f, 10f, 12f)
                rect(c, 0xFF1B5E20, l + 52f, tp + 24f, 10f, 12f)
            }
            Tiles.MUSHROOM -> {
                rect(c, 0xFFD7CCC8, l + 26f, tp + 32f, 12f, 32f)
                rect(c, 0xFFD32F2F, l + 14f, tp + 12f, 36f, 22f)
                rect(c, 0xFFFFFFFF, l + 20f, tp + 16f, 6f, 6f)
                rect(c, 0xFFFFFFFF, l + 38f, tp + 20f, 6f, 6f)
            }
            Tiles.CHEST -> {
                rect(c, 0xFF795548, l + 8f, tp + 16f, 48f, 48f)
                rect(c, 0xFFFFD700, l + 28f, tp + 32f, 8f, 12f)
            }
            Tiles.LAVA -> {
                rect(c, 0xFFFF3D00, l, tp, s, s)
                rect(c, 0xFFFF9100, l + 6f + a, tp + 8f + b, 18f, 14f)
                rect(c, 0xFFFFEA00, l + 28f - a, tp + 28f - b, 14f, 10f)
            }
            Tiles.CRYSTAL -> {
                rect(c, 0xFF4A148C, l, tp, s, s)
                rect(c, 0xFFBA68C8, l + 10f + a, tp + 10f + b, 16f, 20f)
                rect(c, 0xFF00E5FF, l + 30f - a, tp + 24f - b, 12f, 16f)
                rect(c, 0xFFEDE7F6, l + 14f + a, tp + 14f + b, 6f, 8f)
            }
            Tiles.MOSS_STONE -> {
                rect(c, 0xFF616161, l, tp, s, s)
                rect(c, 0xFF2E7D32, l + 6f + a, tp + 6f + b, 18f, 12f)
                rect(c, 0xFF388E3C, l + 32f - a, tp + 32f - b, 16f, 12f)
            }
        }
    }

    private fun drawOre(c: Canvas, l: Float, tp: Float, a: Float, b: Float, main: Long, shine: Long) {
        rect(c, 0xFF808080, l, tp, TILE_SIZE, TILE_SIZE)
        rect(c, 0xFF666666, l + 8f + a, tp + 10f + b, 18f, 12f)
        rect(c, main, l + 12f, tp + 12f, 14f, 14f)
        rect(c, main, l + 36f, tp + 22f, 14f, 14f)
        rect(c, main, l + 20f, tp + 40f, 14f, 14f)
        rect(c, shine, l + 15f, tp + 15f, 5f, 5f)
        rect(c, shine, l + 39f, tp + 25f, 5f, 5f)
    }

    private fun drawMonster(c: Canvas, m: Monster) {
        val x = m.x; val y = m.y; val w = m.width
        val f = m.facing
        when (m.type) {
            MonsterType.SLIME -> {
                paint.color = 0xFF3FA84A.toInt()
                tmpRect.set(x, y + 6f, x + w, y + m.height)
                c.drawRoundRect(tmpRect, 18f, 18f, paint)
                rect(c, 0xFF8BE594, x + 10f, y + 12f, 14f, 8f)
                rect(c, 0xFF000000, x + 14f, y + 22f, 8f, 10f)
                rect(c, 0xFF000000, x + 36f, y + 22f, 8f, 10f)
            }
            MonsterType.ZOMBIE -> {
                rect(c, 0xFF2B3A67, x + 4f, y + 58f, 12f, 26f)
                rect(c, 0xFF2B3A67, x + w - 16f, y + 58f, 12f, 26f)
                rect(c, 0xFF3E7F8F, x, y + 26f, w, 34f)
                rect(c, 0xFF6FAF5A, x + 4f, y, w - 8f, 26f)
                rect(c, 0xFF6FAF5A, if (f > 0) x + w else x - 22f, y + 30f, 22f, 8f)
                rect(c, 0xFFD50000, if (f > 0) x + w - 14f else x + 6f, y + 10f, 8f, 8f)
            }
            MonsterType.SKELETON -> {
                rect(c, 0xFFE8E8E0, x + 8f, y + 58f, 6f, 26f)
                rect(c, 0xFFE8E8E0, x + w - 14f, y + 58f, 6f, 26f)
                rect(c, 0xFFE8E8E0, x + 10f, y + 24f, w - 20f, 36f)
                for (i in 0..2) rect(c, 0xFF555555, x + 10f, y + 30f + i * 8f, w - 20f, 3f)
                rect(c, 0xFFE8E8E0, x + 2f, y, w - 4f, 24f)
                rect(c, 0xFF222222, x + 8f, y + 8f, 6f, 6f)
                rect(c, 0xFF222222, x + w - 14f, y + 8f, 6f, 6f)
                rect(c, 0xFFE8E8E0, if (f > 0) x + w - 6f else x - 14f, y + 28f, 20f, 5f)
            }
            MonsterType.BAT -> {
                val flap = sin(m.flap * 18f) * 8f
                rect(c, 0xFF3A2A4D, x - 8f, y + 4f + flap, 20f, 8f)
                rect(c, 0xFF3A2A4D, x + w - 12f, y + 4f - flap, 20f, 8f)
                paint.color = 0xFF4A3563.toInt()
                tmpRect.set(x + 8f, y + 4f, x + w - 8f, y + m.height)
                c.drawRoundRect(tmpRect, 8f, 8f, paint)
                rect(c, 0xFFFF1744, x + 12f, y + 10f, 4f, 4f)
                rect(c, 0xFFFF1744, x + w - 16f, y + 10f, 4f, 4f)
            }
            MonsterType.SPIDER -> {
                paint.color = 0xFF212121.toInt()
                tmpRect.set(x + 6f, y + 6f, x + w - 6f, y + m.height)
                c.drawRoundRect(tmpRect, 10f, 10f, paint)
                rect(c, 0xFFFF1744, if (f > 0) x + w - 14f else x + 6f, y + 10f, 4f, 4f)
                rect(c, 0xFFFF1744, if (f > 0) x + w - 8f else x + 12f, y + 10f, 4f, 4f)
                rect(c, 0xFF424242, x - 4f, y + 14f, 6f, 16f)
                rect(c, 0xFF424242, x + w - 2f, y + 14f, 6f, 16f)
            }
            MonsterType.GOLEM -> {
                rect(c, 0xFF546E7A, x + 4f, y + 20f, w - 8f, 42f)
                rect(c, 0xFF78909C, x + 8f, y, w - 16f, 22f)
                rect(c, 0xFFFFD54F, if (f > 0) x + w - 18f else x + 10f, y + 8f, 6f, 6f)
                rect(c, 0xFF455A64, if (f > 0) x + w - 6f else x - 12f, y + 24f, 16f, 36f)
                rect(c, 0xFF37474F, x + 6f, y + 62f, 16f, 30f)
                rect(c, 0xFF37474F, x + w - 22f, y + 62f, 16f, 30f)
            }
        }
        if (m.hp < m.type.hp) {
            val barW = w
            val barH = 5f
            val barX = x
            val barY = y - 10f
            rect(c, 0xFF000000, barX - 1f, barY - 1f, barW + 2f, barH + 2f)
            rect(c, 0xFF550000, barX, barY, barW, barH)
            val pct = (m.hp.toFloat() / m.type.hp).coerceIn(0f, 1f)
            val hpColor = if (pct > 0.4f) 0xFF4CAF50 else 0xFFE53935
            rect(c, hpColor, barX, barY, barW * pct, barH)
        }
    }

    private fun drawPlayer(c: Canvas) {
        val px = player.x; val py = player.y; val w = player.width
        val d = player.facing

        // Perisai terbaik di punggung
        val sh = bestShield()
        if (sh != 0) {
            val sx = if (d > 0) px - 14f else px + w - 2f
            rect(c, 0xFF3E2A12, sx - 2f, py + 34f, 20f, 40f)
            rectI(c, Items.info(sh).color, sx, py + 36f, 16f, 36f)
        }

        CharacterRenderer.draw(c, paint, profile, px, py, d)

        // Item di tangan: pedang atau busur
        val sel = inventory.selectedId()
        val info = Items.info(sel)
        if (info.damage > 0) {
            if (d > 0) {
                rect(c, 0xFF5B3A1E, px + w - 4f, py + 44f, 10f, 8f)
                rectI(c, info.color, px + w + 6f, py + 42f, 34f, 6f)
            } else {
                rect(c, 0xFF5B3A1E, px - 6f, py + 44f, 10f, 8f)
                rectI(c, info.color, px - 40f, py + 42f, 34f, 6f)
            }
        } else if (sel == Items.BOW) {
            val bx = if (d > 0) px + w + 8f else px - 14f
            rect(c, 0xFF6B4423, bx, py + 26f, 6f, 40f)
            rect(c, 0xFFEEEEEE, bx + (if (d > 0) -3f else 8f), py + 28f, 2f, 36f)
        }

        namePaint.textSize = 14f * density
        c.drawText(profile.nick, player.centerX, py - 14f, namePaint)
    }

    // ===================== Input sentuh (UI thread) =====================
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (state == GameState.MAP) {                       // geser peta
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = e.x; lastTouchY = e.y }
                MotionEvent.ACTION_MOVE -> if (mapZoom > 1) {
                    panX -= (e.x - lastTouchX) / mapScale
                    panY -= (e.y - lastTouchY) / mapScale
                    lastTouchX = e.x; lastTouchY = e.y
                }
            }
            return true
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            val x = e.getX(i); val y = e.getY(i)
            if (state == GameState.PLAYING && mmRect.contains(x, y)) { toggleMap(); return true }  // tap minimap = buka peta
            taps.add(SpritePool.TapPoint.obtain(x, y))
        }
        return true
    }

    // ===================== Lifecycle Surface =====================
    override fun surfaceCreated(holder: SurfaceHolder) {
        TileRenderer.init(context)
        thread = GameThread(holder, this).also { it.running = true; it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val t = thread ?: return
        t.running = false
        var retry = true
        while (retry) {
            try { t.join(500); retry = false } catch (_: InterruptedException) {}
        }
        thread = null
        TileRenderer.release()
    }

    /**
     * Membersihkan resource memori (Arrow pool, Tap pool, partikel, bitmap minimap)
     * saat activity ditutup untuk meminimalkan beban Garbage Collector.
     */
    fun release() {
        clearArrows()
        clearTaps()
        Arrow.clearPool()
        SpritePool.TapPoint.clearPool()
        SpritePool.clearParticles()
        if (!mapBmp.isRecycled) {
            mapBmp.recycle()
        }
    }
}