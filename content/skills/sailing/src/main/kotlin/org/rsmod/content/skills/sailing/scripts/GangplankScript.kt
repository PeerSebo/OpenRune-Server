package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.events.PlayerWorldEntityLocOpEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseSailingLvl
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.sailing.BoatHullEntity
import org.rsmod.content.skills.sailing.BoatWorldEntityService
import org.rsmod.content.skills.sailing.ON_PLAYER_BOAT_ABOARD
import org.rsmod.content.skills.sailing.ON_PLAYER_BOAT_ASHORE
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.SailingNavigationService
import org.rsmod.content.skills.sailing.VARBIT_ON_PLAYER_BOAT
import org.rsmod.content.skills.sailing.boardedBoat
import org.rsmod.content.skills.sailing.boardedBoatType
import org.rsmod.content.skills.sailing.boardedBoatWorld
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.configs.SailingDocks.SailingDock
import org.rsmod.content.skills.sailing.isOnPlayerBoat
import org.rsmod.content.skills.sailing.ownedBoatSlots
import org.rsmod.content.skills.sailing.ownsBoat
import org.rsmod.content.skills.sailing.readSailingVar
import org.rsmod.content.skills.sailing.sailingFacilityLockedIn
import org.rsmod.content.skills.sailing.sailingLastDock
import org.rsmod.content.skills.sailing.sailingLastMooringPoint
import org.rsmod.content.skills.sailing.sailingLastStandardDock
import org.rsmod.content.skills.sailing.sailingOnPlayerBoat
import org.rsmod.content.skills.sailing.sailingSelectedBoatSlot
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

internal data class SailingBoardTask(
    val dockDbRow: String?,
    val boatSlot: Int,
    val boatType: Int,
    val boatPort: Int,
    val selectedSlot: Int,
    val source: CoordGrid,
) {
    companion object {
        fun capture(player: Player, dockDbRow: String?, boatSlot: Int): SailingBoardTask =
            SailingBoardTask(
                dockDbRow,
                boatSlot,
                player.readSailingVar(SailingBoatVars.field(boatSlot, "type")),
                player.readSailingVar(SailingBoatVars.port(boatSlot)),
                player.sailingSelectedBoatSlot,
                player.coords,
            )
    }
}

internal data class SailingDisembarkTask(val dockDbRow: String?, val departure: SailingBoatDeparture)

// Bind queued exits to hull identity, not a reusable avatar index.
internal data class SailingBoatDeparture(
    val entity: WorldEntity,
    val boatSlot: Int?,
    val rootCoords: CoordGrid,
    val lastDock: Int,
) {
    fun isCurrent(player: Player, boats: BoatWorldEntityService, requireStationary: Boolean): Boolean =
        player.isOnPlayerBoat() &&
            boats.boarded(player) === entity &&
            boats.boardedSlot(player) == boatSlot &&
            player.coords in entity &&
            player.boardedBoatWorld == entity.slotId &&
            SailingSidepanel.currentBoatSlot(player) == boatSlot &&
            player.sailingLastDock == lastDock &&
            (!requireStationary || entity.rootCoords == rootCoords)

    companion object {
        fun capture(player: Player, boats: BoatWorldEntityService): SailingBoatDeparture? {
            val entity = boats.boarded(player) ?: return null
            if (!player.isOnPlayerBoat() || player.coords !in entity) {
                return null
            }
            return SailingBoatDeparture(
                entity,
                boats.boardedSlot(player),
                entity.rootCoords,
                player.sailingLastDock,
            )
        }
    }
}

private enum class BoardMode {
    BOARD,

    PREVIOUS,
}

