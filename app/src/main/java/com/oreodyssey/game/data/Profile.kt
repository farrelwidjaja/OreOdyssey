// app/src/main/java/com/oreodyssey/game/data/Profile.kt
package com.oreodyssey.game.data

data class Profile(
    var nick: String = "Petualang",
    var female: Boolean = false,
    var hairStyle: Int = 1,
    var hairColor: Int = 1,
    var shirt: Int = 8,
    var pants: Int = 0,
    var skin: Int = 1,
    var coins: Int = 100
)

/** Pilihan kustomisasi: 10 gaya rambut, 12 warna rambut, 16 baju, 12 celana/rok, 6 kulit. */
object Palettes {
    val hairStyleNames = arrayOf(
        "Botak", "Pendek", "Poni", "Jabrik", "Panjang",
        "Kuncir", "Mohawk", "Afro", "Bob", "Kepang Dua"
    )
    val hair = intArrayOf(
        0xFF1A1A1A.toInt(), 0xFF3B2314.toInt(), 0xFF6B4423.toInt(), 0xFFA0703C.toInt(),
        0xFFE8C766.toInt(), 0xFFB5341F.toInt(), 0xFFE87A1E.toInt(), 0xFFF2F2F2.toInt(),
        0xFF8E8E8E.toInt(), 0xFF2E6FE0.toInt(), 0xFFE866B5.toInt(), 0xFF8E44AD.toInt()
    )
    val shirt = intArrayOf(
        0xFFD32F2F.toInt(), 0xFFF57C00.toInt(), 0xFFFBC02D.toInt(), 0xFF9CCC65.toInt(),
        0xFF388E3C.toInt(), 0xFF00897B.toInt(), 0xFF00ACC1.toInt(), 0xFF29B6F6.toInt(),
        0xFF1E56D8.toInt(), 0xFF3949AB.toInt(), 0xFF8E24AA.toInt(), 0xFFEC407A.toInt(),
        0xFF6D4C41.toInt(), 0xFF757575.toInt(), 0xFF212121.toInt(), 0xFFF5F5F5.toInt()
    )
    val pants = intArrayOf(
        0xFF1A237E.toInt(), 0xFF1976D2.toInt(), 0xFF212121.toInt(), 0xFF616161.toInt(),
        0xFF5D4037.toInt(), 0xFFBCAAA4.toInt(), 0xFF2E7D32.toInt(), 0xFF827717.toInt(),
        0xFF880E4F.toInt(), 0xFF4A148C.toInt(), 0xFFC62828.toInt(), 0xFFE0E0E0.toInt()
    )
    val skin = intArrayOf(
        0xFFF8D9C0.toInt(), 0xFFF2C79B.toInt(), 0xFFE0A878.toInt(),
        0xFFC68642.toInt(), 0xFF8D5524.toInt(), 0xFF5C3A1E.toInt()
    )
}