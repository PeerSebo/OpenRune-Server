package org.rsmod.content.minigames.gnomeball

import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.NpcTypeCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.map.CoordGrid

internal object GnomeballData {
    private val row by lazy { DbHelper.table("dbtable.gnomeball_rules").single() }
    private val bounds by lazy { ints("bounds").also { require(it.size == 5) } }
    private val timings by lazy { ints("timings") }
    private val ranges by lazy { ints("ranges") }
    private val shooting by lazy { ints("shooting") }
    private val tackling by lazy { ints("tackling") }
    val projectile by lazy { ints("projectile") }
    val goalXp by lazy { ints("goal_xp").map { it / 10.0 } }
    val winXp get() = row.column("dbcol.gnomeball_rules:win_xp", IntCodec) / 10.0
    val gate by lazy { row.column("dbcol.gnomeball_rules:gate", CoordGridCodec) }
    val goal by lazy { row.column("dbcol.gnomeball_rules:goal", CoordGridCodec) }
    val shotRange get() = ranges[0]
    val passRange get() = ranges[1]
    val pursuitRange get() = ranges[2]
    val actionTicks get() = timings[0]
    val wingerTicks get() = timings[1]
    val tackleTicks get() = timings[2]
    val recoveryTicks get() = timings[3]
    val stolenTicks get() = timings[4]
    val tackleMinDamage get() = tackling[6]
    val tackleMaxDamage get() = tackling[7]
    val players by lazy {
        DbHelper.table("dbtable.gnomeball_players").map {
            GnomeballPlayer(it.column("dbcol.gnomeball_players:base", NpcTypeCodec),
                it.column("dbcol.gnomeball_players:ball", NpcTypeCodec),
                it.column("dbcol.gnomeball_players:floor", NpcTypeCodec),
                it.column("dbcol.gnomeball_players:winger", IntCodec) != 0)
        }.also { require(it.size == 4 && it.distinctBy { player -> player.base.id }.size == 4) }
    }

    fun contains(coords: CoordGrid) = coords.level == bounds[4] &&
        coords.x in bounds[0]..bounds[2] && coords.z in bounds[1]..bounds[3]
    fun shotChance(level: Int, distance: Int): Int =
        (shooting[0] + level * shooting[1] / 99 - distance * shooting[2])
            .coerceIn(shooting[3], shooting[4])
    fun tackleChance(level: Int) = (tackling[0] + level / tackling[1]).coerceAtMost(tackling[2])
    fun stealChance(level: Int) = (tackling[3] - level / tackling[4]).coerceAtLeast(tackling[5])
    fun throwEnd(distance: Int) = projectile[0] + projectile[1] * distance
    private fun ints(name: String) = row.multiColumn("dbcol.gnomeball_rules:$name", IntCodec)
}

internal data class GnomeballPlayer(
    val base: dev.openrune.types.NpcServerType,
    val ball: dev.openrune.types.NpcServerType,
    val floor: dev.openrune.types.NpcServerType,
    val winger: Boolean,
)
