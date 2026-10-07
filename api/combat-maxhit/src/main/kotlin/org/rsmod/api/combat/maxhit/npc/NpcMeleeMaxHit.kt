package org.rsmod.api.combat.maxhit.npc

public object NpcMeleeMaxHit {
    public fun calculateBaseDamage(effectiveStrength: Int, strengthBonus: Int): Int {
        return InternalNpcMaxHits.calculateBaseDamage(effectiveStrength, strengthBonus)
    }

    public fun calculateEffectiveStrength(visibleStrengthLvl: Int): Int {
        return InternalNpcMaxHits.calculateEffectiveLevel(visibleStrengthLvl)
    }
}
