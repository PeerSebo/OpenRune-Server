package org.rsmod.api.combat.accuracy.npc

public object NpcMagicAccuracy {
    public fun calculateBaseAttackRoll(effectiveMagic: Int, magicBonus: Int): Int {
        return InternalNpcAccuracy.calculateBaseRoll(effectiveMagic, magicBonus)
    }

    public fun calculateEffectiveMagic(visibleMagicLvl: Int): Int {
        return InternalNpcAccuracy.calculateEffectiveLevel(visibleMagicLvl)
    }

    public fun calculateBaseDefenceRoll(effectiveDefence: Int, defenceBonus: Int): Int {
        return InternalNpcAccuracy.calculateBaseRoll(effectiveDefence, defenceBonus)
    }

    public fun calculateEffectiveDefence(visibleDefenceLvl: Int): Int {
        return InternalNpcAccuracy.calculateEffectiveLevel(visibleDefenceLvl)
    }
}