internal class GangplankScript
@Inject
constructor(
    private val boats: BoatWorldEntityService,
    private val collision: CollisionFlagMap,
    private val navigation: SailingNavigationService,
) :
    PluginScript() {

    override fun ScriptContext.startup() {
        assertBoardingLocAnchors()
        onEvent<PlayerWorldEntityLocOpEvent> { approveRootDockOp(this) }

        for (dock in SailingDocks.ALL) {
            val parent = dock.boardingLoc

            onOpLoc1(parent) { onBoardingLocOp1(dock, it.loc) }
            onOpLoc2(parent) { onBoard(dock, it.loc, BoardMode.PREVIOUS) }
            onOpLoc3(parent) { onBoardFriend(it.loc) }

            onOpLoc1(dock.buoyLoc) { onBuoyDock(dock, it.loc) }
        }

        onOpLoc1(SHIPYARD_GANGPLANK) { onShipyardOp1(it.loc) }

        onPlayerQueueWithArgs<SailingBoardTask>(SailingConstants.QUEUE_BOARD) {
            finishBoard(it.args)
        }
        onPlayerQueueWithArgs<SailingDisembarkTask>(SailingConstants.QUEUE_DISEMBARK) {
            finishDisembark(it.args)
        }

        // Release the hull and relocate before logout saves account coordinates.
        onPlayerLogout { player.leaveBoatOnLogout() }
        onPlayerLogin { player.recoverFromStaleBoarding() }

        onEvent<GameLifecycle.LateCycle> { boats.tick() }
    }

    private fun approveRootDockOp(request: PlayerWorldEntityLocOpEvent) {
        if (request.op != InteractionOp.Op1) return
        if (request.loc.id == SHIPYARD_GANGPLANK.asRSCM(RSCMType.LOC)) {
            val departure = SailingBoatDeparture.capture(request.player, boats) ?: return
            request.accepted = departure.entity === request.worldEntity &&
                departure.isCurrent(request.player, boats, requireStationary = false) &&
                departure.entity.ownerSlot == request.player.slotId &&
                isShipyard(departure.entity.rootCoords) &&
                departure.entity.rootCoords.level == request.loc.coords.level &&
                departure.entity.rootCoords.chebyshevDistance(request.loc.coords) <= ROOT_DOCK_REACH
            return
        }
        val dock = SailingDocks.ALL.firstOrNull { dock ->
            dock.boardingLoc.asRSCM(RSCMType.LOC) == request.loc.id ||
                dock.buoyLoc.asRSCM(RSCMType.LOC) == request.loc.id
        } ?: return
        val departure = SailingBoatDeparture.capture(request.player, boats) ?: return
        if (
            departure.entity !== request.worldEntity ||
                !departure.isCurrent(request.player, boats, requireStationary = false) ||
                departure.entity.ownerSlot != request.player.slotId ||
                !rootDockInRange(departure.entity, request.loc, dock)
        ) {
            return
        }
        request.accepted = true
    }

    private fun rootDockInRange(entity: WorldEntity, loc: BoundLocInfo, dock: SailingDock): Boolean {
        val root = entity.rootCoords
        if (root.level != loc.coords.level) return false
        if (root.chebyshevDistance(loc.coords) <= ROOT_DOCK_REACH) return true
        val typeId = BoatHullEntity.byConfigId(entity.configId)?.typeId ?: return false
        val berth = dock.seaCoordsFor(typeId) ?: return false
        val land = dock.landCoords ?: return false
        return root.chebyshevDistance(berth) <= 2 && land.chebyshevDistance(loc.coords) <= 2
    }

    private fun ProtectedAccess.onBoardingLocOp1(dock: SailingDock, loc: BoundLocInfo) {
        if (player.isOnPlayerBoat()) {
            onDisembark(dock, loc)
        } else {
            onBoard(dock, loc, BoardMode.BOARD)
        }
    }

    private fun ProtectedAccess.onBoard(dock: SailingDock, loc: BoundLocInfo, mode: BoardMode) {
        if (player.isOnPlayerBoat()) {
            return
        }

        if (!SailingDocks.canDock(dock, player.baseSailingLvl)) {
            mes(dockLevelRequirement(dock.levelRequired))
            return
        }

        if (!dock.canSpawnBoat) {
            mes(BOARDING_UNAVAILABLE)
            return
        }

        // TODO(quest): enforce each dock's quest_required gate.

        val owned = player.ownedBoatSlots()
        if (owned.isEmpty()) {
            mes(NO_BOAT_OWNED)
            return
        }

        val slot = resolveBoardSlot(dock, mode, owned)
        if (slot == null) {
            mes(NO_BOAT_MOORED_HERE)
            return
        }

        faceLoc(loc)
        queue(SailingConstants.QUEUE_BOARD, BOARD_TICKS, SailingBoardTask.capture(player, dock.dbrow, slot))
    }

    private fun ProtectedAccess.onDisembark(dock: SailingDock, loc: BoundLocInfo) {
        val departure = SailingBoatDeparture.capture(player, boats) ?: return
        if (!rootDockInRange(departure.entity, loc, dock) || dock.landCoords == null) {
            return
        }
        navigation.halt(player)
        // Root-world clicks run before queue decrement; compensate initial scheduling.
        queue(
            SailingConstants.QUEUE_DISEMBARK,
            DISEMBARK_TICKS + 1,
            SailingDisembarkTask(dock.dbrow, departure),
        )
    }

    private fun ProtectedAccess.onBoardFriend(loc: BoundLocInfo) {
        // TODO: implement Board-friend with friendship checks and another owner's hull.
        faceLoc(loc)
        mes(BOARD_FRIEND_UNAVAILABLE)
    }

    private fun ProtectedAccess.onBuoyDock(dock: SailingDock, loc: BoundLocInfo) {
        val departure = SailingBoatDeparture.capture(player, boats) ?: return
        if (
            !departure.isCurrent(player, boats, requireStationary = false) ||
                !rootDockInRange(departure.entity, loc, dock)
        ) {
            return
        }
        if (!dock.canSpawnBoat) {
            mes(BOARDING_UNAVAILABLE)
            return
        }
        if (!SailingDocks.canDock(dock, player.baseSailingLvl)) {
            mes(dockLevelRequirement(dock.levelRequired))
            return
        }
        navigation.halt(player)
        anchorRespawn(dock)
        departure.boatSlot?.let { player.writeSailingVar(SailingBoatVars.port(it), dock.row.dockId) }
        mes(BUOY_DOCKED)
    }

    private fun ProtectedAccess.onShipyardOp1(loc: BoundLocInfo) {
        faceLoc(loc)
        if (player.isOnPlayerBoat()) {
            val departure = SailingBoatDeparture.capture(player, boats) ?: return
            if (!isShipyard(departure.entity.rootCoords)) return
            navigation.halt(player)
            queue(
                SailingConstants.QUEUE_DISEMBARK,
                DISEMBARK_TICKS,
                SailingDisembarkTask(dockDbRow = null, departure = departure),
            )
            return
        }
        val owned = player.ownedBoatSlots()
        if (owned.isEmpty()) {
            mes(NO_BOAT_OWNED)
            return
        }
        val slot = previousBoatSlot(owned) ?: owned.first()
        queue(
            SailingConstants.QUEUE_BOARD,
            BOARD_TICKS,
            SailingBoardTask.capture(player, dockDbRow = null, boatSlot = slot),
        )
    }

    private fun ProtectedAccess.finishBoard(task: SailingBoardTask) {
        // Revalidate the boat and dock after the boarding delay.
        if (
            player.isOnPlayerBoat() ||
                boats.boarded(player) != null ||
                player.coords != task.source ||
                player.sailingSelectedBoatSlot != task.selectedSlot
        ) {
            return
        }
        val dock = task.dockDbRow?.let(SailingDocks::byDbRow)
        if (task.dockDbRow != null && dock == null) {
            return
        }
        if (!player.ownsBoat(task.boatSlot)) {
            mes(NO_BOAT_OWNED)
            return
        }

        if (
            player.readSailingVar(SailingBoatVars.field(task.boatSlot, "type")) != task.boatType ||
                player.readSailingVar(SailingBoatVars.port(task.boatSlot)) != task.boatPort ||
                (dock != null && task.boatPort != dock.row.dockId)
        ) {
            return
        }
        if ((dock == null && !isShipyard(task.source)) || (dock != null && !dock.canSpawnBoat)) {
            mes(BOARDING_UNAVAILABLE)
            return
        }
        if (dock != null && !SailingDocks.canDock(dock, player.baseSailingLvl)) {
            mes(dockLevelRequirement(dock.levelRequired))
            return
        }

        val worldEntity = spawnBoat(dock, task.boatSlot)
        if (worldEntity == null) {
            mes(BOARDING_UNAVAILABLE)
            return
        }
        if (!relocateToDeck(worldEntity)) {
            boats.despawn(player)
            return
        }

        player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ABOARD
        // boardedBoat is a one-bit flag; store the selected slot separately.
        player.boardedBoat = 1
        player.sailingSelectedBoatSlot = task.boatSlot
        player.boardedBoatType = player.readSailingVar(SailingBoatVars.field(task.boatSlot, "type"))
        player.boardedBoatWorld = worldEntity.slotId

        if (dock != null) anchorRespawn(dock)

        openSidePanel(task.boatSlot)
    }

    private fun ProtectedAccess.finishDisembark(task: SailingDisembarkTask) {
        if (!task.departure.isCurrent(player, boats, requireStationary = true)) {
            return
        }
        val dock = task.dockDbRow?.let(SailingDocks::byDbRow)
        if (task.dockDbRow != null && dock == null) {
            return
        }
        if (!relocateAshore(dock, task.departure.entity)) {
            return
        }

        player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ASHORE
        player.boardedBoat = 0
        player.boardedBoatWorld = NO_BOAT_WORLD

        if (dock != null) {
            anchorRespawn(dock)
            task.departure.boatSlot?.let {
                player.writeSailingVar(SailingBoatVars.port(it), dock.row.dockId)
            }
        }

        // Relocate before releasing the hull; unpinned deck zones may be reused immediately.
        boats.despawn(player)

        closeSidePanel()
    }

    private fun ProtectedAccess.resolveBoardSlot(
        dock: SailingDock,
        mode: BoardMode,
        owned: List<Int>,
    ): Int? {
        val dockId = dock.row.dockId
        val moored = owned.filter { player.readSailingVar(SailingBoatVars.port(it)) == dockId }
        return when (mode) {
            BoardMode.BOARD -> moored.firstOrNull()
            BoardMode.PREVIOUS -> previousBoatSlot(moored) ?: moored.firstOrNull()
        }
    }

    private fun ProtectedAccess.previousBoatSlot(candidates: List<Int>): Int? {
        val selected = player.sailingSelectedBoatSlot
        if (selected in candidates) {
            return selected
        }
        val lastType = player.boardedBoatType
        if (SailingBoats.byTypeId(lastType) == null) {
            return null
        }
        return candidates.firstOrNull {
            player.readSailingVar(SailingBoatVars.field(it, "type")) == lastType
        }
    }

    private fun ProtectedAccess.anchorRespawn(dock: SailingDock) {
        val dockId = dock.row.dockId
        player.sailingLastDock = dockId
        if (dock.isStandardDock) {
            player.sailingLastStandardDock = dockId
        } else {
            player.sailingLastMooringPoint = dockId
        }
    }

    private fun ProtectedAccess.spawnBoat(dock: SailingDock?, boatSlot: Int): WorldEntity? {
        val boatTypeId = player.readSailingVar(SailingBoatVars.field(boatSlot, "type"))
        val seaCoords = if (dock == null && isShipyard(player.coords)) SHIPYARD_BERTH
            else dock?.seaCoordsFor(boatTypeId) ?: return null
        return boats.spawn(player, boatSlot, boatTypeId, seaCoords)?.also {
            if (dock == null) {
                it.moveRoot(SHIPYARD_BERTH.x * 128 + 64, SHIPYARD_BERTH.z * 128)
                it.angle = 1536
            }
        }
    }

    private fun ProtectedAccess.relocateToDeck(worldEntity: WorldEntity): Boolean {
        val deck = boats.deckArrivalCoords(worldEntity)
        telejump(deck)
        return player.coords == deck && player.coords in worldEntity
    }

    // A denied teleport must preserve the occupied deck and boarding state.
    private fun ProtectedAccess.relocateAshore(dock: SailingDock?, worldEntity: WorldEntity): Boolean {
        val land = dockArrivalCoords(dock) ?: return false
        telejump(land)
        return player.coords == land && player.coords !in worldEntity
    }

    private fun ProtectedAccess.dockArrivalCoords(dock: SailingDock?): CoordGrid? {
        if (boats.boarded(player)?.rootCoords?.let(::isShipyard) == true) return SHIPYARD_SHORE
        val lastDock = SailingDocks.byDockId(player.sailingLastDock)
        return dock?.landCoords ?: lastDock?.landCoords ?: SailingDocks.PORT_SARIM.landCoords
    }

    private fun Player.leaveBoatOnLogout() {
        val worldEntity = boats.boarded(this)
        if (worldEntity != null && coords in worldEntity) {
            val land =
                SHIPYARD_SHORE.takeIf { isShipyard(worldEntity.rootCoords) }
                    ?: SailingDocks.byDockId(sailingLastDock)?.landCoords
                    ?: SailingDocks.PORT_SARIM.landCoords
            if (land != null) {
                PathingEntityCommon.telejump(this, collision, land)
            }
        }
        boats.despawn(this)

        if (isOnPlayerBoat()) {
            sailingOnPlayerBoat = ON_PLAYER_BOAT_ASHORE
            boardedBoat = 0
            boardedBoatWorld = NO_BOAT_WORLD
        }
    }

    // Clear persisted boarding state after crashes; live hulls are session-only.
    private fun Player.recoverFromStaleBoarding() {
        if (!isOnPlayerBoat()) {
            return
        }
        if (RegionRegistry.inWorkingArea(coords) && boats.boarded(this) == null) {
            val land = sequenceOf(
                SailingDocks.byDockId(sailingLastDock)?.landCoords,
                SailingDocks.PORT_SARIM.landCoords,
            ).filterNotNull().firstOrNull { collision.isZoneValid(it) }
            if (land != null) {
                PathingEntityCommon.telejump(this, collision, land)
            }
        }
        sailingFacilityLockedIn = 0
        sailingOnPlayerBoat = ON_PLAYER_BOAT_ASHORE
        boardedBoat = 0
        boardedBoatWorld = NO_BOAT_WORLD
    }

    private fun ProtectedAccess.openSidePanel(boatSlot: Int) {
        SailingSidepanel.open(this, boatSlot)
    }

    private fun ProtectedAccess.closeSidePanel() {
        SailingSidepanel.close(this)
    }

    private fun assertBoardingLocAnchors() {
        val onPlayerBoatVarbit = VARBIT_ON_PLAYER_BOAT.asRSCM(RSCMType.VARBIT)

        // Deduplicate parent locs before registration; duplicate event keys fail startup.
        val parents = (SailingDocks.ALL.map { it.boardingLoc } + SHIPYARD_GANGPLANK).toSet()
        check(parents.size == SailingConstants.OQ15_DOCK_ROW_COUNT + 1) {
            "Expected one distinct boarding parent per dock plus the Shipyard, got ${parents.size}."
        }
        for (symbol in parents) {
            val id = symbol.asRSCM(RSCMType.LOC)
            val type =
                ServerCacheManager.getObject(id)
                    ?: error("Boarding parent $symbol ($id) is not in the server cache.")
            check(type.multiVarBit == onPlayerBoatVarbit) {
                "$symbol ($id) is expected to be a multiloc driven by $VARBIT_ON_PLAYER_BOAT " +
                    "($onPlayerBoatVarbit); its multiVarBit is ${type.multiVarBit}. Op handlers " +
                    "registered on it would never fire."
            }
        }

        val buoys = SailingDocks.ALL.map { it.buoyLoc }.toSet()
        check(buoys.size == SailingConstants.OQ15_DOCK_ROW_COUNT) {
            "Expected ${SailingConstants.OQ15_DOCK_ROW_COUNT} distinct buoy symbols, got " +
                "${buoys.size}."
        }
        for (symbol in buoys) {
            val id = symbol.asRSCM(RSCMType.LOC)
            checkNotNull(ServerCacheManager.getObject(id)) {
                "Buoy $symbol ($id) is not in the server cache."
            }
        }
    }
}

