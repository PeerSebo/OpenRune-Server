package org.rsmod.api.combat.accuracy.npc

internal object InternalNpcAccuracy {
    fun calculateBaseRoll(effectiveLevel: Int, bonus: Int): Int {
        val effectiveBonus = bonus + 64
        return effectiveLevel * effectiveBonus
    }

    // NPCs use Controlled's +9 style bonus.
    fun calculateEffectiveLevel(visibleLevel: Int, styleBonus: Int = 9): Int {
        return visibleLevel + styleBonus
    }
}
