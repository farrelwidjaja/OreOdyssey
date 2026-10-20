// app/src/main/java/com/oreodyssey/game/audio/SoundManager.kt
package com.oreodyssey.game.audio

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.oreodyssey.game.R

/**
 * Manager audio game untuk efek suara (SFX via SoundPool)
 * dan musik latar dinamis (BGM via MediaPlayer dengan crossfade mulus).
 */
object SoundManager {

    // Konstanta SFX
    const val SFX_MINE        = 0
    const val SFX_PLACE       = 1
    const val SFX_JUMP        = 2
    const val SFX_HURT        = 3
    const val SFX_SWING       = 4
    const val SFX_ARROW       = 5
    const val SFX_MONSTER_DIE = 6
    const val SFX_PICKUP      = 7

    // Konstanta Track BGM berdasarkan zona kedalaman
    const val BGM_NONE    = 0
    const val BGM_SURFACE = 1
    const val BGM_CAVE    = 2
    const val BGM_DEEP    = 3

    private var pool: SoundPool? = null
    private val soundIds = IntArray(8) { -1 }
    private var initialized = false

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Media players untuk crossfade BGM
    private var activePlayer: MediaPlayer? = null
    private var fadingPlayer: MediaPlayer? = null
    private var currentFadeAnim: ValueAnimator? = null
    var activeTrack: Int = BGM_NONE
        private set
    private var isBgmPaused = false

    // Volume level 0.0f .. 1.0f
    var masterVolume: Float = 1.0f
        private set
    var bgmVolume: Float = 0.8f
        private set
    var sfxVolume: Float = 1.0f
        private set

    /**
     * Logika murni penentuan track BGM berdasarkan kedalaman tambang.
     * Dapat diuji lewat unit test tanpa dependensi Android framework.
     */
    fun trackForDepth(depth: Int): Int = when {
        depth <= 14 -> BGM_SURFACE
        depth <= 35 -> BGM_CAVE
        else -> BGM_DEEP
    }

    /**
     * Hitung volume efektif: base * master * channel
     */
    fun computeVolume(baseVol: Float, master: Float, channel: Float): Float =
        (baseVol * master * channel).coerceIn(0f, 1f)

    /**
     * Inisialisasi SoundPool, volume preferences, dan context.
     */
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("oreodyssey_audio", Context.MODE_PRIVATE)

        masterVolume = prefs?.getFloat("master", 1.0f) ?: 1.0f
        bgmVolume = prefs?.getFloat("bgm", 0.8f) ?: 0.8f
        sfxVolume = prefs?.getFloat("sfx", 1.0f) ?: 1.0f

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        val res = context.resources
        val pkg = context.packageName

        fun load(name: String): Int {
            val id = res.getIdentifier(name, "raw", pkg)
            return if (id != 0) pool!!.load(context, id, 1) else -1
        }

        soundIds[SFX_MINE]        = load("sfx_mine")
        soundIds[SFX_PLACE]       = load("sfx_place")
        soundIds[SFX_JUMP]        = load("sfx_jump")
        soundIds[SFX_HURT]        = load("sfx_hurt")
        soundIds[SFX_SWING]       = load("sfx_swing")
        soundIds[SFX_ARROW]       = load("sfx_arrow")
        soundIds[SFX_MONSTER_DIE] = load("sfx_monster_die")
        soundIds[SFX_PICKUP]      = load("sfx_pickup")

