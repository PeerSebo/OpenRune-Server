package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.table.sailing.SailingBoatHotspotRow
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.scripts.SailingSidepanel
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.loc.LocLayerConstants

internal class BoatDeckService @Inject constructor(private val locs: LocRepository) {
    fun populate(player: Player, slot: Int, hull: BoatHullEntity, entity: WorldEntity): Boolean {
        val status = SailingSidepanel.status(player, slot) ?: return false
        val source = hull.deckSource.southWestZone
        val baseHull = SailingBoats.baseHull(status.boat).loc
        val sourceHull = sequence {
            for (x in 0 until hull.deckSource.zoneSizeX) {
                for (z in 0 until hull.deckSource.zoneSizeZ) {
                    yieldAll(locs.findAll(ZoneKey(source.x + x, source.z + z, 0)))
                }
            }
        }.firstOrNull { it.id == baseHull.id } ?: return false

        val placements = fittings(player, status) + NativeBoatLoc(
            status.hull.loc.id, sourceHull.coords, sourceHull.angleId, sourceHull.shapeId,
        )
        for (placement in placements) {
            val coords = CoordGrid(
                entity.instanceSouthWest.x + placement.coords.x - source.x * 8,
                entity.instanceSouthWest.z + placement.coords.z - source.z * 8,
                placement.coords.level,
            )
            if (coords !in entity) return false
            val loc = LocInfo(
                LocLayerConstants.of(placement.shape), coords,
                LocEntity(placement.id, placement.shape, placement.angle),
            )
            if (!locs.add(loc, Int.MAX_VALUE)) return false
        }
        return true
    }

    internal fun fittings(player: Player, status: SailingSidepanel.BoatStatus): List<NativeBoatLoc> = buildList {
        val boat = status.boat
        val slot = status.slot
        status.keel?.loc?.forEach { add(NativeBoatLoc(it.t1.id, it.t0, it.t2, it.t3)) }
        status.steering?.loc?.forEach { add(NativeBoatLoc(it.t1.id, it.t0, it.t2, it.t3)) }
        status.sail?.loc?.forEach { add(NativeBoatLoc(it.t0.id, it.t1, it.t2, it.t3)) }
        status.brazier?.loc?.forEach { add(NativeBoatLoc(it.t1.id, it.t0, it.t2, it.t3)) }
        boat.staticFacility.forEach { add(NativeBoatLoc(it.t1.id, it.t0, it.t2, it.t3)) }

        // Mast and cloth are separate locs; Set-sails belongs to the cloth.
        val patterns = boat.sailPatternOption.sortedBy { it.sailingRequirement }
        val pattern = patterns.getOrNull(player.readSailingVar(SailingBoatVars.field(slot, "sail_pattern")))
            ?: patterns.firstOrNull()
        pattern?.loc?.forEach { add(NativeBoatLoc(it.t0.id, it.t1, it.t2, it.t3)) }
        val flags = boat.flagOption.sortedBy { it.sailingRequirement }
        flags.getOrNull(player.readSailingVar(SailingBoatVars.field(slot, "flag")))?.loc?.forEach {
            add(NativeBoatLoc(it.t1.id, it.t0, it.t2, it.t3))
        }
        val trims = boat.trimOption.sortedBy { it.sailingRequirement }
        trims.getOrNull(player.readSailingVar(SailingBoatVars.field(slot, "trim")))?.loc?.forEach {
            add(NativeBoatLoc(it.t1.id, it.t0, it.t2, it.t3))
        }

        for (hotspot in SailingBoats.hotspots(boat)) {
            val stored = player.readSailingVar(SailingBoatVars.hotspot(slot, hotspot.hotspotIndex))
            if (stored == 0) continue
            val facility = SailingBoatHotspotRow.getRow(hotspot.layoutRowId).option.firstOrNull {
                it.facilityBottleId == stored
            } ?: continue
            val loc = facility.loc.firstOrNull() ?: continue
            // The third hotspot field is layout metadata, not a loc shape.
            add(NativeBoatLoc(loc.id, hotspot.coords, hotspot.angle, 10))
        }
    }
}

internal data class NativeBoatLoc(val id: Int, val coords: CoordGrid, val angle: Int, val shape: Int)
