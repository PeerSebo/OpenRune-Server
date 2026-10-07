package org.rsmod.api.combat.formulas.maxhit.magic

import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import java.util.EnumSet
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.attributes.CombatStaffAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatMagicAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatNpcAttributeCollector
import org.rsmod.api.combat.formulas.isSlayerTask
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public class PvNMagicMaxHit
@Inject
constructor(
    private val random: GameRandom,
    private val bonuses: WornBonuses,
    private val npcAttributes: CombatNpcAttributeCollector,
    private val magicAttributes: CombatMagicAttributeCollector,
) {
    private var Player.maxHit by intVarp("varp.com_maxhit")

    /** Also updates the player's com_maxhit varp. */
    public fun getSpellMaxHit(
        player: Player,
        target: Npc,
        spell: ItemServerType,
        spellbook: Spellbook?,
        baseMaxHit: Int,
        attackRate: Int,
        usedSunfireRune: Boolean,
    ): IntRange {
        val targetType = target.visType
        val elementalWeakness = targetType.param(params.elemental_weakness_percent)
        val maxHit =
            computeSpellMaxHit(
                source = player,
                target = targetType,
                npc = target,
                spell = spell,
                targetCurrHp = target.hitpoints,
                targetMaxHp = target.baseHitpointsLvl,
                targetWeaknessPercent = elementalWeakness,
                baseMaxHit = baseMaxHit,
                attackRate = attackRate,
                spellbook = spellbook,
                usedSunfireRune = usedSunfireRune,
            )
        player.maxHit = maxHit.last
        return maxHit
    }

    public fun computeSpellMaxHit(
        source: Player,
        target: NpcServerType,
        spell: ItemServerType,
        targetCurrHp: Int,
        targetMaxHp: Int,
        targetWeaknessPercent: Int,
        baseMaxHit: Int,
        attackRate: Int,
        spellbook: Spellbook?,
        usedSunfireRune: Boolean,
        npc: Npc? = null,
    ): IntRange {
        val spellAttributes =
            magicAttributes.spellCollect(source, spell, spellbook, usedSunfireRune, random)

        val slayerTask = target.isSlayerTask(source)
        val npcAttributes = npcAttributes.collect(target, npc, targetCurrHp, targetMaxHp, slayerTask)

        val modifiedDamage =
            computeSpellModifiedDamage(source, baseMaxHit, spellAttributes, npcAttributes)
        return MagicMaxHitOperations.modifySpellDamageRange(
            modifiedDamage = modifiedDamage,
            baseDamage = baseMaxHit,
            attackRate = attackRate,
            targetWeaknessPercent = targetWeaknessPercent,
            spellAttributes = spellAttributes,
            npcAttributes = npcAttributes,
        )
    }

    public fun computeSpellModifiedDamage(
        source: Player,
        baseDamage: Int,
        spellAttributes: EnumSet<CombatSpellAttributes>,
        npcAttributes: EnumSet<CombatNpcAttributes>,
    ): Int {
        val magicDmgBonus = bonuses.magicDamageBonusBase(source)
        val prayerDmgBonus = MagicMaxHitOperations.getMagicDamagePrayerBonus(source)
        return MagicMaxHitOperations.modifySpellBaseDamage(
            baseDamage = baseDamage,
            sourceMagic = source.magicLvl,
            sourceBaseMagicDmgBonus = magicDmgBonus,
            sourceMagicPrayerBonus = prayerDmgBonus,
            spellAttributes = spellAttributes,
            npcAttributes = npcAttributes,
        )
    }

    /**
     * Updates `com_maxhit`; [computeStaffMaxHit] alone does not.
     * @param baseMaxHit Staff spell max hit before modifiers.
     */
    public fun getStaffMaxHit(
        player: Player,
        target: Npc,
        baseMaxHit: Int,
        specialMultiplier: Double,
    ): Int {
        val maxHit =
            computeStaffMaxHit(
                source = player,
                target = target.visType,
                npc = target,
                targetCurrHp = target.hitpoints,
                targetMaxHp = target.baseHitpointsLvl,
                baseMaxHit = baseMaxHit,
                specialMultiplier = specialMultiplier,
            )
        player.maxHit = maxHit
        return maxHit
    }

    public fun computeStaffMaxHit(
        source: Player,
        target: NpcServerType,
        targetCurrHp: Int,
        targetMaxHp: Int,
        baseMaxHit: Int,
        specialMultiplier: Double,
        npc: Npc? = null,
    ): Int {
        val staffAttributes = magicAttributes.staffCollect(source, random)

        val slayerTask = target.isSlayerTask(source)
        val npcAttributes = npcAttributes.collect(target, npc, targetCurrHp, targetMaxHp, slayerTask)

        val modifiedDamage =
            computeStaffModifiedDamage(source, baseMaxHit, staffAttributes, npcAttributes)
        return (modifiedDamage * specialMultiplier).toInt()
    }

    public fun computeStaffModifiedDamage(
        source: Player,
        baseDamage: Int,
        staffAttributes: EnumSet<CombatStaffAttributes>,
        npcAttributes: EnumSet<CombatNpcAttributes>,
    ): Int {
        val magicDmgBonus = bonuses.magicDamageBonusBase(source)
        val prayerDmgBonus = MagicMaxHitOperations.getMagicDamagePrayerBonus(source)
        return MagicMaxHitOperations.modifyStaffBaseDamage(
            baseDamage = baseDamage,
            sourceBaseMagicDmgBonus = magicDmgBonus,
            sourceMagicPrayerBonus = prayerDmgBonus,
            staffAttributes = staffAttributes,
            npcAttributes = npcAttributes,
        )
    }
}
