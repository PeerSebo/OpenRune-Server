package org.rsmod.content.bosses.callisto

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionStrategy
import org.rsmod.routefinder.loc.LocLayerConstants

class Callisto @Inject constructor(
    private val deps: BossDeps,
    private val locs: LocRepository,
    private val movement: NpcMovementProcessor,
    private val ray: RayCastValidator,
) : PluginScript() {
    private class Fight(val epoch: Int, var nextAttack: Int) {
        var stage = 0
        var active = true
        val traps = mutableMapOf<CoordGrid, Trap>()
    }

    private data class Trap(val loc: LocInfo, val armedAt: Int, val expiresAt: Int)
    private val fights = mutableMapOf<Npc, Fight>()
    private var nextEpoch = 0
    private val steps = StepValidator(deps.collision)

    override fun ScriptContext.startup() {
        for (variant in BearVariant.entries) {
            val type = checkNotNull(ServerCacheManager.getNpc(variant.npc.asRSCM(RSCMType.NPC)))
            onAiOpPlayer2(type) { combat(npc, it.target, variant) }
            onAiApPlayer2(type) { combat(npc, it.target, variant) }
            onEvent<NpcStateEvents.Spawn>(type.id) { reset(npc) }
            onEvent<NpcStateEvents.Hide>(type.id) { remove(npc) }
        }
        onEvent<NpcStateEvents.Respawn> { if (isBear(npc)) reset(npc) }
        onEvent<NpcStateEvents.Delete> { remove(npc) }
        onEvent<GameLifecycle.LateCycle> { tickTraps() }
    }

    private fun isBear(npc: Npc): Boolean = BearVariant.entries.any { npc.id == it.npc.asRSCM(RSCMType.NPC) }

    private fun reset(npc: Npc) {
        remove(npc)
        fights[npc] = Fight(++nextEpoch, deps.mapClock.cycle + 3)
        npc.movementLocked = false
        npc.vars["varn.freeze_end_clock"] = 0
        npc.vars["varn.freeze_immunity_end_clock"] = 0
        npc.vars["varn.freeze_guaranteed"] = 1
    }

    private fun remove(npc: Npc) {
        val fight = fights.remove(npc) ?: return
        fight.active = false
        for (trap in fight.traps.values) locs.del(trap.loc, Int.MAX_VALUE)
        fight.traps.clear()
    }

    internal fun combat(npc: Npc, target: Player, variant: BearVariant) {
        if (npc.hitpoints <= 0 || !validTarget(npc, target)) return
        val fight = fights.getOrPut(npc) { Fight(++nextEpoch, deps.mapClock.cycle) }
        if (!fight.active) return

        // AP handlers stop routing; pursuit must continue during attack cooldowns without a second movement pass.
        chaseBear(npc, target, movement)

        val now = deps.mapClock.cycle
        if (now < fight.nextAttack) return
        fight.nextAttack = now + variant.attackTicks
        val nextStage = CallistoRules.enragedStage(npc.hitpoints, npc.baseHitpointsLvl)
        if (nextStage > fight.stage) {
            fight.stage = nextStage
            npc.movementLocked = false
            CombatEffects.unfreeze(npc)
            npc.vars["varn.freeze_guaranteed"] = 1
            npc.anim("seq.npc_callisto_enraged01")
            npc.spotanim(if (variant == BearVariant.ARTIO) "spotanim.fx_callisto_enrage_small" else "spotanim.fx_callisto_enrage")
            deployTraps(npc, fight)
            return
        }

        if (deps.random.of(8) == 0) magic(npc, target, fight)
        else if (CallistoRules.footprintDistance(npc.coords, npc.size, target.coords) <= 1) melee(npc, variant)
        else ranged(npc, variant)
        if (fight.stage == 2) deployTraps(npc, fight)
    }

    private fun melee(npc: Npc, variant: BearVariant) {
        npc.anim("seq.npc_callisto_attack_melee01")
        for (player in participants(npc)) {
            if (CallistoRules.footprintDistance(npc.coords, npc.size, player.coords) > 1) continue
            val raw = if (deps.accuracy.rollMeleeAccuracy(npc, player, MeleeAttackType.Crush, deps.random)) {
                deps.random.of(variant.melee)
            } else 0
            val damage = CallistoRules.meleeDamage(raw, player.vars["varbit.prayer_protectfrommelee"] == 1)
            // Prayer is already reduced at 50%; Typeless prevents the standard modifier from applying twice.
            player.finishNpcHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
        }
    }

    private fun ranged(npc: Npc, variant: BearVariant) {
        npc.anim("seq.npc_callisto_attack_ranged01")
        npc.spotanim("spotanim.fx_callisto_ranged_spotanim")
        for (player in participants(npc)) {
            val proj = projectile(npc, player, "spotanim.fx_callisto_ranged_projectile")
            val damage = if (deps.accuracy.rollRangedAccuracy(npc, player, deps.random)) deps.random.of(variant.rangedMax + 1) else 0
            player.finishNpcHit(npc, proj.serverCycles, HitType.Ranged, damage, deps.playerHitModifier)
            if (damage > 0) player.spotanim("spotanim.fx_callisto_ranged_impact", delay = proj.clientCycles)
        }
    }

    private fun magic(npc: Npc, target: Player, fight: Fight) {
        npc.anim("seq.npc_callisto_attack_magic01")
        val launch = npc.coords.translate(npc.size / 2, npc.size / 2)
        val proj = projectile(npc, target, "spotanim.dragon_ranged_ice_attack")
        val uid = target.uid
        val epoch = fight.epoch
        // WorldMainTickProcess has already processed queues before this NPC combat tick.
        // The first countdown therefore occurs next tick, matching the projectile duration.
        deps.worldQueues.add(proj.serverCycles.coerceAtLeast(1)) {
            if (!resolves(npc, fight, epoch)) return@add
            val primary = uid.resolve(deps.playerList) ?: return@add
            if (!validTarget(npc, primary)) return@add
            val impact = primary.coords
            val victims = participants(npc).filter { it.coords.chebyshevDistance(impact) <= 1 }
            for (victim in victims) resolveMagic(npc, victim, fight, launch)
        }
    }

    private fun resolveMagic(npc: Npc, victim: Player, fight: Fight, launch: CoordGrid) {
        if (victim.vars["varbit.prayer_protectfrommagic"] == 1) {
            victim.finishNpcHit(npc, 1, HitType.Magic, 0, deps.playerHitModifier)
            return
        }
        val from = victim.coords
        val path = CallistoRules.knockbackPath(launch, from) { a, b ->
            b !in fight.traps && insideArena(npc, b) && steps.canTravel(
                level = a.level, x = a.x, z = a.z,
                offsetX = b.x - a.x, offsetZ = b.z - a.z,
                size = 1, extraFlag = 0, collision = CollisionStrategy.Normal,
            )
        }
        if (path.isNotEmpty()) {
            PathingEntityCommon.exactMove(victim, from, path.last(), 0, 20, victim.faceAngle, deps.collision)
        }
        Stun.apply(victim, movementCycles = 2, actionCycles = 2)
        victim.finishNpcHit(npc, 1, HitType.Typeless, CallistoRules.knockbackDamage(path.size), deps.playerHitModifier)
    }

    private fun projectile(npc: Npc, player: Player, spot: String): ProjAnim {
        val type = ProjAnimType(startHeight = 43, endHeight = 31, delay = 25, angle = 10, lengthAdjustment = 25, progress = 15, stepMultiplier = 5)
        return ProjAnim.fromNpcToPlayer(npc, player, spot.asRSCM(RSCMType.SPOTANIM), type).also(deps.worldRepo::projAnim)
    }

    private fun deployTraps(npc: Npc, fight: Fight) {
        val now = deps.mapClock.cycle
        val seeds = participants(npc).map { it.coords }.ifEmpty { listOf(npc.coords) }
        repeat(12) {
            val around = seeds[deps.random.of(seeds.size)]
            val tile = around.translate(deps.random.of(17) - 8, deps.random.of(17) - 8)
            if (!insideArena(npc, tile) || deps.collision.bearBlocked(tile) || tile in fight.traps) return@repeat
            if (CallistoRules.footprintDistance(npc.coords, npc.size, tile) == 0) return@repeat
            if (locs.findAll(tile).any { it.layer == LocLayerConstants.GROUND }) return@repeat
            val loc = LocInfo(
                LocLayerConstants.GROUND,
                tile,
                LocEntity("loc.callisto_trap_loc".asRSCM(RSCMType.LOC), LocShape.CentrepieceStraight.id, LocAngle.West.id),
            )
            if (locs.add(loc, Int.MAX_VALUE)) fight.traps[tile] = Trap(loc, now + 2, now + 16)
        }
    }

    internal fun tickTraps() {
        val now = deps.mapClock.cycle
        for ((npc, fight) in fights.toMap()) {
            if (!resolves(npc, fight, fight.epoch)) {
                remove(npc)
                continue
            }
            val iterator = fight.traps.iterator()
            while (iterator.hasNext()) {
                val (_, trap) = iterator.next()
                val caught = if (now >= trap.armedAt) participants(npc).filter { it.coords == trap.loc.coords } else emptyList()
                if (now >= trap.expiresAt || caught.isNotEmpty()) {
                    locs.del(trap.loc, Int.MAX_VALUE)
                    iterator.remove()
                }
                if (now >= trap.expiresAt) continue
                for (player in caught) {
                    deps.worldRepo.spotanimMap(SpotanimType("spotanim.fx_callisto_trap".asRSCM(RSCMType.SPOTANIM)), player.coords)
                    player.finishNpcHit(npc, 1, HitType.Typeless, deps.random.of(16), deps.playerHitModifier)
                    CombatEffects.freeze(player, 5)
                }
            }
        }
    }

    private fun resolves(npc: Npc, fight: Fight, epoch: Int): Boolean =
        fights[npc] === fight && CallistoRules.canResolve(fight.active && npc.isVisible, fight.epoch, epoch, npc.hitpoints)

    private fun insideArena(npc: Npc, tile: CoordGrid): Boolean =
        CallistoLair.arena(npc.spawnCoords)?.contains(tile) == true

    private fun validTarget(npc: Npc, player: Player): Boolean =
        player.isValidTarget() && insideArena(npc, player.coords)

    private fun participants(npc: Npc): List<Player> = deps.playerList.filter {
        validTarget(npc, it) && ray.hasLineOfSight(npc.coords, it.coords, npc.size, npc.size)
    }
}
