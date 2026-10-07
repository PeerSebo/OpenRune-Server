package org.rsmod.api.poh

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.region.Region
import org.rsmod.game.region.util.RegionRotations
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
public class PohManager
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val locRepo: LocRepository,
    private val dataStore: PohDataStore,
    private val regionBuilder: PohRegionBuilder,
    private val collision: CollisionFlagMap,
    private val eventBus: EventBus,
) {
    public class ActiveHouse(
        public val region: Region,
        public val house: PohHouse,
        public val owner: Long,
    )

    public sealed class EnterResult {
        public data class Success(val enterCoords: CoordGrid) : EnterResult()

        public data object NoHouse : EnterResult()

        public data object NoSpace : EnterResult()
    }

    private val scene = PohScene(locRepo, dataStore, collision)

    private val active = HashMap<Long, ActiveHouse>()

    public fun hasHouse(player: Player): Boolean = player.pohHouseLocation != 0

    public fun activeHouse(player: Player): ActiveHouse? = active[player.ownerId()]

    public fun houseOf(player: Player): PohHouse? =
        activeHouse(player)?.house ?: PohAttributes.load(player, dataStore)

    public fun isInOwnHouse(player: Player): Boolean {
        val current = activeHouse(player) ?: return false
        return player.coords.x in current.region.southWest.x..current.region.northEast.x &&
            player.coords.z in current.region.southWest.z..current.region.northEast.z
    }

    public fun createHouse(player: Player) {
        check(!hasHouse(player)) { "Player already owns a house: ${player.username}" }
        player.pohHouseLocation = PohLocation.RIMMINGTON.varValue
        player.pohHouseStyle = 0
        val house =
            PohHouse(
                size = PohConstants.gridSize(player.constructionLevelForHouse()),
                style = 0,
                location = PohLocation.RIMMINGTON.varValue,
            )
        val gardenSlot = PohRoomSlot(PohFloor.GROUND.destLevel, gridX = 3, gridZ = 3)
        val parlourSlot = PohRoomSlot(PohFloor.GROUND.destLevel, gridX = 3, gridZ = 4)
        house.rooms[gardenSlot] = PohRoom("garden", rotation = 0)
        house.rooms[parlourSlot] = PohRoom("parlour", rotation = 0)

        val centrepiece =
            dataStore.hotspots("garden").first { it.loc == PohConstants.GARDEN_CENTREPIECE_LOC }
        house.furniture[
                PohHotspotSlot(
                    gardenSlot.level,
                    gardenSlot.gridX,
                    gardenSlot.gridZ,
                    centrepiece.index,
                )] = PohConstants.EXIT_PORTAL_LOC
        PohAttributes.save(player, house, dataStore)
    }

    public fun enter(player: Player, buildMode: Boolean): EnterResult {
        val house = houseOf(player) ?: return EnterResult.NoHouse
        house.size = PohConstants.gridSize(player.constructionLevelForHouse())

        discard(player)

        val template = regionBuilder.buildTemplate(house)
        val region = regionRepo.add(template) ?: return EnterResult.NoSpace
        regionRepo.protect(region)
        scene.blockLawnBoundary(region, house)

        val activeHouse = ActiveHouse(region, house, player.ownerId())
        active[player.ownerId()] = activeHouse

        player.pohBuildingMode = if (buildMode) 1 else 0
        player.pohHouseSize = house.size - 3
        scene.refresh(region, activeHouse.house, buildMode)

        val enterCoords = scene.enterCoords(region, house)
        val location = PohLocation.forVarValue(house.location) ?: PohLocation.RIMMINGTON
        player.beginHouseRecovery(location.exitCoords)
        PathingEntityCommon.telejump(player, collision, enterCoords)
        eventBus.publish(PohHouseEnteredEvent(player, buildMode))
        return EnterResult.Success(enterCoords)
    }

    public fun leave(player: Player): CoordGrid {
        val house = houseOf(player)
        discard(player)
        player.endHouseRecovery()
        player.pohBuildingMode = 0
        eventBus.publish(PohHouseExitedEvent(player))
        val location = house?.location?.let(PohLocation::forVarValue) ?: PohLocation.RIMMINGTON
        return location.exitCoords
    }

    public fun handleLogout(player: Player) {
        if (active.containsKey(player.ownerId())) {
            if (isInOwnHouse(player)) {
                val location =
                    PohLocation.forVarValue(player.pohHouseLocation) ?: PohLocation.RIMMINGTON
                player.beginHouseRecovery(location.exitCoords)
            } else {
                player.endHouseRecovery()
            }
            eventBus.publish(PohHouseExitedEvent(player))
        }
        discard(player)
    }

    public fun handleLogin(player: Player) {
        player.endHouseRecovery()
        player.pohBuildingMode = 0
    }

    public fun handleMovement(player: Player) {
        if (activeHouse(player) == null || isInOwnHouse(player)) return
        player.endHouseRecovery()
        player.pohBuildingMode = 0
        eventBus.publish(PohHouseExitedEvent(player))
        discard(player)
    }

    public fun rebuild(player: Player) {
        val current = activeHouse(player) ?: return
        val relative = player.coords - current.region.southWest
        val buildMode = player.pohBuildingMode == 1

        discard(player)

        val template = regionBuilder.buildTemplate(current.house)
        val region = regionRepo.add(template) ?: return
        regionRepo.protect(region)
        scene.blockLawnBoundary(region, current.house)

        val activeHouse = ActiveHouse(region, current.house, current.owner)
        active[current.owner] = activeHouse
        scene.refresh(region, activeHouse.house, buildMode)

        val dest =
            CoordGrid(
                region.southWest.x + relative.x,
                region.southWest.z + relative.z,
                player.coords.level,
            )
        PathingEntityCommon.telejump(player, collision, dest)
        eventBus.publish(PohHouseEnteredEvent(player, buildMode))
    }

    public fun refresh(player: Player) {
        val current = activeHouse(player) ?: return
        scene.refresh(current.region, current.house, buildMode = player.pohBuildingMode == 1)
    }

    public fun persist(player: Player) {
        val house = activeHouse(player)?.house ?: return
        PohAttributes.save(player, house, dataStore)
    }

    public fun applyFurniture(player: Player, slot: PohHotspotSlot, furnitureLoc: String) {
        val current = activeHouse(player) ?: return
        val room = current.house.rooms[slot.roomSlot] ?: return
        val hotspot = dataStore.hotspot(slot.hotspotIndex)
        current.house.furniture[slot] = furnitureLoc

        val coords = hotspotCoords(current.region, slot.roomSlot, room, hotspot)
        val angle = (hotspot.rotation + room.rotation) and 0x3
        locRepo.add(coords, furnitureLoc, Int.MAX_VALUE, LocAngle[angle], LocShape[hotspot.shape])
        PohAttributes.save(player, current.house, dataStore)
    }

    public fun removeFurniture(player: Player, slot: PohHotspotSlot) {
        val current = activeHouse(player) ?: return
        val room = current.house.rooms[slot.roomSlot] ?: return
        val hotspot = dataStore.hotspot(slot.hotspotIndex)
        current.house.furniture.remove(slot)

        val coords = hotspotCoords(current.region, slot.roomSlot, room, hotspot)
        // Restoring the baked map loc: template-space angle, the region registry rotates it.
        locRepo.add(
            coords,
            hotspot.loc,
            Int.MAX_VALUE,
            LocAngle[hotspot.rotation],
            LocShape[hotspot.shape],
        )
        PohAttributes.save(player, current.house, dataStore)
    }

    public fun addRoom(player: Player, slot: PohRoomSlot, room: PohRoom) {
        val current = activeHouse(player) ?: return
        current.house.rooms[slot] = room
        PohAttributes.save(player, current.house, dataStore)
        rebuild(player)
    }

    public fun removeRoom(player: Player, slot: PohRoomSlot) {
        val current = activeHouse(player) ?: return
        current.house.rooms.remove(slot)
        current.house.furniture.keys.removeAll { it.roomSlot == slot }
        PohAttributes.save(player, current.house, dataStore)
        rebuild(player)
    }

    public fun setBuildingMode(player: Player, enabled: Boolean) {
        player.pohBuildingMode = if (enabled) 1 else 0
        refresh(player)
    }

    public fun roomSlotAt(region: Region, coords: CoordGrid): PohRoomSlot? =
        scene.roomSlotAt(region, coords)

    public fun roomBaseCoords(region: Region, slot: PohRoomSlot): CoordGrid =
        scene.roomBaseCoords(region, slot)

    public fun hotspotCoords(
        region: Region,
        slot: PohRoomSlot,
        room: PohRoom,
        hotspot: PohHotspot,
    ): CoordGrid = scene.hotspotCoords(region, slot, room, hotspot)

    public fun findHotspot(
        player: Player,
        coords: CoordGrid,
        locId: Int,
    ): Pair<PohHotspotSlot, PohHotspot>? {
        val current = activeHouse(player) ?: return null
        val slot = roomSlotAt(current.region, coords) ?: return null
        val room = current.house.rooms[slot] ?: return null
        for (hotspot in dataStore.hotspots(room.type)) {
            if (hotspot.loc.asRSCM(RSCMType.LOC) != locId) {
                continue
            }
            if (hotspotCoords(current.region, slot, room, hotspot) == coords) {
                val hotspotSlot = PohHotspotSlot(slot.level, slot.gridX, slot.gridZ, hotspot.index)
                return hotspotSlot to hotspot
            }
        }
        return null
    }

    public fun findBuiltFurniture(
        player: Player,
        coords: CoordGrid,
        locId: Int,
    ): Pair<PohHotspotSlot, String>? {
        val current = activeHouse(player) ?: return null
        val slot = roomSlotAt(current.region, coords) ?: return null
        val room = current.house.rooms[slot] ?: return null
        for ((hotspotSlot, furnitureLoc) in current.house.furnitureAt(slot)) {
            if (furnitureLoc.asRSCM(RSCMType.LOC) != locId) {
                continue
            }
            val hotspot = dataStore.hotspot(hotspotSlot.hotspotIndex)
            if (hotspotCoords(current.region, slot, room, hotspot) == coords) {
                return hotspotSlot to furnitureLoc
            }
        }
        return null
    }

    public fun findDoorEdge(player: Player, coords: CoordGrid): Pair<PohRoomSlot, PohDoorEdge>? {
        val current = activeHouse(player) ?: return null
        val slot = roomSlotAt(current.region, coords) ?: return null
        val room = current.house.rooms[slot] ?: return null
        val type = dataStore.room(room.type)
        val base = roomBaseCoords(current.region, slot)
        for (door in type.doorPlacements) {
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(door.localX, door.localZ, slot.level),
                )
            if (base.translate(translation) == coords) {
                return slot to door.edge.rotate(room.rotation)
            }
        }
        return null
    }

    /**
     * Re-hides the template door-hotspot map loc at [coords]. Deleting a spawned loc removes the
     * record that shadowed the baked map loc, resurrecting it â€” callers that despawn a real door
     * (the door swing) must re-delete the hotspot underneath.
     */
    public fun hideDoorHotspot(player: Player, coords: CoordGrid) {
        val current = activeHouse(player) ?: return
        val style = dataStore.style(current.house.style)
        val slot = roomSlotAt(current.region, coords) ?: return
        val room = current.house.rooms[slot] ?: return
        val type = dataStore.room(room.type)
        val base = roomBaseCoords(current.region, slot)
        for (door in type.doorPlacements) {
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(door.localX, door.localZ, slot.level),
                )
            if (base.translate(translation) == coords) {
                scene.deleteDoorLoc(coords, style, door.rotation)
                return
            }
        }
    }

    private fun discard(player: Player) {
        val current = active.remove(player.ownerId()) ?: return
        regionRepo.unprotect(current.region)
    }

    private fun Player.ownerId(): Long = checkNotNull(uuid) { "Player uuid not assigned: $this" }
}
