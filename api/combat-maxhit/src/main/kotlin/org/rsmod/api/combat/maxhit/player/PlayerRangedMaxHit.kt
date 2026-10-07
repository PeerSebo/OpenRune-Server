package org.rsmod.api.combat.maxhit.player

import org.rsmod.api.combat.maxhit.safePercentScale
import org.rsmod.api.combat.maxhit.scaleByPercent

public object PlayerRangedMaxHit {
    public fun calculateBaseDamage(effectiveRanged: Int, rangedStrengthBonus: Int): Int {
        val effectiveBonus = rangedStrengthBonus + 64
        return (effectiveRanged * effectiveBonus + 320) / 640
    }

    /**
     * Scale decimal modifiers to integers before arithmetic to preserve emulation rounding;
     * styleBonus includes the base +8.
     */
    public fun calculateEffectiveRanged(
        visibleRangedLvl: Int,
        styleBonus: Int,
        prayerBonus: Double,
        voidBonus: Double,
    ): Int {
        require(prayerBonus >= 1.0) { "Prayer bonus must be greater or equal to 1." }
        require(voidBonus >= 1.0) { "Void bonus must be greater or equal to 1." }

        val prayerPercent = safePercentScale(prayerBonus)
        val voidPercent = safePercentScale(voidBonus)

        var effectiveLevel = visibleRangedLvl

        // Sharp eye has a special exception when the effective level is 20 or below.
        val sharpEyeException = effectiveLevel <= 20 && prayerBonus > 1.0 && prayerBonus <= 1.05

        if (sharpEyeException) {
            effectiveLevel += 1
        } else {
            effectiveLevel = scaleByPercent(effectiveLevel, prayerPercent)
        }

        effectiveLevel += styleBonus
        effectiveLevel = scaleByPercent(effectiveLevel, voidPercent)
        return effectiveLevel
    }
}
