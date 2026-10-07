package org.rsmod.content.minigames.blastfurnace.core

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcMode
import dev.openrune.util.BlockWalk
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class BlastFurnaceMachine
@Inject
constructor(
    private val locs: LocRepository,
    private val npcs: NpcRepository,
    private val world: WorldRepository,
    private val collision: CollisionFlagMap,
) {
    private var initialized = false
    private var cycle = 0
    private var machineLocs = emptyList<LocInfo>()
    private var workers = emptyList<Npc>()
    private val beltLoads = mutableListOf<BeltDisplay>()

    private data class BeltDisplay(val npc: Npc, val removeAt: Int, var age: Int = 0)

    fun tick() {
        if (!initialized) {
            restoreMachinery()
            if (!initialized) return
            animateMachinery()
        }
        cycle = (cycle + 1) % 60
        advanceBelt()
        for (worker in workers) {
            if (!worker.isSlotAssigned || worker.isBusy || worker.interaction != null) continue
            when (worker.id) {
                "npc.blast_furnace_automaton_pumper".asRSCM(),
                "npc.blast_furnace_automaton_pedaller".asRSCM() ->
                    worker.faceSquare(worker.coords.translate(-1, 0))
                "npc.blast_furnace_automaton_cog_repairer".asRSCM() ->
                    if (cycle % 5 == 0) work(worker, COGS, "seq.human_hammer_hit")
                "npc.blast_furnace_automaton_pipe_repairer".asRSCM() ->
                    if (cycle % 5 == 0) work(worker, PIPES, "seq.human_hammer_hit")
                "npc.blast_furnace_automaton_stoker_empty".asRSCM(),
                "npc.blast_furnace_automaton_stoker_coke".asRSCM() ->
                    if (cycle % 4 == 0) workStoker(worker)
            }
        }
    }

    fun restoreMachinery() {
        if (initialized) return
        val zones =
            (1925 / 8..1965 / 8).flatMap { x ->
                (4945 / 8..4980 / 8).map { z -> ZoneKey(x, z, 0) }
            }
        val objects = zones.flatMap { locs.findAll(it).toList() }
        val occupants = zones.flatMap { npcs.findAll(it).toList() }
        val workerIds = WORKERS.map { it.asRSCM() }.toSet()
        val beltIds = BELTS.map { it.asRSCM() }.toSet()
        val foundWorkers = occupants.filter { it.id in workerIds }
        if (foundWorkers.isEmpty() || objects.none { it.id in beltIds }) return

        val repairs = REPAIRS.mapKeys { it.key.asRSCM() }
        val workerMachinery = WORKER_MACHINERY.map { it.asRSCM() }.toSet()
        machineLocs =
            objects.mapNotNull { loc ->
                if (loc.id in workerMachinery) {
                    locs.del(loc, Int.MAX_VALUE)
                    return@mapNotNull null
                }
                val replacement = repairs[loc.id]
                if (replacement == null) {
                    loc
                } else {
                    val type = checkNotNull(ServerCacheManager.getObject(replacement.asRSCM()))
                    locs.change(loc, type, Int.MAX_VALUE)
                    loc.copy(entity = loc.entity.copy(id = type.id))
                }
            }
        workers = foundWorkers
        for (worker in workers) {
            worker.mode = NpcMode.None
            worker.routeDestination.clear()
            worker.teleport(collision, workerDestination(worker))
            worker.faceSquare(worker.coords.translate(-1, 0))
        }
        val displayIds = ORE_DISPLAYS.map { it.asRSCM() }.toSet()
        occupants.filter { it.id in displayIds }.forEach { npcs.del(it, Int.MAX_VALUE) }
        initialized = true
    }

    fun animateMachinery() {
        val beltIds = BELTS.map { it.asRSCM() }.toSet()
        val cogIds = COGS.map { it.asRSCM() }.toSet()
        val suffix = if (beltLoads.isEmpty()) "_end" else ""
        for (loc in machineLocs) {
            when (loc.id) {
                in beltIds -> world.locAnim(loc, "seq.blast_furnace_conveyer_belt$suffix")
                in cogIds -> world.locAnim(loc, "seq.blast_furnace_conveyer_cogs$suffix")
            }
        }
    }

    fun loadBelt(ores: List<String>) {
        restoreMachinery()
        if (!initialized) return
        for (ore in ores.distinct()) {
            val symbol = "npc.blast_furnace_${ore.removePrefix("obj.")}"
            if (symbol !in ORE_DISPLAYS || beltLoads.any { it.npc.id == symbol.asRSCM() }) continue
            val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM()))
            val npc = Npc(type.copy(blockWalk = BlockWalk.None), BELT_START)
            npc.mode = NpcMode.None
            val fall = checkNotNull(ServerCacheManager.getAnim("seq.blast_furnace_conveyer_ore_fall".asRSCM()))
            val removeAt = FALL_START_TICK + fall.tickDuration.coerceAtLeast(1)
            npcs.add(npc, removeAt + 2)
            beltLoads += BeltDisplay(npc, removeAt)
        }
        animateMachinery()
    }

    fun pourBars() {
        machineLocs.firstOrNull { it.id == "loc.blast_furnace_dispenser".asRSCM() }?.let {
            world.locAnim(it, "seq.blast_furnace_ore_dispense")
        }
    }

    private fun advanceBelt() {
        if (beltLoads.isEmpty()) return
        val iterator = beltLoads.iterator()
        while (iterator.hasNext()) {
            val display = iterator.next()
            if (!display.npc.isSlotAssigned) {
                iterator.remove()
                continue
            }
            when (++display.age) {
                1 -> PathingEntityCommon.exactMove(
                    display.npc, BELT_START, BELT_END,
                    delay1 = 0, delay2 = BELT_MOVE_CLIENT_CYCLES, dir = 0, collision,
                )
                FALL_START_TICK -> display.npc.anim("seq.blast_furnace_conveyer_ore_fall")
                display.removeAt -> {
                    npcs.del(display.npc, Int.MAX_VALUE)
                    iterator.remove()
                }
            }
        }
        if (beltLoads.isEmpty()) animateMachinery()
    }

    private fun work(worker: Npc, targetSymbols: Set<String>, animation: String) {
        val targets = targetSymbols.map { it.asRSCM() }.toSet()
        val nearest =
            machineLocs.filter { it.id in targets }.minByOrNull {
                abs(worker.coords.x - it.coords.x) + abs(worker.coords.z - it.coords.z)
            } ?: return
        worker.faceSquare(nearest.coords)
        worker.anim(animation)
    }

    private fun workerDestination(worker: Npc): CoordGrid {
        return when (worker.id) {
            "npc.blast_furnace_automaton_pumper".asRSCM() -> CoordGrid(1950, 4961, 0)
            "npc.blast_furnace_automaton_pedaller".asRSCM() -> CoordGrid(1947, 4966, 0)
            "npc.blast_furnace_automaton_stoker_empty".asRSCM(),
            "npc.blast_furnace_automaton_stoker_coke".asRSCM() -> CoordGrid(1950, 4963, 0)
            else -> worker.coords
        }
    }

    private fun workStoker(worker: Npc) {
        val stage = (cycle / 4) % 3
        when (stage) {
            1 -> {
                worker.faceSquare(worker.coords.translate(0, 1))
                worker.anim("seq.human_shovel_coke_up")
            }
            2 -> {
                worker.faceSquare(worker.coords.translate(-1, 0))
                worker.anim("seq.human_shovel_coke_down")
            }
            else -> {
                worker.faceSquare(worker.coords.translate(-1, 0))
                worker.anim("seq.human_shovel_coke_fin")
            }
        }
    }

    private companion object {
        const val FALL_START_TICK = 4
        const val BELT_MOVE_CLIENT_CYCLES = 90
        val BELT_START = CoordGrid(1942, 4966, 0)
        val BELT_END = CoordGrid(1942, 4963, 0)
        val BELTS = setOf("loc.blast_furnace_conveyer_belt_clickable", "loc.blast_furnace_conveyer_belt")
        val COGS =
            setOf("conveyer_cogs", "conveyer_cogs2", "conveyer_cogs3", "conveyer_cogs4", "conveyer_cogs5")
                .map { "loc.blast_furnace_$it" }.toSet()
        val PIPES = setOf("loc.blast_furnace_pipes2", "loc.blast_furnace_pipes6")
        val WORKER_MACHINERY = setOf("loc.blast_furnace_pedals", "loc.blast_furnace_pump")
        val REPAIRS =
            mapOf(
                "stove_low" to "stove_full",
                "stove_medium" to "stove_full",
                "conveyer_cogs_broken" to "conveyer_cogs",
                "conveyer_cogs2_broken" to "conveyer_cogs2",
                "pipes2_broken" to "pipes2",
                "pipes6_broken" to "pipes6",
            ).map { (from, to) -> "loc.blast_furnace_$from" to "loc.blast_furnace_$to" }.toMap()
        val WORKERS =
            setOf("cog_repairer", "pipe_repairer", "pedaller", "pumper", "stoker_empty", "stoker_coke")
                .map { "npc.blast_furnace_automaton_$it" }
        val ORE_DISPLAYS =
            setOf(
                "tin_ore", "copper_ore", "iron_ore", "mithril_ore", "adamantite_ore", "runite_ore",
                "silver_ore", "gold_ore", "coal", "perfect_gold_ore", "smithing_catalyst", "lead_ore", "nickel_ore",
            ).map { "npc.blast_furnace_$it" }
    }
}
