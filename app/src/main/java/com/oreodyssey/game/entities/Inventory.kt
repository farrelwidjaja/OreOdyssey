// app/src/main/java/com/oreodyssey/game/entities/Inventory.kt
package com.oreodyssey.game.entities

/** Slot 0..7 = kotak bawah (hotbar). Slot 8..31 = tas. */
class Inventory {

    companion object {
        const val HOTBAR = 8
        const val BAG = 24
        const val SIZE = HOTBAR + BAG
        const val MAX_STACK = 999
    }

    val ids = IntArray(SIZE)
    val counts = IntArray(SIZE)
    @Volatile var selected = 0

    fun selectedId() = if (counts[selected] > 0) ids[selected] else 0

    /** Item baru masuk ke tas (menumpuk jika sudah ada), hotbar hanya jika tas penuh. */
    @Synchronized
    fun add(id: Int, n: Int = 1): Boolean {
        for (i in 0 until SIZE) if (counts[i] > 0 && ids[i] == id) {
            counts[i] = minOf(MAX_STACK, counts[i] + n); return true
        }
        for (i in HOTBAR until SIZE) if (counts[i] == 0) { ids[i] = id; counts[i] = n; return true }
        for (i in 0 until HOTBAR) if (counts[i] == 0) { ids[i] = id; counts[i] = n; return true }
        return false
    }

    @Synchronized
    fun canAdd(id: Int): Boolean {
        for (i in 0 until SIZE) if (counts[i] == 0 || (ids[i] == id && counts[i] < MAX_STACK)) return true
        return false
    }

    @Synchronized
    fun count(id: Int): Int {
        var s = 0
        for (i in 0 until SIZE) if (ids[i] == id) s += counts[i]
        return s
    }

    @Synchronized
    fun remove(id: Int, n: Int): Boolean {
        if (count(id) < n) return false
        var left = n
        for (i in HOTBAR until SIZE) left = take(i, id, left)
        for (i in 0 until HOTBAR) left = take(i, id, left)
        return true
    }

    private fun take(i: Int, id: Int, left: Int): Int {
        if (left <= 0 || counts[i] == 0 || ids[i] != id) return left
        val t = minOf(left, counts[i])
        counts[i] -= t
        return left - t
    }

    @Synchronized
    fun consumeSelected() { if (counts[selected] > 0) counts[selected]-- }

    fun canCraft(r: Recipe) = r.needs.all { count(it.first) >= it.second }

    @Synchronized
    fun craft(r: Recipe): Boolean {
        if (!canCraft(r) || !canAdd(r.result)) return false
        for (n in r.needs) remove(n.first, n.second)
        return add(r.result, r.count)
    }

    /** Pindah stack dari tas ke hotbar (gabung jika sama, atau slot kosong, atau tukar dengan slot terpilih). */
    @Synchronized
    fun moveToHotbar(slot: Int) {
        if (slot !in HOTBAR until SIZE || counts[slot] == 0) return
        val id = ids[slot]
        for (i in 0 until HOTBAR) if (counts[i] > 0 && ids[i] == id) {
            counts[i] = minOf(MAX_STACK, counts[i] + counts[slot]); counts[slot] = 0; return
        }
        for (i in 0 until HOTBAR) if (counts[i] == 0) {
            ids[i] = id; counts[i] = counts[slot]; counts[slot] = 0; return
        }
        swap(selected, slot)
    }

    /** Pindah stack dari hotbar kembali ke tas. */
    @Synchronized
    fun moveToBag(slot: Int) {
        if (slot !in 0 until HOTBAR || counts[slot] == 0) return
        val id = ids[slot]
        for (i in HOTBAR until SIZE) if (counts[i] > 0 && ids[i] == id) {
            counts[i] = minOf(MAX_STACK, counts[i] + counts[slot]); counts[slot] = 0; return
        }
        for (i in HOTBAR until SIZE) if (counts[i] == 0) {
            ids[i] = id; counts[i] = counts[slot]; counts[slot] = 0; return
        }
    }

    private fun swap(a: Int, b: Int) {
        val ti = ids[a]; val tc = counts[a]
        ids[a] = ids[b]; counts[a] = counts[b]
        ids[b] = ti; counts[b] = tc
    }

    @Synchronized
    fun clear() { ids.fill(0); counts.fill(0); selected = 0 }
}