package org.rsmod.api.farming

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec

internal object FarmingWorldData {
    val patchTypes: Map<String, PatchType> by lazy {
        DbHelper.table("dbtable.farming_patch_types").map { row ->
            PatchType(
                row.required("farming_patch_types:order", IntCodec),
                row.required("farming_patch_types:key", StringCodec),
                row.required("farming_patch_types:seed_family", IntCodec),
                row.required("farming_patch_types:waterable", BooleanCodec),
                row.required("farming_patch_types:cure", StringCodec),
                row.required("farming_patch_types:check_health", BooleanCodec),
                row.required("farming_patch_types:bears_produce", BooleanCodec),
                RSCM.getReverseMapping(RSCMType.LOC, row.required("farming_patch_types:empty", LocIdCodec)),
                row.required("farming_patch_types:weed_cycle_minutes", IntCodec),
            )
        }.sortedBy { it.order }.also(::validatePatchTypes).associateBy { it.key }
    }

    val binTypes: Map<String, BinType> by lazy {
        DbHelper.table("dbtable.farming_bin_types").map { row ->
            BinType(
                row.required("farming_bin_types:order", IntCodec),
                row.required("farming_bin_types:key", StringCodec),
                row.required("farming_bin_types:loc_prefix", StringCodec),
                row.required("farming_bin_types:capacity", IntCodec),
            )
        }.sortedBy { it.order }.also(::validateBinTypes).associateBy { it.key }
    }

    val compostTypes: Map<String, CompostType> by lazy {
        DbHelper.table("dbtable.farming_compost_types").map { row ->
            CompostType(
                row.required("farming_compost_types:order", IntCodec),
                row.required("farming_compost_types:key", StringCodec),
                row.required("farming_compost_types:extra_lives", IntCodec),
                row.required("farming_compost_types:disease_divisor", IntCodec),
            )
        }.sortedBy { it.order }.also(::validateCompostTypes).associateBy { it.key }
    }

    val patches: List<Location> by lazy {
        readLocations("farming_patches").also { validateLocations(it, patchTypes.keys) }
    }

    val bins: List<Location> by lazy {
        readLocations("farming_bins").also { validateLocations(it, binTypes.keys) }
    }

    fun validate() {
        require(patchTypes.isNotEmpty() && binTypes.isNotEmpty() && compostTypes.isNotEmpty())
        require(patches.isNotEmpty() && bins.isNotEmpty())
    }

    fun validatePatchTypes(rows: List<PatchType>) {
        require(rows.map { it.order } == rows.indices.toList()) { "Farming patch family order is invalid." }
        require(rows.map { it.key } == FarmingPatchType.entries.map { it.name }) {
            "Farming patch families must preserve enum identities; rebuild the server cache."
        }
        require(rows.map { it.seedFamily }.distinct().size == rows.size && rows.all { it.seedFamily > 0 })
        require(rows.all { it.cure in FarmingCureMethod.entries.map(FarmingCureMethod::name) })
        require(rows.all { it.emptyLocName.startsWith("loc.") && it.weedCycleMinutes > 0 })
    }

    fun validateBinTypes(rows: List<BinType>) {
        require(rows.map { it.order } == rows.indices.toList()) { "Farming bin family order is invalid." }
        require(rows.map { it.key } == FarmingBinType.entries.map { it.name }) {
            "Farming bin families must preserve enum identities; rebuild the server cache."
        }
        require(rows.all { it.locPrefix.startsWith("loc.") && it.locPrefix.endsWith('_') && it.capacity > 0 })
        require(rows.map { it.locPrefix }.distinct().size == rows.size)
    }

    fun validateCompostTypes(rows: List<CompostType>) {
        require(rows.map { it.order } == rows.indices.toList()) { "Farming compost order is invalid." }
        require(rows.map { it.key } == FarmingCompost.entries.map { it.name }) {
            "Farming compost definitions must preserve saved enum names; rebuild the server cache."
        }
        require(rows.all { it.extraLives >= 0 && it.diseaseDivisor > 0 })
    }

    fun validateLocations(
        rows: List<Location>,
        families: Set<String>,
        resolve: (String) -> Int? = FarmingTransformTable::locIdOrNull,
    ) {
        require(rows.isNotEmpty()) { "Farming world locations are missing; rebuild the server cache." }
        require(rows.map { it.order } == rows.indices.toList()) { "Farming location order is invalid." }
        require(rows.map { it.locName }.distinct().size == rows.size && rows.map { it.locId }.distinct().size == rows.size) {
            "Farming world locations must have distinct saved keys and base locs."
        }
        for (row in rows) {
            require(row.family in families) { "Farming location ${row.locName} has unknown family ${row.family}." }
            require(row.locName.startsWith("loc.") && row.locName.length > 4 && row.locId >= 0)
            require(resolve(row.locName) == row.locId) {
                "Farming location ${row.locName} does not match its typed loc reference."
            }
        }
    }

    private fun readLocations(table: String): List<Location> =
        DbHelper.table("dbtable.$table").map { row ->
            Location(
                row.required("$table:order", IntCodec),
                row.required("$table:loc_name", StringCodec),
                row.required("$table:loc", LocIdCodec),
                row.required("$table:family", StringCodec),
            )
        }.sortedBy { it.order }

    private fun <T, R> DbHelper.required(name: String, codec: DbColumnCodec<T, R>): R {
        val column = getColumn("dbcol.$name")
        require(column.size == 1) { "Farming row $id column $name must have one value." }
        return column.decodeAt(0, codec)
    }

    data class PatchType(
        val order: Int,
        val key: String,
        val seedFamily: Int,
        val waterable: Boolean,
        val cure: String,
        val checkHealth: Boolean,
        val bearsProduce: Boolean,
        val emptyLocName: String,
        val weedCycleMinutes: Int,
    )

    data class BinType(val order: Int, val key: String, val locPrefix: String, val capacity: Int)

    data class CompostType(val order: Int, val key: String, val extraLives: Int, val diseaseDivisor: Int)

    data class Location(val order: Int, val locName: String, val locId: Int, val family: String)
}
