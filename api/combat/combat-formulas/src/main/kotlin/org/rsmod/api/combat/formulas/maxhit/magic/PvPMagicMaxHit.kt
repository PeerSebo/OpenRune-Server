package org.rsmod.api.combat.formulas.maxhit.magic

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import java.util.EnumSet
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatMagicAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.DamageReductionAttributeCollector
import org.rsmod.api.combat.formulas.maxhit.MaxHitOperations
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

public class PvPMagicMaxHit
@Inject
constructor(
    private val random: GameRandom,
    private val bonuses: WornBonuses,
    private val magicAttributes: CombatMagicAttributeCollector,
    private val reductions: DamageReductionAttributeCollector,
) {
    private var Player.maxHit by intVarp("varp.com_maxhit")

    /** Also updates the player's com_maxhit varp. */
    public fun getSpellMaxHit(
        player: Player,
        target: Player,
        spell: ItemServerType,
        spellbook: Spellbook?,
        baseMaxHit: Int,
        usedSunfireRune: Boolean,
    ): IntRange {
        val maxHit =
            computeSpellMaxHit(
                source = player,
                target = target,
                spell = spell,
                baseMaxHit = baseMaxHit,
                spellbook = spellbook,
                usedSunfireRune = usedSunfireRune,
            )
        player.maxHit = maxHit.last
        return maxHit
    }

    public fun computeSpellMaxHit(
        source: Player,
        target: Player,
        spell: ItemServerType,
        baseMaxHit: Int,
        spellbook: Spellbook?,
        usedSunfireRune: Boolean,
    ): IntRange {
        val spellAttributes =
            magicAttributes.spellCollect(source, spell, spellbook, usedSunfireRune, random)

        val modifiedDamage = computeSpellModifiedDamage(source, baseMaxHit, spellAttributes)
        val modifiedDamageRange =
            MagicMaxHitOperations.modifySpellDamageRange(modifiedDamage, spellAttributes)

        val defenceBonus = bonuses.defensiveMagicBonus(target)
        val reductionAttributes = reductions.collectPvP(target, random)

        val reducedMinHit =
            MaxHitOperations.applyDamageReductions(
                startDamage = modifiedDamageRange.first,
                activeDefenceBonus = defenceBonus,
                reductionAttributes = reductionAttributes,
            )

        val reducedMaxHit =
            MaxHitOperations.applyDamageReductions(
                startDamage = modifiedDamageRange.last,
                activeDefenceBonus = defenceBonus,
                reductionAttributes = reductionAttributes,
            )

        return reducedMinHit..reducedMaxHit
    }

    public fun computeSpellModifiedDamage(
        source: Player,
        baseDamage: Int,
        spellAttributes: EnumSet<CombatSpellAttributes>,
    ): Int {
        val magicDmgBonus = bonuses.magicDamageBonusBase(source)
        val prayerDmgBonus = MagicMaxHitOperations.getMagicDamagePrayerBonus(source)
        return MagicMaxHitOperations.modifySpellBaseDamage(
            baseDamage = baseDamage,
            sourceMagic = source.magicLvl,
            sourceBaseMagicDmgBonus = magicDmgBonus,
            sourceMagicPrayerBonus = prayerDmgBonus,
            spellAttributes = spellAttributes,
        )
    }

    /**
     * Updates `com_maxhit`; [computeStaffMaxHit] alone does not.
     * @param baseMaxHit Staff spell max hit before modifiers.
     */
    public fun getStaffMaxHit(
        player: Player,
        target: Player,
        baseMaxHit: Int,
        specialMultiplier: Double,
    ): Int {
        val maxHit = computeStaffMaxHit(player, target, baseMaxHit, specialMultiplier)
        player.maxHit = maxHit
        return maxHit
    }

    public fun computeStaffMaxHit(
        source: Player,
        target: Player,
        baseMaxHit: Int,
        specialMultiplier: Double,
    ): Int {
        val modifiedDamage = computeStaffModifiedDamage(source, baseMaxHit)
        val specMaxHit = (modifiedDamage * specialMultiplier).toInt()

        val defenceBonus = bonuses.defensiveMagicBonus(target)
        val reductionAttributes = reductions.collectPvP(target, random)

        return MaxHitOperations.applyDamageReductions(
            startDamage = specMaxHit,
            activeDefenceBonus = defenceBonus,
            reductionAttributes = reductionAttributes,
        )
    }

    public fun computeStaffModifiedDamage(source: Player, baseDamage: Int): Int {
        val magicDmgBonus = bonuses.magicDamageBonusBase(source)
        val prayerDmgBonus = MagicMaxHitOperations.getMagicDamagePrayerBonus(source)
        return MagicMaxHitOperations.modifyStaffBaseDamage(
            baseDamage = baseDamage,
            sourceBaseMagicDmgBonus = magicDmgBonus,
            sourceMagicPrayerBonus = prayerDmgBonus,
        )
    }
}
