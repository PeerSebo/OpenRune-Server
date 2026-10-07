package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.repo.worldentity.WorldEntityRepository
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.scripts.SailingSidepanel
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey

@Singleton
internal class BoatWorldEntityService
@Inject
constructor(
    private val worldEntities: WorldEntityRepository,
    private val mapClock: MapClock,
    private val eventBus: EventBus,
    private val decks: BoatDeckService,
) {
    // Keep strong ownership until lifecycle cleanup; weak keys could orphan pinned regions and avatars.
    private val boats: MutableMap<Player, LiveHull> = HashMap()

    fun boarded(player: Player): WorldEntity? = boats[player]?.entity

    fun boardedSlot(player: Player): Int? = boats[player]?.ownedBoatSlot

    fun isTutorial(player: Player): Boolean = boats[player]?.ownedBoatSlot == 0

    fun spawnTutorial(player: Player, template: RegionTemplate, root: CoordGrid): WorldEntity? {
        if (player in boats && !despawn(player)) return null
        val hull = BoatHullEntity.SKIFF
        val entity = worldEntities.add(
            configId = hull.configId,
            deckTemplate = template,
            deckZoneSizeX = 1,
            deckZoneSizeZ = 1,
            rootCoords = root,
            ownerSlot = player.slotId,
        ) ?: return null
        entity.moveRoot(root.x * 128 + 64, root.z * 128 + 64)
        boats[player] = LiveHull(entity, hull, 0).also { it.armIdleSeq(mapClock.cycle) }
        return entity
    }

    fun spawn(player: Player, ownedBoatSlot: Int, boatTypeId: Int, rootCoords: CoordGrid): WorldEntity? {
        if (
            ownedBoatSlot !in 1..SailingConstants.MAX_BOAT_SLOTS ||
                !player.ownsBoat(ownedBoatSlot) ||
                player.readSailingVar(SailingBoatVars.field(ownedBoatSlot, "type")) != boatTypeId
        ) {
            return null
        }
        val hull = BoatHullEntity.byTypeId(boatTypeId) ?: return null
        // Retain failed releases for retry; replacing their entry would orphan the hull.
        if (player in boats && !despawn(player)) {
            return null
        }

        val deck = hull.deckSource
        val worldEntity =
            worldEntities.add(
                configId = hull.configId,
                deckTemplate = hull.deckTemplate,
                deckZoneSizeX = deck.zoneSizeX,
                deckZoneSizeZ = deck.zoneSizeZ,
                rootCoords = rootCoords,
                angle = HULL_ANGLE_UNVERIFIED,
                // A positive owner slot gives this player priority in rsprot's 25-slot high-resolution ranking.
                ownerSlot = player.slotId,
            ) ?: return null

        worldEntity.moveRoot(rootCoords.x * 128 + 64, rootCoords.z * 128 + 64)
        val live = LiveHull(worldEntity, hull, ownedBoatSlot)
        // Retain the allocation if cleanup fails so the lifecycle sweep can retry.
        boats[player] = live
        if (!decks.populate(player, ownedBoatSlot, hull, worldEntity)) {
            despawn(player)
            return null
        }
        live.armIdleSeq(mapClock.cycle)

        return worldEntity
    }

    // Move occupants off the deck before unpinning; its zones may be immediately reused.
    fun despawn(player: Player): Boolean {
        val live = boats[player] ?: return false
        if (!worldEntities.del(live.entity)) {
            return false
        }
        boats.remove(player)
        clearBoatUi(player)
        return true
    }

    fun deckArrivalCoords(worldEntity: WorldEntity): CoordGrid {
        val hull =
            BoatHullEntity.byConfigId(worldEntity.configId)
                ?: error("No BoatHullEntity for worldentity config ${worldEntity.configId}.")
        val deck = hull.deckSource
        return worldEntities.deckArrivalCoords(
            worldEntity,
            deck.arrivalOffsetX,
            deck.arrivalOffsetZ,
        )
    }

    fun tick() {
        if (boats.isEmpty()) {
            return
        }
        val cycle = mapClock.cycle
        val iterator = boats.entries.iterator()
        while (iterator.hasNext()) {
            val (player, live) = iterator.next()
            if (isAboard(player, live.entity)) {
                live.armIdleSeqIfDue(cycle)
                continue
            }
            // A false del result means release failed; retain the live entry and retry.
            if (!worldEntities.del(live.entity)) {
                continue
            }
            iterator.remove()
            clearStaleBoardingState(player)
            clearBoatUi(player)
        }
    }

    private fun isAboard(player: Player, worldEntity: WorldEntity): Boolean =
        player.isSlotAssigned && player.coords in worldEntity

    private fun clearBoatUi(player: Player) {
        if (!player.isSlotAssigned) return
        SailingNavigationService.clearControls(player)
        player.writeSailingVar(SailingNavigationService.VAR_MOVE_MODE, SailingMoveMode.Stopped.value)
        player.writeSailingVar("varbit.sailing_boat_time_till_trim", 0)
        player.writeSailingVar("varbit.sailing_boat_time_trim_window", 0)
        player.writeSailingVar("varbit.sailing_boat_speedboost_duration", 0)
        player.writeSailingVar("varbit.sailing_boat_winds_timer_paused", 1)
        SailingSidepanel.close(player, eventBus)
    }

    private fun clearStaleBoardingState(player: Player) {
        if (!player.isSlotAssigned || !player.isOnPlayerBoat()) {
            return
        }
        player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ASHORE
        player.boardedBoat = 0
        player.boardedBoatWorld = NO_BOAT_WORLD_INDEX
    }

    fun assertCacheAnchors() {
        for (hull in BoatHullEntity.entries) {
            check(hull.idleSeq > 0) {
                "${hull.name} idle seq resolved to ${hull.idleSeq}; expected a positive seq id."
            }
            check(hull.idleSeqLoopTicks in 1..IDLE_SEQ_LOOP_TICKS_MAX) {
                "${hull.name} idle seq ${hull.idleSeq} loop length ${hull.idleSeqLoopTicks} is " +
                    "outside 1..$IDLE_SEQ_LOOP_TICKS_MAX ticks; re-check `tickDuration` in the " +
                    "packed server cache before trusting the re-arm cadence."
            }

            val deck = hull.deckSource
            check(deck.zoneSizeX in 1..WorldEntity.MAX_ZONE_SIZE) {
                "${hull.name} deck zoneSizeX ${deck.zoneSizeX} is outside " +
                    "1..${WorldEntity.MAX_ZONE_SIZE} zones."
            }
            check(deck.zoneSizeZ in 1..WorldEntity.MAX_ZONE_SIZE) {
                "${hull.name} deck zoneSizeZ ${deck.zoneSizeZ} is outside " +
                    "1..${WorldEntity.MAX_ZONE_SIZE} zones."
            }

            check(deck.zoneSizeX * ZoneGrid.LENGTH >= hull.hullTilesX) {
                "${hull.name} deck is ${deck.zoneSizeX * ZoneGrid.LENGTH} tiles wide but the " +
                    "hull is ${hull.hullTilesX}."
            }
            check(deck.zoneSizeZ * ZoneGrid.LENGTH >= hull.hullTilesZ) {
                "${hull.name} deck is ${deck.zoneSizeZ * ZoneGrid.LENGTH} tiles long but the " +
                    "hull is ${hull.hullTilesZ}."
            }

            check(deck.southWestZone == hull.expectedSouthWestZone) {
                "${hull.name} deck source zone derived as ${deck.southWestZone} from " +
                    "`dbcol.sailing_boat:hotspot`, but the cache this file was written against " +
                    "gave ${hull.expectedSouthWestZone}. Either the cache changed or the " +
                    "derivation is wrong - resolve it before boats render somewhere else."
            }
            check(deck.zoneSizeX == hull.expectedZoneSizeX) {
                "${hull.name} deck zoneSizeX derived as ${deck.zoneSizeX}, expected " +
                    "${hull.expectedZoneSizeX}."
            }
            check(deck.zoneSizeZ == hull.expectedZoneSizeZ) {
                "${hull.name} deck zoneSizeZ derived as ${deck.zoneSizeZ}, expected " +
                    "${hull.expectedZoneSizeZ}."
            }
        }

        val typeIds = BoatHullEntity.entries.map { it.typeId }.toSet()
        check(typeIds.size == BoatHullEntity.entries.size) {
            "Duplicate type_id in BoatHullEntity: $typeIds"
        }

        // Hull config IDs must be unique because deckArrivalCoords resolves by config ID.
        val configIds = BoatHullEntity.entries.map { it.configId }.toSet()
        check(configIds.size == BoatHullEntity.entries.size) {
            "Duplicate worldentity config id in BoatHullEntity: $configIds"
        }

        // Avatar indices must fit sailing_boarded_boat_world's field width.
        check(WorldEntity.MAX_INDEX <= VARBIT_BOARDED_BOAT_WORLD_MAX) {
            "WorldEntity.MAX_INDEX (${WorldEntity.MAX_INDEX}) no longer fits " +
                "varbit.sailing_boarded_boat_world's $VARBIT_BOARDED_BOAT_WORLD_MAX."
        }
    }
}

