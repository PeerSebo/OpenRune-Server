package org.rsmod.api.combat.manager

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.SequenceServerType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.types.aconverted.SynthType
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.fx.MeleeAnimationAndSound
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.commons.npc.combatPlayDefendAnim
import org.rsmod.api.combat.commons.npc.combatPlayDefendSpot
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.combat.commons.npc.resolveCombatXpMultiplier
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.combat.commons.player.combatPlayDefendSpot
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.commons.player.resolveCombatXpMultiplier
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.PvPPlayerHitHook
import org.rsmod.api.npc.hit.isStyleImmuneTo
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.NpcTInteractions
import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.api.player.interact.PlayerTInteractions
import org.rsmod.api.player.ironman.shouldBlockNpcCombatXp
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull
import org.rsmod.map.CoordGrid

private const val ATTACK_SOUND_RADIUS: Int = 10

public class PlayerAttackManager
@Inject
constructor(
    private val random: GameRandom,
    private val eventBus: EventBus,
    private val worldRepo: WorldRepository,
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val npcHitModifier: NpcHitModifier,
    private val playerHitModifier: PlayerHitModifier,
    private val npcInteractions: NpcInteractions,
    private val npcTInteractions: NpcTInteractions,
    private val playerInteractions: PlayerInteractions,
    private val playerTInteractions: PlayerTInteractions,
    private val pvpPlayerHitHooks: Set<PvPPlayerHitHook>,
) {
    public fun isAttackDelayed(player: Player): Boolean {
        return player.actionDelay > player.currentMapClock
    }

    public fun setNextAttackDelay(player: Player, cycles: Int) {
        player.actionDelay = player.currentMapClock + cycles
    }

    /**
     * Successful attacks must explicitly renew the interaction; otherwise combat stops after
     * this attack.
     */
    public fun continueCombat(source: Player, target: PathingEntity) {
        when (target) {
            is Npc -> continueCombat(source, target)
            is Player -> continueCombat(source, target)
        }
    }

    public fun continueCombat(source: Player, target: Npc) {
        npcInteractions.interact(source, target, InteractionOp.Op2)
    }

    public fun continueCombat(source: Player, target: Player) {
        playerInteractions.interact(source, target, InteractionOp.Op2)
    }

    public fun continueCombat(source: Player, target: PathingEntity, spell: MagicSpell) {
        when (target) {
            is Npc -> continueCombat(source, target, spell)
            is Player -> continueCombat(source, target, spell)
        }
    }

    public fun continueCombat(source: Player, target: Npc, spell: MagicSpell) {
        npcTInteractions.interact(source, target, spell.component, comsub = -1, null)
    }

    public fun continueCombat(source: Player, target: Player, spell: MagicSpell) {
        playerTInteractions.interact(source, target, spell.component, comsub = -1, null)
    }

    public fun stopCombat(player: Player) {
        player.clearPendingAction(eventBus)
    }

    public fun playWeaponFx(player: Player, attack: CombatAttack.Melee) {
        val weapon = getOrNull(attack.weapon)

        val fx = MeleeAnimationAndSound.from(attack.stance)
        val (animParam, soundParam, defaultAnim, defaultSound) = fx

        val attackAnim =
            weapon?.paramOrNull(animParam) ?: SequenceServerType(defaultAnim.asRSCM(RSCMType.SEQ))
        val attackSound =
            weapon?.paramOrNull(soundParam) ?: SynthType(defaultSound.asRSCM(RSCMType.SYNTH))

        player.anim(RSCM.getReverseMapping(RSCMType.SEQ, attackAnim.id), priority = 6)
        playAttackSound(player, attackSound)
    }

    public fun playWeaponFx(player: Player, attack: CombatAttack.Ranged): Boolean {
        val weapon = getInvObj(attack.weapon)
        val attackAnim = weapon.paramOrNull(params.attack_anim_stance1) ?: return false
        player.anim(RSCM.getReverseMapping(RSCMType.SEQ, attackAnim.id), priority = 6)
        val attackSound = weapon.paramOrNull(params.attack_sound_stance1)
        attackSound?.let { playAttackSound(player, it) }
        return true
    }

    /** Exclude [player] from the area sound because they already receive the local synth. */
    private fun playAttackSound(player: Player, synth: SynthType) {
        player.soundSynth(synth)
        worldRepo.soundAreaExcept(
            source = player,
            synth = synth,
            except = player,
            radius = WorldRepository.COMBAT_SOUND_RADIUS,
        )
    }

    public fun giveCombatXp(
        player: Player,
        target: PathingEntity,
        attack: CombatAttack.Melee,
        damage: Int,
    ): Unit =
        when (target) {
            is Npc -> giveCombatXp(player, target, attack, damage)
            is Player -> giveCombatXp(player, target, attack, damage)
        }

    private fun giveCombatXp(player: Player, target: Npc, attack: CombatAttack.Melee, damage: Int) {
        if (player.shouldBlockNpcCombatXp(target) || target.isStyleImmuneTo(HitType.Melee)) {
            return
        }
        val cappedDamage = min(damage, target.hitpoints)
        val multiplier = target.resolveCombatXpMultiplier()
        giveCombatXp(player, attack, cappedDamage, multiplier)
    }

    private fun giveCombatXp(
        player: Player,
        target: Player,
        attack: CombatAttack.Melee,
        damage: Int,
    ) {
        val cappedDamage = min(damage, target.hitpoints)
        val multiplier = target.resolveCombatXpMultiplier()
        giveCombatXp(player, attack, cappedDamage, multiplier)
    }

    private fun giveCombatXp(
        player: Player,
        attack: CombatAttack.Melee,
        damage: Int,
        multiplier: Double = 1.0,
    ) {
        when (attack.style) {
            MeleeAttackStyle.Controlled -> {
                statAdvance(player, "stat.attack", damage * 1.33, multiplier)
                statAdvance(player, "stat.strength", damage * 1.33, multiplier)
                statAdvance(player, "stat.defence", damage * 1.33, multiplier)
            }
            MeleeAttackStyle.Accurate -> {
                statAdvance(player, "stat.attack", damage * 4.0, multiplier)
            }
            MeleeAttackStyle.Aggressive -> {
                statAdvance(player, "stat.strength", damage * 4.0, multiplier)
            }
            MeleeAttackStyle.Defensive -> {
                statAdvance(player, "stat.defence", damage * 4.0, multiplier)
            }
            null -> {
            }
        }
        statAdvance(player, "stat.hitpoints", damage * 1.33, multiplier)
    }

    public fun giveCombatXp(
        player: Player,
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        damage: Int,
    ): Unit =
        when (target) {
            is Npc -> giveCombatXp(player, target, attack, damage)
            is Player -> giveCombatXp(player, target, attack, damage)
        }

    private fun giveCombatXp(
        player: Player,
        target: Npc,
        attack: CombatAttack.Ranged,
        damage: Int,
    ) {
        if (player.shouldBlockNpcCombatXp(target) || target.isStyleImmuneTo(HitType.Ranged)) {
            return
        }
        val cappedDamage = min(damage, target.hitpoints)
        val multiplier = target.resolveCombatXpMultiplier()
        giveCombatXp(player, attack, cappedDamage, multiplier)
    }

    private fun giveCombatXp(
        player: Player,
        target: Player,
        attack: CombatAttack.Ranged,
        damage: Int,
    ) {
        val cappedDamage = min(damage, target.hitpoints)
        val multiplier = target.resolveCombatXpMultiplier()
        giveCombatXp(player, attack, cappedDamage, multiplier)
    }

    private fun giveCombatXp(
        player: Player,
        attack: CombatAttack.Ranged,
        damage: Int,
        multiplier: Double,
    ) {
        when (attack.style) {
            RangedAttackStyle.Accurate -> {
                statAdvance(player, "stat.ranged", damage * 4.0, multiplier)
            }
            RangedAttackStyle.Rapid -> {
                statAdvance(player, "stat.ranged", damage * 4.0, multiplier)
            }
            RangedAttackStyle.Longrange -> {
                statAdvance(player, "stat.ranged", damage * 2.0, multiplier)
                statAdvance(player, "stat.defence", damage * 2.0, multiplier)
            }
            null -> {
            }
        }
        statAdvance(player, "stat.hitpoints", damage * 1.33, multiplier)
    }

    public fun giveCombatXp(
        player: Player,
        target: PathingEntity,
        attack: CombatAttack.Spell,
        damage: Int,
    ): Unit =
        when (target) {
            is Npc -> giveCombatXp(player, target, attack, damage)
            is Player -> giveCombatXp(player, target, attack, damage)
        }

    private fun giveCombatXp(player: Player, target: Npc, attack: CombatAttack.Spell, damage: Int) {
        if (player.shouldBlockNpcCombatXp(target) || target.isStyleImmuneTo(HitType.Magic)) {
            return
        }
        val cappedDamage = min(damage, target.hitpoints)
        val multiplier = target.resolveCombatXpMultiplier()
        giveCombatXp(player, attack, cappedDamage, multiplier)
    }

    private fun giveCombatXp(
        player: Player,
        target: Player,
        attack: CombatAttack.Spell,
        damage: Int,
    ) {
        val cappedDamage = min(damage, target.hitpoints)
        val multiplier = target.resolveCombatXpMultiplier()
        giveCombatXp(player, attack, cappedDamage, multiplier)
    }

    private fun giveCombatXp(
        player: Player,
        attack: CombatAttack.Spell,
        damage: Int,
        multiplier: Double,
    ) {
        if (attack.defensive) {
            statAdvance(player, "stat.magic", damage * 1.33, multiplier)
            statAdvance(player, "stat.defence", damage.toDouble(), multiplier)
        } else {
            statAdvance(player, "stat.magic", damage * 2.0, multiplier)
        }
        statAdvance(player, "stat.hitpoints", damage * 1.33, multiplier)
    }

    public fun giveCombatXp(
        player: Player,
        target: PathingEntity,
        attack: CombatAttack.Staff,
        damage: Int,
    ): Unit =
        when (target) {
            is Npc -> giveCombatXp(player, target, attack, damage)
            is Player -> giveCombatXp(player, target, attack, damage)
        }

    @Suppress("unused")
    private fun giveCombatXp(player: Player, target: Npc, attack: CombatAttack.Staff, damage: Int) {
        if (player.shouldBlockNpcCombatXp(target) || target.isStyleImmuneTo(HitType.Magic)) {
            return
        }
        val cappedDamage = min(damage, target.hitpoints)
        val multiplier = target.resolveCombatXpMultiplier()
        giveStaffCombatXp(player, cappedDamage, multiplier)
    }

    @Suppress("unused")
    private fun giveCombatXp(
        player: Player,
        target: Player,
        attack: CombatAttack.Staff,
        damage: Int,
    ) {
        val cappedDamage = min(damage, target.hitpoints)
        val multiplier = target.resolveCombatXpMultiplier()
        giveStaffCombatXp(player, cappedDamage, multiplier)
    }

    private fun giveStaffCombatXp(player: Player, damage: Int, multiplier: Double) {
        statAdvance(player, "stat.magic", damage * 2.0, multiplier)
        statAdvance(player, "stat.hitpoints", damage * 1.33, multiplier)
    }

    private fun statAdvance(player: Player, stat: String, baseXp: Double, multiplier: Double) {
        player.statAdvance(stat, baseXp * multiplier)
    }

    public fun rollMeleeDamage(
        source: Player,
        target: PathingEntity,
        attack: CombatAttack.Melee,
        accuracyMultiplier: Double = 1.0,
        maxHitMultiplier: Double = 1.0,
        attackType: MeleeAttackType? = attack.type,
        attackStyle: MeleeAttackStyle? = attack.style,
        blockType: MeleeAttackType? = attack.type,
        roundMaxHitUp: Boolean = false,
    ): Int {
        val successfulAccuracyRoll =
            rollMeleeAccuracy(
                source = source,
                target = target,
                multiplier = accuracyMultiplier,
                attackType = attackType,
                attackStyle = attackStyle,
                blockType = blockType,
            )
        if (!successfulAccuracyRoll) {
            return 0
        }
        return rollMeleeMaxHit(
            source,
            target,
            attackType,
            attackStyle,
            maxHitMultiplier,
            roundMaxHitUp,
        )
    }

    public fun rollMeleeAccuracy(
        source: Player,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        multiplier: Double,
    ): Boolean {
        if (source.adminMaxHit) {
            return true
        }
        return when (target) {
            is Npc -> {
                rollMeleeAccuracy(source, target, attackType, attackStyle, blockType, multiplier)
            }
            is Player -> {
                rollMeleeAccuracy(source, target, attackType, attackStyle, blockType, multiplier)
            }
        }
    }

    private fun rollMeleeAccuracy(
        source: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollMeleeAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specMultiplier = specMultiplier,
            random = random,
        )

    private fun rollMeleeAccuracy(
        source: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollMeleeAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specMultiplier = specMultiplier,
            random = random,
        )

    public fun rollMeleeMaxHit(
        source: Player,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        multiplier: Double,
        roundUp: Boolean = false,
    ): Int {
        val maxHit =
            calculateMeleeMaxHit(source, target, attackType, attackStyle, multiplier, roundUp)
        if (source.adminMaxHit) {
            return maxHit
        }
        return random.of(1..maxHit)
    }

    public fun calculateMeleeMaxHit(
        source: Player,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        multiplier: Double,
        roundUp: Boolean = false,
    ): Int {
        return when (target) {
            is Npc ->
                calculateMeleeMaxHit(source, target, attackType, attackStyle, multiplier, roundUp)
            is Player ->
                calculateMeleeMaxHit(source, target, attackType, attackStyle, multiplier, roundUp)
        }
    }

    private fun calculateMeleeMaxHit(
        source: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
        roundUp: Boolean,
    ): Int =
        maxHits.getMeleeMaxHit(source, target, attackType, attackStyle, specMultiplier, roundUp)

    private fun calculateMeleeMaxHit(
        source: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
        roundUp: Boolean,
    ): Int =
        maxHits.getMeleeMaxHit(source, target, attackType, attackStyle, specMultiplier, roundUp)

    /**
     * Awards hero points, but callers must grant combat XP separately. Impact processing can
     * still modify the returned Hit.
     */
    public fun queueMeleeHit(
        source: Player,
        target: PathingEntity,
        damage: Int,
        delay: Int = 1,
    ): Hit =
        when (target) {
            is Npc -> queueMeleeHit(source, target, damage, delay)
            is Player -> queueMeleeHit(source, target, damage, delay)
        }

    private fun queueMeleeHit(source: Player, target: Npc, damage: Int, delay: Int): Hit {
        // Queue retaliation before the hit to preserve the death queue ordering.
        target.queueCombatRetaliate(source)

        val hit = target.queueHit(source, delay, HitType.Melee, damage, npcHitModifier)
        target.combatPlayDefendAnim()
        return hit
    }

    private fun queueMeleeHit(source: Player, target: Player, damage: Int, delay: Int): Hit {
        // Queue retaliation before the hit to preserve the death queue ordering.
        target.queueCombatRetaliate(source)

        val hit = target.queueHit(source, delay, HitType.Melee, damage, playerHitModifier)
        notifyPlayerHit(source, target)
        target.combatPlayDefendAnim()
        return hit
    }

    public fun rollRangedDamage(
        source: Player,
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        accuracyMultiplier: Double = 1.0,
        maxHitMultiplier: Double = 1.0,
        attackType: RangedAttackType? = attack.type,
        attackStyle: RangedAttackStyle? = attack.style,
        blockType: RangedAttackType? = attack.type,
        boltSpecDamage: Int = 0,
    ): Int {
        val successfulAccuracyRoll =
            rollRangedAccuracy(
                source = source,
                target = target,
                multiplier = accuracyMultiplier,
                attackType = attackType,
                attackStyle = attackStyle,
                blockType = blockType,
            )
        if (!successfulAccuracyRoll) {
            return 0
        }
        return rollRangedMaxHit(
            source = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            multiplier = maxHitMultiplier,
            boltSpecDamage = boltSpecDamage,
        )
    }

    public fun rollRangedAccuracy(
        source: Player,
        target: PathingEntity,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        blockType: RangedAttackType?,
        multiplier: Double,
    ): Boolean {
        if (source.adminMaxHit) {
            return true
        }
        return when (target) {
            is Npc -> {
                rollRangedAccuracy(source, target, attackType, attackStyle, blockType, multiplier)
            }
            is Player -> rollRangedAccuracy(source, target, attackType, attackStyle, multiplier)
        }
    }

    private fun rollRangedAccuracy(
        source: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollRangedAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specMultiplier = specMultiplier,
            random = random,
        )

    private fun rollRangedAccuracy(
        source: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollRangedAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            random = random,
        )

    public fun rollRangedMaxHit(
        source: Player,
        target: PathingEntity,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        multiplier: Double,
        boltSpecDamage: Int,
    ): Int {
        val maxHit =
            calculateRangedMaxHit(
                source = source,
                target = target,
                attackType = attackType,
                attackStyle = attackStyle,
                multiplier = multiplier,
                boltSpecDamage = boltSpecDamage,
            )
        if (source.adminMaxHit) {
            return maxHit
        }
        return random.of(1..maxHit)
    }

    public fun calculateRangedMaxHit(
        source: Player,
        target: PathingEntity,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        multiplier: Double,
        boltSpecDamage: Int,
    ): Int {
        return when (target) {
            is Npc ->
                calculateRangedMaxHit(
                    source = source,
                    target = target,
                    attackType = attackType,
                    attackStyle = attackStyle,
                    specMultiplier = multiplier,
                    boltSpecDamage = boltSpecDamage,
                )
            is Player ->
                calculateRangedMaxHit(
                    source = source,
                    target = target,
                    attackType = attackType,
                    attackStyle = attackStyle,
                    specMultiplier = multiplier,
                    boltSpecDamage = boltSpecDamage,
                )
        }
    }

    private fun calculateRangedMaxHit(
        source: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
        boltSpecDamage: Int,
    ): Int =
        maxHits.getRangedMaxHit(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            boltSpecDamage = boltSpecDamage,
        )

    private fun calculateRangedMaxHit(
        source: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
        boltSpecDamage: Int,
    ): Int =
        maxHits.getRangedMaxHit(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            boltSpecDamage = boltSpecDamage,
        )

    /**
     * Awards hero points, not combat XP. Use null ammo for thrown weapons; clientDelay is in 20
     * ms cycles, hitDelay in server cycles. Impact processing can still change damage.
     */
    public fun queueRangedHit(
        source: Player,
        target: PathingEntity,
        ammo: ItemServerType?,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int,
    ): Hit =
        when (target) {
            is Npc -> queueRangedHit(source, target, ammo, damage, clientDelay, hitDelay)
            is Player -> queueRangedHit(source, target, ammo, damage, clientDelay, hitDelay)
        }

    private fun queueRangedHit(
        source: Player,
        target: Npc,
        ammo: ItemServerType?,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int,
    ): Hit {
        // Queue retaliation before the hit to preserve the death queue ordering.
        target.queueCombatRetaliate(source, hitDelay)

        val hit =
            target.queueHit(
                source = source,
                delay = hitDelay,
                type = HitType.Ranged,
                damage = damage,
                modifier = npcHitModifier,
                sourceSecondary = ammo,
            )
        target.combatPlayDefendAnim(clientDelay)
        target.combatPlayDefendSpot(ammo, clientDelay)
        return hit
    }

    private fun queueRangedHit(
        source: Player,
        target: Player,
        ammo: ItemServerType?,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int,
    ): Hit {
        // Queue retaliation before the hit to preserve the death queue ordering.
        target.queueCombatRetaliate(source, hitDelay)

        val hit =
            target.queueHit(
                source = source,
                delay = hitDelay,
                type = HitType.Ranged,
                damage = damage,
                modifier = playerHitModifier,
                sourceSecondary = ammo,
            )
        notifyPlayerHit(source, target)
        target.combatPlayDefendAnim(clientDelay)
        target.combatPlayDefendSpot(ammo, clientDelay)
        return hit
    }

    private fun notifyPlayerHit(source: Player, target: Player) {
        for (hook in pvpPlayerHitHooks) {
            hook.onPlayerHit(source, target)
        }
    }

    /**
     * Damage only: no defend animations, impact effects or retaliation (e.g. Dark bow secondary hits).
     * Still awards hero points; use [queueRangedHit] for full hit effects.
     */
    public fun queueRangedDamage(
        source: Player,
        target: PathingEntity,
        ammo: ItemServerType?,
        damage: Int,
        hitDelay: Int,
    ): Hit =
        when (target) {
            is Npc -> queueRangedDamage(source, target, ammo, damage, hitDelay)
            is Player -> queueRangedDamage(source, target, ammo, damage, hitDelay)
        }

    private fun queueRangedDamage(
        source: Player,
        target: Player,
        ammo: ItemServerType?,
        damage: Int,
        hitDelay: Int,
    ): Hit {
        val hit =
            target.queueHit(
                source = source,
                delay = hitDelay,
                type = HitType.Ranged,
                damage = damage,
                modifier = playerHitModifier,
                sourceSecondary = ammo,
            )
        return hit
    }

    private fun queueRangedDamage(
        source: Player,
        target: Npc,
        ammo: ItemServerType?,
        damage: Int,
        hitDelay: Int,
    ): Hit {
        val hit =
            target.queueHit(
                source = source,
                delay = hitDelay,
                type = HitType.Ranged,
                damage = damage,
                modifier = npcHitModifier,
                sourceSecondary = ammo,
            )
        return hit
    }

    public fun rollSpellAccuracy(
        source: Player,
        target: PathingEntity,
        spell: ItemServerType,
        spellbook: Spellbook?,
        sunfireRune: Boolean,
    ): Boolean {
        if (source.adminMaxHit) {
            return true
        }
        return when (target) {
            is Npc -> rollSpellAccuracy(source, target, spell, spellbook, sunfireRune)
            is Player -> rollSpellAccuracy(source, target, spell, spellbook, sunfireRune)
        }
    }

    private fun rollSpellAccuracy(
        source: Player,
        target: Npc,
        spell: ItemServerType,
        spellbook: Spellbook?,
        sunfireRune: Boolean,
    ): Boolean =
        accuracy.rollSpellAccuracy(
            player = source,
            target = target,
            spell = spell,
            spellbook = spellbook,
            usedSunfireRune = sunfireRune,
            random = random,
        )

    private fun rollSpellAccuracy(
        source: Player,
        target: Player,
        spell: ItemServerType,
        spellbook: Spellbook?,
        sunfireRune: Boolean,
    ): Boolean =
        accuracy.rollSpellAccuracy(
            player = source,
            target = target,
            spell = spell,
            spellbook = spellbook,
            usedSunfireRune = sunfireRune,
            random = random,
        )

    public fun rollSpellMaxHit(
        source: Player,
        target: PathingEntity,
        spell: ItemServerType,
        spellbook: Spellbook?,
        baseMaxHit: Int,
        attackRate: Int,
        sunfireRune: Boolean,
    ): Int {
        val hitRange =
            calculateSpellMaxHit(
                source = source,
                target = target,
                spell = spell,
                spellbook = spellbook,
                baseMaxHit = baseMaxHit,
                attackRate = attackRate,
                sunfireRune = sunfireRune,
            )
        if (source.adminMaxHit) {
            return hitRange.last
        }
        return random.of(hitRange)
    }

    public fun calculateSpellMaxHit(
        source: Player,
        target: PathingEntity,
        spell: ItemServerType,
        spellbook: Spellbook?,
        baseMaxHit: Int,
        attackRate: Int,
        sunfireRune: Boolean,
    ): IntRange =
        when (target) {
            is Npc ->
                calculateSpellMaxHit(
                    source = source,
                    target = target,
                    spell = spell,
                    spellbook = spellbook,
                    baseMaxHit = baseMaxHit,
                    attackRate = attackRate,
                    sunfireRune = sunfireRune,
                )
            is Player ->
                calculateSpellMaxHit(
                    source = source,
                    target = target,
                    spell = spell,
                    spellbook = spellbook,
                    baseMaxHit = baseMaxHit,
                    sunfireRune = sunfireRune,
                )
        }

    private fun calculateSpellMaxHit(
        source: Player,
        target: Npc,
        spell: ItemServerType,
        spellbook: Spellbook?,
        baseMaxHit: Int,
        attackRate: Int,
        sunfireRune: Boolean,
    ): IntRange =
        maxHits.getSpellMaxHitRange(
            player = source,
            target = target,
            spell = spell,
            spellbook = spellbook,
            baseMaxHit = baseMaxHit,
            attackRate = attackRate,
            usedSunfireRune = sunfireRune,
        )

    private fun calculateSpellMaxHit(
        source: Player,
        target: Player,
        spell: ItemServerType,
        spellbook: Spellbook?,
        baseMaxHit: Int,
        sunfireRune: Boolean,
    ): IntRange =
        maxHits.getSpellMaxHitRange(
            player = source,
            target = target,
            spell = spell,
            spellbook = spellbook,
            baseMaxHit = baseMaxHit,
            usedSunfireRune = sunfireRune,
        )

    public fun rollStaffAccuracy(
        source: Player,
        target: PathingEntity,
        attackStyle: MagicAttackStyle?,
        multiplier: Double,
    ): Boolean {
        return when (target) {
            is Npc -> rollStaffAccuracy(source, target, attackStyle, multiplier)
            is Player -> rollStaffAccuracy(source, target, attackStyle, multiplier)
        }
    }

    private fun rollStaffAccuracy(
        source: Player,
        target: Npc,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollStaffAccuracy(
            player = source,
            target = target,
            attackStyle,
            specMultiplier,
            random,
        )

    private fun rollStaffAccuracy(
        source: Player,
        target: Player,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollStaffAccuracy(
            player = source,
            target = target,
            attackStyle,
            specMultiplier,
            random,
        )

    public fun rollStaffMaxHit(
        source: Player,
        target: PathingEntity,
        baseMaxHit: Int,
        multiplier: Double,
    ): Int {
        val maxHit = calculateStaffMaxHit(source, target, baseMaxHit, multiplier)
        if (source.adminMaxHit) {
            return maxHit
        }
        return random.of(1..maxHit)
    }

    public fun calculateStaffMaxHit(
        source: Player,
        target: PathingEntity,
        baseMaxHit: Int,
        multiplier: Double,
    ): Int {
        return when (target) {
            is Npc -> calculateSpellMaxHit(source, target, baseMaxHit, multiplier)
            is Player -> calculateSpellMaxHit(source, target, baseMaxHit, multiplier)
        }
    }

    private fun calculateSpellMaxHit(
        source: Player,
        target: Npc,
        baseMaxHit: Int,
        specMultiplier: Double,
    ): Int =
        maxHits.getStaffMaxHit(
            player = source,
            target = target,
            baseMaxHit = baseMaxHit,
            specialMultiplier = specMultiplier,
        )

    private fun calculateSpellMaxHit(
        source: Player,
        target: Player,
        baseMaxHit: Int,
        specMultiplier: Double,
    ): Int =
        maxHits.getStaffMaxHit(
            player = source,
            target = target,
            baseMaxHit = baseMaxHit,
            specialMultiplier = specMultiplier,
        )

    /**
     * Awards hero points, not combat XP. Callers supply magic sounds/spotanims separately.
     * clientDelay uses 20 ms cycles; hitDelay and retaliationDelay use server cycles. Impact
     * processing can still change damage.
     */
    public fun queueMagicHit(
        source: Player,
        target: PathingEntity,
        spell: ItemServerType?,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int,
        retaliationDelay: Int = hitDelay,
    ): Hit =
        when (target) {
            is Npc ->
                queueMagicHit(
                    source = source,
                    target = target,
                    spell = spell,
                    damage = damage,
                    clientDelay = clientDelay,
                    hitDelay = hitDelay,
                    retaliationDelay = retaliationDelay,
                )
            is Player ->
                queueMagicHit(
                    source = source,
                    target = target,
                    spell = spell,
                    damage = damage,
                    clientDelay = clientDelay,
                    hitDelay = hitDelay,
                    retaliationDelay = retaliationDelay,
                )
        }

    private fun queueMagicHit(
        source: Player,
        target: Npc,
        spell: ItemServerType?,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int,
        retaliationDelay: Int,
    ): Hit {
        // Queue retaliation before the hit to preserve the death queue ordering.
        target.queueCombatRetaliate(source, retaliationDelay)

        val hit =
            target.queueHit(
                source = source,
                delay = hitDelay,
                type = HitType.Magic,
                damage = damage,
                modifier = npcHitModifier,
                sourceSecondary = spell,
            )
        // Targets never play their defend animation for a spell, only for melee and ranged.
        return hit
    }

    private fun queueMagicHit(
        source: Player,
        target: Player,
        spell: ItemServerType?,
        damage: Int,
        clientDelay: Int,
        hitDelay: Int,
        retaliationDelay: Int,
    ): Hit {
        // Queue retaliation before the hit to preserve the death queue ordering.
        target.queueCombatRetaliate(source, retaliationDelay)

        val hit =
            target.queueHit(
                source = source,
                delay = hitDelay,
                type = HitType.Magic,
                damage = damage,
                modifier = playerHitModifier,
                sourceSecondary = spell,
            )
        notifyPlayerHit(source, target)
        // Targets never play their defend animation for a spell, only for melee and ranged.
        return hit
    }

    /**
     * Splashes retaliate after one server cycle. Call [playMagicSplashFx] separately for effects.
     */
    public fun queueSplashHit(
        source: Player,
        target: PathingEntity,
        spell: ItemServerType?,
        clientDelay: Int,
        hitDelay: Int,
    ): Hit =
        queueMagicHit(
            source = source,
            target = target,
            spell = spell,
            damage = 0,
            clientDelay = clientDelay,
            hitDelay = hitDelay,
            retaliationDelay = 1,
        )

    public fun playMagicHitFx(
        source: Player,
        target: PathingEntity,
        clientDelay: Int,
        castSound: String?,
        soundRadius: Int,
        hitSpot: String?,
        hitSpotHeight: Int,
        hitSound: String?,
    ): Unit =
        when (target) {
            is Npc ->
                playMagicHitFx(
                    source = source,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = castSound,
                    hitSpot = hitSpot,
                    hitSpotHeight = hitSpotHeight,
                    hitSound = hitSound,
                )
            is Player ->
                playMagicHitFx(
                    source = source,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = castSound,
                    soundRadius = soundRadius,
                    hitSpot = hitSpot,
                    hitSpotHeight = hitSpotHeight,
                    hitSound = hitSound,
                )
        }

    private fun playMagicHitFx(
        source: Player,
        target: Npc,
        clientDelay: Int,
        castSound: String?,
        hitSpot: String?,
        hitSpotHeight: Int,
        hitSound: String?,
    ) {
        if (castSound != null) {
            soundArea(
                source = source.coords,
                synth = castSound,
                delay = 0,
                loops = 1,
                radius = ATTACK_SOUND_RADIUS,
                size = 0,
            )
        }

        if (hitSpot != null) {
            target.spotanim(hitSpot, delay = clientDelay, height = hitSpotHeight)
        }

        if (hitSound != null) {
            soundArea(
                source = target.coords,
                synth = hitSound,
                delay = clientDelay,
                loops = 1,
                radius = 10,
                size = 0,
            )
        }
    }

    private fun playMagicHitFx(
        source: Player,
        target: Player,
        clientDelay: Int,
        castSound: String?,
        soundRadius: Int,
        hitSpot: String?,
        hitSpotHeight: Int,
        hitSound: String?,
    ) {
        if (castSound != null) {
            soundArea(
                source = source.coords,
                synth = castSound,
                delay = 0,
                loops = 1,
                radius = soundRadius,
                size = 0,
            )
        }

        if (hitSpot != null) {
            target.spotanim(hitSpot, delay = clientDelay, height = hitSpotHeight)
        }

        if (hitSound != null) {
            soundArea(
                source = target.coords,
                synth = hitSound,
                delay = clientDelay,
                loops = 1,
                radius = 10,
                size = 0,
            )
        }
    }

    public fun playMagicSplashFx(
        source: Player,
        target: PathingEntity,
        clientDelay: Int,
        castSound: String?,
        soundRadius: Int,
    ): Unit =
        when (target) {
            is Npc ->
                playMagicSplashFx(
                    source = source,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = castSound,
                )
            is Player ->
                playMagicSplashFx(
                    source = source,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = castSound,
                    soundRadius = soundRadius,
                )
        }

    private fun playMagicSplashFx(
        source: Player,
        target: Npc,
        clientDelay: Int,
        castSound: String?,
    ) {
        if (castSound != null) {
            soundArea(
                source = source.coords,
                synth = castSound,
                delay = 0,
                loops = 1,
                radius = ATTACK_SOUND_RADIUS,
                size = 0,
            )
        }
        target.spotanim("spotanim.failedspell_impact", delay = clientDelay, height = 124)
        soundArea(
            source = target.coords,
            synth = "synth.SPELLFAIL",
            delay = clientDelay,
            loops = 1,
            radius = 10,
            size = 0,
        )
    }

    private fun playMagicSplashFx(
        source: Player,
        target: Player,
        clientDelay: Int,
        castSound: String?,
        soundRadius: Int,
    ) {
        if (castSound != null) {
            soundArea(
                source = source.coords,
                synth = castSound,
                delay = 0,
                loops = 1,
                radius = soundRadius,
                size = 0,
            )
        }
        target.spotanim("spotanim.failedspell_impact", delay = clientDelay, height = 124)
        soundArea(
            source = target.coords,
            synth = "synth.SPELLFAIL",
            delay = clientDelay,
            loops = 1,
            radius = 10,
            size = 0,
        )
    }

    public fun rollMagicalMeleeAccuracy(
        source: Player,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        multiplier: Double,
    ): Boolean {
        return when (target) {
            is Npc -> {
                rollMagicalMeleeAccuracy(source, target, attackType, attackStyle, multiplier)
            }
            is Player -> {
                rollMagicalMeleeAccuracy(source, target, attackType, attackStyle, multiplier)
            }
        }
    }

    private fun rollMagicalMeleeAccuracy(
        source: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollMagicalMeleeAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            random = random,
        )

    private fun rollMagicalMeleeAccuracy(
        source: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollMagicalMeleeAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            random = random,
        )

    public fun rollRangedMeleeAccuracy(
        source: Player,
        target: PathingEntity,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: RangedAttackType?,
        multiplier: Double,
    ): Boolean {
        return when (target) {
            is Npc -> {
                rollRangedMeleeAccuracy(
                    source = source,
                    target = target,
                    attackType = attackType,
                    attackStyle = attackStyle,
                    blockType = blockType,
                    specMultiplier = multiplier,
                )
            }
            is Player -> {
                rollRangedMeleeAccuracy(
                    source = source,
                    target = target,
                    attackType = attackType,
                    attackStyle = attackStyle,
                    specMultiplier = multiplier,
                )
            }
        }
    }

    private fun rollRangedMeleeAccuracy(
        source: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollRangedMeleeAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specMultiplier = specMultiplier,
            random = random,
        )

    private fun rollRangedMeleeAccuracy(
        source: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollRangedMeleeAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            random = random,
        )

    public fun rollRangedMagicAccuracy(
        source: Player,
        target: PathingEntity,
        attackStyle: MagicAttackStyle?,
        blockType: RangedAttackType?,
        multiplier: Double,
    ): Boolean {
        return when (target) {
            is Npc -> rollRangedMagicAccuracy(source, target, attackStyle, blockType, multiplier)
            is Player -> rollRangedMagicAccuracy(source, target, attackStyle, multiplier)
        }
    }

    private fun rollRangedMagicAccuracy(
        source: Player,
        target: Npc,
        attackStyle: MagicAttackStyle?,
        blockType: RangedAttackType?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollRangedMagicAccuracy(
            player = source,
            target = target,
            attackStyle = attackStyle,
            blockType = blockType,
            specMultiplier = specMultiplier,
            random = random,
        )

    private fun rollRangedMagicAccuracy(
        source: Player,
        target: Player,
        attackStyle: MagicAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollRangedMagicAccuracy(
            player = source,
            target = target,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            random = random,
        )

    public fun rollMagicalRangedAccuracy(
        source: Player,
        target: PathingEntity,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        multiplier: Double,
    ): Boolean {
        return when (target) {
            is Npc -> {
                rollMagicalRangedAccuracy(source, target, attackType, attackStyle, multiplier)
            }
            is Player -> {
                rollMagicalRangedAccuracy(source, target, attackType, attackStyle, multiplier)
            }
        }
    }

    private fun rollMagicalRangedAccuracy(
        source: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollMagicalRangedAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            random = random,
        )

    private fun rollMagicalRangedAccuracy(
        source: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
    ): Boolean =
        accuracy.rollMagicalRangedAccuracy(
            player = source,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specMultiplier = specMultiplier,
            random = random,
        )

    public fun spawnProjectile(
        source: Player,
        target: PathingEntity,
        spotanim: String,
        projanim: String,
    ): ProjAnim {
        val spotanimType = SpotanimType(spotanim.asRSCM(RSCMType.SPOTANIM))
        return when (target) {
            is Npc -> spawnProjectile(source, target, spotanimType, projanim)
            is Player -> spawnProjectile(source, target, spotanimType, projanim)
        }
    }

    public fun spawnProjectile(
        source: Player,
        target: Npc,
        spotanim: SpotanimType,
        projanim: String,
    ): ProjAnim = worldRepo.projAnim(source, target, spotanim, projanim)

    public fun spawnProjectile(
        source: Player,
        target: Player,
        spotanim: SpotanimType,
        projanim: String,
    ): ProjAnim = worldRepo.projAnim(source, target, spotanim, projanim)

    public fun soundArea(
        source: CoordGrid,
        synth: String,
        delay: Int,
        loops: Int,
        radius: Int,
        size: Int,
    ): Unit =
        worldRepo.soundArea(
            source,
            synth,
            delay = delay,
            loops = loops,
            radius = radius,
            size = size,
        )
}
