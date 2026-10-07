package org.rsmod.api.combat.accuracy.npc

public object NpcRangedAccuracy {
    public fun calculateBaseAttackRoll(effectiveRanged: Int, rangedBonus: Int): Int {
        return InternalNpcAccuracy.calculateBaseRoll(effectiveRanged, rangedBonus)
    }

    public fun calculateEffectiveRanged(visibleRangedLvl: Int): Int {
        return InternalNpcAccuracy.calculateEffectiveLevel(visibleRangedLvl)
    }

    public fun calculateBaseDefenceRoll(effectiveDefence: Int, defenceBonus: Int): Int {
        return InternalNpcAccuracy.calculateBaseRoll(effectiveDefence, defenceBonus)
    }

    public fun calculateEffectiveDefence(visibleDefenceLvl: Int): Int {
        return InternalNpcAccuracy.calculateEffectiveLevel(visibleDefenceLvl)
    }
}
