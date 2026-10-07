package org.rsmod.api.specials

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.specials.weapon.SpecialAttackWeapons
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.proj.ProjAnim

public class SpecialAttackManager
@Inject
constructor(
    private val energy: SpecialAttackEnergy,
    private val weapons: SpecialAttackWeapons,
    private val manager: PlayerAttackManager,
) {
    public fun hasSpecialEnergy(source: ProtectedAccess, energyInHundreds: Int): Boolean {
        return energy.hasSpecialEnergy(source.player, energyInHundreds)
    }

    public fun takeSpecialEnergy(source: ProtectedAccess, energyInHundreds: Int) {
        energy.takeSpecialEnergy(source.player, energyInHundreds)
    }

    public fun getSpecialEnergyRequirement(obj: String): Int? =
        weapons.getSpecialEnergy(obj.asRSCM(RSCMType.OBJ))

    /**
     * The combat system arms the default weapon delay; this adjusts exceptional special-attack
     * timing.
     */
    public fun setNextAttackDelay(access: ProtectedAccess, cycles: Int) {
        manager.setNextAttackDelay(access.player, cycles)
    }

    public fun continueCombat(source: ProtectedAccess, target: PathingEntity) {
        manager.continueCombat(source.player, target)
    }

    public fun continueCombat(source: ProtectedAccess, target: Npc) {
        manager.continueCombat(source.player, target)
    }

    public fun continueCombat(source: ProtectedAccess, target: Player) {
        manager.continueCombat(source.player, target)
    }

    public fun stopCombat(access: ProtectedAccess): Unit = manager.stopCombat(access.player)

    public fun giveCombatXp(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Melee,
        damage: Int,
    ): Unit = manager.giveCombatXp(source.player, target, attack, damage)

    public fun giveCombatXp(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        damage: Int,
    ): Unit = manager.giveCombatXp(source.player, target, attack, damage)

    public fun giveCombatXp(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        damage: Int,
    ): Unit = manager.giveCombatXp(source.player, target, attack, damage)

    public fun giveCombatXp(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Staff,
        damage: Int,
    ): Unit = manager.giveCombatXp(source.player, target, attack, damage)

    public fun rollMeleeDamage(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Melee,
        accuracyMultiplier: Double,
        maxHitMultiplier: Double,
        attackType: MeleeAttackType? = attack.type,
        attackStyle: MeleeAttackStyle? = attack.style,
        blockType: MeleeAttackType? = attack.type,
    ): Int =
        manager.rollMeleeDamage(
            source = source.player,
            target = target,
            attack = attack,
            accuracyMultiplier = accuracyMultiplier,
            maxHitMultiplier = maxHitMultiplier,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
        )

    public fun rollMeleeAccuracy(
        source: ProtectedAccess,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        multiplier: Double,
    ): Boolean =
        manager.rollMeleeAccuracy(
            source = source.player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            multiplier = multiplier,
        )

    public fun rollMeleeMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        multiplier: Double,
    ): Int = manager.rollMeleeMaxHit(source.player, target, attackType, attackStyle, multiplier)

    public fun calculateMeleeMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        multiplier: Double,
    ): Int =
        manager.calculateMeleeMaxHit(source.player, target, attackType, attackStyle, multiplier)

    public fun queueMeleeHit(
        source: ProtectedAccess,
        target: PathingEntity,
        damage: Int,
        delay: Int = 1,
    ): Hit = manager.queueMeleeHit(source.player, target, damage, delay)

    public fun rollRangedDamage(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        accuracyMultiplier: Double = 1.0,
        maxHitMultiplier: Double = 1.0,
        attackType: RangedAttackType? = attack.type,
        attackStyle: RangedAttackStyle? = attack.style,
        blockType: RangedAttackType? = attack.type,
        boltSpecDamage: Int = 0,
    ): Int =
        manager.rollRangedDamage(
            source = source.player,
            target = target,
            attack = attack,
            accuracyMultiplier = accuracyMultiplier,
            maxHitMultiplier = maxHitMultiplier,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            boltSpecDamage = boltSpecDamage,
        )

    public fun rollRangedAccuracy(
        source: ProtectedAccess,
        target: PathingEntity,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        blockType: RangedAttackType?,
        multiplier: Double,
    ): Boolean =
        manager.rollRangedAccuracy(
            source = source.player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            multiplier = multiplier,
        )

    public fun rollRangedMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        multiplier: Double,
        boltSpecDamage: Int,
    ): Int =
        manager.rollRangedMaxHit(
            source = source.player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            multiplier = multiplier,
            boltSpecDamage = boltSpecDamage,
        )

    public fun calculateRangedMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        multiplier: Double,
        boltSpecDamage: Int,
    ): Int =
        manager.calculateRangedMaxHit(
            source = source.player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            multiplier = multiplier,
            boltSpecDamage = boltSpecDamage,
        )

    public fun queueRangedHit(
        source: ProtectedAccess,
        target: PathingEntity,
        ammo: ItemServerType?,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int = 1 + (clientDelay / 30),
    ): Hit =
        manager.queueRangedHit(
            source = source.player,
            target = target,
            ammo = ammo,
            damage = damage,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
        )

    public fun queueRangedDamage(
        source: ProtectedAccess,
        target: PathingEntity,
        ammo: ItemServerType?,
        damage: Int,
        hitDelay: Int,
    ): Hit =
        manager.queueRangedDamage(
            source = source.player,
            target = target,
            ammo = ammo,
            damage = damage,
            hitDelay = hitDelay,
        )

    public fun rollStaffAccuracy(
        source: ProtectedAccess,
        target: PathingEntity,
        attackStyle: MagicAttackStyle?,
        multiplier: Double,
    ): Boolean =
        manager.rollStaffAccuracy(
            source = source.player,
            target = target,
            attackStyle = attackStyle,
            multiplier = multiplier,
        )

    public fun rollStaffMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        baseMaxHit: Int,
        multiplier: Double,
    ): Int =
        manager.rollStaffMaxHit(
            source = source.player,
            target = target,
            baseMaxHit = baseMaxHit,
            multiplier = multiplier,
        )

    public fun calculateStaffMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        baseMaxHit: Int,
        multiplier: Double,
    ): Int =
        manager.calculateStaffMaxHit(
            source = source.player,
            target = target,
            baseMaxHit = baseMaxHit,
            multiplier = multiplier,
        )

    public fun queueMagicHit(
        source: ProtectedAccess,
        target: PathingEntity,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int = 1 + (clientDelay / 30),
        spell: ItemServerType? = null,
    ): Hit =
        manager.queueMagicHit(
            source = source.player,
            target = target,
            spell = spell,
            damage = damage,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
        )

    public fun spawnProjectile(
        source: ProtectedAccess,
        target: PathingEntity,
        spotanim: String,
        projanim: String,
    ): ProjAnim = manager.spawnProjectile(source.player, target, spotanim, projanim)

    public fun soundArea(
        source: PathingEntity,
        synth: String,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
        size: Int = 0,
    ): Unit =
        manager.soundArea(
            source.coords,
            synth = synth,
            delay = delay,
            loops = loops,
            radius = radius,
            size = size,
        )
}