        initialized = true
    }

    // =========================================================================
    // SFX Playback
    // =========================================================================

    fun play(sfxIndex: Int, volume: Float = 1f) {
        val p = pool ?: return
        val id = soundIds.getOrElse(sfxIndex) { -1 }
        val effectiveVol = computeVolume(volume, masterVolume, sfxVolume)
        if (id > 0 && effectiveVol > 0f) {
            p.play(id, effectiveVol, effectiveVol, 1, 0, 1f)
        }
    }

    // =========================================================================
    // Dynamic BGM & Crossfade
    // =========================================================================

    /**
     * Memperbarui musik latar sesuai kedalaman pemain saat ini.
     * Dipanggil dari update loop game view.
     */
    fun updateBgmForDepth(depth: Int) {
        val target = trackForDepth(depth)
        if (target != activeTrack) {
            playBgm(target)
        }
    }

    /**
     * Memainkan track BGM baru dengan efek crossfade transisi halus.
     */
    fun playBgm(track: Int, fadeDurationMs: Long = 1200L) {
        if (track == activeTrack) return
        mainHandler.post {
            executePlayBgm(track, fadeDurationMs)
        }
    }

    private fun executePlayBgm(track: Int, fadeDurationMs: Long) {
        if (track == activeTrack) return
        val ctx = appContext ?: return

        currentFadeAnim?.cancel()
        currentFadeAnim = null

        val resId = when (track) {
            BGM_SURFACE -> R.raw.bgm_surface
            BGM_CAVE -> R.raw.bgm_cave
            BGM_DEEP -> R.raw.bgm_deep
            else -> 0
        }

        if (resId == 0) {
            val prev = activePlayer
            activePlayer = null
            activeTrack = BGM_NONE
            if (prev != null) {
                val anim = ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = fadeDurationMs
                    addUpdateListener { va ->
                        val t = va.animatedValue as Float
                        val v = t * (masterVolume * bgmVolume)
                        try { prev.setVolume(v, v) } catch (_: Exception) {}
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            try { prev.stop(); prev.release() } catch (_: Exception) {}
                        }
                    })
                }
                currentFadeAnim = anim
                anim.start()
            }
            return
        }

        try {
            val newPlayer = MediaPlayer.create(ctx, resId) ?: return
            newPlayer.isLooping = true
            newPlayer.setVolume(0f, 0f)
            newPlayer.start()

            if (isBgmPaused) {
                newPlayer.pause()
            }

            fadingPlayer?.let {
                try { it.stop(); it.release() } catch (_: Exception) {}
            }
            fadingPlayer = activePlayer
            activePlayer = newPlayer
            activeTrack = track

            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = fadeDurationMs
                addUpdateListener { va ->
                    val t = va.animatedValue as Float
                    val currentTarget = masterVolume * bgmVolume
                    try {
                        activePlayer?.setVolume(t * currentTarget, t * currentTarget)
                        fadingPlayer?.setVolume((1f - t) * currentTarget, (1f - t) * currentTarget)
                    } catch (_: Exception) {}
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            fadingPlayer?.stop()
                            fadingPlayer?.release()
                        } catch (_: Exception) {}
                        fadingPlayer = null
                        val finalVol = masterVolume * bgmVolume
                        try { activePlayer?.setVolume(finalVol, finalVol) } catch (_: Exception) {}
                    }
                })
            }
            currentFadeAnim = anim
            anim.start()
        } catch (_: Exception) {}
    }

    /**
     * Pause BGM saat activity masuk ke background (onPause).
     */
    fun pauseBgm() {
        mainHandler.post {
            isBgmPaused = true
            try {
                if (activePlayer?.isPlaying == true) activePlayer?.pause()
                if (fadingPlayer?.isPlaying == true) fadingPlayer?.pause()
            } catch (_: Exception) {}
        }
    }

    /**
     * Resume BGM saat activity aktif kembali (onResume).
     */
    fun resumeBgm() {
        mainHandler.post {
            isBgmPaused = false
            try {
                activePlayer?.start()
                fadingPlayer?.start()
                applyBgmVolume()
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // Volume Settings Management
    // =========================================================================

    fun setMasterVolume(v: Float) {
        masterVolume = v.coerceIn(0f, 1f)
        applyBgmVolume()
        savePrefs()
    }

    fun setBgmVolume(v: Float) {
        bgmVolume = v.coerceIn(0f, 1f)
        applyBgmVolume()
        savePrefs()
    }

    fun setSfxVolume(v: Float) {
        sfxVolume = v.coerceIn(0f, 1f)
        savePrefs()
    }

    private fun applyBgmVolume() {
        val target = masterVolume * bgmVolume
        try {
            activePlayer?.setVolume(target, target)
        } catch (_: Exception) {}
    }

    private fun savePrefs() {
        prefs?.edit()
            ?.putFloat("master", masterVolume)
            ?.putFloat("bgm", bgmVolume)
            ?.putFloat("sfx", sfxVolume)
            ?.apply()
    }

    /**
     * Bebaskan resource SoundPool dan MediaPlayers.
     */
    fun release() {
        pool?.release()
        pool = null
        soundIds.fill(-1)

        currentFadeAnim?.cancel()
        currentFadeAnim = null

        try {
            activePlayer?.stop()
            activePlayer?.release()
        } catch (_: Exception) {}
        activePlayer = null

        try {
            fadingPlayer?.stop()
            fadingPlayer?.release()
        } catch (_: Exception) {}
        fadingPlayer = null

        activeTrack = BGM_NONE
        initialized = false
        appContext = null
        prefs = null
    }
}
