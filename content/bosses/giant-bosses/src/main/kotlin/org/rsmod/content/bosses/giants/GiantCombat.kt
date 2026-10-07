package org.rsmod.content.bosses.giants

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.PlayerAbsorption
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.knockback
import org.rsmod.api.player.output.mes
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

class GiantCombat @Inject internal constructor(
    deps: BossDeps,
    private val rooms: GiantRooms,
    private val ray: RayCastValidator,
    private val oborHits: OborRangedModifier,
    private val movement: NpcMovementProcessor,
) : BossPluginScript(deps) {
    override val spec = boss("npc.hillgiant_boss") {
        stats(attackRate = 6)
        val attack = ability("attack") { include(external("giants.obor")) }
        phase("fight") { rotationSelector { +then(attack) } }
    }
    private val bryophyta = boss("npc.gb_mossgiant") {
        stats(attackRate = 6)
        val attack = ability("attack") { include(external("giants.bryophyta")) }
        phase("fight") { rotationSelector { +then(attack) } }
    }
    private val growthling = boss("npc.gb_growthling") {
        stats(attackRate = 4)
        val attack = ability("attack") { include(external("giants.growthling")) }
        phase("fight") { rotationSelector { +then(attack) } }
    }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("giants.obor") { _, npc, target, _ -> obor(npc, target) }
        deps.extensionRegistry.register("giants.bryophyta") { _, npc, target, _ -> bryophyta(npc, target) }
        deps.extensionRegistry.register("giants.growthling") { _, npc, target, _ -> growthling(npc, target) }
        BossCombat.register(this, spec, deps, onCombatTick = { npc, target ->
            if (valid(npc, target, 10) != null) pursueObor(npc, target, movement)
        })
        BossCombat.register(this, bryophyta, deps, onModifyHit = {
            val room = rooms.forNpc(npc)
            // Poison/venom has no player source and continues through the growthling shield.
            if (room != null && room.growthlings.isNotEmpty() && (hit.isFromPlayer || hit.isFromNpc)) {
                hit.damage = 0
            }
        })
        onEvent<NpcHitEvents.BeforeImpact>("npc.gb_mossgiant".asRSCM()) {
            val room = rooms.forNpc(npc)
            if (room != null && room.growthlings.isNotEmpty() && (hit.isFromPlayer || hit.isFromNpc)) damage = 0
        }
        val cuttingTools = GiantTools.equipped.map {
            checkNotNull(ServerCacheManager.getItem(it.asRSCM(RSCMType.OBJ)))
        }
        BossCombat.register(this, growthling, deps, onModifyHit = {
            val room = rooms.forNpc(npc)
            val cutting = room != null && (npc in room.finishing ||
                (hit.isFromPlayer && hit.type == HitType.Melee && cuttingTools.any(hit::isRighthandObj)))
            if (cutting) room?.finishing?.add(npc)
            hit.damage = GiantRules.growthlingDamage(npc.hitpoints, hit.damage, cutting)
        })
        val type = checkNotNull(ServerCacheManager.getNpc("npc.gb_growthling".asRSCM(RSCMType.NPC)))
        onEvent<NpcHitEvents.BeforeImpact>(type.id) {
            val room = rooms.forNpc(npc) ?: return@onEvent
            damage = GiantRules.growthlingDamage(npc.hitpoints, damage, npc in room.finishing)
        }
        onNpcHit(type) {
            val room = rooms.forNpc(npc) ?: return@onNpcHit
            if (npc.hitpoints <= 0 && npc in room.finishing) {
                rooms.growthlingDied(room, npc)
            }
        }
    }

    private fun valid(npc: Npc, target: Player, range: Int): GiantRoom? {
        val room = rooms.forNpc(npc) ?: return null
        if (rooms.forPlayer(target) !== room || room.owner !== target || !target.isValidTarget() ||
            npc.hitpoints <= 0 || !npc.isVisible || GiantRules.distance(npc.coords, npc.size, target.coords) > range) return null
        if (room.kind == GiantBoss.OBOR && room.source(target.coords).z >= 9807) {
            npc.clearInteractionRoute()
            return null
        }
        if (!ray.hasLineOfSight(target.coords, npc.coords, destWidth = npc.size, destLength = npc.size)) return null
        return room
    }

    private fun obor(npc: Npc, target: Player) {
        val room = valid(npc, target, 10) ?: return
        val adjacent = GiantRules.distance(npc.coords, npc.size, target.coords) <= 1
        if (adjacent && deps.random.of(2) == 0) {
            npc.anim("seq.giant_update_basic_attack")
            deps.worldRepo.soundArea(npc.coords, "synth.GIANT_ATTACK", radius = 8)
            val accurate = deps.accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, deps.random)
            target.finishNpcHit(npc, 1, HitType.Melee, if (accurate) deps.random.of(23) else 0, deps.playerHitModifier)
            if (deps.random.of(4) == 0) knockback(room, npc, target)
        } else {
            npc.anim("seq.giant_basic_stamp")
            deps.worldRepo.soundArea(npc.coords, "synth.GIANT_ATTACK", radius = 8)
            val site = target.coords
            target.spotanim("spotanim.rockfall", delay = 20, height = 100)
            val accurate = deps.accuracy.rollRangedAccuracy(npc, target, deps.random)
            val damage = if (accurate) deps.random.of(27) else 0
            deps.worldQueues.add(2) {
                if (rooms.forPlayer(target) === room && target.isValidTarget() && target.coords == site) {
                    deps.worldRepo.soundArea(site, "synth.ROCK_IMPACT", radius = 8)
                    target.finishNpcHit(npc, 1, HitType.Ranged, damage, oborHits)
                }
            }
        }
    }

    private fun knockback(room: GiantRoom, npc: Npc, target: Player) {
        deps.worldQueues.add(1) {
            if (rooms.forPlayer(target) !== room || !target.isValidTarget() ||
                !npc.isSlotAssigned || npc.hitpoints <= 0 || npc.level != target.level) return@add
            val dx = (target.coords.x * 2 - (npc.coords.x * 2 + npc.size - 1)).compareTo(0)
            val dz = (target.coords.z * 2 - (npc.coords.z * 2 + npc.size - 1)).compareTo(0)
            var destination = target.coords
            repeat(3) {
                val next = CoordGrid(destination.x + dx, destination.z + dz, destination.level)
                if (room.source(next).z < 9807 &&
                    deps.collision[next.x, next.z, next.level] and WALK_BLOCK == 0 &&
                    ray.hasLineOfWalk(destination, next)) destination = next
            }
            val facing = Direction.entries.firstOrNull { it.xOff == -dx && it.zOff == -dz }?.angle ?: target.faceAngle
            target.knockback(destination, facing, deps.collision)
            target.mes("Obor knocks you back!")
        }
    }

    private fun bryophyta(npc: Npc, target: Player) {
        val room = valid(npc, target, 10) ?: return
        if (room.growthlings.isEmpty() && deps.mapClock.cycle >= room.nextGrowthlings && deps.random.of(5) == 0) {
            npc.anim("seq.giant_basic_inject_grow")
            rooms.summon(room)
            return
        }
        val melee = GiantRules.distance(npc.coords, npc.size, target.coords) <= 1 && deps.random.of(2) == 0
        val accurate = if (melee) deps.accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, deps.random)
            else deps.accuracy.rollMagicAccuracy(npc, target, deps.random)
        npc.anim(if (melee) "seq.giant_update_moss_attack" else "seq.giant_basic_spell")
        if (!melee) {
            deps.worldRepo.projAnim(ProjAnim.fromNpcToPlayer(npc, target,
                "spotanim.curse_travel".asRSCM(RSCMType.SPOTANIM), MAGIC_FLIGHT))
            target.spotanim("spotanim.roving_mossgiant_impact", delay = 20)
        }
        target.finishNpcHit(npc, if (melee) 1 else 2, if (melee) HitType.Melee else HitType.Magic,
            if (accurate) deps.random.of(17) else 0, deps.playerHitModifier)
        if (target.members && accurate && deps.random.of(6) == 0) CombatEffects.poison(target, 8)
    }

    private fun growthling(npc: Npc, target: Player) {
        valid(npc, target, 1) ?: return
        npc.anim("seq.tanglefoot_attack")
        val accurate = deps.accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Slash, deps.random)
        target.finishNpcHit(npc, 1, HitType.Melee, if (accurate) deps.random.of(6) else 0, deps.playerHitModifier)
    }

    private companion object {
        const val WALK_BLOCK = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR
        val MAGIC_FLIGHT = ProjAnimType(startHeight = 60, endHeight = 30, delay = 0, angle = 10,
            lengthAdjustment = 15, progress = 0, stepMultiplier = 3)
    }
}

internal fun pursueObor(npc: Npc, target: Player, movement: NpcMovementProcessor) {
    // AP interactions skip the engine's movement pass, including during attack cooldowns.
    if (npc.hasMovedThisCycle || npc.movementLocked || npc.level != target.level ||
        GiantRules.distance(npc.coords, npc.size, target.coords) <= 1) return
    npc.routeRequest = RouteRequestPathingEntity(target.avatar)
    npc.tempMoveSpeed = MoveSpeed.Walk
    movement.process(npc)
}

internal class OborRangedModifier @Inject constructor(private val events: EventBus) : PlayerHitModifier {
    override fun HitBuilder.modify(target: Player) {
        events.publish(PlayerHitEvents.Modify(target, this))
        if (target.adminGodMode) { damage = 0; return }
        damage = GiantRules.oborRangedDamage(damage, target.vars["varbit.prayer_protectfrommissiles"] == 1)
        damage = PlayerAbsorption.absorb(target, damage)
    }
}
