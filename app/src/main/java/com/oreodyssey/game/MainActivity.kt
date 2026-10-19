// app/src/main/java/com/oreodyssey/game/MainActivity.kt
package com.oreodyssey.game

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.facebook.shimmer.ShimmerFrameLayout
import com.oreodyssey.game.ai.AiCompanionManager
import com.oreodyssey.game.ai.AiContextPayload
import com.oreodyssey.game.audio.SoundManager
import com.oreodyssey.game.core.GameState
import com.oreodyssey.game.ui.TileRenderer
import com.oreodyssey.game.core.GameView
import com.oreodyssey.game.data.Session
import com.oreodyssey.game.data.Store
import com.oreodyssey.game.entities.Inventory
import com.oreodyssey.game.entities.Items
import com.oreodyssey.game.entities.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), GameView.Listener {

    private lateinit var gameView: GameView
    private lateinit var hudLayer: View
    private lateinit var bagPanel: View
    private lateinit var mapControls: View
    private lateinit var overlay: View
    private lateinit var sageDialog: LinearLayout
    private lateinit var sageShimmer: ShimmerFrameLayout
    private lateinit var tvSageResponse: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSub: TextView
    private lateinit var tvHp: TextView
    private lateinit var tvTime: TextView
    private lateinit var hpBar: ProgressBar
    private lateinit var btnAction: Button
    private lateinit var btnNew: Button
    private lateinit var btnChar: Button
    private lateinit var btnExit: Button
    private lateinit var hotbar: LinearLayout
    private lateinit var bagGrid: GridLayout
    private lateinit var recipeList: LinearLayout
    private val hotbarBtns = ArrayList<Button>()
    private val bagBtns = ArrayList<Button>()
    private val recipeBtns = ArrayList<Button>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = Session.profile
        if (profile == null) {          // sesi hilang (mis. app dimatikan sistem) -> kembali ke login
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_main)
        enterFullscreen()
        SoundManager.init(this)
        TileRenderer.init(this)

        gameView = findViewById(R.id.gameView)
        hudLayer = findViewById(R.id.hudLayer)
        bagPanel = findViewById(R.id.bagPanel)
        mapControls = findViewById(R.id.mapControls)
        overlay = findViewById(R.id.overlay)
        sageDialog = findViewById(R.id.sageDialog)
        sageShimmer = findViewById(R.id.sageShimmer)
        tvSageResponse = findViewById(R.id.tvSageResponse)
        tvTitle = findViewById(R.id.tvTitle)
        tvSub = findViewById(R.id.tvSub)
        tvHp = findViewById(R.id.tvHp)
        tvTime = findViewById(R.id.tvTime)
        hpBar = findViewById(R.id.hpBar)
        btnAction = findViewById(R.id.btnAction)
        btnNew = findViewById(R.id.btnNew)
        btnChar = findViewById(R.id.btnChar)
        btnExit = findViewById(R.id.btnExit)
        hotbar = findViewById(R.id.hotbar)
        bagGrid = findViewById(R.id.bagGrid)
        recipeList = findViewById(R.id.recipeList)

        gameView.profile = profile
        gameView.saveFile = if (Session.isGuest) null else Store.saveFile(this, Session.accountId!!)

        buildHotbar(); buildBag(); buildRecipes()

        // Tombol Crystal Sage
        findViewById<Button>(R.id.btnSage).setOnClickListener { openSageDialog() }
        findViewById<Button>(R.id.btnSageClose).setOnClickListener {
            sageShimmer.stopShimmer()
            sageDialog.visibility = View.GONE
        }

        // Tombol gerak layar sentuh (aktif selama ditahan)
        holdButton(findViewById(R.id.btnLeft)) { gameView.setLeft(it) }
        holdButton(findViewById(R.id.btnRight)) { gameView.setRight(it) }
        holdButton(findViewById(R.id.btnJump)) { gameView.setJump(it) }

        findViewById<Button>(R.id.btnPause).setOnClickListener { gameView.pauseGame() }
        findViewById<Button>(R.id.btnBag).setOnClickListener { gameView.toggleBag() }
        findViewById<Button>(R.id.btnMap).setOnClickListener { gameView.toggleMap() }
        findViewById<Button>(R.id.btnBagClose).setOnClickListener { gameView.toggleBag() }
        findViewById<Button>(R.id.btnMapIn).setOnClickListener { gameView.mapZoomIn() }
        findViewById<Button>(R.id.btnMapOut).setOnClickListener { gameView.mapZoomOut() }
        findViewById<Button>(R.id.btnMapClose).setOnClickListener { gameView.toggleMap() }

        btnAction.setOnClickListener {
            when (gameView.state) {
                GameState.PAUSE -> gameView.resumeGame()
                GameState.GAME_OVER -> gameView.respawn()
                else -> gameView.startGame(true)         // MENU: lanjut save jika ada
            }
        }
        btnNew.setOnClickListener { gameView.saveFile?.delete(); gameView.startGame(false) }
        btnChar.setOnClickListener {
            startActivity(Intent(this, CharacterActivity::class.java).putExtra("edit", true))
        }
        btnExit.setOnClickListener {
            gameView.saveNow()
            Session.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (gameView.state) {
                    GameState.PLAYING -> gameView.pauseGame()
                    GameState.BAG -> gameView.toggleBag()
                    GameState.MAP -> gameView.toggleMap()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })

        refreshInventoryUi()
        onHudChanged(Player.MAX_HEALTH, "")
        gameView.listener = this
        onStateChanged(gameView.state)
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) Session.profile?.let { gameView.profile = it }   // setelah ubah karakter
    }

    // ===================== Keyboard (laptop / PC) =====================
    // Lewat dispatchKeyEvent supaya tombol SPACE tidak "menekan" tombol yang sedang fokus.
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (!::gameView.isInitialized) return super.dispatchKeyEvent(e)
        val down = e.action == KeyEvent.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> { gameView.setLeft(down); return true }
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> { gameView.setRight(down); return true }
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_UP -> { gameView.setJump(down); return true }
            KeyEvent.KEYCODE_B -> { if (first) gameView.toggleBag(); return true }
            KeyEvent.KEYCODE_M -> { if (first) gameView.toggleMap(); return true }
            KeyEvent.KEYCODE_ESCAPE -> {
                if (first) when (gameView.state) {
                    GameState.PLAYING -> gameView.pauseGame()
                    GameState.PAUSE -> gameView.resumeGame()
                    GameState.BAG -> gameView.toggleBag()
                    GameState.MAP -> gameView.toggleMap()
                    else -> {}
                }
                return true
            }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_8 -> {
                if (first) gameView.selectSlot(e.keyCode - KeyEvent.KEYCODE_1)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun holdButton(b: Button, onHold: (Boolean) -> Unit) {
        b.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> onHold(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onHold(false)
            }
            true
        }
    }

    // ===================== Crystal Sage AI Dialog =====================
    private fun openSageDialog() {
        // Hanya bisa dibuka saat game berjalan atau tas terbuka
        val gs = gameView.state
        if (gs != GameState.PLAYING && gs != GameState.BAG) return

        sageDialog.visibility = View.VISIBLE
        tvSageResponse.visibility = View.GONE
        sageShimmer.visibility = View.VISIBLE
        sageShimmer.startShimmer()

        // Ambil daftar item yang ada di inventaris
        val inv = gameView.inventory
        val heldItems = (0 until Inventory.SIZE)
            .filter { inv.counts[it] > 0 }
            .map { Items.info(inv.ids[it]).name }
            .distinct()
            .take(6)

        val ctx = AiContextPayload(
            playerHp = gameView.playerHealth,
            playerMaxHp = Player.MAX_HEALTH,
            depthBelowSurface = gameView.playerDepth,
            timeOfDay = gameView.currentTimeLabel,
            isDaytime = gameView.isDaytime,
            inventoryItems = heldItems,
            nearbyMonsters = gameView.nearbyMonsterNames,
            playerName = Session.profile?.nick ?: "Petualang",
            currentBiome = gameView.currentBiome,
            equippedItem = gameView.currentEquippedItemName,
            surroundingEvent = gameView.currentSurroundingEvent
        )

        CoroutineScope(Dispatchers.Main).launch {
            val response = AiCompanionManager.ask(ctx)
            sageShimmer.stopShimmer()
            sageShimmer.visibility = View.GONE
            tvSageResponse.visibility = View.VISIBLE
            tvSageResponse.alpha = 0f
            tvSageResponse.text = response
            tvSageResponse.animate()
                .alpha(1f)
                .setDuration(350)
                .start()
            SoundManager.play(SoundManager.SFX_PICKUP)
        }
    }

    // ===================== Inventory UI =====================
    private fun makeCell(): Button = Button(this).apply {
        isAllCaps = false; textSize = 9f
        setPadding(0, 0, 0, 0)
        minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0
        includeFontPadding = false
        stateListAnimator = null
    }

    private fun buildHotbar() {
        for (i in 0 until Inventory.HOTBAR) {
            val b = makeCell()
            b.layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(2) }
            b.setOnClickListener {
                if (gameView.state == GameState.BAG) {            // tas terbuka: kembalikan item ke tas
                    gameView.inventory.moveToBag(i); refreshInventoryUi()
                } else gameView.selectSlot(i)
            }
            b.setOnLongClickListener {
                val inv = gameView.inventory
                if (inv.counts[i] > 0) toast(Items.info(inv.ids[i]).name); true
            }
            hotbar.addView(b); hotbarBtns.add(b)
        }
    }

    private fun buildBag() {
        for (i in 0 until Inventory.BAG) {
            val slot = Inventory.HOTBAR + i
            val b = makeCell()
            b.layoutParams = GridLayout.LayoutParams().apply {
                width = dp(46); height = dp(46); setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            b.setOnClickListener { gameView.inventory.moveToHotbar(slot); refreshInventoryUi() }
            b.setOnLongClickListener {
                val inv = gameView.inventory
                if (inv.counts[slot] > 0) toast(Items.info(inv.ids[slot]).name); true
            }
            bagGrid.addView(b); bagBtns.add(b)
        }
    }

    private fun buildRecipes() {
        Items.recipes.forEachIndexed { i, r ->
            val b = Button(this).apply {
                isAllCaps = false; textSize = 11f; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                val need = r.needs.joinToString(" + ") { "${it.second} ${Items.info(it.first).name}" }
                text = "${Items.info(r.result).name} x${r.count}\n$need"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    toast(if (gameView.craftRecipe(i)) "Berhasil: ${Items.info(r.result).name}" else "Bahan kurang / tas penuh")
                }
            }
            recipeList.addView(b); recipeBtns.add(b)
        }
    }

    private fun styleCell(b: Button, id: Int, n: Int, selected: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(6).toFloat()
        if (n > 0) {
            val info = Items.info(id)
            bg.setColor(info.color)
            b.text = "${info.short}\n$n"
            val lum = (Color.red(info.color) * 299 + Color.green(info.color) * 587 + Color.blue(info.color) * 114) / 1000
            b.setTextColor(if (lum > 140) Color.BLACK else Color.WHITE)
        } else {
            bg.setColor(0xCC222222.toInt())
            b.text = ""
        }
        bg.setStroke(dp(if (selected) 3 else 1), if (selected) Color.WHITE else 0xFF000000.toInt())
        b.background = bg
    }

    private fun refreshInventoryUi() {
        val inv = gameView.inventory
        for (i in 0 until Inventory.HOTBAR) styleCell(hotbarBtns[i], inv.ids[i], inv.counts[i], i == inv.selected)
        for (i in 0 until Inventory.BAG) {
            val s = Inventory.HOTBAR + i
            styleCell(bagBtns[i], inv.ids[s], inv.counts[s], false)
        }
        Items.recipes.forEachIndexed { i, r -> recipeBtns[i].alpha = if (inv.canCraft(r)) 1f else 0.4f }
    }

    // ===================== Callback dari GameView (UI thread) =====================
    override fun onInventoryChanged() { refreshInventoryUi() }

    override fun onHudChanged(health: Int, timeText: String) {
        hpBar.progress = health.coerceIn(0, Player.MAX_HEALTH)
        tvHp.text = "HP $health/${Player.MAX_HEALTH}"
        tvTime.text = timeText
    }

    override fun onStateChanged(state: GameState) {
        hudLayer.visibility = if (state == GameState.MAP) View.GONE else View.VISIBLE
        mapControls.visibility = if (state == GameState.MAP) View.VISIBLE else View.GONE
        bagPanel.visibility = if (state == GameState.BAG) View.VISIBLE else View.GONE
        // Tutup dialog Sage jika game keluar dari state PLAYING/BAG
        if (state != GameState.PLAYING && state != GameState.BAG) {
            sageDialog.visibility = View.GONE
        }
        when (state) {
            GameState.PLAYING, GameState.BAG, GameState.MAP -> overlay.visibility = View.GONE
            else -> showOverlay(state)
        }
        if (state == GameState.BAG) refreshInventoryUi()
    }

    private fun showOverlay(state: GameState) {
        tvTitle.text = when (state) { GameState.GAME_OVER -> "KAMU MATI"; GameState.PAUSE -> "PAUSE"; else -> "ORE ODYSSEY" }
        btnAction.text = when (state) { GameState.GAME_OVER -> "BANGKIT"; GameState.PAUSE -> "LANJUT"; else -> "MAIN" }
        tvSub.text = when {
            state == GameState.GAME_OVER -> "Kamu dikalahkan monster."
            Session.isGuest -> "Mode TAMU: progres TIDAK tersimpan"
            else -> "Halo, ${Session.profile?.nick}! Progres tersimpan otomatis."
        }
        val saveExists = gameView.saveFile?.exists() == true
        btnNew.visibility = if (state == GameState.MENU && saveExists) View.VISIBLE else View.GONE
        btnChar.visibility = if (state == GameState.MENU || state == GameState.PAUSE) View.VISIBLE else View.GONE
        btnExit.text = if (Session.isGuest) "KELUAR (tanpa simpan)" else "SIMPAN & KELUAR AKUN"
        overlay.visibility = View.VISIBLE
    }

    private fun enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onPause() {
        super.onPause()
        if (::gameView.isInitialized) {
            gameView.pauseGame()
            gameView.saveNow()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gameView.isInitialized) {
            gameView.release()
        }
        SoundManager.release()
        TileRenderer.release()
    }
}