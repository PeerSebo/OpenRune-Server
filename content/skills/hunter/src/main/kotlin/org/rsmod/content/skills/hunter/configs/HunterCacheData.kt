package org.rsmod.content.skills.hunter.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.enum
import dev.openrune.types.dbcol.DbColumnCodec.EnumTypeIdCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.ItemServerTypeCodec
import dev.openrune.types.dbcol.DbColumnCodec.LocTypeCodec
import dev.openrune.types.dbcol.DbColumnCodec.NpcTypeCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.game.stat.PlayerStatMap

internal object HunterCacheData {
    val falconry: Map<Int, Falconry> by lazy {
        val rows = DbHelper.table("dbtable.hunter_falconry").map { row ->
            val level = row.column("dbcol.hunter_falconry:level", IntCodec)
            val chanceEnum = row.column("dbcol.hunter_falconry:chances", EnumTypeIdCodec)
            val chances = enum(chanceEnum, IntCodec, IntCodec).sortedBy { it.key }
            require(chances.isNotEmpty() && chances.map { it.key } == (level until level + chances.size).toList())
            require(chances.all { it.value in 0..256 })
            Falconry(
                code = row.column("dbcol.hunter_falconry:code", IntCodec),
                npc = symbol(RSCMType.NPC, row.column("dbcol.hunter_falconry:prey", NpcTypeCodec).id),
                falcon = symbol(RSCMType.NPC, row.column("dbcol.hunter_falconry:falcon", NpcTypeCodec).id),
                level = level,
                xp = row.column("dbcol.hunter_falconry:xp", IntCodec) / PlayerStatMap.XP_FINE_PRECISION.toDouble(),
                loot = row.multiColumn("dbcol.hunter_falconry:loot", ItemServerTypeCodec).map { symbol(RSCMType.OBJ, it.id) },
                chances = chances.map { it.value },
            )
        }
        require(rows.map { it.code }.toSet() == FalconryPrey.entries.map { it.code }.toSet())
        require(rows.distinctBy { it.code }.size == rows.size)
        rows.associateBy { it.code }
    }

    val birdhouses: Map<Int, Birdhouse> by lazy {
        val rows = DbHelper.table("dbtable.hunter_birdhouses").map { row ->
            Birdhouse(
                code = row.column("dbcol.hunter_birdhouses:code", IntCodec),
                house = symbol(RSCMType.OBJ, row.column("dbcol.hunter_birdhouses:house", ItemServerTypeCodec).id),
                built = symbol(RSCMType.LOC, row.column("dbcol.hunter_birdhouses:built", LocTypeCodec).id),
                seeded = symbol(RSCMType.LOC, row.column("dbcol.hunter_birdhouses:seeded", LocTypeCodec).id),
                finished = symbol(RSCMType.LOC, row.column("dbcol.hunter_birdhouses:finished", LocTypeCodec).id),
                level = row.column("dbcol.hunter_birdhouses:level", IntCodec),
                xp = row.column("dbcol.hunter_birdhouses:xp", IntCodec) / PlayerStatMap.XP_FINE_PRECISION.toDouble(),
                logs = symbol(RSCMType.OBJ, row.column("dbcol.hunter_birdhouses:logs", ItemServerTypeCodec).id),
                craftingLevel = row.column("dbcol.hunter_birdhouses:crafting_level", IntCodec),
                craftingXp = row.column("dbcol.hunter_birdhouses:crafting_xp", IntCodec) / PlayerStatMap.XP_FINE_PRECISION.toDouble(),
                builtState = row.column("dbcol.hunter_birdhouses:built_state", IntCodec),
            )
        }
        require(rows.map { it.code }.toSet() == BirdhouseTier.entries.map { it.code }.toSet())
        require(rows.distinctBy { it.code }.size == rows.size)
        require(rows.all { it.builtState > 0 && (it.builtState - 1) % BirdhouseTier.STATES_PER_TIER == 0 })
        require(rows.distinctBy { it.builtState }.size == rows.size)
        rows.associateBy { it.code }
    }

    private fun symbol(type: RSCMType, id: Int): String = RSCM.getReverseMapping(type, id)

    data class Falconry(
        val code: Int,
        val npc: String,
        val falcon: String,
        val level: Int,
        val xp: Double,
        val loot: List<String>,
        val chances: List<Int>,
    )

    data class Birdhouse(
        val code: Int,
        val house: String,
        val built: String,
        val seeded: String,
        val finished: String,
        val level: Int,
        val xp: Double,
        val logs: String,
        val craftingLevel: Int,
        val craftingXp: Double,
        val builtState: Int,
    )
}