private const val SHIPYARD_GANGPLANK: String = "loc.sailing_gangplank_shipyard"
private val SHIPYARD_SHORE = CoordGrid(2086, 2724)
private val SHIPYARD_BERTH = CoordGrid(2090, 2724)

private fun isShipyard(coords: CoordGrid): Boolean =
    MapSquareKey.from(coords) == MapSquareKey.from(SHIPYARD_SHORE)

private const val BOARD_TICKS: Int = 1

private const val DISEMBARK_TICKS: Int = 1

private const val ROOT_DOCK_REACH: Int = 8

private const val NO_BOAT_OWNED: String = "You don't own a boat. Speak to a shipwright to buy one."

private const val NO_BOAT_MOORED_HERE: String = "You don't have a boat moored here."

private const val BOARD_FRIEND_UNAVAILABLE: String = "You can't board another player's boat yet."

private const val BUOY_DOCKED: String = "You'll return to this dock if your boat sinks."

private const val BOARDING_UNAVAILABLE: String = "Your boat cannot be boarded at this dock right now."

private const val NO_BOAT_WORLD: Int = 0

private fun dockLevelRequirement(level: Int): String =
    "You need a Sailing level of $level to dock here."

private const val DOCK_DBROW_PREFIX: String = "dbrow.sailing_dock_"
private const val MOORING_LOC_PREFIX: String = "loc.sailing_mooring_"
private const val BUOY_LOC_PREFIX: String = "loc.sailing_docking_buoy_"

private val SailingDock.suffix: String
    get() = dbrow.removePrefix(DOCK_DBROW_PREFIX)

private val SailingDock.boardingLoc: String
    get() = gangplank ?: (MOORING_LOC_PREFIX + suffix)

private val SailingDock.buoyLoc: String
    get() = BUOY_LOC_PREFIX + suffix
