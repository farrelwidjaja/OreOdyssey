// app/src/main/java/com/oreodyssey/game/data/CloudSavePayload.kt
package com.oreodyssey.game.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Model data serialisasi untuk menyimpan dan menyinkronkan progres pemain ke Cloud.
 * Mencakup profil, koin, posisi pemain, isi tas (inventory), dan waktu dunia.
 */
data class CloudSavePayload(
    val accountId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val profile: Profile,
    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerHealth: Int = 100,
    val invIds: IntArray = IntArray(24) { 0 },
    val invCounts: IntArray = IntArray(24) { 0 },
    val invSelected: Int = 0,
    val dayTime: Float = 0f
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("accountId", accountId)
        root.put("timestamp", timestamp)
        root.put("version", 1)

        val prof = JSONObject()
        prof.put("nick", profile.nick)
        prof.put("female", profile.female)
        prof.put("hs", profile.hairStyle)
        prof.put("hc", profile.hairColor)
        prof.put("sh", profile.shirt)
        prof.put("pa", profile.pants)
        prof.put("sk", profile.skin)
        prof.put("coins", profile.coins)
        root.put("profile", prof)

        val ply = JSONObject()
        ply.put("x", playerX.toDouble())
        ply.put("y", playerY.toDouble())
        ply.put("hp", playerHealth)
        root.put("player", ply)

        val inv = JSONObject()
        val idsArr = JSONArray()
        val cntsArr = JSONArray()
        for (i in invIds.indices) idsArr.put(invIds[i])
        for (i in invCounts.indices) cntsArr.put(invCounts[i])
        inv.put("ids", idsArr)
        inv.put("counts", cntsArr)
        inv.put("selected", invSelected)
        root.put("inventory", inv)

        root.put("dayTime", dayTime.toDouble())
        return root.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CloudSavePayload
        if (accountId != other.accountId) return false
        if (timestamp != other.timestamp) return false
        if (profile != other.profile) return false
        if (playerX != other.playerX) return false
        if (playerY != other.playerY) return false
        if (playerHealth != other.playerHealth) return false
        if (!invIds.contentEquals(other.invIds)) return false
        if (!invCounts.contentEquals(other.invCounts)) return false
        if (invSelected != other.invSelected) return false
        if (dayTime != other.dayTime) return false
        return true
    }

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + profile.hashCode()
        result = 31 * result + playerX.hashCode()
        result = 31 * result + playerY.hashCode()
        result = 31 * result + playerHealth
        result = 31 * result + invIds.contentHashCode()
        result = 31 * result + invCounts.contentHashCode()
        result = 31 * result + invSelected
        result = 31 * result + dayTime.hashCode()
        return result
    }

    companion object {
        fun fromJson(jsonStr: String): CloudSavePayload? {
            return try {
                val root = JSONObject(jsonStr)
                val accountId = root.getString("accountId")
                val timestamp = root.getLong("timestamp")

                val profObj = root.getJSONObject("profile")
                val profile = Profile(
                    nick = profObj.getString("nick"),
                    female = profObj.getBoolean("female"),
                    hairStyle = profObj.getInt("hs"),
                    hairColor = profObj.getInt("hc"),
                    shirt = profObj.getInt("sh"),
                    pants = profObj.getInt("pa"),
                    skin = profObj.getInt("sk"),
                    coins = profObj.optInt("coins", 100)
                )

                val plyObj = root.optJSONObject("player")
                val px = plyObj?.optDouble("x", 0.0)?.toFloat() ?: 0f
                val py = plyObj?.optDouble("y", 0.0)?.toFloat() ?: 0f
                val hp = plyObj?.optInt("hp", 100) ?: 100

                val invObj = root.optJSONObject("inventory")
                val idsArr = invObj?.optJSONArray("ids")
                val cntsArr = invObj?.optJSONArray("counts")
                val invIds = IntArray(24) { idx -> idsArr?.optInt(idx, 0) ?: 0 }
                val invCounts = IntArray(24) { idx -> cntsArr?.optInt(idx, 0) ?: 0 }
                val invSelected = invObj?.optInt("selected", 0) ?: 0
                val dayTime = root.optDouble("dayTime", 0.0).toFloat()

                CloudSavePayload(
                    accountId = accountId,
                    timestamp = timestamp,
                    profile = profile,
                    playerX = px,
                    playerY = py,
                    playerHealth = hp,
                    invIds = invIds,
                    invCounts = invCounts,
                    invSelected = invSelected,
                    dayTime = dayTime
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
