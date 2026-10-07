package org.rsmod.content.skills.farming.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column

internal data class FarmingSaplingDef(
    val id: String,
    val seed: String,
    val levelReq: Int,
    val potSeed: String,
    val potSeedWatered: String,
    val sapling: String,
)

internal object FarmingSaplingData {
    val all: List<FarmingSaplingDef> by lazy {
        DbHelper.table("dbtable.farming_saplings").map { row ->
            fun obj(name: String) = RSCM.getReverseMapping(
                RSCMType.OBJ, row.column("dbcol.farming_saplings:$name", ObjIdCodec),
            )
            FarmingSaplingDef(
                row.column("dbcol.farming_saplings:key", StringCodec),
                obj("seed"),
                row.column("dbcol.farming_saplings:level", IntCodec),
                obj("seedling"),
                obj("watered"),
                obj("sapling"),
            )
        }.also { recipes ->
            require(recipes.isNotEmpty()) { "Farming saplings are missing; rebuild the cache." }
            require(recipes.distinctBy { it.id }.size == recipes.size)
            require(recipes.distinctBy { it.seed }.size == recipes.size)
            require(recipes.all { it.levelReq in 1..99 })
        }
    }
}
