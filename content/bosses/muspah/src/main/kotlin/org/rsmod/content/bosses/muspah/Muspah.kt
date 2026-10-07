package org.rsmod.content.bosses.muspah

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.Angles
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.knockback
import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.add as addCollisionFlag
import org.rsmod.game.map.collision.get
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.remove as removeCollisionFlag
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

class Muspah
@Inject
constructor(
    deps: BossDeps,
    private val locRepo: LocRepository,
    private val npcList: NpcList,
    private val bossHpBar: BossHpBarScript,
    private val regions: RegionRegistry,
    private val aiPlayerInteractions: AiPlayerInteractions,
) : BossPluginScript(deps) {

    private val logger = InlineLogger()

    private val rangedId by lazy { "npc.muspah".asRSCM(RSCMType.NPC) }
    private val meleeId by lazy { "npc.muspah_melee".asRSCM(RSCMType.NPC) }
    private val teleportId by lazy { "npc.muspah_teleport".asRSCM(RSCMType.NPC) }
    private val soulsplitId by lazy { "npc.muspah_soulsplit".asRSCM(RSCMType.NPC) }
    private val finalId by lazy { "npc.muspah_final".asRSCM(RSCMType.NPC) }
    private val projectileHazardId by lazy { "npc.muspah_projectile".asRSCM(RSCMType.NPC) }

    private val liveFormIds by lazy {
        setOf(rangedId, meleeId, teleportId, soulsplitId, finalId)
    }

    private val fights = IdentityHashMap<Npc, MuspahFight>()

    private fun fightFor(npc: Npc): MuspahFight =
        fights.getOrPut(npc) { MuspahFight(npc, regions[npc.spawnCoords]) }

    private fun isActive(fight: MuspahFight): Boolean =
        fights[fight.npc] === fight && fight.npc.isSlotAssigned &&
            fight.npc.isVisible && fight.npc.hitpoints > 0 && ownsRegion(fight)

    private fun ownsRegion(fight: MuspahFight): Boolean =
        regions[fight.npc.spawnCoords] === fight.region

    private fun isInFight(fight: MuspahFight, player: Player): Boolean =
        player.isValidTarget() && player.level == fight.npc.level &&
            regions[player.coords] === fight.region &&
            player.coords.chebyshevDistance(fight.npc.spawnCoords) <= SHOCKWAVE_RADIUS

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = { onMuspahHit(npc, hit) },
            onCombatTickAccess = { updateMeleeStillness(npc) },
        )

        deps.extensionRegistry.register("muspah_melee_hit") { _, npc, target, _ ->
            val stillTicks = npc.vars[MELEE_STILL_TICKS_VARN]
            val stillBonus = (stillTicks * MELEE_STILL_DAMAGE_PER_TICK).coerceAtMost(MELEE_STILL_DAMAGE_CAP)
            val penetration =
                (stillTicks * MELEE_STILL_PENETRATION_PER_TICK).coerceAtMost(MELEE_STILL_PENETRATION_CAP)
            val damage = deps.random.of(MELEE_MAX_HIT + stillBonus + 1)
            target.finishNpcHit(npc, 1, HitType.Melee, damage, deps.playerHitModifier, prayerPenetration = penetration)
        }

        registerAbilityHandlers()

        for (formId in liveFormIds) {
            val type = ServerCacheManager.getNpc(formId) ?: continue
            onNpcHit(type) {
                if (formId == soulsplitId) resolveSoulsplitShield(npc)
                if (npc.hitpoints <= 0) {
                    val fight = fights[npc]
                    deps.worldQueues.add(SPIKE_DEATH_CLEANUP_DELAY) {
                        if (fight != null && fights[npc] === fight) {
                            fights.remove(npc)
                            clearSpikes(fight)
                        }
                    }
                }
            }
        }

        onEvent<NpcStateEvents.Respawn> {
            if (npc.visType.id in liveFormIds) fights.remove(npc)?.let(::clearSpikes)
        }
        onEvent<NpcStateEvents.Delete> {
            if (npc.visType.id in liveFormIds) fights.remove(npc)?.let(::clearSpikes)
        }

        onEvent<PlayerHitEvents.Impact> { onSoulsplitHit(hit) }
    }

    private fun onSoulsplitHit(hit: Hit) {
        if (!hit.isFromNpc) return
        val npc = hit.resolveNpcSource(npcList) ?: return
        if (npc.visType.id != soulsplitId) return
        drainShield(npc, SOULSPLIT_HIT_SELF_DRAIN)
        soulSplitHeal(npc, hit.damage)
        resolveSoulsplitShield(npc)
    }

    private fun clearSpikes(fight: MuspahFight) {
        fight.clouds.toList().forEach { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
        fight.clouds.clear()
        if (!ownsRegion(fight)) {
            fight.transientSpikes.clear()
            fight.activeSpikes.clear()
            return
        }
        fight.transientSpikes.forEach { locRepo.del(it, Int.MAX_VALUE) }
        fight.transientSpikes.clear()
        val spikes = fight.activeSpikes.toList()
        fight.activeSpikes.clear()
        if (spikes.isEmpty()) return
        val despawnSeqId = SPIKE_DESPAWN_SEQ.asRSCM(RSCMType.SEQ)
        val despawnAnim = ServerCacheManager.getAnim(despawnSeqId)
        val despawnTicks = despawnAnim?.tickDuration ?: 1
        spikes.forEach {
            deps.collision.removeCollisionFlag(it.coords, CollisionFlag.BLOCK_PLAYERS)
            deps.worldRepo.locAnim(it, SPIKE_DESPAWN_SEQ)
        }
        deps.worldQueues.add(despawnTicks) {
            if (!ownsRegion(fight)) return@add
            spikes.forEach { locRepo.del(it, Int.MAX_VALUE) }
        }
    }

    private fun updateMeleeStillness(npc: Npc) {
        val fight = fightFor(npc)
        if (npc.coords == fight.lastCoords) {
            npc.vars[MELEE_STILL_TICKS_VARN] =
                (npc.vars[MELEE_STILL_TICKS_VARN] + 1).coerceAtMost(MELEE_STILL_TICKS_CAP)
        } else {
            fight.lastCoords = npc.coords
            npc.vars[MELEE_STILL_TICKS_VARN] = 0
        }
    }

    override val spec: BossSpec by lazy {
        val teleportWindup =
            (ServerCacheManager.getAnim(TELEPORT_DISAPPEAR_SEQ.asRSCM(RSCMType.SEQ))?.tickDuration ?: 1)
                .coerceAtLeast(1)
        val shockwaveWindup =
            (ServerCacheManager.getAnim(FINAL_WINDUP_SEQ.asRSCM(RSCMType.SEQ))?.tickDuration
                    ?: FINAL_WINDUP_FALLBACK_TICKS)
                .coerceAtLeast(1)
        val teleportDuration = teleportWindup + TELEPORT_STEP_TICKS * (TELEPORT_LOOP.size + 1) + 2

        boss("npc.muspah", "npc.muspah_melee", "npc.muspah_teleport", "npc.muspah_soulsplit", "npc.muspah_final") {
            stats(attackRate = ATTACK_RATE)

            val rangedAttack =
                ability("ranged_attack") {
                    anim("seq.npc_muspah_attack_ranged_01")
                    projectile {
                        spotanim = "spotanim.projectile_muspah_attack_ranged_01"
                        config = RANGED_PROJECTILE_CONFIG
                        hit {
                            delay = RANGED_HIT_DELAY
                            damage(0..RANGED_MAX_HIT).roll()
                            type(Ranged)
                        }
                    }
                }

            val meleeHit =
                ability("melee_hit") {
                    anim("seq.npc_muspah_attack_melee_01")
                    include(external("muspah_melee_hit"))
                }

            val magicAttack =
                ability("magic_attack") {
                    anim("seq.npc_muspah_attack_magic_01")
                    spotanim("spotanim.vfx_muspah_attack_magic_01")
                    delay(MAGIC_PROJECTILE_WINDUP)
                    projectile {
                        spotanim = MAGIC_PROJECTILE_SPOTANIM
                        config = MAGIC_PROJECTILE_CONFIG
                        resolveOnImpact = true
                        hit {
                            damage(0..MAGIC_MAX_HIT).roll()
                            type(Magic)
                            spotanim(MAGIC_IMPACT_SPOTANIM)
                        }
                    }
                }

            val toMelee = ability(ABILITY_TO_MELEE, formTransform(PHASE_MELEE))
            val toRanged = ability(ABILITY_TO_RANGED, formTransform(PHASE_RANGED))

            val homingSpike =
                ability(
                    ABILITY_HOMING_SPIKE,
                    sequence(anim(HOMING_SPIKE_SEQ), external("muspah_homing_spikes")),
                )

            val cloudTeleport =
                ability(
                    ABILITY_CLOUD_TELEPORT,
                    sequence(
                        resetSwitchCounters(),
                        extendSpecialCooldown(teleportDuration + SPECIAL_COOLDOWN_BUFFER),
                        transmog("npc.muspah_teleport", Int.MAX_VALUE),
                        anim(TELEPORT_DISAPPEAR_SEQ),
                        spotanim(TELEPORT_DISAPPEAR_SPOTANIM),
                        wait(teleportWindup),
                        *TELEPORT_LOOP
                            .map { (dx, dz) ->
                                sequence(
                                    wait(TELEPORT_STEP_TICKS),
                                    teleport(spawnTile(dx, dz)),
                                    faceTile(spawnTile(1, 0)),
                                    anim("seq.npc_muspah_teleport_attack_01"),
                                    spotanim("spotanim.vfx_muspah_teleport_attack_01"),
                                    external("muspah_hazard_batch"),
                                )
                            }
                            .toTypedArray(),
                        wait(TELEPORT_STEP_TICKS),
                        teleport(spawnTile(TELEPORT_RETURN_OFFSET.first, TELEPORT_RETURN_OFFSET.second)),
                        anim(TELEPORT_APPEAR_SEQ),
                        spotanim(TELEPORT_APPEAR_SPOTANIM),
                        transitionTo(PHASE_MELEE),
                        faceTarget(),
                        wait(2),
                        external("muspah_resume_combat"),
                    ),
                )

            val finalShockwave =
                ability(
                    ABILITY_FINAL_SHOCKWAVE,
                    sequence(
                        lockMovement(),
                        anim(TELEPORT_DISAPPEAR_SEQ),
                        spotanim(TELEPORT_DISAPPEAR_SPOTANIM),
                        wait(teleportWindup),
                        teleport(spawnTile()),
                        anim(TELEPORT_APPEAR_SEQ),
                        spotanim(TELEPORT_APPEAR_SPOTANIM),
                        wait(FINAL_TELEPORT_TO_WINDUP_DELAY),
                        anim(FINAL_WINDUP_SEQ),
                        spotanim(FINAL_WINDUP_SPOTANIM_START),
                        wait(shockwaveWindup),
                        spotanim(FINAL_WINDUP_SPOTANIM_RELEASE),
                        external("muspah_shockwave_release"),
                        wait(FINAL_SHOCKWAVE_RECOVER_TICKS),
                        unlockMovement(),
                        transitionTo(PHASE_SOULSPLIT),
                        setVarn("varn.immune_melee", 1),
                        setVarn("varn.immune_ranged", 1),
                        setVarn("varn.immune_magic", 1),
                        external("muspah_soulsplit_enter"),
                        faceTarget(),
                        external("muspah_resume_combat"),
                    ),
                )

            val toFinal =
                ability(
                    ABILITY_TO_FINAL,
                    sequence(
                        transitionTo(PHASE_FINAL),
                        setVarn("varn.muspah_final_attack_count", 0),
                        external("muspah_final_enter"),
                    ),
                )

            val finalMagicAttack =
                ability(
                    ABILITY_FINAL_MAGIC,
                    sequence(
                        addVarn("varn.muspah_final_attack_count", 1),
                        whenever(
                            varnAtLeast("varn.muspah_final_attack_count", FINAL_SPIKE_ATTACK_INTERVAL),
                            sequence(
                                setVarn("varn.muspah_final_attack_count", 0),
                                external("muspah_spike_slam"),
                            ),
                        ),
                        run(magicAttack),
                    ),
                )

            val inRangedOrMelee = InPhase(PHASE_RANGED) or InPhase(PHASE_MELEE)
            val finalTriggered = varnIs("varn.muspah_final_phase", 1)
            val shockwaveReady = inRangedOrMelee and finalTriggered
            val shieldBroken = InPhase(PHASE_SOULSPLIT) and varnIs("varn.muspah_shield_broken", 1)

            val spikeUsed = Condition.AbilityUsed(ABILITY_HOMING_SPIKE)
            val cloudUsed = Condition.AbilityUsed(ABILITY_CLOUD_TELEPORT)
            val specialOffCooldown =
                varnIs("varn.muspah_special_cooldown", 0) or
                    varnExpired("varn.muspah_special_cooldown")
            val specialReady =
                inRangedOrMelee and Condition.Not(finalTriggered) and specialOffCooldown
            val meleeAtPrimary = InPhase(PHASE_MELEE) and HpBelow(SPECIAL_HP_PRIMARY)
            val rangedAtPrimary = InPhase(PHASE_RANGED) and HpBelow(SPECIAL_HP_PRIMARY)
            val atFallback = HpBelow(SPECIAL_HP_FALLBACK)

            val spikeReady =
                specialReady and ((meleeAtPrimary and Condition.Not(cloudUsed)) or atFallback)
            val cloudReady =
                specialReady and
                    ((rangedAtPrimary and Condition.Not(spikeUsed)) or (atFallback and spikeUsed))

            val rangedSwitchReady = switchReady(PHASE_RANGED, RANGED_SWITCH_DAMAGE)
            val meleeSwitchReady = switchReady(PHASE_MELEE, MELEE_SWITCH_DAMAGE)

            phase(PHASE_RANGED, transmog = "npc.muspah") {
                forceWhen(shockwaveReady, finalShockwave, once = true)
                forceWhen(spikeReady, homingSpike, once = true)
                forceWhen(cloudReady, cloudTeleport, once = true)
                forceWhen(rangedSwitchReady, toMelee)
                weightedSelectorRandom {
                    +random(rangedAttack, weight = RANGED_ATTACK_WEIGHT)
                    +random(magicAttack, weight = RANGED_MAGIC_WEIGHT)
                }
            }

            phase(PHASE_MELEE, transmog = "npc.muspah_melee") {
                forceWhen(shockwaveReady, finalShockwave, once = true)
                forceWhen(spikeReady, homingSpike, once = true)
                forceWhen(cloudReady, cloudTeleport, once = true)
                forceWhen(meleeSwitchReady, toRanged)
                weightedSelectorRandom { +random(meleeHit, weight = 1, requires = WithinMeleeRange) }
            }

            phase(PHASE_SOULSPLIT, transmog = "npc.muspah_soulsplit") {
                forceWhen(shieldBroken, toFinal, once = true)
                weightedSelectorRandom { +random(magicAttack, weight = 1) }
            }

            phase(PHASE_FINAL, transmog = "npc.muspah_final") {
                weightedSelectorRandom { +random(finalMagicAttack, weight = 1) }
            }
        }
    }

    private fun formTransform(phase: String) =
        sequence(
            resetSwitchCounters(),
            extendSpecialCooldown(TRANSFORM_ANIM_DELAY + SPECIAL_COOLDOWN_BUFFER),
            anim(TRANSFORM_DISAPPEAR_SEQ),
            wait(TRANSFORM_ANIM_DELAY),
            transitionTo(phase),
            spotanim(TRANSFORM_APPEAR_SPOTANIM),
            anim(TRANSFORM_APPEAR_SEQ),
            resetSwitchCounters(),
            external("muspah_spike_slam"),
        )

    private fun resetSwitchCounters() =
        sequence(
            setVarn("varn.muspah_damage_since_switch", 0),
            setVarn("varn.muspah_hits_since_switch", 0),
        )

    private fun extendSpecialCooldown(ticks: Int) =
        setVarn(
            "varn.muspah_special_cooldown",
            varn("varn.muspah_special_cooldown") atLeast (Now + ticks),
        )

    private fun switchReady(phase: String, damageThreshold: Int) =
        InPhase(phase) and
            Condition.Not(varnIs("varn.muspah_final_phase", 1)) and
            varnAtLeast("varn.muspah_hits_since_switch", SWITCH_MIN_HITS) and
            varnAtLeast("varn.muspah_damage_since_switch", damageThreshold)

    private fun registerAbilityHandlers() {
        val handlers = deps.extensionRegistry

        handlers.register("muspah_spike_slam") { _, npc, target, _ ->
            if (npc.isValidTarget() && target.isValidTarget()) beginSpikeSlam(npc, target)
        }
        handlers.register("muspah_homing_spikes") { _, npc, target, _ ->
            if (npc.isValidTarget() && target.isValidTarget()) beginHomingSpikeAttack(npc, target)
        }
        handlers.register("muspah_resume_combat") { _, npc, target, _ ->
            val fight = fightFor(npc)
            if (isActive(fight) && isInFight(fight, target)) {
                npc.apPlayer2(target, aiPlayerInteractions)
            }
        }
        handlers.register("muspah_hazard_batch") { _, npc, _, _ ->
            if (npc.isValidTarget()) spawnHazardCloudBatch(fightFor(npc))
        }
        handlers.register("muspah_shockwave_release") { _, npc, _, _ -> unleashShockwave(npc) }
        handlers.register("muspah_soulsplit_enter") { _, npc, _, _ ->
            npc.vars["varn.muspah_pre_soulsplit_hp"] = npc.hitpoints
            npc.vars["varn.muspah_pre_soulsplit_max_hp"] = npc.baseHitpointsLvl
            npc.vars["varn.muspah_shield_broken"] = 0
            npc.baseHitpointsLvl = SOULSPLIT_SHIELD_POINTS
            npc.hitpoints = SOULSPLIT_SHIELD_POINTS
            npc.vars["varn.muspah_shield_hp"] = SOULSPLIT_SHIELD_POINTS
            showShieldHeadbar(npc)
            recolourBossBar(npc)
            syncBossBar(npc)
        }
        handlers.register("muspah_final_enter") { _, npc, _, _ -> recolourBossBar(npc) }
    }

    private fun onMuspahHit(npc: Npc, hit: HitBuilder) {
        val fight = fightFor(npc)
        val visId = npc.visType.id
        if (visId !in liveFormIds || !hit.isFromPlayer) return
        val attacker = hit.sourceUid?.let { PlayerUid(it).resolve(deps.playerList) }

        if (visId == soulsplitId) {
            if (attacker?.vars["varbit.prayer_smite"] == 1) {
                drainShield(npc, hit.damage * SMITE_SHIELD_DRAIN_PERCENT / 100)
            }
            hit.damage = 0
            return
        }

        if (visId == finalId) return

        val hpAfterHit = npc.hitpoints - hit.damage
        if (fight.finalPhaseTriggered || hpAfterHit < FINAL_PHASE_HP_THRESHOLD) {
            if (!fight.finalPhaseTriggered) {
                fight.finalPhaseTriggered = true
                logger.debug { "[muspah] final phase shockwave queued: hp=$hpAfterHit" }
            }
            hit.damage = hit.damage.coerceAtMost((npc.hitpoints - 1).coerceAtLeast(0))
            return
        }

        npc.vars["varn.muspah_damage_since_switch"] += hit.damage
        npc.vars["varn.muspah_hits_since_switch"]++
    }

    private fun drainShield(npc: Npc, amount: Int) {
        val shieldHp = npc.vars["varn.muspah_shield_hp"]
        val drained = minOf(amount, shieldHp)
        npc.vars["varn.muspah_shield_hp"] = shieldHp - drained
        if (shieldHp - drained <= 0) {
            npc.vars["varn.muspah_shield_broken"] = 1
            resolveSoulsplitShield(npc)
        } else {
            npc.hitpoints = shieldHp - drained
        }
        if (drained <= 0) return
        showShieldHitmark(npc, hitmark_groups.prayer_drain.tint!!, drained)
    }

    private fun soulSplitHeal(npc: Npc, damage: Int) {
        if (npc.vars["varn.muspah_shield_broken"] == 1) return
        val shieldHp = npc.vars["varn.muspah_shield_hp"]
        val healed = minOf(damage / 2, SOULSPLIT_SHIELD_POINTS - shieldHp)
        if (healed <= 0) return
        npc.vars["varn.muspah_shield_hp"] = shieldHp + healed
        npc.hitpoints = shieldHp + healed
        showShieldHitmark(npc, hitmark_groups.heal.lit, healed)
    }

    private fun showShieldHitmark(npc: Npc, hitmarkName: String, amount: Int) {
        val hitmark = hitmarkName.asRSCM(RSCMType.HITMARK)
        npc.showHitmark(
            Hitmark.fromNoSource(
                self = hitmark,
                source = hitmark,
                public = hitmark,
                damage = amount,
                delay = 0,
            )
        )
    }

    private fun recolourBossBar(npc: Npc) {
        for (player in deps.playerList) {
            if (player.coords.chebyshevDistance(npc.coords) <= BOSS_BAR_RADIUS) {
                bossHpBar.onRecolour(player, npc)
            }
        }
    }

    private fun resolveSoulsplitShield(npc: Npc) {
        if (npc.vars["varn.muspah_shield_broken"] == 1) {
            clearStyleImmunity(npc)
            npc.baseHitpointsLvl = npc.vars["varn.muspah_pre_soulsplit_max_hp"]
            npc.hitpoints = npc.vars["varn.muspah_pre_soulsplit_hp"]
        } else {
            npc.hitpoints = npc.vars["varn.muspah_shield_hp"]
            showShieldHeadbar(npc)
        }
        syncBossBar(npc)
    }

    private fun syncBossBar(npc: Npc) {
        for (player in deps.playerList) {
            if (player.coords.chebyshevDistance(npc.coords) <= BOSS_BAR_RADIUS) {
                bossHpBar.onUpdate(player, npc)
            }
        }
    }

    private fun clearStyleImmunity(npc: Npc) {
        npc.vars["varn.immune_melee"] = 0
        npc.vars["varn.immune_ranged"] = 0
        npc.vars["varn.immune_magic"] = 0
    }

    private fun showShieldHeadbar(npc: Npc) {
        val headbar = npc.visHeadbar(params.headbar)
        val fill = npc.hitpoints * headbar.segments / SOULSPLIT_SHIELD_POINTS
        npc.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = fill,
                endFill = fill,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun unleashShockwave(npc: Npc) {
        val fight = fightFor(npc)
        val origin = npc.coords
        val size = npc.size
        val outerSpot = SpotanimType(SHOCKWAVE_SPOTANIM_OUTER.asRSCM(RSCMType.SPOTANIM))
        val innerSpot = SpotanimType(SHOCKWAVE_SPOTANIM_INNER.asRSCM(RSCMType.SPOTANIM))

        val alreadyHit = mutableSetOf<PlayerUid>()
        val scheduled = mutableSetOf<CoordGrid>()
        for (endpoint in shockwavePerimeter(origin, size, SHOCKWAVE_RADIUS)) {
            for (tile in shockwaveRayTiles(origin, size, endpoint)) {
                val distance = bboxChebyshevDistance(origin, size, tile)
                val delay = distance * SHOCKWAVE_DELAY_PER_TILE
                if (scheduled.add(tile)) {
                    deps.worldRepo.spotanimMap(outerSpot, tile, 0, delay)
                    deps.worldRepo.spotanimMap(innerSpot, tile, 0, delay + SHOCKWAVE_ECHO_OFFSET)
                    val ticks = (delay / CLIENT_CYCLES_PER_TICK).coerceAtLeast(1)
                    deps.worldQueues.add(ticks) {
                        if (!isActive(fight)) return@add
                        for (occupant in deps.playerList) {
                            if (occupant.coords != tile || !isInFight(fight, occupant) ||
                                !alreadyHit.add(occupant.uid)) continue
                            val damage = deps.random.of(SHOCKWAVE_MAX_HIT + 1)
                            occupant.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
                        }
                    }
                }

                if (isShockwaveBlocked(tile)) break
            }
        }
    }

    private fun shockwavePerimeter(origin: CoordGrid, size: Int, radius: Int): List<CoordGrid> {
        val center = origin.translate(size / 2, size / 2)
        val tiles = mutableListOf<CoordGrid>()
        for (dx in -radius..radius) {
            tiles += center.translate(dx, -radius)
            tiles += center.translate(dx, radius)
        }
        for (dz in -radius + 1 until radius) {
            tiles += center.translate(-radius, dz)
            tiles += center.translate(radius, dz)
        }
        return tiles
    }

    private fun shockwaveRayTiles(origin: CoordGrid, size: Int, endpoint: CoordGrid): List<CoordGrid> {
        val center = origin.translate(size / 2, size / 2)
        val tiles = mutableListOf<CoordGrid>()
        var x = center.x
        var z = center.z
        val dx = kotlin.math.abs(endpoint.x - x)
        val dz = kotlin.math.abs(endpoint.z - z)
        val stepX = if (x < endpoint.x) 1 else -1
        val stepZ = if (z < endpoint.z) 1 else -1
        var err = dx - dz

        fun addTile(tx: Int, tz: Int) {
            val tile = CoordGrid(tx, tz, center.level)
            if (bboxChebyshevDistance(origin, size, tile) > 0) tiles += tile
        }

        addTile(x, z)
        while (x != endpoint.x || z != endpoint.z) {
            val e2 = 2 * err
            val stepsX = e2 > -dz
            val stepsZ = e2 < dx
            if (stepsX) {
                err -= dz
                x += stepX
            }
            if (stepsZ) {
                err += dx
                z += stepZ
            }
            if (stepsX && stepsZ) {
                addTile(x - stepX, z)
                addTile(x, z - stepZ)
            }
            addTile(x, z)
        }
        return tiles
    }

    private fun bboxChebyshevDistance(origin: CoordGrid, size: Int, tile: CoordGrid): Int {
        val minX = origin.x
        val maxX = origin.x + size - 1
        val minZ = origin.z
        val maxZ = origin.z + size - 1
        val dx = when {
            tile.x < minX -> minX - tile.x
            tile.x > maxX -> tile.x - maxX
            else -> 0
        }
        val dz = when {
            tile.z < minZ -> minZ - tile.z
            tile.z > maxZ -> tile.z - maxZ
            else -> 0
        }
        return maxOf(dx, dz)
    }

    private fun isShockwaveBlocked(coord: CoordGrid): Boolean =
        deps.collision.isWalkBlocked(coord) || deps.collision[coord] and CollisionFlag.BLOCK_PLAYERS != 0

    private fun spawnHazardCloudBatch(fight: MuspahFight) {
        val anchor = fight.npc.spawnCoords
        val type = ServerCacheManager.getNpc(projectileHazardId) ?: return
        val count = HAZARD_BATCH_MIN + deps.random.of(HAZARD_BATCH_MAX - HAZARD_BATCH_MIN + 1)
        repeat(count) {
            val coord =
                anchor.translate(
                    deps.random.of(ARENA_RADIUS * 2) - ARENA_RADIUS,
                    deps.random.of(ARENA_RADIUS * 2) - ARENA_RADIUS,
                )
            val cloud = Npc(type, coord)
            cloud.mode = NpcMode.None
            val lifetime = HAZARD_DRIFT_MIN + deps.random.of(HAZARD_DRIFT_MAX - HAZARD_DRIFT_MIN + 1)
            deps.npcRepo.add(cloud, lifetime + 2)
            fight.clouds += cloud
            hitHazardCloudOccupant(fight, cloud)
            val direction = HAZARD_DIRECTIONS[deps.random.of(HAZARD_DIRECTIONS.size)]
            driftHazardCloud(fight, cloud, direction, lifetime)
        }
    }

    private fun hitHazardCloudOccupant(fight: MuspahFight, cloud: Npc) {
        val occupant = deps.playerList.firstOrNull { it.coords == cloud.coords && isInFight(fight, it) } ?: return
        val damage = deps.random.of(HAZARD_MAX_HIT + 1)
        occupant.finishNpcHit(cloud, 1, HitType.Typeless, damage, deps.playerHitModifier)
    }

    private fun driftHazardCloud(fight: MuspahFight, cloud: Npc, direction: Pair<Int, Int>, ticksLeft: Int) {
        if (ticksLeft <= 0) {
            deps.worldQueues.add(1) {
                if (cloud.isSlotAssigned) deps.npcRepo.del(cloud, Int.MAX_VALUE)
                fight.clouds.remove(cloud)
            }
            return
        }
        deps.worldQueues.add(1) {
            if (!cloud.isSlotAssigned) return@add
            if (!isActive(fight)) {
                deps.npcRepo.del(cloud, Int.MAX_VALUE)
                fight.clouds.remove(cloud)
                return@add
            }
            val dest = cloud.coords.translate(direction.first, direction.second)
            PathingEntityCommon.teleport(cloud, deps.collision, dest)
            hitHazardCloudOccupant(fight, cloud)
            driftHazardCloud(fight, cloud, direction, ticksLeft - 1)
        }
    }

    private fun beginSpikeSlam(npc: Npc, target: Player) {
        val fight = fightFor(npc)
        if (!isInFight(fight, target)) return
        val tiles = mutableSetOf(target.coords)
        val scatterCount = SPIKE_SCATTER_MIN + deps.random.of(SPIKE_SCATTER_MAX - SPIKE_SCATTER_MIN + 1)
        repeat(scatterCount) {
            val coord = randomWalkableTile(npc.spawnCoords, ARENA_RADIUS) ?: return@repeat
            tiles += coord
        }

        val warningSpot = SpotanimType(SPIKE_WARNING_SPOTANIM.asRSCM(RSCMType.SPOTANIM))
        tiles.forEach { deps.worldRepo.spotanimMap(warningSpot, it) }

        deps.worldQueues.add(SPIKE_PRE_DELAY) {
            if (!isActive(fight)) return@add
            val spawnSpot = SpotanimType(SPIKE_SPAWN_SPOTANIM.asRSCM(RSCMType.SPOTANIM))
            tiles.forEach {
                fight.transientSpikes +=
                    locRepo.add(it, SPIKE_TELEGRAPH_LOC, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
                deps.worldRepo.spotanimMap(spawnSpot, it)
            }
            shakeCameraNear(npc)
            deps.worldQueues.add(SPIKE_SOLIDIFY_DELAY) {
                if (!isActive(fight)) return@add
                tiles.forEach { coord ->
                    fight.transientSpikes.removeIf { it.coords == coord }
                    spawnSpike(npc, coord)
                }
            }
        }
    }

    private fun shakeCameraNear(npc: Npc) {
        val fight = fightFor(npc)
        val nearby = deps.playerList.filter { isInFight(fight, it) && it.coords.chebyshevDistance(npc.coords) <= CAM_SHAKE_RADIUS }
            .map { it to it.uid }
        for ((player, _) in nearby) {
            Camera.camShake(player, axis = CamShakeAxis.LEFT_RIGHT, random = CAM_SHAKE_RANDOM_X, amplitude = 0, rate = 0)
            Camera.camShake(player, axis = CamShakeAxis.UP_DOWN, random = CAM_SHAKE_RANDOM_Y, amplitude = 0, rate = 0)
            Camera.camShake(player, axis = CamShakeAxis.FORWARDS_BACKWARDS, random = CAM_SHAKE_RANDOM_Z, amplitude = 0, rate = 0)
        }
        deps.worldQueues.add(CAM_SHAKE_DURATION) {
            for ((player, uid) in nearby) {
                if (player.uid == uid && uid.resolve(deps.playerList) === player) Camera.camReset(player)
            }
        }
    }

    private fun spawnSpike(npc: Npc, coord: CoordGrid) {
        if (locRepo.findLoc(coord, SPIKE_LOC)) return
        val fight = fightFor(npc)
        val loc = locRepo.add(coord, SPIKE_LOC, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        fight.activeSpikes += loc

        deps.collision.removeCollisionFlag(coord, SPIKE_LOC_COLLISION_MASK)
        deps.collision.addCollisionFlag(coord, CollisionFlag.BLOCK_PLAYERS)

        deps.worldQueues.add(1) {
            if (!isActive(fight)) return@add
            if (!locRepo.findLoc(coord, SPIKE_LOC)) return@add
            val occupant = deps.playerList.firstOrNull { it.coords == coord && isInFight(fight, it) }
            if (occupant != null) {
                triggerSpikeHit(npc, occupant, coord)
            }
        }
    }

    private fun triggerSpikeHit(npc: Npc, player: Player, coord: CoordGrid) {
        val damage = SPIKE_DAMAGE_MIN + deps.random.of(SPIKE_DAMAGE_MAX - SPIKE_DAMAGE_MIN + 1)
        player.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
        npc.heal((damage * SPIKE_HEAL_FRACTION).toInt(), showHitsplat = true)
        knockbackPlayer(player, coord)
    }

    private fun knockbackPlayer(player: Player, coord: CoordGrid): CoordGrid {
        val safeTile = randomAdjacentClearTile(coord) ?: coord
        if (safeTile == player.coords) return safeTile
        val from = player.coords
        player.knockback(safeTile, Angles.bearing(safeTile, from), deps.collision)
        return safeTile
    }

    private fun beginHomingSpikeAttack(npc: Npc, target: Player) {
        repeat(HOMING_SPIKE_PAIR_COUNT) {
            val origin = randomWalkableTile(target.coords, HOMING_SPIKE_ORIGIN_RADIUS) ?: target.coords
            launchHomingSpikePair(npc, target, origin)
        }
    }

    private fun launchHomingSpikePair(npc: Npc, target: Player, origin: CoordGrid) {
        val fight = fightFor(npc)
        val targetUid = target.uid
        val warningSpot = SpotanimType(SPIKE_WARNING_SPOTANIM.asRSCM(RSCMType.SPOTANIM))
        val coordA = origin
        val coordB = origin.translate(0, -1)
        deps.worldRepo.spotanimMap(warningSpot, coordA)
        deps.worldRepo.spotanimMap(warningSpot, coordB)

        deps.worldQueues.add(HOMING_SPIKE_TELEGRAPH_DELAY) {
            if (isActive(fight) && !fight.finalPhaseTriggered && isInFight(fight, target) && target.uid == targetUid) {
                spawnHomingSpike(fight, targetUid, coordA)
            }
        }
        deps.worldQueues.add(HOMING_SPIKE_TELEGRAPH_DELAY + HOMING_SPIKE_PAIR_OFFSET) {
            if (isActive(fight) && !fight.finalPhaseTriggered && isInFight(fight, target) && target.uid == targetUid) {
                spawnHomingSpike(fight, targetUid, coordB)
            }
        }
    }

    private fun spawnHomingSpike(fight: MuspahFight, targetUid: PlayerUid, coord: CoordGrid) {
        val npc = fight.npc
        val loc = locRepo.add(coord, HOMING_SPIKE_LOC, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        fight.transientSpikes += loc
        deps.worldRepo.spotanimMap(SpotanimType(HOMING_SPIKE_SPAWN_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), coord)
        hitHomingSpikeOccupant(npc, coord)
        val expiryTick = deps.mapClock.cycle + HOMING_SPIKE_LIFETIME_TICKS
        advanceHomingSpike(fight, targetUid, loc, expiryTick)
    }

    private fun advanceHomingSpike(fight: MuspahFight, targetUid: PlayerUid, loc: LocInfo, expiryTick: Int) {
        val npc = fight.npc
        deps.worldQueues.add(HOMING_SPIKE_MOVE_INTERVAL) {
            if (!isActive(fight) || fight.finalPhaseTriggered) {
                if (ownsRegion(fight) && fight.transientSpikes.remove(loc)) locRepo.del(loc, Int.MAX_VALUE)
                return@add
            }

            val target = targetUid.resolve(deps.playerList)
            if (deps.mapClock.cycle >= expiryTick || target == null || !isInFight(fight, target)) {
                solidifyHomingSpike(fight, loc)
                return@add
            }

            val next = stepToward(loc.coords, target.coords)
            if (next == loc.coords) {
                solidifyHomingSpike(fight, loc)
                return@add
            }

            locRepo.del(loc, Int.MAX_VALUE)
            fight.transientSpikes.remove(loc)
            val nextLoc =
                locRepo.add(next, HOMING_SPIKE_LOC, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
            fight.transientSpikes += nextLoc
            deps.worldRepo.spotanimMap(SpotanimType(HOMING_SPIKE_SPAWN_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), next)
            hitHomingSpikeOccupant(npc, next)
            advanceHomingSpike(fight, targetUid, nextLoc, expiryTick)
        }
    }

    private fun solidifyHomingSpike(fight: MuspahFight, loc: LocInfo) {
        fight.transientSpikes.remove(loc)
        locRepo.del(loc, Int.MAX_VALUE)
        spawnSpike(fight.npc, loc.coords)
    }

    private fun stepToward(from: CoordGrid, to: CoordGrid): CoordGrid {
        val dx = (to.x - from.x).coerceIn(-1, 1)
        val dz = (to.z - from.z).coerceIn(-1, 1)
        if (dx == 0 && dz == 0) return from
        val next = from.translate(dx, dz)
        return if (deps.collision.isWalkBlocked(next)) from else next
    }

    private fun hitHomingSpikeOccupant(npc: Npc, coord: CoordGrid) {
        val fight = fightFor(npc)
        val occupant = deps.playerList.firstOrNull { it.coords == coord && isInFight(fight, it) } ?: return
        val damage = HOMING_SPIKE_DAMAGE_MIN + deps.random.of(HOMING_SPIKE_DAMAGE_MAX - HOMING_SPIKE_DAMAGE_MIN + 1)
        occupant.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
        npc.heal((damage * SPIKE_HEAL_FRACTION).toInt(), showHitsplat = true)
        knockbackPlayer(occupant, coord)
    }

    private fun randomWalkableTile(center: CoordGrid, radius: Int): CoordGrid? {
        val span = radius * 2 + 1
        repeat(RANDOM_TILE_ATTEMPTS) {
            val coord = center.translate(deps.random.of(span) - radius, deps.random.of(span) - radius)
            if (!deps.collision.isWalkBlocked(coord)) return coord
        }
        return null
    }

    private fun randomAdjacentClearTile(center: CoordGrid): CoordGrid? {
        val candidates =
            HAZARD_DIRECTIONS.map { center.translate(it.first, it.second) }.filter {
                !deps.collision.isWalkBlocked(it) && !locRepo.findLoc(it, SPIKE_LOC)
            }
        if (candidates.isEmpty()) return null
        return candidates[deps.random.of(candidates.size)]
    }

    private companion object {
        private const val PHASE_RANGED = "ranged"
        private const val PHASE_MELEE = "melee"
        private const val PHASE_SOULSPLIT = "soulsplit"
        private const val PHASE_FINAL = "final"

        private const val ABILITY_HOMING_SPIKE = "homing_spike"
        private const val ABILITY_CLOUD_TELEPORT = "cloud_teleport"
        private const val ABILITY_TO_MELEE = "to_melee"
        private const val ABILITY_TO_RANGED = "to_ranged"
        private const val ABILITY_FINAL_SHOCKWAVE = "final_shockwave"
        private const val ABILITY_TO_FINAL = "to_final"
        private const val ABILITY_FINAL_MAGIC = "final_magic"

        private const val ATTACK_RATE = 6

        private const val TRANSFORM_DISAPPEAR_SEQ = "seq.npc_muspah_transform_disappear_02"
        private const val TRANSFORM_APPEAR_SEQ = "seq.npc_muspah_transform_appear_02"
        private const val TRANSFORM_APPEAR_SPOTANIM = "spotanim.vfx_muspah_teleport_appear_01"
        private const val TRANSFORM_ANIM_DELAY = 1

        private const val RANGED_SWITCH_DAMAGE = 100
        private const val MELEE_SWITCH_DAMAGE = 80
        private const val SWITCH_MIN_HITS = 4

        private const val SOULSPLIT_SHIELD_POINTS = 75
        private const val SMITE_SHIELD_DRAIN_PERCENT = 25
        private const val FINAL_SPIKE_ATTACK_INTERVAL = 4
        private const val SOULSPLIT_HIT_SELF_DRAIN = 2
        private const val BOSS_BAR_RADIUS = 30

        private const val RANGED_MAX_HIT = 61
        private const val MELEE_MAX_HIT = 34
        private const val MAGIC_MAX_HIT = 72

        private const val MELEE_STILL_TICKS_VARN = "varn.muspah_melee_still_ticks"
        private const val MELEE_STILL_DAMAGE_PER_TICK = 1
        private const val MELEE_STILL_DAMAGE_CAP = 20
        private const val MELEE_STILL_PENETRATION_PER_TICK = 5
        private const val MELEE_STILL_PENETRATION_CAP = 50
        private const val MELEE_STILL_TICKS_CAP =
            MELEE_STILL_DAMAGE_CAP / MELEE_STILL_DAMAGE_PER_TICK

        private const val TELEPORT_STEP_TICKS = 1
        private const val TELEPORT_DISAPPEAR_SEQ = "seq.npc_muspah_teleport_disappear_01"
        private const val TELEPORT_DISAPPEAR_SPOTANIM = "spotanim.vfx_muspah_teleport_disappear_01"
        private const val TELEPORT_APPEAR_SEQ = "seq.npc_muspah_teleport_appear_01"
        private const val TELEPORT_APPEAR_SPOTANIM = "spotanim.vfx_muspah_teleport_appear_01"
        private const val ARENA_RADIUS = 8
        private const val RANDOM_TILE_ATTEMPTS = 5

        private const val FINAL_PHASE_HP_THRESHOLD = 127
        private const val FINAL_TELEPORT_TO_WINDUP_DELAY = 2
        private const val FINAL_WINDUP_SEQ = "seq.npc_muspah_attack_explosion_01"
        private const val FINAL_WINDUP_SPOTANIM_START = "spotanim.vfx_muspah_attack_explosion_01"
        private const val FINAL_WINDUP_SPOTANIM_RELEASE = "spotanim.vfx_muspah_attack_explosion_02"
        private const val FINAL_WINDUP_FALLBACK_TICKS = 5
        private const val FINAL_SHOCKWAVE_RECOVER_TICKS = 3

        private const val SHOCKWAVE_SPOTANIM_OUTER = "spotanim.projectile_muspah_attack_explode_03_dark"
        private const val SHOCKWAVE_SPOTANIM_INNER = "spotanim.projectile_muspah_attack_explode_03"
        private const val SHOCKWAVE_RADIUS = 20
        private const val SHOCKWAVE_DELAY_PER_TILE = 3
        private const val SHOCKWAVE_ECHO_OFFSET = 15
        private const val SHOCKWAVE_MAX_HIT = 80

        private const val SPIKE_LOC = "loc.muspah_spike"
        private const val SPIKE_TELEGRAPH_LOC = "loc.muspah_spike_pre"

        private const val SPIKE_DEATH_CLEANUP_DELAY = 5

        private const val SPIKE_LOC_COLLISION_MASK: Int =
            CollisionFlag.LOC or CollisionFlag.LOC_PROJ_BLOCKER or CollisionFlag.LOC_ROUTE_BLOCKER

        private const val SPIKE_DESPAWN_SEQ = "seq.npc_muspah_spike_despawn_01"

        private const val SPIKE_WARNING_SPOTANIM = "spotanim.vfx_muspah_spike_warning_01"
        private const val SPIKE_SPAWN_SPOTANIM = "spotanim.vfx_muspah_spike_spawn_01"

        private const val SPIKE_PRE_DELAY = 2
        private const val SPIKE_SOLIDIFY_DELAY = 1

        private const val CAM_SHAKE_RADIUS = 15
        private const val CAM_SHAKE_RANDOM_X = 5
        private const val CAM_SHAKE_RANDOM_Y = 8
        private const val CAM_SHAKE_RANDOM_Z = 8
        private const val CAM_SHAKE_DURATION = 2

        private const val CLIENT_CYCLES_PER_TICK = 30

        private const val SPIKE_SCATTER_MIN = 2
        private const val SPIKE_SCATTER_MAX = 4
        private const val SPIKE_DAMAGE_MIN = 15
        private const val SPIKE_DAMAGE_MAX = 25
        private const val SPIKE_HEAL_FRACTION = 0.75

        private const val SPECIAL_HP_PRIMARY = 0.75
        private const val SPECIAL_HP_FALLBACK = 0.50
        private const val SPECIAL_COOLDOWN_BUFFER = 5

        private const val HOMING_SPIKE_SEQ = "seq.npc_muspah_attack_summon_01"
        private const val HOMING_SPIKE_LOC = "loc.muspah_spike_follow"
        private const val HOMING_SPIKE_SPAWN_SPOTANIM = "spotanim.vfx_muspah_spike_spawn_02"

        private const val HOMING_SPIKE_PAIR_COUNT = 2
        private const val HOMING_SPIKE_ORIGIN_RADIUS = 6
        private const val HOMING_SPIKE_TELEGRAPH_DELAY = 2
        private const val HOMING_SPIKE_PAIR_OFFSET = 1
        private const val HOMING_SPIKE_MOVE_INTERVAL = 2
        private const val HOMING_SPIKE_LIFETIME_TICKS = 34

        private const val HOMING_SPIKE_DAMAGE_MIN = 8
        private const val HOMING_SPIKE_DAMAGE_MAX = 16

        private val RANGED_PROJECTILE_CONFIG =
            ProjectileConfig(
                startHeight = 32,
                endHeight = 25,
                startDelay = 26,
                travelTime = 34,
                angle = 5,
                progress = 244,
                stepMultiplier = 0,
            )

        private const val RANGED_ATTACK_WEIGHT = 5
        private const val RANGED_MAGIC_WEIGHT = 1

        private const val MAGIC_PROJECTILE_WINDUP = 2
        private const val MAGIC_PROJECTILE_SPOTANIM = "spotanim.projectile_muspah_attack_magic_01"
        private const val MAGIC_IMPACT_SPOTANIM = "spotanim.impact_muspah_attack_magic_01"

        private val MAGIC_PROJECTILE_CONFIG =
            ProjectileConfig(
                startHeight = 100,
                endHeight = 24,
                startDelay = 24,
                travelTime = 30,
                angle = 5,
                progress = 0,
                stepMultiplier = 0,
            )

        private const val HAZARD_BATCH_MIN = 2
        private const val HAZARD_BATCH_MAX = 4
        private const val HAZARD_DRIFT_MIN = 3
        private const val HAZARD_DRIFT_MAX = 6
        private const val HAZARD_MAX_HIT = 20

        private val HAZARD_DIRECTIONS =
            listOf(
                1 to 0,
                -1 to 0,
                0 to 1,
                0 to -1,
                1 to 1,
                1 to -1,
                -1 to 1,
                -1 to -1,
            )

        private const val RANGED_HIT_DELAY = 3

        private val TELEPORT_LOOP =
            listOf(
                10 to -1,
                -4 to 5,
                1 to -9,
                8 to 5,
                -6 to -1,
                7 to -7,
                2 to 4,
                -5 to -8,
            )

        private val TELEPORT_RETURN_OFFSET = 6 to 5
    }
}
