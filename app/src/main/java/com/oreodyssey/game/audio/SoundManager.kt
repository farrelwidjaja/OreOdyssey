// app/src/main/java/com/oreodyssey/game/audio/SoundManager.kt
package com.oreodyssey.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Manager suara efek (SFX) berbasis SoundPool Android.
 * Latensi rendah, cocok untuk game loop 60 FPS.
 *
 * CARA PAKAI:
 * 1. Panggil SoundManager.init(context) di onCreate Activity.
 * 2. Panggil SoundManager.play(SoundManager.SFX_MINE) saat aksi terjadi.
 * 3. Panggil SoundManager.release() di onDestroy Activity.
 *
 * FILE AUDIO:
 * Taruh file .ogg/.wav di folder: app/src/main/res/raw/
 * Nama file harus sesuai dengan yang di-load di init():
 *   - sfx_mine.ogg
 *   - sfx_place.ogg
 *   - sfx_jump.ogg
 *   - sfx_hurt.ogg
 *   - sfx_swing.ogg
 *   - sfx_arrow.ogg
 *   - sfx_monster_die.ogg
 *   - sfx_pickup.ogg
 */
object SoundManager {

    const val SFX_MINE        = 0
    const val SFX_PLACE       = 1
    const val SFX_JUMP        = 2
    const val SFX_HURT        = 3
    const val SFX_SWING       = 4
    const val SFX_ARROW       = 5
    const val SFX_MONSTER_DIE = 6
    const val SFX_PICKUP      = 7

    private var pool: SoundPool? = null
    private val soundIds = IntArray(8) { -1 }
    private var initialized = false

    /**
     * Inisialisasi SoundPool dan load semua file SFX.
     * Aman dipanggil berulang kali — tidak akan double-init.
     */
    fun init(context: Context) {
        if (initialized) return
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

        // Load setiap SFX — jika file tidak ada, id tetap -1 dan play() silent
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

    /**
     * Putar SFX berdasarkan konstanta di atas.
     * Tidak akan crash jika file tidak ada atau SoundPool belum diinit.
     * @param sfxIndex Konstanta SFX_* di atas
     * @param volume Volume 0.0f - 1.0f (default 1.0f)
     */
    fun play(sfxIndex: Int, volume: Float = 1f) {
        val p = pool ?: return
        val id = soundIds.getOrElse(sfxIndex) { -1 }
        if (id > 0) p.play(id, volume, volume, 1, 0, 1f)
    }

    /**
     * Bebaskan resource SoundPool. Panggil dari onDestroy Activity.
     */
    fun release() {
        pool?.release()
        pool = null
        soundIds.fill(-1)
        initialized = false
    }
}
