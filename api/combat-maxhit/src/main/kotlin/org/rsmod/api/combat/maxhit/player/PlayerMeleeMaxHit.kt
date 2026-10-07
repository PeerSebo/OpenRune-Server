package org.rsmod.api.combat.maxhit.player

import org.rsmod.api.combat.maxhit.safePercentScale
import org.rsmod.api.combat.maxhit.scaleByPercent

public object PlayerMeleeMaxHit {
    public fun calculateBaseDamage(effectiveStrength: Int, strengthBonus: Int): Int {
        val effectiveBonus = strengthBonus + 64
        return (effectiveStrength * effectiveBonus + 320) / 640
    }

    /**
     * Scale decimal modifiers to integers before arithmetic to preserve emulation rounding;
     * styleBonus includes the base +8.
     */
    public fun calculateEffectiveStrength(
        visibleStrengthLvl: Int,
        styleBonus: Int,
        prayerBonus: Double,
        voidBonus: Double,
        weaponBonus: Double,
    ): Int {
        require(prayerBonus >= 1.0) { "Prayer bonus must be greater or equal to 1." }
        require(voidBonus >= 1.0) { "Void bonus must be greater or equal to 1." }
        require(weaponBonus >= 1.0) { "Weapon bonus must be greater or equal to 1." }

        val prayerPercent = safePercentScale(prayerBonus)
        val voidPercent = safePercentScale(voidBonus)
        val weaponPercent = safePercentScale(weaponBonus - 1.0)

        var effectiveLevel = visibleStrengthLvl

        // Burst of strength has a special exception when the effective level is 20 or below.
        val burstOfStrengthException =
            effectiveLevel <= 20 && prayerBonus > 1.0 && prayerBonus <= 1.05

        if (burstOfStrengthException) {
            effectiveLevel += 1
        } else {
            effectiveLevel = scaleByPercent(effectiveLevel, prayerPercent)
        }

        // `weaponPercent` calculation is based off the base `visibleStrengthLvl`.
        effectiveLevel += scaleByPercent(visibleStrengthLvl, weaponPercent)
        effectiveLevel += styleBonus
        effectiveLevel = scaleByPercent(effectiveLevel, voidPercent)
        return effectiveLevel
    }
}
