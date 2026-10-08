// app/src/main/java/com/oreodyssey/game/MainActivity.kt
package com.oreodyssey.game

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.oreodyssey.game.core.GameState
import com.oreodyssey.game.core.GameView
import com.oreodyssey.game.entities.Inventory
import com.oreodyssey.game.entities.Player

class MainActivity : AppCompatActivity(), GameView.Listener {

    private lateinit var gameView: GameView
    private lateinit var tvHearts: TextView
    private lateinit var tvGear: TextView
    private lateinit var overlay: LinearLayout
    private lateinit var craftPanel: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnAction: Button
    private lateinit var slots: List<Button>
    private lateinit var craftButtons: List<Button>
    private val slotNames = listOf("Dirt", "Stone", "Copper", "Kayu")
    private val levelNames = listOf("-", "Kayu", "Batu")

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enterFullscreen()

        gameView = findViewById(R.id.gameView)
        tvHearts = findViewById(R.id.tvHearts)
        tvGear = findViewById(R.id.tvGear)
        overlay = findViewById(R.id.overlay)
        craftPanel = findViewById(R.id.craftPanel)
        tvTitle = findViewById(R.id.tvTitle)
        btnAction = findViewById(R.id.btnAction)
        slots = listOf(R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3).map { findViewById<Button>(it) }
        craftButtons = listOf(R.id.btnCraft0, R.id.btnCraft1, R.id.btnCraft2, R.id.btnCraft3)
            .map { findViewById<Button>(it) }

        gameView.listener = this

        // Tombol gerak: aktif selama ditahan
        holdButton(findViewById(R.id.btnLeft)) { gameView.setLeft(it) }
        holdButton(findViewById(R.id.btnRight)) { gameView.setRight(it) }
        holdButton(findViewById(R.id.btnJump)) { gameView.setJump(it) }

        findViewById<Button>(R.id.btnPause).setOnClickListener { gameView.pauseGame() }
        findViewById<Button>(R.id.btnCraft).setOnClickListener { gameView.openCraft() }
        findViewById<Button>(R.id.btnCraftClose).setOnClickListener { gameView.resumeGame() }
        slots.forEachIndexed { i, b -> b.setOnClickListener { gameView.selectSlot(i) } }

        // Teks resep + klik craft
        val info = findViewById<TextView>(R.id.tvCraftInfo)
        info.text = "Tap blok pohon untuk dapat kayu. Tap slime dengan pedang untuk menyerang."
        craftButtons.forEachIndexed { i, b ->
            val r = Inventory.recipes[i]
            b.text = "${r.name}\n${r.wood} Kayu" + if (r.stone > 0) " + ${r.stone} Batu" else ""
            b.setOnClickListener {
                val ok = gameView.craft(i)
                Toast.makeText(
                    this,
                    if (ok) "Berhasil membuat ${r.name}!" else "Bahan kurang atau sudah punya yang lebih bagus",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAction.setOnClickListener {
            when (gameView.state) {
                GameState.PAUSE -> gameView.resumeGame()
                else -> gameView.newGame()          // MENU & GAME_OVER
            }
        }

        onHudChanged(Player.MAX_HEALTH, IntArray(4), 0, 0, 0)
        onStateChanged(gameView.state)
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

    // ---- Callback dari GameView (selalu di UI thread) ----
    override fun onStateChanged(state: GameState) {
        when (state) {
            GameState.PLAYING -> { overlay.visibility = View.GONE; craftPanel.visibility = View.GONE }
            GameState.CRAFTING -> { overlay.visibility = View.GONE; craftPanel.visibility = View.VISIBLE }
            GameState.MENU -> showOverlay("ORE ODYSSEY", "MAIN")
            GameState.PAUSE -> showOverlay("PAUSE", "LANJUT")
            GameState.GAME_OVER -> showOverlay("KAMU MATI", "ULANGI")
        }
    }

    override fun onHudChanged(health: Int, counts: IntArray, sword: Int, shield: Int, shieldDur: Int) {
        val h = health.coerceIn(0, Player.MAX_HEALTH)
        tvHearts.text = "♥".repeat(h) + "♡".repeat(Player.MAX_HEALTH - h)
        tvGear.text = "Pedang: ${levelNames[sword]}  |  Perisai: ${levelNames[shield]}" +
                if (shield > 0) " ($shieldDur)" else ""
        slots.forEachIndexed { i, b ->
            b.text = "${slotNames[i]}\n${counts[i]}"
            b.alpha = if (i == gameView.inventory.selected) 1f else 0.5f
        }
    }

    private fun showOverlay(title: String, button: String) {
        craftPanel.visibility = View.GONE
        tvTitle.text = title
        btnAction.text = button
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
        gameView.pauseGame()
    }
}