// Re-arm idle animation each loop so newly arriving observers receive it.
private class LiveHull(val entity: WorldEntity, val hull: BoatHullEntity, val ownedBoatSlot: Int) {
    private var nextIdleSeqCycle: Int = 0

    fun armIdleSeq(cycle: Int) {
        entity.anim(hull.idleSeq)
        nextIdleSeqCycle = cycle + hull.idleSeqLoopTicks
    }

    fun armIdleSeqIfDue(cycle: Int) {
        if (cycle >= nextIdleSeqCycle) {
            armIdleSeq(cycle)
        }
    }
}

internal enum class BoatHullEntity(
    val typeId: Int,
    val configId: Int,
    val hullTilesX: Int,
    val hullTilesZ: Int,
    private val dbrow: String,
    private val idleSeqSymbol: String,
    val expectedSouthWestZone: ZoneKey,
    val expectedZoneSizeX: Int,
    val expectedZoneSizeZ: Int,
) {
    RAFT(
        typeId = 0,
        configId = 1,
        hullTilesX = 1,
        hullTilesZ = 3,
        dbrow = SailingBoats.DBROW_RAFT,
        idleSeqSymbol = "seq.sailing_worldentity_boat_kandarin_1x3_idle_01",
        expectedSouthWestZone = ZoneKey(480, 807, DECK_SOURCE_LEVEL),
        expectedZoneSizeX = 1,
        expectedZoneSizeZ = 1,
    ),

    SKIFF(
        typeId = 1,
        configId = 2,
        hullTilesX = 2,
        hullTilesZ = 5,
        dbrow = SailingBoats.DBROW_SKIFF,
        idleSeqSymbol = "seq.sailing_worldentity_boat_kandarin_2x5_idle_01",
        expectedSouthWestZone = ZoneKey(480, 806, DECK_SOURCE_LEVEL),
        expectedZoneSizeX = 1,
        expectedZoneSizeZ = 1,
    ),

    SLOOP(
        typeId = 2,
        configId = 3,
        hullTilesX = 3,
        hullTilesZ = 10,
        dbrow = SailingBoats.DBROW_SLOOP,
        idleSeqSymbol = "seq.sailing_worldentity_boat_kandarin_3x8_idle_01",
        expectedSouthWestZone = ZoneKey(480, 804, DECK_SOURCE_LEVEL),
        expectedZoneSizeX = 1,
        expectedZoneSizeZ = 2,
    );

    // Resolve gamevals lazily after cache loading.
    val idleSeq: Int by lazy { idleSeqSymbol.asRSCM(RSCMType.SEQ) }

    val idleSeqLoopTicks: Int by lazy {
        val duration = ServerCacheManager.getAnim(idleSeq)?.tickDuration ?: 0
        if (duration > 0) duration else IDLE_SEQ_LOOP_TICKS_FALLBACK
    }

    val row: SailingBoatRow by lazy { SailingBoatRow.getRow(dbrow) }

    val deckSource: DeckSourceMap by lazy { deriveDeckSource(this) }

    // Copy all planes: hull geometry is on zero, walkable deck and rigging on one.
    val deckTemplate: RegionTemplate by lazy {
        val deck = deckSource
        val southWest = deck.southWestZone
        RegionTemplate.create {
            for (level in 0..WorldEntityRepository.MAX_DECK_LEVEL) {
                for (zoneX in 0 until deck.zoneSizeX) {
                    for (zoneZ in 0 until deck.zoneSizeZ) {
                        this[zoneX, zoneZ, level] =
                            ZoneKey(southWest.x + zoneX, southWest.z + zoneZ, level)
                    }
                }
            }
        }
    }

    companion object {
        fun byTypeId(typeId: Int): BoatHullEntity? = entries.firstOrNull { it.typeId == typeId }

        fun byConfigId(configId: Int): BoatHullEntity? =
            entries.firstOrNull { it.configId == configId }
    }
}

