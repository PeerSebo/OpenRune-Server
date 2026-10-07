package org.rsmod.api.combat.accuracy.player

import org.rsmod.api.combat.accuracy.safePercentScale
import org.rsmod.api.combat.accuracy.scaleByPercent

public object PlayerMagicAccuracy {
    public fun calculateBaseAttackRoll(effectiveMagic: Int, magicBonus: Int): Int {
        val effectiveBonus = magicBonus + 64
        return effectiveMagic * effectiveBonus
    }

    /**
     * Scale decimal modifiers to integers before arithmetic to preserve emulation rounding;
     * styleBonus includes the base +9.
     */
    public fun calculateEffectiveMagic(
        visibleMagicLvl: Int,
        styleBonus: Int,
        prayerBonus: Double,
        voidBonus: Double,
    ): Int {
        require(prayerBonus >= 1.0) { "Prayer bonus must be greater or equal to 1." }
        require(voidBonus >= 1.0) { "Void bonus must be greater or equal to 1." }

        val prayerPercent = safePercentScale(prayerBonus)
        val voidPercent = safePercentScale(voidBonus)

        var effectiveLevel = visibleMagicLvl
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
     * Apply integer-scaled bonuses before combining 30% Defence and 70% Magic; add styleBonus
     * last.
     */
    public fun calculateEffectiveDefence(
        visibleDefenceLvl: Int,
        visibleMagicLvl: Int,
        styleBonus: Int,
        defencePrayerBonus: Double,
        magicPrayerBonus: Double,
        armourBonus: Double,
    ): Int {
        require(defencePrayerBonus >= 1.0) { "Defence prayer bonus must be greater or equal to 1." }
        require(magicPrayerBonus >= 1.0) { "Magic prayer bonus must be greater or equal to 1." }
        require(armourBonus >= 1.0) { "Armour bonus must be greater or equal to 1." }

        val defPrayerPercent = safePercentScale(defencePrayerBonus)
        val armourPercent = safePercentScale(armourBonus - 1.0)
        var effectiveDefenceLevel = visibleDefenceLvl

        effectiveDefenceLevel = scaleByPercent(effectiveDefenceLevel, defPrayerPercent)
        effectiveDefenceLevel += scaleByPercent(effectiveDefenceLevel, armourPercent)

        val magicPrayerPercent = safePercentScale(magicPrayerBonus)
        var effectiveMagicLevel = visibleMagicLvl

        effectiveMagicLevel = scaleByPercent(effectiveMagicLevel, magicPrayerPercent)
        effectiveDefenceLevel = (effectiveMagicLevel * 7 / 10) + (effectiveDefenceLevel * 3 / 10)

        effectiveDefenceLevel += styleBonus
        return effectiveDefenceLevel
    }
}
