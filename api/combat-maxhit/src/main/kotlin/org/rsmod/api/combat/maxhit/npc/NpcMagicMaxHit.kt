package org.rsmod.api.combat.maxhit.npc

public object NpcMagicMaxHit {
    public fun calculateBaseDamage(effectiveMagic: Int, magicStrengthBonus: Int): Int {
        return InternalNpcMaxHits.calculateBaseDamage(effectiveMagic, magicStrengthBonus)
    }

    public fun calculateEffectiveMagic(visibleMagicLvl: Int): Int {
        return InternalNpcMaxHits.calculateEffectiveLevel(visibleMagicLvl)
    }
}
