package org.rsmod.content.skills.hunter.tracking

import kotlin.math.floor
import org.rsmod.api.random.GameRandom

internal object HerbiboarLoot {
    data class Item(val type: String, val count: Int)
    private data class Herb(val type: String, val low: Int, val high: Int)
    private val herbs = listOf(
        Herb("ranarr", -10, 20), Herb("torstol", -70, 20), Herb("snapdragon", -60, 20),
        Herb("dwarf_weed", -50, 30), Herb("lantadyme", -30, 40), Herb("cadantine", -10, 50),
        Herb("kwuarm", 10, 60), Herb("avantoe", 20, 60), Herb("irit", 30, 70),
        Herb("tarromin", 70, -20), Herb("harralander", 100, -30), Herb("marentill", 170, -40),
    )
    fun herb(level: Int, random: GameRandom): String {
        val bounded = level.coerceIn(31, 99)
        for (herb in herbs) {
            // Floor negative bounds; truncation would incorrectly enable herbs below their chart thresholds.
            val chance = (1 + floor(herb.low * (99 - bounded) / 98.0 + herb.high * (bounded - 1) / 98.0 + 0.5).toInt()).coerceIn(0, 256)
            if (random.of(256) < chance) return "obj.unidentified_${herb.type}"
        }
        return "obj.unidentified_guam"
    }
    fun hunterXp(baseLevel: Int): Double = when {
        baseLevel <= 94 -> -450.0 + 30 * baseLevel
        else -> 2385.0 + 19 * (baseLevel.coerceAtMost(99) - 95)
    }
    fun inspection(random: GameRandom): Item? = when (random.of(3500)) {
        in 0..1259 -> Item("obj.fossil_numulite", random.of(5, 24))
        in 1260..1329 -> Item("obj.fossil_small_unid", 1)
        in 1330..1364 -> Item("obj.fossil_medium_unid", 1)
        in 1365..1392 -> Item("obj.fossil_large_unid", 1)
        in 1393..1399 -> Item("obj.fossil_rare_unid", 1)
        else -> null
    }
}
