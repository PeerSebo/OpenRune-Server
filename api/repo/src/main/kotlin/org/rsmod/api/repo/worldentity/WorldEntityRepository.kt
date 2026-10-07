package org.rsmod.api.repo.worldentity

import jakarta.inject.Inject
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistryResult
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.game.entity.WorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid

/** Avatar allocation and release require the game thread. Decks remain allocated until release. */
public class WorldEntityRepository
@Inject
constructor(private val registry: WorldEntityRegistry, private val regions: RegionRepository) {
    /** Deck dimensions use zones, rootCoords use world tiles, and angle uses 0..2047 units. */
    public fun add(
        configId: Int,
        deckTemplate: RegionTemplate,
        deckZoneSizeX: Int,
        deckZoneSizeZ: Int,
        rootCoords: CoordGrid,
        angle: Int = 0,
        ownerSlot: Int = WorldEntity.NO_OWNER,
        activeLevel: Int = DECK_LEVEL,
        minLevel: Int = 0,
        maxLevel: Int = MAX_DECK_LEVEL,
    ): WorldEntity? {
        require(deckZoneSizeX in 1..WorldEntity.MAX_ZONE_SIZE) {
            "`deckZoneSizeX` must be in 1..${WorldEntity.MAX_ZONE_SIZE} zones: $deckZoneSizeX"
        }
        require(deckZoneSizeZ in 1..WorldEntity.MAX_ZONE_SIZE) {
            "`deckZoneSizeZ` must be in 1..${WorldEntity.MAX_ZONE_SIZE} zones: $deckZoneSizeZ"
        }

        val region = regions.addWorldEntity(deckTemplate) ?: return null

        val regionZoneLength = region.zoneLength
        if (deckZoneSizeX > regionZoneLength || deckZoneSizeZ > regionZoneLength) {
            regions.del(region)
            return null
        }

        val worldEntity =
            WorldEntity(
                configId = configId,
                region = region,
                instanceSouthWest = region.southWest,
                zoneSizeX = deckZoneSizeX,
                zoneSizeZ = deckZoneSizeZ,
                minLevel = minLevel,
                maxLevel = maxLevel,
                activeLevel = activeLevel,
            )
        worldEntity.rootCoords = rootCoords
        worldEntity.angle = angle
        worldEntity.ownerSlot = ownerSlot

        val result = registry.add(worldEntity)
        if (result !is WorldEntityRegistryResult.Add.Success) {
            regions.del(region)
            return null
        }
        return worldEntity
    }

    /**
     * Releases the avatar before its deck, retaining both when avatar release fails.
     *
     * @return `true` when the entity is no longer registered - either it was deleted now, or it was
     *   already gone - in which case the caller must drop its own reference. `false` means the
     *   avatar release **threw** and the entity is still live; the caller must retry on a later
     *   tick rather than treat the hull as gone.
     */
    public fun del(worldEntity: WorldEntity): Boolean {
        val result = registry.del(worldEntity)
        if (result is WorldEntityRegistryResult.Delete.AvatarReleaseFailure) {
            return false
        }
        return regions.del(worldEntity.region)
    }

    public fun deckArrivalCoords(
        worldEntity: WorldEntity,
        offsetX: Int,
        offsetZ: Int,
        level: Int = worldEntity.activeLevel,
    ): CoordGrid {
        val southWest = worldEntity.instanceSouthWest
        val coords = CoordGrid(southWest.x + offsetX, southWest.z + offsetZ, level)
        check(coords in worldEntity) {
            "Deck arrival coords fall outside the world entity rectangle: " +
                "coords=$coords, worldEntity=$worldEntity"
        }
        return coords
    }

    public fun findByInstanceCoords(coords: CoordGrid): WorldEntity? =
        registry.findByInstanceCoords(coords)

    public fun findByIndex(index: Int): WorldEntity? = registry[index]

    public companion object {
        /**
         * Deck plane. `WorldEntityType.mainLevel` is `1` for every boat config id at rev 240
         * (verified via `./gradlew :or-cache:dumpWorldEntities`). Whether the client actually
         * requires `activeLevel` to equal `mainLevel`, or `mainLevel` is a pure client-side
         * authoring hint, is unverified. If the deck renders
         * at the wrong height, try `activeLevel = 0` with the deck template authored on plane 0.
         */
        public const val DECK_LEVEL: Int = 1

        /** rsprot requires instance levels in `0..<4`. */
        public const val MAX_DECK_LEVEL: Int = 3

        public const val TILES_PER_ZONE: Int = ZoneGrid.LENGTH
    }
}
