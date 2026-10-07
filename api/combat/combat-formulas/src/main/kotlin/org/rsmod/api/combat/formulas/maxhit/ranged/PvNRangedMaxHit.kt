package org.rsmod.api.combat.formulas.maxhit.ranged

import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import java.util.EnumSet
import kotlin.math.max
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatRangedAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatNpcAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatRangedAttributeCollector
import org.rsmod.api.combat.formulas.isSlayerTask
import org.rsmod.api.combat.maxhit.player.PlayerRangedMaxHit
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public class PvNRangedMaxHit
@Inject
constructor(
    private val bonuses: WornBonuses,
    private val weaponSpeeds: WeaponSpeeds,
    private val npcAttributes: CombatNpcAttributeCollector,
    private val rangedAttributes: CombatRangedAttributeCollector,
) {
    private var Player.maxHit by intVarp("varp.com_maxhit")

    /**
     * Updates `com_maxhit`; [computeMaxHit] alone does not.
     * @param boltSpecDamage Additive proc damage; Opal bolts use floor(visible Ranged level × 10%).
     */
    public fun getMaxHit(
        player: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specialMultiplier: Double,
        boltSpecDamage: Int,
    ): Int {
        val targetType = target.visType
        val targetMagic = max(target.magicLvl, targetType.param(params.attack_magic))
        val maxHit =
            computeMaxHit(
                source = player,
                target = targetType,
                npc = target,
                targetCurrHp = target.hitpoints,
                targetMaxHp = target.baseHitpointsLvl,
                targetMagic = targetMagic,
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
        target: NpcServerType,
        targetCurrHp: Int,
        targetMaxHp: Int,
        targetMagic: Int,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specialMultiplier: Double,
        boltSpecDamage: Int,
        npc: Npc? = null,
    ): Int {
        val rangeAttributes = rangedAttributes.collect(source, attackType, attackStyle)

        val slayerTask = target.isSlayerTask(source)
        val npcAttributes = npcAttributes.collect(target, npc, targetCurrHp, targetMaxHp, slayerTask)

        val modifiedDamage =
            computeModifiedDamage(source, targetMagic, attackStyle, rangeAttributes, npcAttributes)
        val specMaxHit = (modifiedDamage * specialMultiplier).toInt()
        return modifyPostSpec(source, specMaxHit, boltSpecDamage, rangeAttributes, npcAttributes)
    }

    public fun computeModifiedDamage(
        source: Player,
        targetMagic: Int,
        attackStyle: RangedAttackStyle?,
        rangeAttributes: EnumSet<CombatRangedAttributes>,
        npcAttributes: EnumSet<CombatNpcAttributes>,
    ): Int {
        val effectiveRanged = RangedMaxHitOperations.calculateEffectiveRanged(source, attackStyle)
        val rangedBonus = bonuses.rangedStrengthBonus(source)
        val baseDamage = PlayerRangedMaxHit.calculateBaseDamage(effectiveRanged, rangedBonus)
        return RangedMaxHitOperations.modifyBaseDamage(
            baseDamage = baseDamage,
            targetMagic = targetMagic,
            rangeAttributes = rangeAttributes,
            npcAttributes = npcAttributes,
        )
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
