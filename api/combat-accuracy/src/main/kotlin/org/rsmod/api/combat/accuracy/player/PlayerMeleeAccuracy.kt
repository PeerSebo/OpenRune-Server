package org.rsmod.api.combat.accuracy.player

import org.rsmod.api.combat.accuracy.safePercentScale
import org.rsmod.api.combat.accuracy.scaleByPercent

public object PlayerMeleeAccuracy {
    public fun calculateBaseAttackRoll(effectiveAttack: Int, attackBonus: Int): Int {
        val effectiveBonus = attackBonus + 64
        return effectiveAttack * effectiveBonus
    }

    /**
     * Scale decimal modifiers to integers before arithmetic to preserve emulation rounding;
     * styleBonus includes the base +8.
     */
    public fun calculateEffectiveAttack(
        visibleAttackLvl: Int,
        styleBonus: Int,
        prayerBonus: Double,
        voidBonus: Double,
    ): Int {
        require(prayerBonus >= 1.0) { "Prayer bonus must be greater or equal to 1." }
        require(voidBonus >= 1.0) { "Void bonus must be greater or equal to 1." }

        val prayerPercent = safePercentScale(prayerBonus)
        val voidPercent = safePercentScale(voidBonus)

        var effectiveLevel = visibleAttackLvl
        effectiveLevel = scaleByPercent(effectiveLevel, prayerPercent)
        effectiveLevel += styleBonus
        effectiveLevel = scaleByPercent(effectiveLevel, voidPercent)
        return effectiveLevel
    }

    public fun calculateBaseDefenceRoll(effectiveDefence: Int, defenceBonus: Int): Int {
        val effectiveBonus = defenceBonus + 64
        return effectiveDefence * effectiveBonus
    }

    /**
     * Scale decimal modifiers to integers before arithmetic to preserve emulation rounding;
     * styleBonus includes the base +8.
     */
    public fun calculateEffectiveDefence(
        visibleDefenceLvl: Int,
        styleBonus: Int,
        prayerBonus: Double,
        armourBonus: Double,
    ): Int {
        require(prayerBonus >= 1.0) { "Prayer bonus must be greater or equal to 1." }
        require(armourBonus >= 1.0) { "Armour bonus must be greater or equal to 1." }

        val prayerPercent = safePercentScale(prayerBonus)
        val armourPercent = safePercentScale(armourBonus - 1.0)

        var effectiveLevel = visibleDefenceLvl
        effectiveLevel = scaleByPercent(effectiveLevel, prayerPercent)
        effectiveLevel += scaleByPercent(effectiveLevel, armourPercent)
        effectiveLevel += styleBonus
        return effectiveLevel
    }
}
