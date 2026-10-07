package org.rsmod.content.skills.hunter.configs

internal enum class FalconryPrey(val code: Int) {
    SPOTTED(1), DARK(2), DASHING(3);

    private val data: HunterCacheData.Falconry get() = HunterCacheData.falconry.getValue(code)
    val npc: String get() = data.npc
    val falcon: String get() = data.falcon
    val level: Int get() = data.level
    val xp: Double get() = data.xp
    val loot: List<String> get() = data.loot

    fun chance(hunterLevel: Int): Int =
        if (hunterLevel < level) 0
        else data.chances[(hunterLevel - level).coerceAtMost(data.chances.lastIndex)]
}
