// app/src/main/java/com/oreodyssey/game/data/Store.kt
package com.oreodyssey.game.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Sesi yang sedang aktif. accountId == null berarti mode Tamu (tidak bisa menyimpan). */
object Session {
    var accountId: String? = null
    var profile: Profile? = null
    var suggestedNick: String? = null
    val isGuest get() = accountId == null

    fun logout() { accountId = null; profile = null; suggestedNick = null }
}

/** Penyimpanan akun & profil di HP menggunakan EncryptedSharedPreferences (AES-256-GCM). */
object Store {

    /**
     * Buat atau buka EncryptedSharedPreferences.
     * Kalau device tidak support hardware keystore (emulator lama), fallback ke SharedPreferences biasa.
     */
    private fun p(c: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(c)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                c,
                "oreodyssey_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback untuk emulator / device yang tidak support hardware keystore
            c.getSharedPreferences("oreodyssey", Context.MODE_PRIVATE)
        }
    }

    /**
     * Hash password dengan salt dinamis: SHA-256(username + ":" + salt + ":" + password).
     * Salt di-generate sekali saat register dan disimpan bersama hash.
     */
    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun generateSalt(): String =
        (1..16).map { ('a'..'z').random() }.joinToString("")

    fun normalize(u: String) = u.trim().lowercase()

    fun register(c: Context, user: String, pw: String): String? {
        val u = normalize(user)
        if (u.length < 3) return "Username minimal 3 karakter"
        if (!u.all { it.isLetterOrDigit() || it == '_' }) return "Username hanya huruf, angka, dan _"
        if (pw.length < 4) return "Password minimal 4 karakter"
        val prefs = p(c)
        if (prefs.contains("pw_$u")) return "Username sudah dipakai"
        val salt = generateSalt()
        val hash = sha("$u:$salt:$pw")
        prefs.edit()
            .putString("pw_$u", hash)
            .putString("salt_$u", salt)
            .apply()
        return null
    }

    fun login(c: Context, user: String, pw: String): String? {
        val u = normalize(user)
        val prefs = p(c)
        val savedHash = prefs.getString("pw_$u", null) ?: return "Akun belum terdaftar. Buat akun dulu."
        val salt = prefs.getString("salt_$u", "") ?: ""
        // Support hash lama (tanpa salt) agar akun yang sudah dibuat tetap bisa login
        val hash = if (salt.isEmpty()) sha("$u:$pw") else sha("$u:$salt:$pw")
        if (savedHash != hash) return "Password salah"
        return null
    }

    fun saveProfile(c: Context, id: String, pr: Profile) {
        val o = JSONObject()
            .put("nick", pr.nick).put("female", pr.female)
            .put("hs", pr.hairStyle).put("hc", pr.hairColor)
            .put("sh", pr.shirt).put("pa", pr.pants).put("sk", pr.skin)
        p(c).edit().putString("pf_$id", o.toString()).apply()
    }

    fun loadProfile(c: Context, id: String): Profile? {
        val s = p(c).getString("pf_$id", null) ?: return null
        return try {
            val o = JSONObject(s)
            Profile(
                o.getString("nick"), o.getBoolean("female"), o.getInt("hs"),
                o.getInt("hc"), o.getInt("sh"), o.getInt("pa"), o.getInt("sk")
            )
        } catch (e: Exception) { null }
    }

    fun saveFile(c: Context, id: String): File =
        File(c.filesDir, "save_" + id.replace(Regex("[^A-Za-z0-9]"), "_") + ".dat")
}
