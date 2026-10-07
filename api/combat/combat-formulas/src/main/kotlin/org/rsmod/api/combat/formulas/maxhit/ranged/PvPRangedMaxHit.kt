package org.rsmod.api.combat.formulas.maxhit.ranged

import jakarta.inject.Inject
import java.util.EnumSet
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatRangedAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatRangedAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.DamageReductionAttributeCollector
import org.rsmod.api.combat.formulas.maxhit.MaxHitOperations
import org.rsmod.api.combat.maxhit.player.PlayerRangedMaxHit
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

public class PvPRangedMaxHit
@Inject
constructor(
    private val random: GameRandom,
    private val bonuses: WornBonuses,
    private val weaponSpeeds: WeaponSpeeds,
    private val rangedAttributes: CombatRangedAttributeCollector,
    private val reductions: DamageReductionAttributeCollector,
) {
    private var Player.maxHit by intVarp("varp.com_maxhit")

    /**
     * Updates `com_maxhit`; [computeMaxHit] alone does not.
     * @param boltSpecDamage Additive proc damage; Opal bolts use floor(visible Ranged level × 10%).
     */
    public fun getMaxHit(
        player: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specialMultiplier: Double,
        boltSpecDamage: Int,
    ): Int {
        val maxHit =
            computeMaxHit(
                source = player,
                target = target,
                attackType = attackType,
                attackStyle = attackStyle,
                specialMultiplier = specialMultiplier,
                boltSpecDamage = boltSpecDamage,
            )
        player.maxHit = maxHit
        return maxHit
    }

    public fun computeMaxHit(
        source: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specialMultiplier: Double,
        boltSpecDamage: Int,
    ): Int {
        val npcAttributes = EnumSet.noneOf(CombatNpcAttributes::class.java)
        val rangeAttributes = rangedAttributes.collect(source, attackType, attackStyle)

        val modifiedDamage = computeModifiedDamage(source, attackStyle, rangeAttributes)
        val specMaxHit = (modifiedDamage * specialMultiplier).toInt()
        val finalMaxHit =
            modifyPostSpec(source, specMaxHit, boltSpecDamage, rangeAttributes, npcAttributes)

        val reductionAttributes = reductions.collectPvP(target, random)
        return MaxHitOperations.applyDamageReductions(finalMaxHit, null, reductionAttributes)
    }

    public fun computeModifiedDamage(
        source: Player,
        attackStyle: RangedAttackStyle?,
        rangeAttributes: EnumSet<CombatRangedAttributes>,
    ): Int {
        val effectiveRanged = RangedMaxHitOperations.calculateEffectiveRanged(source, attackStyle)
        val rangedBonus = bonuses.rangedStrengthBonus(source)
        val baseDamage = PlayerRangedMaxHit.calculateBaseDamage(effectiveRanged, rangedBonus)
        return RangedMaxHitOperations.modifyBaseDamage(baseDamage, rangeAttributes)
    }

    public fun modifyPostSpec(
        source: Player,
        modifiedDamage: Int,
        boltSpecDamage: Int,
        rangeAttributes: EnumSet<CombatRangedAttributes>,
        npcAttributes: EnumSet<CombatNpcAttributes>,
    ): Int {
        val attackRate = weaponSpeeds.actual(source)
        return RangedMaxHitOperations.modifyPostSpec(
            modifiedDamage = modifiedDamage,
            boltSpecDamage = boltSpecDamage,
            attackRate = attackRate,
            rangeAttributes = rangeAttributes,
            npcAttributes = npcAttributes,
        )
    }
}
