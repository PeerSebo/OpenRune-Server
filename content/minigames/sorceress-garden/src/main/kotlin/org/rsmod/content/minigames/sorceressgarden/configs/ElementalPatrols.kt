package org.rsmod.content.minigames.sorceressgarden.configs

import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.NpcTypeCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import org.rsmod.map.CoordGrid

object ElementalPatrols {
    class Patrol(val season: GardenSeason, val loop: List<CoordGrid>)

    private val Directions: Map<String, Pair<Int, Int>> =
        mapOf(
            "N" to (0 to 1),
            "S" to (0 to -1),
            "E" to (1 to 0),
            "W" to (-1 to 0),
            "NE" to (1 to 1),
            "NW" to (-1 to 1),
            "SE" to (1 to -1),
            "SW" to (-1 to -1),
        )

    internal fun route(x: Int, z: Int, runs: String): List<CoordGrid> {
        val start = CoordGrid(x, z, level = 0)
        val loop = mutableListOf(start)
        for (token in runs.split(' ')) {
            val match =
                requireNotNull(Regex("([A-Z]+)(\\d+)").matchEntire(token)) {
                    "Malformed run token '$token' in patrol route at $start."
                }
            val (dir, count) = match.destructured
            val (dx, dz) =
                requireNotNull(Directions[dir]) { "Unknown direction '$dir' at $start." }
            repeat(count.toInt()) { loop += loop.last().translate(dx, dz) }
        }
        require(loop.removeLast() == start) {
            "Patrol runs '$runs' do not close back onto their spawn tile $start."
        }
        return loop
    }

    val all: Map<String, Patrol> by lazy {
        val rows = DbHelper.table("dbtable.garden_patrols").map(::read).sortedBy { it.order }
        require(rows.isNotEmpty() && rows.map { it.order } == rows.indices.toList()) {
            "Garden patrols require a unique contiguous order."
        }
        require(rows.distinctBy { it.npc }.size == rows.size) { "Duplicate Garden elemental patrol NPC." }
        rows.associate { it.npc to Patrol(it.season, route(it.start.x, it.start.z, it.runs)) }
    }

    internal fun read(row: DbHelper): Definition {
        val start = row.required("start", CoordGridCodec)
        require(start.level == 0)
        return Definition(
            row.required("order", IntCodec),
            row.required("npc", NpcTypeCodec).internalName,
            GardenSeason.valueOf(row.required("season", StringCodec)),
            start,
            row.required("runs", StringCodec),
        )
    }

    private fun <T, R> DbHelper.required(name: String, codec: DbColumnCodec<T, R>): R {
        val column = getColumn("dbcol.garden_patrols:$name")
        require(column.size == 1) { "Garden patrol row $id column $name must have one value." }
        return column.decodeAt(0, codec)
    }

    internal data class Definition(
        val order: Int,
        val npc: String,
        val season: GardenSeason,
        val start: CoordGrid,
        val runs: String,
    )
}
