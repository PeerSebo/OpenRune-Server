package org.rsmod.content.minigames.giantsfoundry.quest

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.map.CoordGrid

internal object SleepingGiantsData {
    data class Scenery(val original: String, val cutscene: String,
        val coords: List<CoordGrid>, val originalLevel: Int)

    val scenery by lazy {
        DbHelper.table("dbtable.sleeping_giants_scenery").map { row ->
            val prefix = "dbcol.sleeping_giants_scenery:"
            Scenery(
                RSCM.getReverseMapping(RSCMType.LOC, row.column(prefix + "original", LocIdCodec)),
                RSCM.getReverseMapping(RSCMType.LOC, row.column(prefix + "cutscene", LocIdCodec)),
                row.multiColumn(prefix + "coords", CoordGridCodec),
                row.column(prefix + "original_level", IntCodec),
            )
        }
    }

    data class Repair(
        val name: String,
        val original: String,
        val multi: String,
        val broken: String,
        val repaired: String,
        val state: String,
        val tool: String,
        val materials: List<Pair<String, Int>>,
        val nails: Int,
        val animations: List<Pair<String, Int>>,
        val coords: CoordGrid,
    )

    val repairs by lazy {
        DbHelper.table("dbtable.sleeping_giants_repairs").map { row ->
            val prefix = "dbcol.sleeping_giants_repairs:"
            val items = row.multiColumn(prefix + "materials", ObjIdCodec).map {
                RSCM.getReverseMapping(RSCMType.OBJ, it)
            }
            val quantities = row.multiColumn(prefix + "counts", IntCodec)
            val sequences = row.multiColumn(prefix + "animations", IntCodec).map {
                RSCM.getReverseMapping(RSCMType.SEQ, it)
            }
            val durations = row.multiColumn(prefix + "durations", IntCodec)
            require(items.size == quantities.size && quantities.all { it >= 0 })
            require(sequences.size == durations.size && durations.all { it > 0 })
            fun loc(name: String) = RSCM.getReverseMapping(RSCMType.LOC, row.column(prefix + name, LocIdCodec))
            Repair(
                row.column(prefix + "name", StringCodec),
                loc("original"), loc("multi"), loc("broken"), loc("repaired"),
                RSCM.getReverseMapping(RSCMType.VARBIT, row.column(prefix + "state", IntCodec)),
                RSCM.getReverseMapping(RSCMType.OBJ, row.column(prefix + "tool", ObjIdCodec)),
                items.zip(quantities).filter { it.second > 0 }, row.column(prefix + "nails", IntCodec),
                sequences.zip(durations), CoordGrid(row.column(prefix + "coords", IntCodec)),
            )
        }.also { require(it.size == 3) }
    }

    val supplies by lazy {
        val row = DbHelper.table("dbtable.sleeping_giants_supplies").single()
        val items = row.multiColumn("dbcol.sleeping_giants_supplies:items", ObjIdCodec).map {
            RSCM.getReverseMapping(RSCMType.OBJ, it)
        }
        val counts = row.multiColumn("dbcol.sleeping_giants_supplies:counts", IntCodec)
        require(items.size == counts.size && counts.all { it > 0 } && counts.sum() == 20)
        items.zip(counts)
    }

    val nails by lazy {
        DbHelper.table("dbtable.sleeping_giants_supplies").single()
            .multiColumn("dbcol.sleeping_giants_supplies:nails", ObjIdCodec)
            .map { RSCM.getReverseMapping(RSCMType.OBJ, it) }
    }
}
