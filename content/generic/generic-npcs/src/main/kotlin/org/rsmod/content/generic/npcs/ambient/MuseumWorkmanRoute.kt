package org.rsmod.content.generic.npcs.ambient

import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn

internal object MuseumWorkmanRoute {
    private val row by lazy { DbHelper.table("dbtable.museum_workman_route").single() }
    val spawn by lazy { row.column("dbcol.museum_workman_route:spawn", CoordGridCodec) }
    val loader by lazy { row.column("dbcol.museum_workman_route:loader", CoordGridCodec) }
    val rocks by lazy { row.column("dbcol.museum_workman_route:specimen_rocks", CoordGridCodec) }
    val toMuseum by lazy {
        row.multiColumn("dbcol.museum_workman_route:to_museum", CoordGridCodec)
            .also { require(it.size >= 2 && it.all { tile -> tile.level == spawn.level }) }
    }
    val toDigsite by lazy {
        row.multiColumn("dbcol.museum_workman_route:to_digsite", CoordGridCodec)
            .also {
                require(it.size >= 2 && it.all { tile -> tile.level == spawn.level })
                require(it.first() == toMuseum.last())
            }
    }
}
