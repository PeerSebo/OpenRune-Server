package org.rsmod.content.bosses.kalphitequeen

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.EffectInterpreter
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KalphiteQueen @Inject constructor(
    private val deps: BossDeps,
    private val death: NpcDeath,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
) : PluginScript() {
    private val lifecycle = QueenLifecycle(deps.worldQueues) { deps.mapClock.cycle }
    private val workers = IdentityHashMap<Npc, MutableList<Npc>>()
    private val ground: NpcServerType by lazy { ServerCacheManager.getNpc(GROUND.asRSCM(RSCMType.NPC))!! }
    private val flying: NpcServerType by lazy { ServerCacheManager.getNpc(FLYING.asRSCM(RSCMType.NPC))!! }
    private var Npc.aggressivePlayer by typePlayerUidVarn("varn.aggressive_player")
    private var Player.aggressiveNpc by typeNpcUidVarp("varp.aggressive_npc")

    override fun ScriptContext.startup() {
        BossCombat.register(this, SPEC, deps)
        // GameCycle increments the clock immediately before LateCycle; use the completed cycle.
        onEvent<GameLifecycle.LateCycle> { lifecycle.tick(deps.mapClock.cycle - 1) }
        deps.extensionRegistry.register(ATTACK) { access, npc, target, _ -> attack(checkNotNull(access), npc, target) }
        onEvent<NpcStateEvents.Spawn>(ground.id) { lifecycle.reset(npc) }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.id == ground.id) { cleanupWorkers(npc); lifecycle.reset(npc) }
        }
        onEvent<NpcStateEvents.Delete> {
            if (npc.id == ground.id) { cleanupWorkers(npc); lifecycle.remove(npc) }
        }
        for (form in listOf(ground, flying)) {
            onNpcQueue(form, "queue.death") { queenDeath() }
            onNpcHit(form) {
                if (hit.isFromPlayer) lifecycle.combat(npc, deps.mapClock.cycle)
                if (npc.hitpoints > 0 && hit.isFromPlayer && deps.random.of(20) == 0) {
                    hatchWorker(npc, hit.resolvePlayerSource(deps.playerList))
                }
            }
        }
    }

    private suspend fun StandardNpcAccess.queenDeath() {
        lifecycle.acceptDeathQueue(npc)
        if (lifecycle.transform(npc, flying) {
                deps.encounterRegistry.of(npc).lastAbilityTick = deps.mapClock.cycle
            }) {
            anim("seq.kalphite_update_queen_death")
            locs.add(coords, "loc.kalphite_update_queen_emerging_legs", QueenLifecycle.TRANSFORM_TICKS,
                LocAngle.West, LocShape.CentrepieceStraight)
            spotanim("spotanim.kalphite_update_flying_queen_emerge_spotanim")
            return
        }
        if (!lifecycle.markFinalDeath(npc)) return
        cleanupWorkers(npc)
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        npc.aggressivePlayer?.resolve(deps.playerList)?.let { player ->
            flying.paramOrNull(params.death_sound)?.let { player.soundSynth(it) }
            if (player.aggressiveNpc == npc.uid) VarPlayerIntMapSetter.set(player, "varp.lastcombat", 0)
        }
        val sequence = ServerCacheManager.getAnim("seq.kalphite_update_flying_queen_death".asRSCM(RSCMType.SEQ))!!
        anim("seq.kalphite_update_flying_queen_death")
        delay(sequence)
        death.spawnDrops(this)
        if (npc.respawns) deps.npcRepo.despawn(npc, npc.type.respawnRate)
        else deps.npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun attack(access: StandardNpcAccess, npc: Npc, target: Player) {
        if (!target.isValidTarget() || target.level != npc.level || npc.hitpoints <= 0) return
        if (lifecycle.phase(npc) == QueenLifecycle.Phase.Transforming) return
        lifecycle.combat(npc, deps.mapClock.cycle)
        val airborne = lifecycle.phase(npc) == QueenLifecycle.Phase.Airborne
        val adjacent = npc.isWithinDistance(target, 1)
        val style = if (adjacent) deps.random.of(4) else 2 + deps.random.of(2)
        when (style) {
            0, 1 -> {
                access.anim(if (airborne) "seq.kalphite_update_flying_queen_stinger_attack" else "seq.kalphite_update_queen_jaws_attack")
                EffectInterpreter(npc, target, SPEC, deps.encounterRegistry.of(npc), deps)
                    .run(access, hit(Roll(0..31).withAccuracy(), Melee))
            }
            2 -> {
                access.anim(if (airborne) "seq.kalphite_update_flying_queen_ranged_attack" else "seq.kalphite_update_queen_ranged_attack")
                ranged(npc, target)
            }
            else -> {
                access.anim(if (airborne) "seq.kalphite_update_flying_queen_ranged_attack" else "seq.kalphite_update_queen_ranged_attack")
                access.spotanim("spotanim.kalphite_queen_glow")
                magic(npc, target)
            }
        }
    }

    private fun ranged(npc: Npc, target: Player) {
        val victims = deps.playerList.filter {
            it.isValidTarget() && it.level == npc.level && it.coords.chebyshevDistance(target.coords) <= 3
        }
        for (victim in victims) {
            val projectile = ProjAnim.fromNpcToPlayer(npc, victim, "spotanim.kalphite_queen_spine".asRSCM(RSCMType.SPOTANIM), RANGED_TRAVEL)
            deps.worldRepo.projAnim(projectile)
            val rolled = deps.random.of(32)
            victim.finishNpcHit(npc, projectile.serverCycles, HitType.Ranged, rolled, deps.playerHitModifier)
            if (rolled > 0) {
                val uid = victim.uid
                deps.worldQueues.add(projectile.serverCycles) {
                    val player = uid.resolve(deps.playerList) ?: return@add
                    if (!player.isValidTarget() || player.level != npc.level) return@add
                    val spectral = player.lefthand?.id == "obj.spectral".asRSCM(RSCMType.OBJ)
                    if (!spectral || deps.random.of(2) == 0) CombatEffects.statDrain(player, listOf("stat.prayer"), 1)
                }
            }
        }
    }

    private fun magic(npc: Npc, target: Player) {
        val remaining = deps.playerList.filter {
            it !== target && it.isValidTarget() && it.level == npc.level && npc.isWithinDistance(it, 15)
        }.toMutableList()
        var victim = target
        var previous: Player? = null
        var elapsed = 0
        while (true) {
            val source = previous
            val spot = "spotanim.kalphite_glow_travel".asRSCM(RSCMType.SPOTANIM)
            val projectile = if (source == null) ProjAnim.fromNpcToPlayer(npc, victim, spot, MAGIC_TRAVEL)
                else ProjAnim(spot, MAGIC_TRAVEL.startHeight, MAGIC_TRAVEL.endHeight,
                    MAGIC_TRAVEL.delay, ProjAnim.calculateEndTime(MAGIC_TRAVEL, source.coords.chebyshevDistance(victim.coords)),
                    MAGIC_TRAVEL.angle, MAGIC_TRAVEL.progress, -(source.slotId + 1), -(victim.slotId + 1), source.coords, victim.coords)
            val delayed = projectile.copy(startTime = projectile.startTime + elapsed, endTime = projectile.endTime + elapsed)
            deps.worldRepo.projAnim(delayed)
            victim.finishNpcHit(npc, delayed.serverCycles, HitType.Magic, deps.random.of(32), deps.playerHitModifier)
            victim.spotanim("spotanim.kalphite_glow_impact", delay = delayed.clientCycles)
            elapsed = delayed.endTime
            val next = remaining.filter { it.coords.chebyshevDistance(victim.coords) <= 8 }
                .minByOrNull { it.coords.chebyshevDistance(victim.coords) } ?: break
            previous = victim
            victim = next
            remaining.remove(next)
        }
    }

    private fun hatchWorker(queen: Npc, target: Player?) {
        if (target == null || !target.isValidTarget()) return
        val active = workers.getOrPut(queen) { mutableListOf() }
        active.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        if (active.size >= 12) return
        val zone = ZoneKey.from(queen.coords)
        val eggs = (-1..1).flatMap { dx -> (-1..1).flatMap { dz ->
            locs.findAll(ZoneKey(zone.x + dx, zone.z + dz, zone.level)).toList()
        } }.filter { it.id == "loc.kalphite_chamber_egg_update".asRSCM(RSCMType.LOC) && queen.isWithinDistance(it.coords, 3) }
        val egg = deps.random.pickOrNull(eggs) ?: return
        val tile = listOf(egg.coords.translate(1, 0), egg.coords.translate(-1, 0), egg.coords.translate(0, 1), egg.coords.translate(0, -1))
            .firstOrNull { !deps.collision.queenTileBlocked(it) } ?: return
        val type = ServerCacheManager.getNpc("npc.kalphite_worker_chamber".asRSCM(RSCMType.NPC))!!
        val worker = Npc(type).apply { coords = tile }
        deps.npcRepo.add(worker, 100)
        worker.opPlayer2(target, interactions)
        active += worker
    }

    private fun cleanupWorkers(queen: Npc) {
        workers.remove(queen)?.filter { it.isSlotAssigned }?.forEach { deps.npcRepo.del(it, Int.MAX_VALUE) }
    }

    companion object {
        const val GROUND = "npc.kalphite_queen"
        const val FLYING = "npc.kalphite_flyingqueen"
        private const val ATTACK = "kalphite_queen.attack"
        val SPEC = boss(GROUND, FLYING) {
            stats(attackRate = 4)
            val attack = ability("attack") { include(external(ATTACK)) }
            phase("combat") { rotationSelector { +then(attack) } }
        }
        private val RANGED_TRAVEL = ProjAnimType(startHeight = 50, endHeight = 31, delay = 20, lengthAdjustment = 25, stepMultiplier = 3, angle = 10)
        private val MAGIC_TRAVEL = ProjAnimType(startHeight = 50, endHeight = 31, delay = 20, lengthAdjustment = 35, stepMultiplier = 5, angle = 10)
    }
}
