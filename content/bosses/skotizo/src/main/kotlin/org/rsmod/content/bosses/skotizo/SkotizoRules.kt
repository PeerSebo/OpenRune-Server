package org.rsmod.content.bosses.skotizo

import org.rsmod.map.CoordGrid

internal object SkotizoRules {
    const val Key = "skotizo"
    const val Boss = "npc.cata_boss"
    const val Minion = "npc.cata_boss_minion"
    const val Ankou = "npc.cata_boss_ankou"
    val Closed = listOf("n", "e", "s", "w").map { "npc.cata_boss_altar_closed_$it" }
    val Open = listOf("n", "e", "s", "w").map { "npc.cata_boss_altar_open_$it" }
    val Entry = CoordGrid(2272, 5652)
    val Exit = CoordGrid(1663, 10045)
    val BossSpawn = CoordGrid(2271, 5661)
    val Altars = listOf(CoordGrid(2272, 5677), CoordGrid(2287, 5663), CoordGrid(2272, 5650), CoordGrid(2257, 5663))
    val Demons = listOf(CoordGrid(2268, 5661), CoordGrid(2278, 5661), CoordGrid(2273, 5657))
    fun reducedDamage(damage: Int, altars: Int, demonbane: Boolean): Int =
        damage * (100 - altars.coerceIn(0, 4) * if (demonbane) 15 else 25) / 100
    fun minionsUnlocked(hitpoints: Int): Boolean = hitpoints in 1..150
    fun distance(origin: CoordGrid, size: Int, target: CoordGrid): Int =
        maxOf((origin.x - target.x).coerceAtLeast(0), (target.x - origin.x - size + 1).coerceAtLeast(0),
            (origin.z - target.z).coerceAtLeast(0), (target.z - origin.z - size + 1).coerceAtLeast(0))
}

internal class AltarCycle {
    private val active = BooleanArray(4)
    private var allSince: Int? = null
    var exhausted = false
        private set
    val count get() = active.count { it }
    fun isActive(index: Int) = active[index]
    fun tick(tick: Int) { if (allSince?.let { tick - it >= 300 } == true) exhausted = true }
    fun activate(index: Int, tick: Int): Boolean {
        tick(tick)
        if (exhausted || active[index]) return false
        active[index] = true
        if (count == 4) allSince = tick
        return true
    }
    fun deactivate(index: Int, tick: Int) {
        tick(tick)
        active[index] = false
        allSince = null
    }
}
