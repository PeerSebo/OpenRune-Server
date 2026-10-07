package org.rsmod.api.game.process.player

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import jakarta.inject.Inject
import java.util.ArrayList
import java.util.WeakHashMap
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.message.ZoneProt
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.registry.zone.ZoneUpdateTransformer
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.api.utils.zone.SharedZoneEnclosedBuffers
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

public class PlayerZoneUpdateProcessor
@Inject
constructor(
    private val updates: ZoneUpdateMap,
    private val locReg: LocRegistry,
    private val objReg: ObjRegistry,
    private val enclosedBuffers: SharedZoneEnclosedBuffers,
    private val worldEntities: WorldEntityRegistry,
) {
    private data class RootView(val zone: ZoneKey, val base: CoordGrid)
    private val aboardViews: MutableMap<Player, RootView> = WeakHashMap()

    public fun computeEnclosedBuffers() {
        enclosedBuffers.computeSharedBuffers()
    }

    public fun process(player: Player) {
        player.processZoneUpdates()
    }

    public fun clearEnclosedBuffers() {
        enclosedBuffers.clear()
    }

    public fun clearPendingZoneUpdates() {
        updates.clear()
    }

    private fun Player.processZoneUpdates() {
        // Deck changes belong to the boat's client world and are sent after its rebuild in
        // RspCycle. Normal scenery around the boat still belongs to the root ocean build area.
        val aboard = worldEntities.findByInstanceCoords(coords)
        val currZone = ZoneKey.from(aboard?.rootCoords ?: coords)
        val observerLevel = aboard?.projectedLevel ?: level
        val visibleZones = visibleZoneKeys
        val buildArea = if (aboard == null) buildArea else BuildAreaUtils.calculateBuildArea(currZone)
        val previousView = aboardViews[this]
        val prevZone = if (aboard != null) {
            aboardViews[this] = RootView(currZone, buildArea)
            if (previousView?.base != buildArea) {
                visibleZones.clear()
                ZoneKey.NULL
            } else previousView.zone
        } else if (aboardViews.remove(this) != null) {
            visibleZones.clear()
            ZoneKey.NULL
        } else lastProcessedZone

        if (currZone != prevZone) {
            val currZones =
                currZone.computeVisibleNeighbouringZones().filterWithinBuildArea(buildArea)

            val newZones = IntArrayList(currZones).apply { removeAll(visibleZones) }
            processNewVisibleZones(buildArea, newZones)

            refreshVisibleZoneKeys(currZones)

            // Process existing zones before newly visible ones to avoid duplicating an update
            // with their initial snapshot.
            val oldZones = IntArrayList(currZones).apply { removeAll(newZones) }
            processVisibleZoneUpdates(buildArea, oldZones, observerLevel)
        } else {
            processVisibleZoneUpdates(buildArea, visibleZones, observerLevel)
        }

        // This field also drives PlayerRegistry membership. It must remain in instance space.
        lastProcessedZone = ZoneKey.from(coords)
    }

    private fun Player.processNewVisibleZones(buildArea: CoordGrid, zones: IntList) {
        for (zone in zones.intIterator()) {
            val key = ZoneKey(zone)
            val zoneBase = key.toCoords()
            sendZoneResetUpdate(buildArea, zoneBase)
            sendZonePersistentUpdates(buildArea, zoneBase, key)
        }
    }

    private fun Player.sendZoneResetUpdate(buildArea: CoordGrid, zoneBase: CoordGrid) {
        val deltaX = zoneBase.x - buildArea.x
        val deltaZ = zoneBase.z - buildArea.z
        val message = UpdateZoneFullFollows(deltaX, deltaZ, zoneBase.level)
        client.write(message)
    }

    private fun Player.sendZonePersistentUpdates(
        buildArea: CoordGrid,
        zoneBase: CoordGrid,
        zone: ZoneKey,
    ) {
        val spawnedLocs = locReg.findAllSpawned(zone)
        sendPersistentLocs(spawnedLocs)

        val spawnedObjs = objReg.findAll(zone)
        sendPersistentObjs(buildArea, zoneBase, spawnedObjs, observerUUID)
    }

    private fun Player.sendPersistentLocs(locs: Sequence<LocInfo>) {
        for (loc in locs) {
            val prot = ZoneUpdateTransformer.toPersistentLocChange(loc)
            client.write(prot)
        }
    }

    private fun Player.sendPersistentObjs(
        buildArea: CoordGrid,
        zoneBase: CoordGrid,
        objs: Sequence<Obj>,
        observerId: Long?,
    ) {
        val updates = ArrayList<ZoneProt>()
        for (obj in objs) {
            val prot = ZoneUpdateTransformer.toPersistentObjAdd(obj, observerId) ?: continue
            updates += prot
        }
        if (updates.isNotEmpty()) {
            sendZonePlayerEnclosedUpdates(buildArea, zoneBase, updates)
        }
    }

    private fun Player.refreshVisibleZoneKeys(zones: IntList) {
        visibleZoneKeys.clear()
        visibleZoneKeys.addAll(zones)
    }

    private fun Player.processVisibleZoneUpdates(buildArea: CoordGrid, currZones: List<Int>, observerLevel: Int) {
        for (zone in currZones) {
            val zoneKey = ZoneKey(zone)
            val zoneBase = zoneKey.toCoords()
            sendZoneSharedEnclosedUpdates(buildArea, zoneKey, zoneBase, observerLevel)
            sendZonePlayerEnclosedUpdates(buildArea, zoneKey, zoneBase, observerLevel)
        }
    }

    private fun Player.sendZonePlayerEnclosedUpdates(
        buildArea: CoordGrid,
        zone: ZoneKey,
        zoneBase: CoordGrid,
        observerLevel: Int,
    ) {
        val updates = updates[zone] ?: return
        check(updates.isNotEmpty) { "`updates` for zone should not be empty: $zone" }
        val playerSpecific =
            updates.toPlayerSpecificEnclosed(observerUUID, uid.packed, observerLevel)
        sendZonePlayerEnclosedUpdates(buildArea, zoneBase, playerSpecific)
    }

    private fun Player.sendZonePlayerEnclosedUpdates(
        buildArea: CoordGrid,
        zoneBase: CoordGrid,
        updates: List<ZoneProt>,
    ) {
        if (updates.isEmpty()) {
            return
        }
        val buffer = enclosedBuffers.computeBufferForClient(OldSchoolClientType.DESKTOP, updates)
        val deltaX = zoneBase.x - buildArea.x
        val deltaZ = zoneBase.z - buildArea.z
        val message = UpdateZonePartialEnclosed(deltaX, deltaZ, zoneBase.level, buffer)
        client.write(message)
    }

    private fun Player.sendZoneSharedEnclosedUpdates(
        buildArea: CoordGrid,
        zone: ZoneKey,
        zoneBase: CoordGrid,
        observerLevel: Int,
    ) {
        val enclosed = enclosedBuffers.get(zone, observerLevel) ?: return
        val buffer = enclosed[OldSchoolClientType.DESKTOP] ?: return
        val deltaX = zoneBase.x - buildArea.x
        val deltaZ = zoneBase.z - buildArea.z
        val prot = UpdateZonePartialEnclosed(deltaX, deltaZ, zoneBase.level, buffer)
        client.write(prot)
    }

    private fun Iterable<ZoneProt>.toPlayerSpecificEnclosed(
        observerId: Long?,
        observerUid: Int,
        observerLevel: Int,
    ): List<ZoneProt> {
        val enclosed = ArrayList<ZoneProt>()
        for (update in this) {
            val prot =
                (update as? ZoneUpdateTransformer.PartialFollowsZoneProt)
                    ?.toEnclosed(observerId, observerUid, observerLevel)
            if (prot != null) {
                enclosed += prot
            }
        }
        return enclosed
    }

    /**
     * observerId controls ground-item visibility; observerUid excludes combat participants.
     * Filter sounds by observer level, while scenery still crosses planes.
     */
    private fun ZoneUpdateTransformer.PartialFollowsZoneProt.toEnclosed(
        observerId: Long?,
        observerUid: Int,
        observerLevel: Int,
    ): ZoneProt? =
        when (this) {
            is ZoneUpdateTransformer.ObjPrivateZoneProt ->
                if (isVisibleTo(observerId)) backing else null
            is ZoneUpdateTransformer.ObjReveal ->
                if (observerId == obj.receiverId) null else backing
            is ZoneUpdateTransformer.SoundAreaExcluded ->
                if (isAudibleTo(observerUid, observerLevel)) backing else null
            else -> backing
        }

    /**
     * Visible zone columns include all four planes; filter area sounds by observer level
     * instead of dropping upstairs scenery.
     */
    private fun ZoneKey.computeVisibleNeighbouringZones(): IntList {
        val zones = IntArrayList(ZONE_VIEW_TOTAL_COUNT)
        for (x in -ZONE_VIEW_RADIUS..ZONE_VIEW_RADIUS) {
            for (z in -ZONE_VIEW_RADIUS..ZONE_VIEW_RADIUS) {
                val zone = translate(x, z)
                for (level in 0 until LEVEL_COUNT) {
                    zones.add(ZoneKey(zone.x, zone.z, level).packed)
                }
            }
        }
        return zones
    }

    private fun IntList.filterWithinBuildArea(buildArea: CoordGrid): IntList {
        val zones = IntArrayList(size)
        forEach { zone ->
            val zoneBase = ZoneKey(zone).toCoords()
            val deltaX = zoneBase.x - buildArea.x
            val deltaZ = zoneBase.z - buildArea.z
            val viewable = deltaX in BUILD_AREA_BOUNDS && deltaZ in BUILD_AREA_BOUNDS
            if (viewable) {
                zones.add(zone)
            }
        }
        return zones
    }

    public companion object {
        public const val ZONE_VIEW_RADIUS: Int = 3
        public const val LEVEL_COUNT: Int = 4
        public const val ZONE_VIEW_TOTAL_COUNT: Int =
            (2 * ZONE_VIEW_RADIUS + 1) * (2 * ZONE_VIEW_RADIUS + 1) * LEVEL_COUNT

        public val BUILD_AREA_BOUNDS: IntRange = 0 until BuildAreaUtils.SIZE
    }
}
