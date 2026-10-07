package org.rsmod.content.generic.locs.passages

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.events.interact.LocDefaultEvents
import org.rsmod.api.player.events.interact.OpDefaultEvent
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.generic.locs.gate.GateTranslations
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.get
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class GenericPassageScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val collision: CollisionFlagMap,
    private val locInteractions: LocInteractions,
) : PluginScript() {
    private val openedPanels = HashMap<PanelKey, OpenedPassage>()

    private val locNames: Map<Int, String> by lazy {
        val table = ConstantProvider.mappings[RSCMType.LOC.prefix].orEmpty()
        HashMap<Int, String>(table.size).also { names ->
            for ((name, id) in table) {
                names[id] = name.removePrefix("loc.").lowercase()
            }
        }
    }

    private val locsByTwinKey: Map<String, List<ObjectServerType>> by lazy {
        ServerCacheManager.getObjects().values.filter { it.id in locNames }.groupBy { twinKey(it) }
    }

    override fun ScriptContext.startup() {
        onProtectedEvent<LocDefaultEvents.Op1>(OpDefaultEvent.ID) { interact(it.loc, it.type, 0) }
        onProtectedEvent<LocDefaultEvents.Op2>(OpDefaultEvent.ID) { interact(it.loc, it.type, 1) }
        onProtectedEvent<LocDefaultEvents.Op3>(OpDefaultEvent.ID) { interact(it.loc, it.type, 2) }
    }

    private suspend fun ProtectedAccess.interact(
        loc: BoundLocInfo,
        type: ObjectServerType,
        opIndex: Int,
    ) {
        val op = type.actions.getOpOrNull(opIndex)
        val action = op?.let { Passages.classify(type.name, it, loc.shape) }
        if (action == null) {
            mes(constants.dm_default, ChatType.Engine)
            logger.debug { "No generic passage handling for loc '${type.name}' op=$op type=$type" }
            return
        }
        arriveDelay()
        when (action) {
            PassageAction.OpenDoor -> openDoor(loc, type)
            PassageAction.CloseDoor -> closeDoor(loc, type)
            PassageAction.OpenTrapdoor -> openTrapdoor(loc, type)
            PassageAction.ClimbUp -> climb(type, up = true)
            PassageAction.ClimbDown -> climb(type, up = false)
            PassageAction.ClimbEither -> climbEither(type)
            PassageAction.Enter -> enter()
            PassageAction.ClimbOver -> climbOver(loc)
        }
    }

    private data class Panel(
        val coords: CoordGrid,
        val shape: LocShape,
        val angle: LocAngle,
        val base: ObjectServerType,
        val vis: ObjectServerType,
    )

    private data class PanelKey(val coords: CoordGrid, val locId: Int)

    private data class OpenedPassage(val opened: List<Panel>, val closed: List<Panel>)

    private fun ProtectedAccess.openDoor(loc: BoundLocInfo, type: ObjectServerType) {
        if (openedPanels.containsKey(PanelKey(loc.coords, type.id))) {
            closeDoor(loc, type)
            return
        }
        val sound = type.paramOrNull(params.opensound)
        if (sound != null) soundSynth(sound) else soundSynth(DEFAULT_OPEN_SOUND)

        val base = ServerCacheManager.getObject(loc.id) ?: type
        val clicked = Panel(loc.coords, loc.shape, loc.angle, base, type)
        val plan = planOpen(clicked)
        val swingsOntoPlayer =
            clicked.shape == LocShape.WallDiagonal && plan?.opened?.any { it.coords == coords } == true
        if (plan == null || swingsOntoPlayer) {
            locRepo.del(loc, DOOR_DURATION)
            return
        }
        logger.debug {
            val closed = plan.closed.joinToString { "${it.vis.internalNameOrEmpty()}@${it.coords}/${it.angle}" }
            val opened = plan.opened.joinToString { "${it.vis.internalNameOrEmpty()}@${it.coords}/${it.angle}" }
            "Opening passage picket=${clicked.isPicketGate()} closed=[$closed] opened=[$opened]"
        }
        for (panel in plan.closed) {
            val info = findPanel(panel)
            if (info == null) {
                logger.warn { "Closed panel not found in map: ${panel.vis.internalNameOrEmpty()} at ${panel.coords}" }
                continue
            }
            locRepo.del(info, DOOR_DURATION)
        }
        for (panel in plan.opened) {
            val key = PanelKey(panel.coords, panel.base.id)
            locRepo.add(panel.coords, panel.base, DOOR_DURATION, panel.angle, panel.shape) {
                openedPanels.remove(key)
            }
            openedPanels[key] = plan
        }
    }

    private fun ProtectedAccess.planOpen(clicked: Panel): OpenedPassage? {
        if (!clicked.shape.isTranslatable()) {
            return null
        }
        val partnerRight =
            findClosedPartner(
                clicked,
                DoorTranslations.translateClose(clicked.coords, clicked.shape, clicked.angle),
            )
        val partnerLeft =
            if (partnerRight != null) {
                null
            } else {
                findClosedPartner(
                    clicked,
                    DoorTranslations.translateCloseOpposite(
                        clicked.coords,
                        clicked.shape,
                        clicked.angle,
                    ),
                )
            }
        val left = partnerLeft ?: clicked
        val right = if (partnerLeft != null) clicked else partnerRight

        val picket = clicked.isPicketGate()
        val leftOpened = left.opened(isLeft = true, alone = right == null, picket = picket)
        val rightOpened = right?.opened(isLeft = false, alone = false, picket = picket)
        return OpenedPassage(
            opened = listOfNotNull(leftOpened, rightOpened),
            closed = listOfNotNull(left, right),
        )
    }

    private fun Panel.opened(isLeft: Boolean, alone: Boolean, picket: Boolean): Panel {
        val openType = findTwin(vis, "Close") ?: vis
        val (openCoords, openAngle) =
            when {
                picket && isLeft -> coords + GateTranslations.leftGateOpen(shape, angle) to angle.turn(3)
                picket -> coords + GateTranslations.rightGateOpen(shape, angle) to angle.turn(3)
                isLeft && !alone -> DoorTranslations.translateOpen(coords, shape, angle) to angle.turn(3)
                else -> DoorTranslations.translateOpen(coords, shape, angle) to angle.turn(1)
            }
        return Panel(openCoords, shape, openAngle, base = openType, vis = openType)
    }

    private fun Panel.isPicketGate(): Boolean {
        if (shape != LocShape.WallStraight) {
            return false
        }
        val name = vis.internalNameOrEmpty()
        return PICKET_GATE_NAMES.any { name.contains(it) }
    }

    private fun ProtectedAccess.findClosedPartner(like: Panel, coords: CoordGrid): Panel? {
        for (candidate in locRepo.findAll(coords)) {
            if (candidate.shape != like.shape || candidate.angle != like.angle) {
                continue
            }
            val base = ServerCacheManager.getObject(candidate.entity.id) ?: continue
            val vis = visibleType(candidate, base)
            if (vis.name != like.vis.name || vis.actions.getOpOrNull(0) != "Open") {
                continue
            }
            return Panel(candidate.coords, candidate.shape, candidate.angle, base, vis)
        }
        return null
    }

    private fun ProtectedAccess.visibleType(
        info: LocInfo,
        base: ObjectServerType,
    ): ObjectServerType {
        val bound = BoundLocInfo(info, base)
        val multi = locInteractions.multiLoc(bound, base, player.vars) ?: return base
        return ServerCacheManager.getObject(multi.id) ?: base
    }

    private fun findPanel(panel: Panel): LocInfo? = locRepo.findExact(panel.coords, panel.base)

    private fun ProtectedAccess.closeDoor(loc: BoundLocInfo, type: ObjectServerType) {
        val tracked = openedPanels[PanelKey(loc.coords, type.id)]
        val restore = tracked?.closed ?: singleClosed(loc, type)?.let(::listOf) ?: return
        val closesOntoPlayer =
            loc.shape == LocShape.WallDiagonal && restore.any { it.coords == coords }
        if (closesOntoPlayer) {
            mes("You cannot close the door while you are standing in the doorway.")
            return
        }
        val sound = type.paramOrNull(params.closesound)
        if (sound != null) soundSynth(sound) else soundSynth(DEFAULT_CLOSE_SOUND)

        val remove =
            tracked?.opened
                ?: listOf(Panel(loc.coords, loc.shape, loc.angle, base = type, vis = type))
        for (panel in remove) {
            val info = findPanel(panel) ?: continue
            locRepo.del(info, DOOR_DURATION)
            openedPanels.remove(PanelKey(panel.coords, panel.base.id))
        }
        for (panel in restore) {
            locRepo.add(panel.coords, panel.base, DOOR_DURATION, panel.angle, panel.shape)
        }
    }

    private fun singleClosed(loc: BoundLocInfo, type: ObjectServerType): Panel? {
        val closedType = findTwin(type, "Open") ?: return null
        if (!loc.shape.isTranslatable()) {
            return null
        }
        val panel = Panel(loc.coords, loc.shape, loc.angle, base = type, vis = type)
        return if (panel.isPicketGate()) {
            Panel(
                loc.coords + GateTranslations.leftGateClose(loc.shape, loc.angle),
                loc.shape,
                loc.angle.turn(-3),
                base = closedType,
                vis = closedType,
            )
        } else {
            Panel(
                DoorTranslations.translateClose(loc.coords, loc.shape, loc.angle),
                loc.shape,
                loc.angle.turn(-1),
                base = closedType,
                vis = closedType,
            )
        }
    }

    private fun LocShape.isTranslatable(): Boolean =
        this == LocShape.WallStraight || this == LocShape.WallDiagonal

    private fun findTwin(type: ObjectServerType, firstOp: String): ObjectServerType? {
        val sameName = locsByTwinKey[twinKey(type)]?.filter { it.id != type.id && it.sameFootprint(type) }
        val byName =
            sameName?.firstOrNull { it.actions.getOpOrNull(0) == firstOp }
                ?: sameName?.firstOrNull { candidate ->
                    val wantOpen = firstOp == "Close"
                    val looksOpen = candidate.internalNameOrEmpty().contains("open")
                    candidate.actions.getOpOrNull(0) == null && looksOpen == wantOpen
                }
        if (byName != null) {
            return byName
        }
        for (offset in TWIN_SEARCH_OFFSETS) {
            val candidate = ServerCacheManager.getObject(type.id + offset) ?: continue
            if (candidate.name != type.name || !candidate.sameFootprint(type)) {
                continue
            }
            if (candidate.actions.getOpOrNull(0) == firstOp) {
                return candidate
            }
        }
        return null
    }

    private fun ObjectServerType.sameFootprint(other: ObjectServerType): Boolean =
        width == other.width && length == other.length

    private fun ObjectServerType.internalNameOrEmpty(): String = locNames[id] ?: ""

    private fun twinKey(type: ObjectServerType): String {
        val name = type.internalNameOrEmpty()
        if (name.isEmpty()) {
            return "#${type.id}"
        }
        return name.replace("closed", "").replace("open", "").replace("close", "").replace("_", "")
    }

    private suspend fun ProtectedAccess.openTrapdoor(loc: BoundLocInfo, type: ObjectServerType) {
        val opened = findTwin(type, "Climb-down") ?: findTwin(type, "Close")
        if (opened != null) {
            soundSynth(DEFAULT_OPEN_SOUND)
            locRepo.change(loc, opened, DOOR_DURATION)
            return
        }
        climb(type, up = false)
    }

    private suspend fun ProtectedAccess.climbEither(type: ObjectServerType) {
        val up = choice2("Climb up.", true, "Climb down.", false, title = "Climb up or down?")
        climb(type, up)
    }

    private suspend fun ProtectedAccess.climb(type: ObjectServerType, up: Boolean) {
        val dest = Passages.climbDestination(coords, up)?.let(::landing)
        if (dest == null) {
            mes(if (up) "You cannot see a way up from here." else "You cannot see a way down from here.")
            return
        }
        val climbAnim = type.paramOrNull(params.climb_anim)
        if (climbAnim != null) {
            anim(RSCM.getReverseMapping(RSCMType.SEQ, climbAnim.id))
        } else if (type.name in LADDER_NAMES) {
            anim(LADDER_ANIM)
        }
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.enter() {
        val dest = landing(Passages.enterDestination(coords))
        if (dest == null) {
            mes("You cannot see a way through.")
            return
        }
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climbOver(loc: BoundLocInfo) {
        val dest = Passages.farSide(loc, coords)
        if (dest == null || !walkable(dest)) {
            mes("You cannot climb over from here.")
            return
        }
        hopTo(dest, CLIMB_OVER_ANIM, ticks = 2)
    }

    private fun landing(dest: CoordGrid): CoordGrid? {
        if (!hasSurroundings(dest)) {
            return null
        }
        return Passages.landingCandidates(dest).firstOrNull(::walkable)
    }

    private fun walkable(coords: CoordGrid): Boolean =
        collision.isZoneValid(coords) && !collision.isWalkBlocked(coords)

    private fun hasSurroundings(centre: CoordGrid): Boolean {
        for (dz in -SURROUNDINGS_RADIUS..SURROUNDINGS_RADIUS) {
            for (dx in -SURROUNDINGS_RADIUS..SURROUNDINGS_RADIUS) {
                val tile = centre.translate(dx, dz)
                if (!collision.isZoneValid(tile)) {
                    continue
                }
                if (collision[tile] and SURROUNDINGS_MASK != 0) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        private val logger = InlineLogger()

        private const val DOOR_DURATION = 500

        private val TWIN_SEARCH_OFFSETS =
            listOf(1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 8, -8, 10, -10, 12, -12)

        private val LADDER_NAMES = setOf("Ladder", "Rope", "Rope ladder")

        private val PICKET_GATE_NAMES = listOf("fence", "wooden", "pvpa_access_gate")

        private const val SURROUNDINGS_RADIUS = 8

        private const val SURROUNDINGS_MASK = CollisionFlag.ROOF.inv()

        private const val DEFAULT_OPEN_SOUND = "synth.DOOR_OPEN"
        private const val DEFAULT_CLOSE_SOUND = "synth.DOOR_CLOSE"
        private const val LADDER_ANIM = "seq.human_reachforladder"
        private const val CLIMB_OVER_ANIM = "seq.human_walk_style"
    }
}
