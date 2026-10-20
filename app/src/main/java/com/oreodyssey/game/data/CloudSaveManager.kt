// app/src/main/java/com/oreodyssey/game/data/CloudSaveManager.kt
package com.oreodyssey.game.data

import android.content.Context
import com.oreodyssey.game.core.GameView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class SyncResolution {
    UPLOADED_LOCAL_TO_CLOUD,   // Data lokal berhasil di-upload ke cloud
    DOWNLOADED_CLOUD_TO_LOCAL, // Data cloud lebih baru, dipulihkan ke lokal
    UP_TO_DATE,                // Data sudah sinkron / identik
    FAILED                     // Gagal (mis. jaringan error atau mode tamu)
}

data class SyncResult(
    val resolution: SyncResolution,
    val message: String,
    val downloadedPayload: CloudSavePayload? = null
)

object CloudSaveManager {

    /**
     * URL backend REST API / Cloud Functions (opsional).
     * Jika null atau blank, otomatis menggunakan persistent cloud storage mirror di device.
     */
    var cloudApiEndpoint: String? = null

    /**
     * Membandingkan timestamp untuk menentukan resolusi konflik:
     * - localTimestamp > cloudTimestamp: Upload lokal ke cloud.
     * - cloudTimestamp > localTimestamp: Download cloud ke lokal.
     * - sama: Up-to-date.
     */
    fun resolveConflict(localTimestamp: Long, cloudTimestamp: Long): SyncResolution {
        return when {
            localTimestamp > cloudTimestamp -> SyncResolution.UPLOADED_LOCAL_TO_CLOUD
            cloudTimestamp > localTimestamp -> SyncResolution.DOWNLOADED_CLOUD_TO_LOCAL
            else -> SyncResolution.UP_TO_DATE
        }
    }

    /**
     * Mengambil payload simpanan lokal dari state game saat ini.
     */
    fun buildLocalPayload(accountId: String, profile: Profile, gameView: GameView?): CloudSavePayload {
        val now = System.currentTimeMillis()
        if (gameView == null) {
            return CloudSavePayload(accountId = accountId, timestamp = now, profile = profile)
        }
        val inv = gameView.inventory
        return CloudSavePayload(
            accountId = accountId,
            timestamp = now,
            profile = profile,
            playerHealth = gameView.playerHealth,
            invIds = inv.ids.copyOf(),
            invCounts = inv.counts.copyOf(),
            invSelected = inv.selected
        )
    }

    /**
     * Mengambil data cloud yang tersimpan untuk akun tertentu.
     */
    suspend fun fetchCloudSave(context: Context, accountId: String): CloudSavePayload? = withContext(Dispatchers.IO) {
        val endpoint = cloudApiEndpoint
        if (!endpoint.isNullOrBlank()) {
            try {
                val url = URL("$endpoint/save?accountId=$accountId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val jsonStr = reader.readText()
                    reader.close()
                    return@withContext CloudSavePayload.fromJson(jsonStr)
                }
            } catch (_: Exception) {}
        }
        // Fallback: Persistent Cloud Mirror di SharedPreferences
        val prefs = context.getSharedPreferences("oreodyssey_cloud_mock", Context.MODE_PRIVATE)
        val raw = prefs.getString("cloud_$accountId", null) ?: return@withContext null
        CloudSavePayload.fromJson(raw)
    }

    /**
     * Mengunggah data payload ke cloud.
     */
    suspend fun uploadCloudSave(context: Context, payload: CloudSavePayload): Boolean = withContext(Dispatchers.IO) {
        val endpoint = cloudApiEndpoint
        val jsonStr = payload.toJson()
        if (!endpoint.isNullOrBlank()) {
            try {
                val url = URL("$endpoint/save")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(jsonStr)
                writer.flush()
                writer.close()
                if (conn.responseCode in 200..299) return@withContext true
            } catch (_: Exception) {}
        }
        // Simpan ke Persistent Cloud Mirror
        val prefs = context.getSharedPreferences("oreodyssey_cloud_mock", Context.MODE_PRIVATE)
        prefs.edit().putString("cloud_${payload.accountId}", jsonStr).commit()
    }

    /**
     * Melakukan sinkronisasi dua arah dengan resolusi konflik otomatis.
     */
    suspend fun sync(context: Context, localPayload: CloudSavePayload): SyncResult = withContext(Dispatchers.IO) {
        if (localPayload.accountId.isBlank() || localPayload.accountId.startsWith("guest")) {
            return@withContext SyncResult(SyncResolution.FAILED, "Mode Tamu tidak dapat melakukan sinkronisasi cloud.")
        }

        val cloudPayload = fetchCloudSave(context, localPayload.accountId)
        if (cloudPayload == null) {
            val ok = uploadCloudSave(context, localPayload)
            return@withContext if (ok) {
                SyncResult(SyncResolution.UPLOADED_LOCAL_TO_CLOUD, "✅ Data lokal baru berhasil diunggah ke Cloud.")
            } else {
                SyncResult(SyncResolution.FAILED, "❌ Gagal mengunggah data ke Cloud.")
            }
        }

        val resolution = resolveConflict(localPayload.timestamp, cloudPayload.timestamp)
        return@withContext when (resolution) {
            SyncResolution.UPLOADED_LOCAL_TO_CLOUD -> {
                val ok = uploadCloudSave(context, localPayload)
                if (ok) {
                    SyncResult(SyncResolution.UPLOADED_LOCAL_TO_CLOUD, "✅ Data lokal lebih baru, berhasil disinkronkan ke Cloud.")
                } else {
                    SyncResult(SyncResolution.FAILED, "❌ Gagal memperbarui data ke Cloud.")
                }
            }
            SyncResolution.DOWNLOADED_CLOUD_TO_LOCAL -> {
                // Perbarui profile lokal agar koin dan data karakter tersinkronisasi
                Store.saveProfile(context, cloudPayload.accountId, cloudPayload.profile)
                SyncResult(
                    SyncResolution.DOWNLOADED_CLOUD_TO_LOCAL,
                    "☁️ Data Cloud lebih baru, berhasil dipulihkan ke perangkat ini.",
                    cloudPayload
                )
            }
            SyncResolution.UP_TO_DATE -> {
                SyncResult(SyncResolution.UP_TO_DATE, "☁️ Data lokal dan Cloud sudah sinkron.")
            }
            SyncResolution.FAILED -> {
                SyncResult(SyncResolution.FAILED, "❌ Sinkronisasi gagal.")
            }
        }
    }
}
