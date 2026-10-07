package org.rsmod.content.generic.npcs.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.PluginPack
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

class GenericNpcsPluginPack : PluginPack() {
    override fun extraTasks(): List<CacheTask> = listOf(VarrockTraineeIdle())

    override fun dbTables() = listOf(
        edgevilleBankers(),
        dbTable("dbtable.museum_workman_route", serverOnly = true) {
            column("spawn", 0, VarType.COORDGRID)
            column("loader", 1, VarType.COORDGRID)
            column("specimen_rocks", 2, VarType.COORDGRID)
            column("to_museum", 3, VarType.COORDGRID)
            column("to_digsite", 4, VarType.COORDGRID)
            row("dbrow.museum_workman_route") {
                columnCoord(0, CoordGrid(3310, 3425))
                columnCoord(1, CoordGrid(3357, 3415))
                columnCoord(2, CoordGrid(3356, 3415))
                val toMuseum = listOf(
                    CoordGrid(3330, 3425), CoordGrid(3298, 3425), CoordGrid(3297, 3428),
                    CoordGrid(3293, 3428), CoordGrid(3267, 3428), CoordGrid(3264, 3429),
                    CoordGrid(3264, 3440), CoordGrid(3264, 3444),
                )
                val toDigsite = listOf(
                    toMuseum.last(), CoordGrid(3264, 3442), CoordGrid(3264, 3438),
                    CoordGrid(3264, 3429), CoordGrid(3267, 3428), CoordGrid(3294, 3428),
                    CoordGrid(3298, 3428), CoordGrid(3298, 3425), CoordGrid(3330, 3425),
                    CoordGrid(3358, 3414),
                )
                column(3, *toMuseum.map { it.packed }.toTypedArray<Any>())
                column(4, *toDigsite.map { it.packed }.toTypedArray<Any>())
            }
        },
    )
}