internal data class DeckSourceMap(
    val southWestZone: ZoneKey,
    val zoneSizeX: Int,
    val zoneSizeZ: Int,
    val arrivalOffsetX: Int,
    val arrivalOffsetZ: Int,
)

private const val DECK_SOURCE_LEVEL: Int = 1

private fun deriveDeckSource(hull: BoatHullEntity): DeckSourceMap {
    val hotspots = SailingBoats.hotspots(hull.row)
    check(hotspots.isNotEmpty()) {
        "${hull.name}: `dbcol.sailing_boat:hotspot` is empty, so its deck map cannot be derived."
    }

    val coords = hotspots.map { it.coords }
    for (coord in coords) {
        check(coord.level == DECK_SOURCE_LEVEL) {
            "${hull.name}: hotspot $coord is on level ${coord.level}, expected " +
                "$DECK_SOURCE_LEVEL. `dbcol.sailing_boat:hotspot` is being read as an absolute " +
                "source-map coordinate; a level of 0 would mean it is something else entirely."
        }
    }

    val minX = coords.minOf { it.x }
    val maxX = coords.maxOf { it.x }
    val minZ = coords.minOf { it.z }
    val maxZ = coords.maxOf { it.z }

    val southWestZone = ZoneKey(minX / ZoneGrid.LENGTH, minZ / ZoneGrid.LENGTH, DECK_SOURCE_LEVEL)
    val northEastZone = ZoneKey(maxX / ZoneGrid.LENGTH, maxZ / ZoneGrid.LENGTH, DECK_SOURCE_LEVEL)

    val rectangleSouthWestX = southWestZone.x * ZoneGrid.LENGTH
    val rectangleSouthWestZ = southWestZone.z * ZoneGrid.LENGTH

    return DeckSourceMap(
        southWestZone = southWestZone,
        zoneSizeX = northEastZone.x - southWestZone.x + 1,
        zoneSizeZ = northEastZone.z - southWestZone.z + 1,
        arrivalOffsetX = ((minX + maxX) / 2) - rectangleSouthWestX,
        arrivalOffsetZ = ((minZ + maxZ) / 2) - rectangleSouthWestZ,
    )
}

private const val HULL_ANGLE_UNVERIFIED: Int = 0

private const val IDLE_SEQ_LOOP_TICKS_FALLBACK: Int = 8

private const val IDLE_SEQ_LOOP_TICKS_MAX: Int = 100

// Zero means root world/ashore; world-entity indices begin at one.
private const val NO_BOAT_WORLD_INDEX: Int = 0

private const val VARBIT_BOARDED_BOAT_WORLD_MAX: Int = 4095
