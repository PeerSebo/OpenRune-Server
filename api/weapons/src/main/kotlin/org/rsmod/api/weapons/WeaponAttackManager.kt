package org.rsmod.api.weapons

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.proj.ProjAnim

public class WeaponAttackManager @Inject constructor(private val manager: PlayerAttackManager) {
    /** The combat system arms the default weapon delay; this adjusts exceptional attack timing. */
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

    /**
     * Return true from attack after stopping combat; false still allows one default attack in
     * the already-running combat script.
     */
    public fun stopCombat(access: ProtectedAccess): Unit = manager.stopCombat(access.player)

    public fun playWeaponFx(access: ProtectedAccess, attack: CombatAttack.Melee) {
        manager.playWeaponFx(access.player, attack)
    }

    public fun playWeaponFx(access: ProtectedAccess, attack: CombatAttack.Ranged) {
        manager.playWeaponFx(access.player, attack)
    }

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
        roundMaxHitUp: Boolean = false,
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
            roundMaxHitUp = roundMaxHitUp,
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
        roundUp: Boolean = false,
    ): Int =
        manager.rollMeleeMaxHit(
            source.player,
            target,
            attackType,
            attackStyle,
            multiplier,
            roundUp,
        )

    public fun calculateMeleeMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        multiplier: Double,
        roundUp: Boolean = false,
    ): Int =
        manager.calculateMeleeMaxHit(
            source.player,
            target,
            attackType,
            attackStyle,
            multiplier,
            roundUp,
        )

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

    public fun rollStaffSplash(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Staff,
    ): Boolean = !rollStaffAccuracy(source, target, attack)

    public fun rollStaffAccuracy(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Staff,
    ): Boolean =
        manager.rollStaffAccuracy(
            source = source.player,
            target = target,
            attackStyle = attack.style,
            multiplier = 1.0,
        )

    public fun rollStaffMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        baseMaxHit: Int,
    ): Int =
        manager.rollStaffMaxHit(
            source = source.player,
            target = target,
            baseMaxHit = baseMaxHit,
            multiplier = 1.0,
        )

    public fun calculateStaffMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        baseMaxHit: Int,
    ): Int =
        manager.calculateStaffMaxHit(
            source = source.player,
            target = target,
            baseMaxHit = baseMaxHit,
            multiplier = 1.0,
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

    public fun playMagicHitFx(
        source: ProtectedAccess,
        target: PathingEntity,
        clientDelay: Int,
        castSound: String?,
        soundRadius: Int,
        hitSpot: String?,
        hitSpotHeight: Int,
        hitSound: String?,
    ): Unit =
        manager.playMagicHitFx(
            source = source.player,
            target = target,
            clientDelay = clientDelay,
            castSound = castSound,
            soundRadius = soundRadius,
            hitSpot = hitSpot,
            hitSpotHeight = hitSpotHeight,
            hitSound = hitSound,
        )

    public fun queueSplashHit(
        source: ProtectedAccess,
        target: PathingEntity,
        clientDelay: Int,
        hitDelay: Int = 1 + (clientDelay / 30),
        spell: ItemServerType? = null,
    ): Hit =
        manager.queueSplashHit(
            source = source.player,
            target = target,
            spell = spell,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
        )

    public fun playSplashFx(
        source: ProtectedAccess,
        target: PathingEntity,
        clientDelay: Int,
        castSound: String?,
        soundRadius: Int,
    ): Unit =
        manager.playMagicSplashFx(
            source = source.player,
            target = target,
            clientDelay = clientDelay,
            castSound = castSound,
            soundRadius = soundRadius,
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
    ): Unit = manager.soundArea(source.coords, synth, delay, loops, radius, size)
}
