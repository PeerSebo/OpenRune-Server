package org.rsmod.api.registry.loc

import dev.openrune.ServerCacheManager
import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet
import jakarta.inject.Inject
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.map.ZoneLocMap
import org.rsmod.game.map.collision.addLoc
import org.rsmod.game.map.collision.removeLoc
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

public class LocRegistryNormal
@Inject
constructor(
    private val updates: ZoneUpdateMap,
    private val collision: CollisionFlagMap,
    private val locZones: LocZoneStorage,
) {
    private val mapLocs: ZoneLocMap by locZones::mapLocs
    private val spawnedLocs: ZoneLocMap by locZones::spawnedLocs

    public fun add(loc: LocInfo): LocRegistryResult.Add {
        check(!RegionRegistry.inWorkingArea(loc.coords)) {
            "Cannot add loc to region-reserved area with `LocRegistryNormal`."
        }

        val zoneKey = ZoneKey.from(loc.coords)
        val locZoneKey = loc.toLocZoneGridKey()
        val spawnZone = spawnedLocs.getOrPut(zoneKey)
        val mapLoc = mapLocs[zoneKey]?.get(locZoneKey.packed)
        val exactMapLocExists = loc.entity.packed == mapLoc

        removeSpawnedLoc(spawnZone, locZoneKey, loc.coords)

        if (!exactMapLocExists) {
            spawnZone[locZoneKey.packed] = loc.entity.packed
        }

        addLocCollision(loc)
        updates.locAdd(loc)

        return if (exactMapLocExists) {
            LocRegistryResult.Add.NormalMapLoc
        } else {
            LocRegistryResult.Add.NormalSpawned
        }
    }

    public fun del(loc: LocInfo): LocRegistryResult.Delete {
        val zoneKey = ZoneKey.from(loc.coords)
        val locZoneKey = loc.toLocZoneGridKey()

        // Deleting spawned locs takes priority over static map locs.
        val deletedSpawnedLoc = deleteSpawnedLoc(zoneKey, locZoneKey, loc)
        if (deletedSpawnedLoc) {
            return LocRegistryResult.Delete.NormalSpawned
        }

        val deletedMapLoc = deleteStaticLoc(zoneKey, locZoneKey, loc)
        if (deletedMapLoc) {
            return LocRegistryResult.Delete.NormalMapLoc
        }

        return LocRegistryResult.Delete.LocNotFound
    }

    public fun findType(coords: CoordGrid, loc: Int): LocInfo? {
        val spawnedLoc = spawnedLocs.findType(coords, loc)
        if (spawnedLoc != null) {
            return spawnedLoc.takeIf { it.entity.id != DELETED_LOC_ID }
        }

        val mapLoc = mapLocs.findType(coords, loc) ?: return null
        // Map locs can sometimes be replaced by a spawned loc that occupies the same "layer."
        // Take for example trees being replaced by tree stumps during woodcutting; these will
        // share shape and angle, but are different locs. Without this condition, the original
        // tree would be found in `mapLocs` and would otherwise return as a valid `LocInfo.`
        val layeredLoc = spawnedLocs.findLayer(coords, mapLoc.layer)

        return mapLoc.takeIf { layeredLoc == null }
    }

    public fun findShape(coords: CoordGrid, shape: Int): LocInfo? {
        val spawnedLoc = spawnedLocs.findShape(coords, shape)
        if (spawnedLoc != null) {
            return spawnedLoc.takeIf { it.entity.id != DELETED_LOC_ID }
        }

        val mapLoc = mapLocs.findShape(coords, shape) ?: return null
        // Map locs can sometimes be replaced by a spawned loc that occupies the same "layer."
        // Take for example trees being replaced by tree stumps during woodcutting; these will
        // share shape and angle, but are different locs. Without this condition, the original
        // tree would be found in `mapLocs` and would otherwise return as a valid `LocInfo.`
        val layeredLoc = spawnedLocs.findLayer(coords, mapLoc.layer)

        return mapLoc.takeIf { layeredLoc == null }
    }

    public fun findAll(zone: ZoneKey): Sequence<LocInfo> {
        val spawnedLocs = spawnedLocs[zone]?.byte2IntEntrySet()
        val staticLocs = mapLocs[zone]?.byte2IntEntrySet()
        if (spawnedLocs == null && staticLocs == null) {
            return emptySequence()
        }
        return sequence {
            val processedKeys = ByteOpenHashSet()
            val zoneCoords = zone.toCoords()
            if (spawnedLocs != null) {
                for (entry in spawnedLocs) {
                    processedKeys += entry.byteKey
                    val entity = LocEntity(entry.intValue)
                    if (entity.id == DELETED_LOC_ID) {
                        continue
                    }
                    val key = LocZoneKey(entry.byteKey)
                    val coords = zoneCoords.translate(key.x, key.z)
                    val loc = LocInfo(key.layer, coords, entity)
                    yield(loc)
                }
            }
            if (staticLocs != null) {
                for (entry in staticLocs) {
                    if (entry.byteKey in processedKeys) {
                        continue
                    }
                    val entity = LocEntity(entry.intValue)
                    val key = LocZoneKey(entry.byteKey)
                    val coords = zoneCoords.translate(key.x, key.z)
                    val loc = LocInfo(key.layer, coords, entity)
                    yield(loc)
                }
            }
        }
    }

    /** Internal removal does not publish a zone update. */
    private fun removeSpawnedLoc(zone: Byte2IntOpenHashMap, key: LocZoneKey, coords: CoordGrid) {
        val removed = zone.remove(key.packed)
        if (removed != zone.defaultReturnValue()) {
            val entity = LocEntity(removed)
            val removeLoc = LocInfo(key.layer, coords, entity)
            removeLocCollision(removeLoc)
        }
    }

    private fun deleteSpawnedLoc(zoneKey: ZoneKey, locZoneKey: LocZoneKey, loc: LocInfo): Boolean {
        val zone = spawnedLocs[zoneKey] ?: return false
        val previousLoc = zone.get(locZoneKey.packed)
        if (previousLoc == zone.defaultReturnValue()) {
            return false
        }

        // Match id, shape and angle as well as tile/layer so a replaced loc cannot delete its
        // successor.
        if (loc.entity.packed != previousLoc) {
            return false
        }

        val removed = zone.remove(locZoneKey.packed)
        if (removed == zone.defaultReturnValue()) {
            return false
        }

        removeLocCollision(loc)

        val maskedStaticLoc = mapLocs[zoneKey]?.getOrDefault(locZoneKey.packed, null)
        if (maskedStaticLoc != null) {
            val maskedLoc = LocInfo(loc.layer, loc.coords, LocEntity(maskedStaticLoc))
            addLocCollision(maskedLoc)
            updates.locAdd(maskedLoc)
        } else {
            updates.locDel(loc)
        }

        return true
    }

    private fun deleteStaticLoc(zoneKey: ZoneKey, locZoneKey: LocZoneKey, loc: LocInfo): Boolean {
        val staticZone = mapLocs[zoneKey] ?: return false
        val staticLoc = staticZone.getOrDefault(locZoneKey.packed, null) ?: return false

        // Match id, shape and angle as well as tile/layer so a replaced loc cannot delete its
        // successor.
        if (loc.entity.packed != staticLoc) {
            return false
        }

        val deletedLoc = LocEntity(staticLoc).copy(id = DELETED_LOC_ID)
        spawnedLocs[zoneKey, locZoneKey] = deletedLoc
        removeLocCollision(loc)
        updates.locDel(loc)

        return true
    }

    private fun addLocCollision(loc: LocInfo) {
        val type = ServerCacheManager.getObject(loc.id) ?: return
        collision.addLoc(loc, type)
    }

    private fun removeLocCollision(loc: LocInfo) {
        val type = ServerCacheManager.getObject(loc.id) ?: return
        collision.removeLoc(loc, type)
    }

    public companion object {
        public val DELETED_LOC_ID: Int = LocEntity.NULL.id
    }
}
