package org.rsmod.api.combat.maxhit.npc

public object NpcRangedMaxHit {
    public fun calculateBaseDamage(effectiveRanged: Int, rangedStrengthBonus: Int): Int {
        return InternalNpcMaxHits.calculateBaseDamage(effectiveRanged, rangedStrengthBonus)
    }

    public fun calculateEffectiveRanged(visibleRangedLvl: Int): Int {
        return InternalNpcMaxHits.calculateEffectiveLevel(visibleRangedLvl)
    }
}
