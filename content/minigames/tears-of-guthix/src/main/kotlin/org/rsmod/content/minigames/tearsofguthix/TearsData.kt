package org.rsmod.content.minigames.tearsofguthix

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.StatIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.map.CoordGrid

internal object TearsData {
    val rules by lazy {
        val row = DbHelper.table("dbtable.tears_rules").single()
        TearsRules(
            row.column("dbcol.tears_rules:stream_ticks", IntCodec),
            row.column("dbcol.tears_rules:weekly_days", IntCodec),
            row.column("dbcol.tears_rules:xp_requirement", IntCodec),
            row.column("dbcol.tears_rules:optional_ticks", IntCodec),
            row.column("dbcol.tears_rules:entry", CoordGridCodec),
            row.column("dbcol.tears_rules:return", CoordGridCodec),
            row.multiColumn("dbcol.tears_rules:bounds", IntCodec),
            row.column("dbcol.tears_rules:approach_ticks", IntCodec),
            row.multiColumn("dbcol.tears_rules:entry_route", CoordGridCodec),
            row.column("dbcol.tears_rules:tail_pause_ticks", IntCodec),
            row.column("dbcol.tears_rules:crossing_timeout_ticks", IntCodec),
            row.column("dbcol.tears_rules:arrival_pause_ticks", IntCodec),
        ).also {
            require(it.bounds.size == 5 && it.streamTicks >= 6 && it.weeklyDays > 0)
            require(it.entryRoute.size >= 3 && it.entryRoute.first() == it.entry)
            require(it.entryRoute.all(it::contains) && it.tailPauseTicks > 0)
            require(it.crossingTimeoutTicks > it.entryRoute.size + it.tailPauseTicks)
            require(it.arrivalPauseTicks > 0)
        }
    }
    val walls by lazy {
        DbHelper.table("dbtable.tears_walls").sortedBy {
            it.column("dbcol.tears_walls:order", IntCodec)
        }.map { row ->
            TearsWall(
                row.column("dbcol.tears_walls:coords", CoordGridCodec),
                row.column("dbcol.tears_walls:stand", CoordGridCodec),
                row.column("dbcol.tears_walls:angle", IntCodec),
                RSCM.getReverseMapping(RSCMType.LOC, row.column("dbcol.tears_walls:blue", LocIdCodec)),
                RSCM.getReverseMapping(RSCMType.LOC, row.column("dbcol.tears_walls:green", LocIdCodec)),
                RSCM.getReverseMapping(RSCMType.LOC, row.column("dbcol.tears_walls:dry", LocIdCodec)),
            )
        }.also { require(it.size == 9 && it.distinctBy(TearsWall::coords).size == 9) }
    }
    val skills by lazy {
        DbHelper.table("dbtable.tears_skills").sortedBy {
            it.column("dbcol.tears_skills:order", IntCodec)
        }.map { row ->
            TearsSkill(
                RSCM.getReverseMapping(RSCMType.STAT, row.column("dbcol.tears_skills:stat", StatIdCodec)),
                row.column("dbcol.tears_skills:quest", StringCodec),
                row.column("dbcol.tears_skills:house", IntCodec) != 0,
            )
        }.also { require(it.size == 24 && it.distinctBy(TearsSkill::stat).size == 24) }
    }
}

internal data class TearsRules(
    val streamTicks: Int,
    val weeklyDays: Int,
    val xpRequirement: Int,
    val optionalTicks: Int,
    val entry: CoordGrid,
    val returnCoord: CoordGrid,
    val bounds: List<Int>,
    val approachTicks: Int,
    val entryRoute: List<CoordGrid>,
    val tailPauseTicks: Int,
    val crossingTimeoutTicks: Int,
    val arrivalPauseTicks: Int,
) {
    fun contains(coords: CoordGrid) = coords.level == bounds[4] &&
        coords.x in bounds[0]..bounds[2] && coords.z in bounds[1]..bounds[3]
}

internal data class TearsWall(
    val coords: CoordGrid,
    val stand: CoordGrid,
    val angle: Int,
    val blue: String,
    val green: String,
    val dry: String,
)

internal data class TearsSkill(val stat: String, val quest: String, val needsHouse: Boolean)
