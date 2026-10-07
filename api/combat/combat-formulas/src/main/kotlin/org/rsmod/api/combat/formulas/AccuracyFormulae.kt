package org.rsmod.api.combat.formulas

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.accuracy.magic.NvNMagicAccuracy
import org.rsmod.api.combat.formulas.accuracy.magic.NvPMagicAccuracy
import org.rsmod.api.combat.formulas.accuracy.magic.PvNMagicAccuracy
import org.rsmod.api.combat.formulas.accuracy.magic.PvPMagicAccuracy
import org.rsmod.api.combat.formulas.accuracy.melee.NvNMeleeAccuracy
import org.rsmod.api.combat.formulas.accuracy.melee.NvPMeleeAccuracy
import org.rsmod.api.combat.formulas.accuracy.melee.PvNMeleeAccuracy
import org.rsmod.api.combat.formulas.accuracy.melee.PvPMeleeAccuracy
import org.rsmod.api.combat.formulas.accuracy.multi.NvNMultiStyleAccuracy
import org.rsmod.api.combat.formulas.accuracy.multi.NvPMultiStyleAccuracy
import org.rsmod.api.combat.formulas.accuracy.multi.PvNMultiStyleAccuracy
import org.rsmod.api.combat.formulas.accuracy.multi.PvPMultiStyleAccuracy
import org.rsmod.api.combat.formulas.accuracy.ranged.NvNRangedAccuracy
import org.rsmod.api.combat.formulas.accuracy.ranged.NvPRangedAccuracy
import org.rsmod.api.combat.formulas.accuracy.ranged.PvNRangedAccuracy
import org.rsmod.api.combat.formulas.accuracy.ranged.PvPRangedAccuracy
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public class AccuracyFormulae
@Inject
constructor(
    private val nvpMagicAccuracy: NvPMagicAccuracy,
    private val nvnMagicAccuracy: NvNMagicAccuracy,
    private val pvnMagicAccuracy: PvNMagicAccuracy,
    private val pvpMagicAccuracy: PvPMagicAccuracy,
    private val nvpMeleeAccuracy: NvPMeleeAccuracy,
    private val nvnMeleeAccuracy: NvNMeleeAccuracy,
    private val pvnMeleeAccuracy: PvNMeleeAccuracy,
    private val pvpMeleeAccuracy: PvPMeleeAccuracy,
    private val nvpRangedAccuracy: NvPRangedAccuracy,
    private val nvnRangedAccuracy: NvNRangedAccuracy,
    private val pvnRangedAccuracy: PvNRangedAccuracy,
    private val pvpRangedAccuracy: PvPRangedAccuracy,
    private val nvpMultiAccuracy: NvPMultiStyleAccuracy,
    private val nvnMultiAccuracy: NvNMultiStyleAccuracy,
    private val pvnMultiAccuracy: PvNMultiStyleAccuracy,
    private val pvpMultiAccuracy: PvPMultiStyleAccuracy,
) {
    public fun rollMeleeAccuracy(
        player: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getMeleeHitChance(player, target, attackType, attackStyle, blockType, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMeleeHitChance(
        player: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specMultiplier: Double,
    ): Int =
        pvnMeleeAccuracy.getHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specialMultiplier = specMultiplier,
        )

    public fun rollMeleeAccuracy(
        player: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getMeleeHitChance(player, target, attackType, attackStyle, blockType, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMeleeHitChance(
        player: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specMultiplier: Double,
    ): Int =
        pvpMeleeAccuracy.getHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specialMultiplier = specMultiplier,
        )

    public fun rollMeleeAccuracy(
        npc: Npc,
        target: Player,
        attackType: MeleeAttackType?,
        random: GameRandom,
    ): Boolean {
        val hitChance = getMeleeHitChance(npc, target, attackType)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMeleeHitChance(npc: Npc, target: Player, attackType: MeleeAttackType?): Int =
        nvpMeleeAccuracy.getHitChance(npc, target, attackType)

    public fun rollMeleeAccuracy(
        npc: Npc,
        target: Npc,
        attackType: MeleeAttackType?,
        random: GameRandom,
    ): Boolean {
        val hitChance = getMeleeHitChance(npc, target, attackType)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMeleeHitChance(npc: Npc, target: Npc, attackType: MeleeAttackType?): Int =
        nvnMeleeAccuracy.getHitChance(npc, target, attackType)

    public fun rollRangedAccuracy(
        player: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getRangedHitChance(player, target, attackType, attackStyle, blockType, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedHitChance(
        player: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
    ): Int =
        pvnRangedAccuracy.getHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specialMultiplier = specMultiplier,
        )

    public fun rollRangedAccuracy(
        player: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance = getRangedHitChance(player, target, attackType, attackStyle, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedHitChance(
        player: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvpRangedAccuracy.getHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollRangedAccuracy(npc: Npc, target: Player, random: GameRandom): Boolean {
        val hitChance = getRangedHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedHitChance(npc: Npc, target: Player): Int =
        nvpRangedAccuracy.getHitChance(npc, target)

    public fun rollRangedAccuracy(npc: Npc, target: Npc, random: GameRandom): Boolean {
        val hitChance = getRangedHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedHitChance(npc: Npc, target: Npc): Int =
        nvnRangedAccuracy.getHitChance(npc, target)

    public fun rollSpellAccuracy(
        player: Player,
        target: Npc,
        spell: ItemServerType,
        spellbook: Spellbook?,
        usedSunfireRune: Boolean,
        random: GameRandom,
    ): Boolean {
        val hitChance = getSpellHitChance(player, target, spell, spellbook, usedSunfireRune)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getSpellHitChance(
        player: Player,
        target: Npc,
        spell: ItemServerType,
        spellbook: Spellbook?,
        usedSunfireRune: Boolean,
    ): Int =
        pvnMagicAccuracy.getSpellHitChance(
            player = player,
            target = target,
            spell = spell,
            spellbook = spellbook,
            usedSunfireRune = usedSunfireRune,
        )

    public fun rollSpellAccuracy(
        player: Player,
        target: Player,
        spell: ItemServerType,
        spellbook: Spellbook?,
        usedSunfireRune: Boolean,
        random: GameRandom,
    ): Boolean {
        val hitChance = getSpellHitChance(player, target, spell, spellbook, usedSunfireRune)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getSpellHitChance(
        player: Player,
        target: Player,
        spell: ItemServerType,
        spellbook: Spellbook?,
        usedSunfireRune: Boolean,
    ): Int =
        pvpMagicAccuracy.getSpellHitChance(
            player = player,
            target = target,
            spell = spell,
            spellbook = spellbook,
            usedSunfireRune = usedSunfireRune,
        )

    public fun rollStaffAccuracy(
        player: Player,
        target: Npc,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance = getStaffHitChance(player, target, attackStyle, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getStaffHitChance(
        player: Player,
        target: Npc,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvnMagicAccuracy.getStaffHitChance(
            player = player,
            target = target,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollStaffAccuracy(
        player: Player,
        target: Player,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance = getStaffHitChance(player, target, attackStyle, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getStaffHitChance(
        player: Player,
        target: Player,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvpMagicAccuracy.getStaffHitChance(
            player = player,
            target = target,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollMagicAccuracy(npc: Npc, target: Player, random: GameRandom): Boolean {
        val hitChance = getMagicHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicHitChance(npc: Npc, target: Player): Int =
        nvpMagicAccuracy.getHitChance(npc, target)

    public fun rollMagicAccuracy(npc: Npc, target: Npc, random: GameRandom): Boolean {
        val hitChance = getMagicHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicHitChance(npc: Npc, target: Npc): Int =
        nvnMagicAccuracy.getHitChance(npc, target)

    public fun rollMagicalMeleeAccuracy(
        player: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getMagicalMeleeHitChance(player, target, attackType, attackStyle, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicalMeleeHitChance(
        player: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvnMultiAccuracy.getMagicalMeleeHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollMagicalMeleeAccuracy(
        player: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getMagicalMeleeHitChance(player, target, attackType, attackStyle, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicalMeleeHitChance(
        player: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvpMultiAccuracy.getMagicalMeleeHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollMagicalMeleeAccuracy(npc: Npc, target: Player, random: GameRandom): Boolean {
        val hitChance = getMagicalMeleeHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicalMeleeHitChance(npc: Npc, target: Player): Int =
        nvpMultiAccuracy.getMagicalMeleeHitChance(npc, target)

    public fun rollMagicalMeleeAccuracy(npc: Npc, target: Npc, random: GameRandom): Boolean {
        val hitChance = getMagicalMeleeHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicalMeleeHitChance(npc: Npc, target: Npc): Int =
        nvnMultiAccuracy.getMagicalMeleeHitChance(npc, target)

    public fun rollRangedMeleeAccuracy(
        player: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getRangedMeleeHitChance(
                player = player,
                target = target,
                attackType = attackType,
                attackStyle = attackStyle,
                blockType = blockType,
                specMultiplier = specMultiplier,
            )
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedMeleeHitChance(
        player: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
    ): Int =
        pvnMultiAccuracy.getRangedMeleeHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specialMultiplier = specMultiplier,
        )

    public fun rollRangedMeleeAccuracy(
        player: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getRangedMeleeHitChance(
                player = player,
                target = target,
                attackType = attackType,
                attackStyle = attackStyle,
                specMultiplier = specMultiplier,
            )
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedMeleeHitChance(
        player: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvpMultiAccuracy.getRangedMeleeHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollRangedMeleeAccuracy(npc: Npc, target: Player, random: GameRandom): Boolean {
        val hitChance = getRangedMeleeHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedMeleeHitChance(npc: Npc, target: Player): Int =
        nvpMultiAccuracy.getRangedMeleeHitChance(npc, target)

    public fun rollRangedMeleeAccuracy(npc: Npc, target: Npc, random: GameRandom): Boolean {
        val hitChance = getRangedMeleeHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedMeleeHitChance(npc: Npc, target: Npc): Int =
        nvnMultiAccuracy.getRangedMeleeHitChance(npc, target)

    public fun rollRangedMagicAccuracy(
        player: Player,
        target: Npc,
        attackStyle: MagicAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getRangedMagicHitChance(player, target, attackStyle, blockType, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedMagicHitChance(
        player: Player,
        target: Npc,
        attackStyle: MagicAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
    ): Int =
        pvnMultiAccuracy.getRangedMagicHitChance(
            player = player,
            target = target,
            attackStyle = attackStyle,
            blockType = blockType,
            specialMultiplier = specMultiplier,
        )

    public fun rollRangedMagicAccuracy(
        player: Player,
        target: Player,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance = getRangedMagicHitChance(player, target, attackStyle, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedMagicHitChance(
        player: Player,
        target: Player,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvpMultiAccuracy.getRangedMagicHitChance(
            player = player,
            target = target,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollRangedMagicAccuracy(npc: Npc, target: Player, random: GameRandom): Boolean {
        val hitChance = getRangedMagicHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedMagicHitChance(npc: Npc, target: Player): Int =
        nvpMultiAccuracy.getRangedMagicHitChance(npc, target)

    public fun rollRangedMagicAccuracy(npc: Npc, target: Npc, random: GameRandom): Boolean {
        val hitChance = getRangedMagicHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getRangedMagicHitChance(npc: Npc, target: Npc): Int =
        nvnMultiAccuracy.getRangedMagicHitChance(npc, target)

    public fun rollMagicalRangedAccuracy(
        player: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getMagicalRangedHitChance(player, target, attackType, attackStyle, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicalRangedHitChance(
        player: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvnMultiAccuracy.getMagicalRangedHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollMagicalRangedAccuracy(
        player: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
        random: GameRandom,
    ): Boolean {
        val hitChance =
            getMagicalRangedHitChance(player, target, attackType, attackStyle, specMultiplier)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicalRangedHitChance(
        player: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
    ): Int =
        pvpMultiAccuracy.getMagicalRangedHitChance(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
        )

    public fun rollMagicalRangedAccuracy(npc: Npc, target: Player, random: GameRandom): Boolean {
        val hitChance = getMagicalRangedHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicalRangedHitChance(npc: Npc, target: Player): Int =
        nvpMultiAccuracy.getMagicalRangedHitChance(npc, target)

    public fun rollMagicalRangedAccuracy(npc: Npc, target: Npc, random: GameRandom): Boolean {
        val hitChance = getMagicalRangedHitChance(npc, target)
        return isSuccessfulHit(hitChance, random)
    }

    public fun getMagicalRangedHitChance(npc: Npc, target: Npc): Int =
        nvnMultiAccuracy.getMagicalRangedHitChance(npc, target)

    public companion object {
        public fun isSuccessfulHit(hitChance: Int, random: GameRandom): Boolean {
            val randomRoll = random.of(maxExclusive = HIT_CHANCE_SCALE)
            return hitChance > randomRoll
        }
    }
}
