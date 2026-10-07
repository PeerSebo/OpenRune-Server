package org.rsmod.content.raids.theatreofblood

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.get
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
internal class TheatreRoomObjects @Inject constructor(
    private val instances: InstanceManager,
    private val locs: LocRepository,
    private val objs: ObjRepository,
    private val collision: CollisionFlagMap,
    private val inventory: TheatreInventory,
    private val npcs: NpcRepository,
) {
    private class Objects {
        val ground = mutableListOf<Obj>()
        val locations = mutableListOf<LocInfo>()
        val npcs = mutableListOf<Npc>()
    }

    private val owned = IdentityHashMap<TheatreRun, Objects>()

    fun enterRoom(run: TheatreRun) {
        clear(run)
        val source = sequenceOf(run.roomrow.orator) + TheatreRoomObjectRules.exitCandidates(run.roomrow.arrival)
        val tile = source.filter { run.room == 5 || !run.roomrow.contains(it) }
            .map { resolve(run, it) }.firstOrNull { clearFootprint(it, 2) }
            ?: error("No accessible Orator position in ${run.roomrow.name}")
        val orator = Npc(ORATOR, tile)
        orator.mode = NpcMode.None
        orator.respawns = false
        npcs.add(orator, Int.MAX_VALUE)
        instances.attachNpc(run.session.id, orator)
        owned.getOrPut(run) { Objects() }.npcs += orator
        if (run.room == 5) {
            for (barrier in run.roomrow.barriers) {
                spawn(run, "loc.tob_walkway_verzik_barrier_op", resolve(run, barrier), LocAngle.West)
            }
        }
        if (run.room == 5 && run.stage == TheatreManager.ACTIVE) enterFinalRoom(run)
    }

    fun enterFinalRoom(run: TheatreRun) {
        check(run.room == 5 && run.stage == TheatreManager.ACTIVE)
        val objects = owned.getOrPut(run) { Objects() }
        if (objects.ground.isNotEmpty()) return
        val participants = occupants(run)
        val drops = participants.map { player ->
            val index = run.members.indexOf(player)
            val source = TheatreData.rules.dawnbringerTiles[index]
            val tile = resolve(run, source)
            check(clearFootprint(tile, 1)) { "Dawnbringer pickup tile is blocked: $source" }
            Obj.fromOwner(player, tile, "obj.verzik_special_weapon", 1)
        }
        try {
            for (obj in drops) {
                check(objs.add(obj, Int.MAX_VALUE, Int.MAX_VALUE)) { "Failed to place Dawnbringer" }
                objects.ground += obj
            }
        } catch (failure: Throwable) {
            clearDawnbringer(run)
            throw failure
        }
    }

    fun complete(run: TheatreRun) {
        check(run.stage == TheatreManager.COMPLETED)
        clear(run)
        val rules = TheatreData.rules
        val entry = resolve(run, rules.treasuryEntry)
        val chests = rules.rewardChests.map { resolve(run, it) }
        check(clearFootprint(entry, 1)) { "Theatre treasure entry is blocked" }
        check(chests.all { clearFootprint(it, 3) }) { "Theatre treasure chest footprint is blocked" }
        try {
            for ((index, tile) in chests.withIndex()) {
                spawn(run, "loc.tob_treasureroom_chest_loc$index", tile)
            }
        } catch (failure: Throwable) {
            clear(run)
            throw failure
        }
        for (player in occupants(run)) {
            refresh(player, run)
            PathingEntityCommon.telejump(player, collision, entry)
        }
    }

    fun refresh(player: Player, run: TheatreRun) {
        if (run.stage != TheatreManager.COMPLETED || player !in occupants(run)) return
        val uniqueIds = TheatreData.rewardsList.filter { it.unique }
            .mapTo(mutableSetOf()) { it.obj.asRSCM(RSCMType.OBJ) }
        val rare = run.members.map { member ->
            inventory.rewards(member).filterNotNull { true }.any { it.id in uniqueIds }
        }
        val ownIndex = run.members.indexOf(player)
        val pending = inventory.rewards(player).isNotEmpty()
        for (index in 0..4) {
            val state = TheatreRoomObjectRules.chestState(index == ownIndex && pending,
                rare.getOrElse(index) { false })
            VarPlayerIntMapSetter.set(player, "varbit.tob_treasureroom_chest_$index", state)
        }
    }

    fun clearDawnbringer(run: TheatreRun) {
        val objects = owned[run] ?: return
        objects.ground.forEach { objs.del(it, Int.MAX_VALUE) }
        objects.ground.clear()
    }

    fun clear(run: TheatreRun) {
        val objects = owned.remove(run) ?: return
        objects.ground.forEach { objs.del(it, Int.MAX_VALUE) }
        objects.locations.asReversed().forEach { locs.del(it, Int.MAX_VALUE) }
        objects.npcs.forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
    }

    private fun spawn(run: TheatreRun, symbol: String, tile: CoordGrid, angle: LocAngle = LocAngle.West) {
        val shape = LocShape.CentrepieceStraight
        val loc = LocInfo(LocLayerConstants.of(shape.id), tile,
            LocEntity(symbol.asRSCM(RSCMType.LOC), shape.id, angle.id))
        check(locs.add(loc, Int.MAX_VALUE)) { "Failed to place Theatre object: $symbol at $tile" }
        owned.getOrPut(run) { Objects() }.locations += loc
    }

    private fun resolve(run: TheatreRun, source: CoordGrid): CoordGrid =
        checkNotNull(instances.resolveCoord(run.session, source)) {
            "Theatre object is outside its instance: $source"
        }

    private fun occupants(run: TheatreRun): List<Player> =
        run.members.filter { it.uuid in run.session.occupants }

    private fun clearFootprint(tile: CoordGrid, size: Int): Boolean =
        (0 until size).all { x -> (0 until size).all { z ->
            collision[tile.translate(x, z)] and WALK_BLOCKED == 0
        } }

    companion object {
        const val EXIT = "loc.tob_treasureroom_teleportout"
        val ORATOR: String get() = TheatreData.rules.exitOrator
        private const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or
            CollisionFlag.GROUND_DECOR
    }
}

internal object TheatreRoomObjectRules {
    fun chestState(owner: Boolean, rare: Boolean): Int =
        (if (owner) 2 else 0) + (if (rare) 1 else 0)

    fun exitCandidates(entry: CoordGrid): Sequence<CoordGrid> = sequence {
        for (distance in 2..6) {
            for (x in -distance..distance) {
                for (z in -distance..distance) {
                    if (kotlin.math.max(kotlin.math.abs(x), kotlin.math.abs(z)) == distance) {
                        yield(entry.translate(x, z))
                    }
                }
            }
        }
    }
}
