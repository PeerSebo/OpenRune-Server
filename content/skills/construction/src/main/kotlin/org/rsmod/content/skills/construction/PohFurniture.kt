package org.rsmod.content.skills.construction

import dev.openrune.types.ObjectServerType
import org.rsmod.api.table.PohFurnitureRow

object PohFurniture {
    private val byHotspotLocId: Map<Int, List<PohFurnitureRow>> by lazy {
        val map = HashMap<Int, MutableList<PohFurnitureRow>>()
        for (row in PohFurnitureRow.all()) {
            for (hotspot in row.hotspotLoc) {
                map.getOrPut(hotspot.id) { mutableListOf() } += row
            }
        }
        map.mapValues { entry -> entry.value.sortedWith(compareBy({ it.level }, { it.xp })) }
    }

    private val byBuiltLocId: Map<Int, List<PohFurnitureRow>> by lazy {
        val map = HashMap<Int, MutableList<PohFurnitureRow>>()
        for (row in PohFurnitureRow.all()) {
            for (built in row.builtLoc) {
                map.getOrPut(built.id) { mutableListOf() } += row
            }
        }
        map
    }

    fun optionsFor(hotspotLocId: Int): List<PohFurnitureRow> =
        byHotspotLocId[hotspotLocId].orEmpty()

    fun rowsForBuilt(builtLocId: Int): List<PohFurnitureRow> = byBuiltLocId[builtLocId].orEmpty()

    fun builtLocFor(row: PohFurnitureRow, hotspotLocId: Int): ObjectServerType? {
        val index = row.hotspotLoc.indexOfFirst { it.id == hotspotLocId }
        return if (index >= 0) row.builtLoc[index] else null
    }

    fun hotspotLocFor(row: PohFurnitureRow, builtLocId: Int): ObjectServerType? {
        val index = row.builtLoc.indexOfFirst { it.id == builtLocId }
        return if (index >= 0) row.hotspotLoc[index] else null
    }

    fun allBuiltLocs(): Set<String> =
        PohFurnitureRow.all().flatMap { row -> row.builtLoc.map { it.internalName } }.toSet()
}
