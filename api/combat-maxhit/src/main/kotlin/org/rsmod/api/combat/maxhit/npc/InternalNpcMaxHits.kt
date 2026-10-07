package org.rsmod.api.combat.maxhit.npc

internal object InternalNpcMaxHits {
    fun calculateBaseDamage(effectiveLevel: Int, bonus: Int): Int {
        val effectiveBonus = bonus + 64
        return (effectiveLevel * effectiveBonus + 320) / 640
    }

    // NPCs use Controlled's +9 style bonus.
    fun calculateEffectiveLevel(visibleLevel: Int, styleBonus: Int = 9): Int {
        return visibleLevel + styleBonus
    }
}
