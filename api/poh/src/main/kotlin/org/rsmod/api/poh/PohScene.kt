package org.rsmod.api.poh

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.region.Region
import org.rsmod.game.region.util.RegionRotations
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag.BLOCK_WALK
import org.rsmod.routefinder.loc.LocLayerConstants
import org.rsmod.routefinder.util.Rotations

internal class PohScene(
    private val locRepo: LocRepository,
    private val dataStore: PohDataStore,
    private val collision: CollisionFlagMap,
) {
    internal fun roomSlotAt(region: Region, coords: CoordGrid): PohRoomSlot? {
        val zoneX = (coords.x - region.southWest.x) / PohConstants.ZONE_TILE_LENGTH
        val zoneZ = (coords.z - region.southWest.z) / PohConstants.ZONE_TILE_LENGTH
        val gridX = zoneX - PohConstants.GRID_ZONE_OFFSET
        val gridZ = zoneZ - PohConstants.GRID_ZONE_OFFSET
        if (gridX !in 0 until PohConstants.GRID_LENGTH) return null
        if (gridZ !in 0 until PohConstants.GRID_LENGTH) return null
        return PohRoomSlot(coords.level, gridX, gridZ)
    }

    internal fun roomBaseCoords(region: Region, slot: PohRoomSlot): CoordGrid =
        CoordGrid(
            region.southWest.x +
                (slot.gridX + PohConstants.GRID_ZONE_OFFSET) * PohConstants.ZONE_TILE_LENGTH,
            region.southWest.z +
                (slot.gridZ + PohConstants.GRID_ZONE_OFFSET) * PohConstants.ZONE_TILE_LENGTH,
            slot.level,
        )

    /**
     * Stamps a blocking ring on the outermost tile row of the lawn ring so players can't wander
     * into the visual lawn padding (real houses fence you into the buildable area, and the padding
     * exists only to keep the map-edge height cliff outside the draw distance). The dungeon level
     * is additionally blocked wherever no dungeon room exists: its filler zones are invisible
     * height spacers with no floor to stand on.
     */
    internal fun blockLawnBoundary(region: Region, house: PohHouse) {
        val min = PohConstants.WALKABLE_MIN_TILE
        val max = PohConstants.WALKABLE_MAX_TILE
        for (level in PohFloor.DUNGEON.destLevel..PohFloor.UPPER.destLevel) {
            for (t in min..max) {
                collision.add(region.southWest.x + min, region.southWest.z + t, level, BLOCK_WALK)
                collision.add(region.southWest.x + max, region.southWest.z + t, level, BLOCK_WALK)
                collision.add(region.southWest.x + t, region.southWest.z + min, level, BLOCK_WALK)
                collision.add(region.southWest.x + t, region.southWest.z + max, level, BLOCK_WALK)
            }
        }
        for (gridX in 0 until PohConstants.GRID_LENGTH) {
            for (gridZ in 0 until PohConstants.GRID_LENGTH) {
                val slot = PohRoomSlot(PohFloor.DUNGEON.destLevel, gridX, gridZ)
                if (house.rooms.containsKey(slot)) {
                    continue
                }
                val base = roomBaseCoords(region, slot)
                for (dx in 0 until PohConstants.ZONE_TILE_LENGTH) {
                    for (dz in 0 until PohConstants.ZONE_TILE_LENGTH) {
                        collision.add(base.x + dx, base.z + dz, slot.level, BLOCK_WALK)
                    }
                }
            }
        }
    }

    internal fun hotspotCoords(
        region: Region,
        slot: PohRoomSlot,
        room: PohRoom,
        hotspot: PohHotspot,
    ): CoordGrid {
        val type = locType(hotspot.loc)
        val width = Rotations.rotate(hotspot.rotation, type.width, type.length)
        val length = Rotations.rotate(hotspot.rotation, type.length, type.width)
        val translation =
            RegionRotations.translateLoc(
                room.rotation,
                ZoneGrid(hotspot.localX, hotspot.localZ, slot.level),
                width,
                length,
            )
        return roomBaseCoords(region, slot).translate(translation)
    }

    internal fun refresh(region: Region, house: PohHouse, buildMode: Boolean) {
        val style = dataStore.style(house.style)

        for ((slot, room) in house.rooms) {
            refreshRoomHotspots(region, house, style, slot, room, buildMode)
            refreshSharedPassages(region, house, style, slot, room, buildMode)
            refreshWindows(region, style, slot, room)
        }
        ensureExitPortal(region, house)
    }

    private fun refreshWindows(region: Region, style: PohStyle, slot: PohRoomSlot, room: PohRoom) {
        val base = roomBaseCoords(region, slot)
        for (window in dataStore.windows(room.type)) {
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(window.localX, window.localZ, slot.level),
                )
            val coords = base.translate(translation)
            // Map-loc deletes match the template entity (the registry applies zone rotation).
            val entity =
                LocEntity(
                    WINDOW_PLACEHOLDER_LOC.asRSCM(RSCMType.LOC),
                    window.shape,
                    window.rotation,
                )
            locRepo.del(LocInfo(LocLayerConstants.of(window.shape), coords, entity), Int.MAX_VALUE)
            locRepo.add(
                coords,
                style.doors.window,
                Int.MAX_VALUE,
                LocAngle[(window.rotation + room.rotation) and 0x3],
                LocShape[window.shape],
            )
        }
    }

    private fun refreshRoomHotspots(
        region: Region,
        house: PohHouse,
        style: PohStyle,
        slot: PohRoomSlot,
        room: PohRoom,
        buildMode: Boolean,
    ) {
        for (hotspot in dataStore.hotspots(room.type)) {
            val hotspotSlot = PohHotspotSlot(slot.level, slot.gridX, slot.gridZ, hotspot.index)
            val built = house.furniture[hotspotSlot]
            val coords = hotspotCoords(region, slot, room, hotspot)
            val regionAngle = (hotspot.rotation + room.rotation) and 0x3
            // Wall-embedded hotspots (chapel "Window space") leave a hole in the wall when bare
            // deleted; Zenyte's removeHotspot swaps them to the style's wall piece instead.
            val wallEmbedded = hotspot.shape == WALL_SHAPE && hotspot.name == WINDOW_SPACE_NAME
            if (built != null) {
                // Spawned locs carry region-space angles; the registry stores them verbatim.
                locRepo.add(
                    coords,
                    built,
                    Int.MAX_VALUE,
                    LocAngle[regionAngle],
                    LocShape[hotspot.shape],
                )
            } else if (buildMode) {
                if (wallEmbedded) {
                    deleteSpawnedWallFiller(coords, style, regionAngle)
                }
                // Map-loc restores are matched against the *template* entity: the region registry
                // applies the zone rotation itself, so the angle here is the unrotated one.
                locRepo.add(
                    coords,
                    hotspot.loc,
                    Int.MAX_VALUE,
                    LocAngle[hotspot.rotation],
                    LocShape[hotspot.shape],
                )
            } else {
                // Same template-space rule for deletes; a rotated angle silently finds nothing.
                val entity =
                    LocEntity(hotspot.loc.asRSCM(RSCMType.LOC), hotspot.shape, hotspot.rotation)
                val loc = LocInfo(LocLayerConstants.of(hotspot.shape), coords, entity)
                locRepo.del(loc, Int.MAX_VALUE)
                if (wallEmbedded) {
                    locRepo.add(
                        coords,
                        style.doors.wallFiller,
                        Int.MAX_VALUE,
                        LocAngle[regionAngle],
                        LocShape[hotspot.shape],
                    )
                }
            }
        }
    }

    private fun deleteSpawnedWallFiller(coords: CoordGrid, style: PohStyle, angle: Int) {
        val entity = LocEntity(style.doors.wallFiller.asRSCM(RSCMType.LOC), WALL_SHAPE, angle)
        val loc = LocInfo(LocLayerConstants.of(WALL_SHAPE), coords, entity)
        locRepo.del(loc, Int.MAX_VALUE)
    }

    /**
     * Building mode shows door hotspots; normal mode substitutes one door pair for shared
     * passages, wall filler for closed edges, and leaves exterior rooms open.
     */
    private fun refreshSharedPassages(
        region: Region,
        house: PohHouse,
        style: PohStyle,
        slot: PohRoomSlot,
        room: PohRoom,
        buildMode: Boolean,
    ) {
        val type = dataStore.room(room.type)
        val base = roomBaseCoords(region, slot)
        val exterior = room.type in EXTERIOR_ROOMS
        for (door in type.doorPlacements) {
            val rotatedEdge = door.edge.rotate(room.rotation)
            val neighbor = house.rooms[slot.translate(rotatedEdge)]
            val passage =
                neighbor != null &&
                    dataStore.room(neighbor.type).doorEdges.any {
                        it.rotate(neighbor.rotation) == rotatedEdge.opposite
                    }
            val neighborExterior = neighbor != null && neighbor.type in EXTERIOR_ROOMS
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(door.localX, door.localZ, slot.level),
                )
            val coords = base.translate(translation)
            val regionAngle = (door.rotation + room.rotation) and 0x3
            if (buildMode) {
                deleteSpawnedDoorLoc(coords, style, regionAngle)
                if (passage && !ownsSharedDoorway(slot, exterior, rotatedEdge, neighborExterior)) {
                    // A shared doorway keeps a single ghost pair, on the owning face; keep the
                    // other face's baked hotspot hidden so the doorway isn't doubled up.
                    deleteDoorLoc(coords, style, door.rotation)
                    continue
                }
                val hotspotLoc =
                    if (door.side == PohDoorSide.LEFT) style.doorLeftLoc else style.doorRightLoc
                locRepo.add(
                    coords,
                    hotspotLoc,
                    Int.MAX_VALUE,
                    LocAngle[door.rotation],
                    LocShape[DOOR_SHAPE],
                )
            } else {
                deleteDoorLoc(coords, style, door.rotation)
                // A doorway only gets one door pair, on the interior room's wall face: doors
                // exist solely on the house's exterior boundary (garden <-> room). Interior
                // rooms connect through open archways and garden edges stay open lawn.
                val replacement =
                    when {
                        passage && !exterior && neighborExterior ->
                            if (door.side == PohDoorSide.LEFT) {
                                style.doors.closedLeft
                            } else {
                                style.doors.closedRight
                            }
                        passage -> null
                        exterior -> null
                        else -> style.doors.wallFiller
                    } ?: continue
                locRepo.add(
                    coords,
                    replacement,
                    Int.MAX_VALUE,
                    LocAngle[regionAngle],
                    LocShape[DOOR_SHAPE],
                )
            }
        }
    }

    /**
     * Decides which face of a shared doorway hosts the single door pair / ghost hotspot pair. The
     * interior room owns garden boundaries (where the real door lives outside building mode);
     * same-kind pairs fall back to a deterministic slot ordering so exactly one side wins.
     */
    private fun ownsSharedDoorway(
        slot: PohRoomSlot,
        exterior: Boolean,
        rotatedEdge: PohDoorEdge,
        neighborExterior: Boolean,
    ): Boolean {
        if (exterior != neighborExterior) {
            return !exterior
        }
        val neighbor = slot.translate(rotatedEdge)
        val ordering =
            compareValuesBy(
                slot,
                neighbor,
                PohRoomSlot::level,
                PohRoomSlot::gridX,
                PohRoomSlot::gridZ,
            )
        return ordering < 0
    }

    internal fun deleteDoorLoc(coords: CoordGrid, style: PohStyle, angle: Int) {
        for (doorLoc in listOf(style.doorLeftLoc, style.doorRightLoc)) {
            val entity = LocEntity(doorLoc.asRSCM(RSCMType.LOC), DOOR_SHAPE, angle)
            val loc = LocInfo(LocLayerConstants.of(DOOR_SHAPE), coords, entity)
            if (locRepo.del(loc, Int.MAX_VALUE)) {
                return
            }
        }
    }

    private fun deleteSpawnedDoorLoc(coords: CoordGrid, style: PohStyle, angle: Int) {
        val doors = style.doors
        val spawned =
            listOf(
                doors.closedLeft,
                doors.closedRight,
                doors.openLeft,
                doors.openRight,
                doors.wallFiller,
            )
        for (locName in spawned) {
            val entity = LocEntity(locName.asRSCM(RSCMType.LOC), DOOR_SHAPE, angle)
            val loc = LocInfo(LocLayerConstants.of(DOOR_SHAPE), coords, entity)
            if (locRepo.del(loc, Int.MAX_VALUE)) {
                return
            }
        }
    }

    private fun ensureExitPortal(region: Region, house: PohHouse) {
        if (house.furniture.values.any { it == PohConstants.EXIT_PORTAL_LOC }) {
            return
        }
        val (slot, room) = findGarden(house) ?: return
        val centrepiece = gardenCentrepiece(room) ?: return
        val coords = hotspotCoords(region, slot, room, centrepiece)
        val angle = (centrepiece.rotation + room.rotation) and 0x3
        locRepo.add(
            coords,
            PohConstants.EXIT_PORTAL_LOC,
            Int.MAX_VALUE,
            LocAngle[angle],
            LocShape[centrepiece.shape],
        )
    }

    private fun findGarden(house: PohHouse): Pair<PohRoomSlot, PohRoom>? {
        val entry =
            house.rooms.entries.firstOrNull { it.value.type == "garden" }
                ?: house.rooms.entries.firstOrNull { it.value.type == "superior_garden" }
                ?: return null
        return entry.key to entry.value
    }

    private fun gardenCentrepiece(room: PohRoom): PohHotspot? =
        dataStore.hotspots(room.type).firstOrNull { it.name == "Centrepiece space" }

    internal fun enterCoords(region: Region, house: PohHouse): CoordGrid {
        val garden = findGarden(house)
        if (garden != null) {
            val (slot, room) = garden
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(GARDEN_ENTER_X, GARDEN_ENTER_Z, slot.level),
                )
            val coords = roomBaseCoords(region, slot).translate(translation)
            return walkableNear(coords)
        }
        val slot =
            house.rooms.keys.firstOrNull { it.level == PohFloor.GROUND.destLevel }
                ?: house.rooms.keys.first()
        return walkableNear(roomBaseCoords(region, slot).translate(3, 3))
    }

    private fun walkableNear(coords: CoordGrid): CoordGrid {
        if (!isBlocked(coords)) {
            return coords
        }
        for (radius in 1..3) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val candidate = coords.translate(dx, dz)
                    if (!isBlocked(candidate)) {
                        return candidate
                    }
                }
            }
        }
        return coords
    }

    private fun isBlocked(coords: CoordGrid): Boolean =
        !collision.isZoneValid(coords) || collision.isWalkBlocked(coords)

    private fun locType(loc: String): ObjectServerType {
        val id = loc.asRSCM(RSCMType.LOC)
        return ServerCacheManager.getObject(id) ?: error("Missing loc type: $loc")
    }

    private companion object {
        private const val DOOR_SHAPE: Int = 0

        private const val WALL_SHAPE: Int = 0

        private const val WINDOW_SPACE_NAME: String = "Window space"

        private const val WINDOW_PLACEHOLDER_LOC: String = "loc.poh_dynamic_window"

        private const val GARDEN_ENTER_X: Int = 3
        private const val GARDEN_ENTER_Z: Int = 1

        private val EXTERIOR_ROOMS: Set<String> =
            setOf("garden", "formal_garden", "superior_garden", "menagerie_outdoor")
    }
}
