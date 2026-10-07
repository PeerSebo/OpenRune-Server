package org.rsmod.content.skills.farming.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.DbRowIdCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbException
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import org.rsmod.api.farming.FarmingAnchor
import org.rsmod.api.farming.FarmingConstants
import org.rsmod.api.farming.FarmingCropVisuals
import org.rsmod.api.farming.FarmingPatchType

internal object FarmingCropData {
    val crops: List<FarmingCropDef> by lazy {
        read(DbHelper.table("dbtable.farming_crops"), DbHelper.table("dbtable.farming_crop_visuals"))
    }

    fun read(cropRows: List<DbHelper>, visualRows: List<DbHelper>): List<FarmingCropDef> {
        require(cropRows.isNotEmpty()) { "Farming crops are missing; rebuild the server cache." }
        val ordered = cropRows.sortedBy { it.int("order") }
        require(ordered.map { it.int("order") } == ordered.indices.toList()) {
            "Farming crop order must be unique and contiguous."
        }
        val visuals = readVisuals(visualRows, ordered.map { it.id }.toSet())
        return ordered.map { readCrop(it, visuals.getValue(it.id)) }.also(::validate)
    }

    private fun readCrop(row: DbHelper, visuals: FarmingCropVisuals): FarmingCropDef {
        val paymentItem = row.objOptional("protection_item")
        val paymentCount = row.optional("protection_count", IntCodec)
        require((paymentItem == null) == (paymentCount == null)) {
            "Farming protection needs both an item and a count: ${row.id}."
        }
        val saveLow = row.optional("save_low", IntCodec)
        val saveHigh = row.optional("save_high", IntCodec)
        require((saveLow == null) == (saveHigh == null)) {
            "Farming harvest-save chance needs both endpoints: ${row.id}."
        }
        return FarmingCropDef(
            id = row.column("dbcol.farming_crops:key", StringCodec),
            type = FarmingPatchType.valueOf(row.column("dbcol.farming_crops:patch_type", StringCodec)),
            seed = RSCM.getReverseMapping(RSCMType.OBJ, row.column("dbcol.farming_crops:seed", ObjIdCodec)),
            produce = row.objOptional("produce"),
            levelReq = row.int("level"),
            deferredPlantXp = row.int("plant_xp_tenths") / 10.0,
            harvestXp = row.int("harvest_xp_tenths") / 10.0,
            checkHealthXp = row.int("check_xp_tenths") / 10.0,
            growthCycles = row.int("growth_cycles"),
            cycleMinutes = row.int("cycle_minutes"),
            seedsPerPatch = row.int("seeds"),
            visuals = visuals,
            protection = paymentItem?.let { ProtectionPayment(it, checkNotNull(paymentCount)) },
            sapling = row.objOptional("sapling"),
            baseLives = row.int("base_lives"),
            produceCapacity = row.int("produce_capacity"),
            produceRegrowMinutes = row.int("regrow_minutes"),
            diseaseChance = row.int("disease_chance"),
            chanceToSave = saveLow?.let { ChanceToSave(it, checkNotNull(saveHigh)) },
            potSeed = row.objOptional("pot_seed"),
            potSeedWatered = row.objOptional("pot_seed_watered"),
            placedNotGrown = row.column("dbcol.farming_crops:placed", BooleanCodec),
            waterable = row.optional("waterable", BooleanCodec),
            hesporiSeedDenominator = row.optional("hespori_denominator", IntCodec),
        )
    }

