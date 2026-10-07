package org.rsmod.content.generic.npcs.ambient

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.npc.vars.boolVarn
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class MuseumWorkman @Inject constructor(
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val world: WorldRepository,
    private val routes: RouteFactory,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val opened = mutableMapOf<Gate, GateChange>()

    override fun ScriptContext.startup() {
        onAiTimer(Empty) { travel(npc) }
        onAiTimer(Loaded) { travel(npc) }
        for (gate in Gates) {
            onAiTimer(gate.guard) { operateGate(npc, gate) }
            onOpNpc1(gate.guard) { refuseEntrance(gate) }
        }
        for ((symbol, gate) in GuardedEntrances) {
            onOpLoc1(symbol) {
                if (it.loc.coords.level == gate.center.level && it.loc.coords.chebyshevDistance(gate.center) <= 3) {
                    refuseEntrance(gate)
                }
            }
        }
        onEvent<NpcStateEvents.Spawn>(Empty.asRSCM()) {
            if (npc.spawnCoords == Spawn) travel(npc)
        }
        onEvent<NpcStateEvents.Spawn>(Loaded.asRSCM()) {
            if (npc.spawnCoords == Spawn) travel(npc)
        }
        onEvent<NpcStateEvents.Spawn>(DigsiteGate.guard.asRSCM()) { faceGate(npc) }
        onEvent<NpcStateEvents.Spawn>(Loader.asRSCM()) { faceLoadingStance(npc) }
        onGameStartup {
            npcs.findAll(Spawn).filter { it.isType(Empty) || it.isType(Loaded) }.forEach(::travel)
            npcs.findAll(DigsiteGate.guardPosition).forEach(::faceGate)
            npcs.findAll(LoaderPosition).forEach(::faceLoadingStance)
        }
    }

    internal fun travel(npc: Npc) {
        if (npc.spawnCoords != Spawn || !npc.available()) return
        val loaded = npc.isVisType(Loaded)
        if (npc.waypoint == 0) npc.waypoint = if (loaded) 2 else ToDigsite.lastIndex
        if (npc.pause > 0) {
            npc.pause--
            if (loaded && npc.pause == UnloadTicks) {
                npc.say("Another lot for ya!")
                npc.anim(Unload)
            }
            if (!loaded && npc.pause == LoaderThrowTicks) {
                loader()?.apply {
                    faceLoadingStance(this)
                    say("Got another load for ya!")
                    anim(LoaderThrow)
                }
            }
            if (!loaded && npc.pause == LoaderThrowTicks - LoaderPickupTicks) {
                loader()?.faceSquare(npc.coords)
            }
            if (!loaded && npc.pause == 2) npc.say("Great, just what my arms need!")
            if (npc.pause == 0) {
                if (!loaded) loader()?.apply {
                    resetAnim()
                    faceLoadingStance(this)
                }
                npc.resetAnim()
                npc.transmog(checkNotNull(ServerCacheManager.getNpc((if (loaded) Empty else Loaded).asRSCM())), Int.MAX_VALUE)
                npc.waypoint = 1
            }
            return
        }
        val path = if (loaded) ToMuseum else ToDigsite
        if (npc.routeDestination.isNotEmpty()) {
            greetGuards(npc)
            if (npc.hasMovedPreviousCycle && ++npc.squeak % 4 == 0) {
                world.soundArea(npc.coords, "synth.VM_WHEELBARROW_SQUEEK", radius = 7)
            }
            if (npc.cyclesWithoutMovement < BlockedRouteRetry) return
            npc.abortRoute()
        }
        if (npc.coords == path[npc.waypoint - 1]) npc.waypoint++
        if (npc.waypoint > path.size) {
            exchangeLoad(npc, loaded)
            npc.greeted = false
            return
        }
        if (crossGate(npc, path[npc.waypoint - 1])) return
        val route = routes.create(npc.avatar, path[npc.waypoint - 1])
        if (route.success && !route.alternative) {
            npc.defaultMode()
            npc.walk(route.map { CoordGrid(it.x, it.z, it.level) })
        }
    }

    internal fun crossGate(npc: Npc, destination: CoordGrid): Boolean {
        if (npc.spawnCoords != Spawn || npc.size != 2 || !npc.available() || (!npc.isType(Empty) && !npc.isType(Loaded))) return false
        val passage = Passages.firstOrNull { it.from == npc.coords && it.destination == destination && npc.isVisType(it.type) } ?: return false
        if (passage.gate !in opened || passage.gate.changes.any { (coords, loc) -> locs.findExact(coords, LocShape.WallStraight) != loc }) return false
        // Native invisible doors keep player collision while the workman crosses by NPC teleport.
        npc.teleport(collision, passage.to)
        return true
    }

    private fun exchangeLoad(npc: Npc, loaded: Boolean) {
        npc.noneMode()
        if (loaded) {
            npc.faceSquare(npc.coords.translate(0, 1), npc.size, npc.size)
            npc.pause = UnloadSettleTicks + UnloadTicks
        } else {
            loader()?.apply {
                resetAnim()
                faceLoadingStance(this)
            }
            npc.faceSquare(npc.coords.translate(0, 1), npc.size, npc.size)
            npc.pause = LoaderReadyTicks + LoaderThrowTicks
        }
    }

    private fun loader(): Npc? =
        npcs.findAll(LoaderPosition).firstOrNull { it.isType(Loader) && it.available() }

    private fun faceLoadingStance(npc: Npc) {
        if (npc.isType(Loader) && npc.spawnCoords == LoaderPosition) {
            npc.respawnDirection(Direction.West)
            npc.faceSquare(MuseumWorkmanRoute.rocks)
        }
    }

    internal fun operateGate(guard: Npc, gate: Gate) {
        if (guard.spawnCoords != gate.guardPosition) return
        val active = opened[gate]
        if (active != null) {
            if (active.workman.isSlotAssigned && active.workman.isVisible &&
                active.workman.isVisType(active.passage.type) &&
                active.workman.coords.chebyshevDistance(gate.center) <= 4 &&
                !active.passage.cleared(active.workman.coords)
            ) {
                guard.gateCloseDelay = GateCloseTailTicks + 1
                return
            }
            if (--guard.gateCloseDelay > 0) return
            closeGate(guard, gate, active)
            return
        }
        if (!guard.available()) return
        val nearby = npcs.findAll(ZoneKey.from(gate.center), 1).firstOrNull {
            it.spawnCoords == Spawn && (it.isType(Empty) || it.isType(Loaded)) &&
                it.available() && it.isSlotAssigned && passageApproach(it, gate) != null
        } ?: return
        val passage = checkNotNull(passageApproach(nearby, gate))
        val originals = gate.changes.keys.associateWith { locs.findExact(it, LocShape.WallStraight) }
        val existing = originals.values.filterNotNull()
        if (existing.size < 2 || existing.any { it.id !in GateIds }) return
        for ((coords, replacement) in gate.changes) {
            originals[coords]?.let { locs.del(it, Int.MAX_VALUE) }
            replacement?.let { locs.add(it, Int.MAX_VALUE) }
        }
        opened[gate] = GateChange(originals, nearby, passage)
        guard.gateCloseDelay = GateCloseTailTicks + 1
        guard.faceSquare(nearby.coords)
        if (gate == DigsiteGate) {
            val loaded = nearby.visType.id == Loaded.asRSCM()
            guard.say(if (loaded) "Keep 'em coming!" else "Don't dawdle there!")
            if (loaded) nearby.say("Bleah")
        }
    }

    private fun passageApproach(npc: Npc, gate: Gate): Passage? = Passages.firstOrNull {
        it.gate == gate && npc.isVisType(it.type) && npc.pause == 0 &&
            npc.coords.level == gate.center.level && npc.coords.chebyshevDistance(it.from) <= 2 &&
            !it.cleared(npc.coords) &&
            (if (npc.isVisType(Loaded)) ToMuseum else ToDigsite).getOrNull(npc.waypoint - 1) == it.destination
    }

    private fun closeGate(guard: Npc, gate: Gate, change: GateChange) {
        opened.remove(gate)
        for ((coords, replacement) in gate.changes) {
            val current = locs.findExact(coords, LocShape.WallStraight)
            if (current != replacement) continue
            current?.let { locs.del(it, Int.MAX_VALUE) }
            change.originals[coords]?.let { locs.add(it, Int.MAX_VALUE) }
        }
        faceGate(guard)
    }

    private suspend fun ProtectedAccess.refuseEntrance(gate: Gate) {
        val guard = npcs.findAll(gate.guardPosition).firstOrNull { it.isType(gate.guard) && it.isVisible }
        guard?.faceSquare(player.coords)
        startDialogue {
            chatNpcSpecific("Museum guard", gate.guard, neutral,
                if (gate == MuseumGate) "Sorry, this entrance is for the workmen delivering specimens. Please use the main museum entrance."
                else "Sorry, this gate is reserved for the museum workmen and their deliveries.")
        }
    }

    private fun greetGuards(npc: Npc) {
        if (npc.greeted || npc.coords.x !in 3268..3277 || npc.coords.z !in 3427..3429) return
        val guard = npcs.findAll(ZoneKey.from(npc.coords), 1).firstOrNull {
            it.available() && VarrockGuards.any(it::isType) && it.isWithinDistance(npc, 5)
        } ?: return
        guard.faceSquare(npc.coords)
        guard.say("Good day.")
        npc.faceSquare(guard.coords)
        npc.say("Morning!")
        npc.greeted = true
    }

    private fun faceGate(guard: Npc) {
        if (!guard.isType(DigsiteGate.guard) || guard.spawnCoords != DigsiteGate.guardPosition) return
        guard.respawnDirection(Direction.North)
        guard.faceSquare(guard.coords.translate(0, 1))
    }

    @OptIn(InternalApi::class)
    private fun Npc.available(): Boolean = isVisible && !isBusy && activeCoroutine == null && !hasInteraction()
    private class GateChange(val originals: Map<CoordGrid, LocInfo?>, val workman: Npc, val passage: Passage)

    private var Npc.waypoint by intVarn("varn.museum_workman_waypoint")
    private var Npc.pause by intVarn("varn.museum_workman_pause")
    private var Npc.squeak by intVarn("varn.museum_workman_squeak")
    private var Npc.greeted by boolVarn("varn.museum_workman_greeted")
    private var Npc.gateCloseDelay by intVarn("varn.museum_workman_gate_close_delay")

    internal data class Gate(val guard: String, val guardPosition: CoordGrid, val center: CoordGrid, val changes: Map<CoordGrid, LocInfo?>)
    private data class Passage(val gate: Gate, val type: String, val from: CoordGrid, val to: CoordGrid, val clear: CoordGrid, val destination: CoordGrid) {
        fun cleared(coords: CoordGrid): Boolean = when {
            to.x > from.x -> coords.x >= clear.x
            to.x < from.x -> coords.x <= clear.x
            to.z > from.z -> coords.z >= clear.z
            else -> coords.z <= clear.z
        }
    }

    internal companion object {
        private const val BlockedRouteRetry = 3
        const val Empty = "npc.vm_patrol_route_wheelbarrow_empty"
        const val Loaded = "npc.vm_patrol_route_wheelbarrow"
        const val Loader = "npc.vm_digworkman_loader"
        const val LoaderThrow = "seq.vm_wheelbarrow_loader"
        const val Unload = "seq.vm_patrol_wheelbarrow_dumping_rocks"
        private const val LoaderReadyTicks = 2
        private const val LoaderPickupTicks = 2
        private const val UnloadSettleTicks = 2
        private const val GateCloseTailTicks = 2
        private val UnloadTicks get() = checkNotNull(ServerCacheManager.getAnim(Unload.asRSCM())).tickDuration + 1
        private val LoaderThrowTicks get() = checkNotNull(ServerCacheManager.getAnim(LoaderThrow.asRSCM())).tickDuration + 1
        val VarrockGuards = setOf(
            "npc.fai_varrock_guard02",
            "npc.fai_varrock_guard02_variant01",
            "npc.fai_varrock_guard02_variant02",
            "npc.fai_varrock_guard02_f",
            "npc.fai_varrock_guard02_f_variant01",
            "npc.fai_varrock_guard02_f_variant02",
            "npc.fai_varrock_guard_captain02",
        )
        val Spawn get() = MuseumWorkmanRoute.spawn
        val LoaderPosition get() = MuseumWorkmanRoute.loader
        val UnloadPosition get() = ToMuseum.last()
        val LoadPosition get() = ToDigsite.last()
        val ToMuseum get() = MuseumWorkmanRoute.toMuseum
        val ToDigsite get() = MuseumWorkmanRoute.toDigsite
        private fun wall(symbol: String, x: Int, z: Int, angle: Int) = LocInfo(0, CoordGrid(x, z), LocEntity(symbol.asRSCM(), 0, angle))
        val DigsiteGate = Gate("npc.vm_museum_guard2", CoordGrid(3295, 3427), CoordGrid(3296, 3428), listOf(
            wall("loc.museum_workman_fencegate_open_l", 3294, 3429, 1), wall("loc.museum_workman_fencegate_open_r", 3295, 3429, 1),
            wall("loc.vm_fencing_invisible_wall", 3296, 3429, 0), wall("loc.vm_fencing_invisible_wall", 3296, 3428, 0),
        ).associateBy { it.coords })
        val MuseumGate = Gate("npc.vm_museum_guard3", CoordGrid(3263, 3441), CoordGrid(3264, 3441), listOf(
            wall("loc.vm_invisdoor", 3264, 3441, 1), wall("loc.vm_invisdoor", 3265, 3441, 1),
            wall("loc.fai_varrock_museum_door_open_r", 3264, 3442, 0), wall("loc.fai_varrock_museum_door_open_l", 3265, 3442, 2),
        ).associateBy { it.coords })
        val Gates = listOf(DigsiteGate, MuseumGate)
        private val GuardedEntrances = mapOf(
            "loc.vm_fencegate_l" to DigsiteGate,
            "loc.vm_fencegate_r" to DigsiteGate,
            "loc.museum_workman_fencegate_open_l" to DigsiteGate,
            "loc.museum_workman_fencegate_open_r" to DigsiteGate,
            "loc.fai_varrock_museum_door_closed_l" to MuseumGate,
            "loc.fai_varrock_museum_door_closed_r" to MuseumGate,
            "loc.vm_invisdoor" to MuseumGate,
        )
        private val Passages = listOf(
            Passage(DigsiteGate, Loaded, CoordGrid(3297, 3428), CoordGrid(3295, 3428), CoordGrid(3294, 3428), CoordGrid(3293, 3428)),
            Passage(DigsiteGate, Empty, CoordGrid(3294, 3428), CoordGrid(3296, 3428), CoordGrid(3296, 3428), CoordGrid(3298, 3428)),
            Passage(MuseumGate, Loaded, CoordGrid(3264, 3440), CoordGrid(3264, 3442), CoordGrid(3264, 3442), UnloadPosition),
            Passage(MuseumGate, Empty, CoordGrid(3264, 3442), CoordGrid(3264, 3440), CoordGrid(3264, 3439), CoordGrid(3264, 3438)),
        )
        private val GateIds = setOf(
            "loc.vm_fencegate_l".asRSCM(),
            "loc.vm_fencegate_r".asRSCM(),
            "loc.museum_workman_fencegate_open_l".asRSCM(),
            "loc.museum_workman_fencegate_open_r".asRSCM(),
            "loc.vm_fencing_invisible_wall".asRSCM(),
            "loc.vm_invisdoor".asRSCM(),
            "loc.vm_invisbarrier".asRSCM(),
            "loc.fai_varrock_museum_door_closed_l".asRSCM(),
            "loc.fai_varrock_museum_door_open_l".asRSCM(),
            "loc.fai_varrock_museum_door_closed_r".asRSCM(),
            "loc.fai_varrock_museum_door_open_r".asRSCM(),
        )
    }
}
