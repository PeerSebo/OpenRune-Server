package org.rsmod.api.combat.accuracy.npc

public object NpcMeleeAccuracy {
    public fun calculateBaseAttackRoll(effectiveAttack: Int, attackBonus: Int): Int {
        return InternalNpcAccuracy.calculateBaseRoll(effectiveAttack, attackBonus)
    }

    public fun calculateEffectiveAttack(visibleAttackLvl: Int): Int {
        return InternalNpcAccuracy.calculateEffectiveLevel(visibleAttackLvl)
    }

    public fun calculateBaseDefenceRoll(effectiveDefence: Int, defenceBonus: Int): Int {
        return InternalNpcAccuracy.calculateBaseRoll(effectiveDefence, defenceBonus)
    }

    public fun calculateEffectiveDefence(visibleDefenceLvl: Int): Int {
        return InternalNpcAccuracy.calculateEffectiveLevel(visibleDefenceLvl)
    }
}