    private fun readVisuals(rows: List<DbHelper>, cropIds: Set<Int>): Map<Int, FarmingCropVisuals> {
        val bands = rows.map { row ->
            val crop = row.column("dbcol.farming_crop_visuals:crop", DbRowIdCodec)
            val band = row.column("dbcol.farming_crop_visuals:band", StringCodec)
            require(crop in cropIds) { "Farming visual references an unknown crop row: $crop." }
            require(band in BANDS) { "Unknown Farming visual band '$band'." }
            val loc = RSCM.getReverseMapping(RSCMType.LOC, row.column("dbcol.farming_crop_visuals:anchor", LocIdCodec))
            val stage = row.column("dbcol.farming_crop_visuals:first_stage", IntCodec)
            val stagesColumn = row.existingColumn("dbcol.farming_crop_visuals:stages")
            val stages = stagesColumn?.let { column ->
                require(column.size > 0) { "An explicit Farming stage list cannot be empty." }
                (0 until column.size).map { RSCM.getReverseMapping(RSCMType.LOC, column.decodeAt(it, LocIdCodec)) }
            }.orEmpty()
            require(stages.isEmpty() || stages.first() == loc) {
                "The first explicit Farming stage must match its anchor: $crop/$band."
            }
            Triple(crop, band, FarmingAnchor(loc, stage, stages))
        }
        require(bands.distinctBy { it.first to it.second }.size == bands.size) {
            "A Farming crop cannot have duplicate visual bands."
        }
        val grouped = bands.groupBy { it.first }
        require(grouped.keys == cropIds) { "Every Farming crop must have visuals." }
        return grouped.mapValues { (crop, rows) ->
            val byBand = rows.associate { it.second to it.third }
            FarmingCropVisuals(
                growing = requireNotNull(byBand["growing"]) { "Farming crop $crop needs a growing anchor." },
                watered = byBand["watered"],
                diseased = byBand["diseased"],
                dead = byBand["dead"],
                unchecked = byBand["unchecked"],
                grownChecked = byBand["grown_checked"],
                stump = byBand["stump"],
            )
        }
    }

    fun validate(crops: List<FarmingCropDef>) {
        require(crops.isNotEmpty()) { "Farming crops are missing; rebuild the server cache." }
        require(crops.all { it.id.isNotBlank() } && crops.distinctBy { it.id }.size == crops.size) {
            "Farming crop save identities must be unique and nonempty."
        }
        require(crops.distinctBy { it.plantedObj }.size == crops.size) { "Farming planted items must be unique." }
        for (crop in crops) {
            require(crop.levelReq in 1..99 && crop.seedsPerPatch > 0) { "Invalid Farming planting requirement: ${crop.id}." }
            require(listOf(crop.deferredPlantXp, crop.harvestXp, crop.checkHealthXp).all { it.isFinite() && it >= 0 }) {
                "Farming XP must be finite and nonnegative: ${crop.id}."
            }
            require(crop.growthCycles >= 0 && crop.cycleMinutes > 0) { "Invalid Farming growth interval: ${crop.id}." }
            require(crop.baseLives >= 0 && crop.produceCapacity >= 0 && crop.produceRegrowMinutes >= 0)
            require(crop.produceCapacity == 0 || crop.produceRegrowMinutes > 0)
            require(crop.diseaseChance in 0..FarmingConstants.DISEASE_DENOMINATOR)
            crop.protection?.let { require(it.count > 0 && it.obj.startsWith("obj.")) }
            crop.chanceToSave?.let { require(it.low in 0..255 && it.high in 0..255) }
            crop.hesporiSeedDenominator?.let { require(it > 0) }
            val potChain = listOf(crop.sapling, crop.potSeed, crop.potSeedWatered)
            require(potChain.all { it == null } || potChain.all { it != null }) {
                "Farming saplings require both seedling states: ${crop.id}."
            }
        }
    }

    private fun DbHelper.int(name: String): Int = column("dbcol.farming_crops:$name", IntCodec)

    private fun DbHelper.objOptional(name: String): String? =
        optional(name, ObjIdCodec)?.let { RSCM.getReverseMapping(RSCMType.OBJ, it) }

    private fun <T, R> DbHelper.optional(name: String, codec: DbColumnCodec<T, R>): R? =
        existingColumn("dbcol.farming_crops:$name")?.decodeAt(0, codec)

    private fun DbHelper.existingColumn(name: String): DbHelper.Column? = try {
        getColumn(name)
    } catch (_: DbException.MissingColumn) {
        null
    }

    private val BANDS = setOf("growing", "watered", "diseased", "dead", "unchecked", "grown_checked", "stump")
}
