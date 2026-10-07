package org.rsmod.api.spells.attack

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.consumedRune
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.hit.Hit
import org.rsmod.game.proj.ProjAnim

public class SpellAttackManager
@Inject
constructor(private val manager: PlayerAttackManager, private val runes: MagicRuneManager) {
    private val ProtectedAccess.autocastEnabled by boolVarBit("varbit.autocast_set")

    /**
     * Failure stops combat; success grants cast XP only. Damage XP remains the caller's
     * responsibility.
     */
    public fun attemptCast(
        source: ProtectedAccess,
        attack: CombatAttack.Spell,
    ): MagicRuneManager.CastResult {
        val castResult = runes.attemptCast(source.player, attack.spell)

        if (castResult.isFailure()) {
            stopCombat(source)
            return castResult
        }

        giveCastXp(source, attack)
        return castResult
    }

    public fun giveCastXp(access: ProtectedAccess, attack: CombatAttack.Spell) {
        access.statAdvance("stat.magic", attack.spell.castXp)
    }

    /**
     * Continued combat uses the selected autocast spell, which can differ from the spell just
     * cast.
     */
    public fun continueCombatIfAutocast(source: ProtectedAccess, target: PathingEntity) {
        if (!source.autocastEnabled) {
            manager.stopCombat(source.player)
            return
        }
        manager.continueCombat(source.player, target)
    }

    public fun stopCombat(access: ProtectedAccess) {
        manager.stopCombat(access.player)
    }

    public fun giveCombatXp(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        damage: Int,
    ) {
        manager.giveCombatXp(source.player, target, attack, damage)
    }

    public fun rollSplash(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        castResult: MagicRuneManager.CastResult,
    ): Boolean = !rollSpellAccuracy(source, target, attack, castResult)

    public fun rollSpellAccuracy(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        castResult: MagicRuneManager.CastResult,
    ): Boolean =
        manager.rollSpellAccuracy(
            source = source.player,
            target = target,
            spell = attack.spell.obj,
            spellbook = attack.spell.spellbook,
            sunfireRune = castResult.consumedRune() && castResult.usedSunfire,
        )

    public fun rollMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        castResult: MagicRuneManager.CastResult,
        baseMaxHit: Int,
        attackRate: Int = constants.combat_spell_attackrate,
    ): Int =
        manager.rollSpellMaxHit(
            source = source.player,
            target = target,
            spell = attack.spell.obj,
            spellbook = attack.spell.spellbook,
            baseMaxHit = baseMaxHit,
            attackRate = attackRate,
            sunfireRune = castResult.consumedRune() && castResult.usedSunfire,
        )

    public fun calculateMaxHit(
        source: ProtectedAccess,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        cast: MagicRuneManager.CastResult,
        baseMaxHit: Int,
        attackRate: Int = constants.combat_spell_attackrate,
    ): IntRange =
        manager.calculateSpellMaxHit(
            source = source.player,
            target = target,
            spell = attack.spell.obj,
            spellbook = attack.spell.spellbook,
            baseMaxHit = baseMaxHit,
            attackRate = attackRate,
            sunfireRune = cast.consumedRune() && cast.usedSunfire,
        )

    public fun queueMagicHit(
        source: ProtectedAccess,
        target: PathingEntity,
        spell: ItemServerType,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int = 1 + (clientDelay / 30),
    ): Hit =
        manager.queueMagicHit(
            source = source.player,
            target = target,
            spell = spell,
            damage = damage,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
            retaliationDelay = hitDelay,
        )

    public fun queueSplashHit(
        source: ProtectedAccess,
        target: PathingEntity,
        spell: ItemServerType,
        clientDelay: Int,
        hitDelay: Int = 1 + (clientDelay / 30),
    ): Hit =
        manager.queueSplashHit(
            source = source.player,
            target = target,
            spell = spell,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
        )

    public fun playHitFx(
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
}
