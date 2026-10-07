package org.rsmod.content.skills.farming.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.ComponentIdCodec
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbException
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.NpcIdCodec
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.VarbitIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.api.farming.FarmingBinFill
import org.rsmod.api.farming.FarmingBinProduct
import org.rsmod.api.farming.FarmingCompost
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPatchRegistry
import org.rsmod.api.farming.FarmingPatchType
import org.rsmod.content.skills.farming.FarmingPatchGroups

internal object FarmingServiceData {
    val countSlots: List<LeprechaunCountSlot> by lazy {
        ordered("farming_leprechaun_slots").map { row ->
            val component = symbol(RSCMType.COMPONENT, row.column("dbcol.farming_leprechaun_slots:component", ComponentIdCodec))
            require(component.startsWith("component.farming_tools:"))
            LeprechaunCountSlot(
                component.substringAfter(':'),
                symbol(RSCMType.OBJ, row.column("dbcol.farming_leprechaun_slots:item", ObjIdCodec)),
                row.multiColumn("dbcol.farming_leprechaun_slots:fields", VarbitIdCodec)
                    .map { symbol(RSCMType.VARBIT, it) },
                row.column("dbcol.farming_leprechaun_slots:published_cap", IntCodec),
            )
        }.also(::validateCountSlots)
    }

    val countSlotsByComponent: Map<String, LeprechaunCountSlot> by lazy {
        countSlots.associateBy { it.component }
    }

    val companions: List<Companion> by lazy {
        ordered("farming_companion_rules").map { row ->
            Companion(
                row.column("dbcol.farming_companion_rules:crop", StringCodec),
                row.column("dbcol.farming_companion_rules:protects_all", BooleanCodec),
                try {
                    row.multiColumn("dbcol.farming_companion_rules:allotments", StringCodec).toSet()
                } catch (_: DbException.MissingColumn) {
                    emptySet()
                },
            )
        }.also { rows ->
            unique(rows.map { it.crop }, "companion crops")
            require(rows.count { it.protectsAll } == 1)
            for (row in rows) {
                require(FarmingCrops.byId(row.crop)?.type == FarmingPatchType.FLOWER)
                require(row.protectsAll == row.allotments.isEmpty())
                require(row.allotments.all { FarmingCrops.byId(it)?.type == FarmingPatchType.ALLOTMENT })
            }
        }
    }

    val farms: List<FarmingPatchGroups.Farm> by lazy {
        ordered("farming_patch_groups").map { row ->
            FarmingPatchGroups.Farm(
                row.column("dbcol.farming_patch_groups:flower", StringCodec),
                row.multiColumn("dbcol.farming_patch_groups:allotments", StringCodec),
            )
        }.also { rows ->
            val patches = FarmingPatchRegistry()
            unique(rows.flatMap { listOf(it.flower) + it.allotments }, "grouped patches")
            for (row in rows) {
                require(patches.byKey(FarmingPatchKey(row.flower))?.type == FarmingPatchType.FLOWER)
                require(row.allotments.size == 2)
                require(row.allotments.all { patches.byKey(FarmingPatchKey(it))?.type == FarmingPatchType.ALLOTMENT })
            }
        }
    }

    val gardeners: List<Gardener> by lazy {
        ordered("farming_gardeners").map { row ->
            Gardener(
                symbol(RSCMType.NPC, row.column("dbcol.farming_gardeners:npc", NpcIdCodec)),
                row.column("dbcol.farming_gardeners:choice", BooleanCodec),
                row.multiColumn("dbcol.farming_gardeners:patches", StringCodec),
            )
        }.also(::validateGardeners)
    }

    val notingRules: List<NotingRule> by lazy {
        ordered("farming_leprechaun_noting").map { row ->
            NotingRule(
                symbol(RSCMType.OBJ, row.column("dbcol.farming_leprechaun_noting:item", ObjIdCodec)),
                row.column("dbcol.farming_leprechaun_noting:allowed", BooleanCodec),
            )
        }.also { rows -> unique(rows.map { it.item }, "noting items") }
    }

    val compostIngredients: List<CompostIngredient> by lazy {
        ordered("farming_compost_ingredients").map { row ->
            CompostIngredient(
                symbol(RSCMType.OBJ, row.column("dbcol.farming_compost_ingredients:item", ObjIdCodec)),
                FarmingBinFill.valueOf(row.column("dbcol.farming_compost_ingredients:fill", StringCodec)),
                row.column("dbcol.farming_compost_ingredients:before_crops", BooleanCodec),
            )
        }.also { rows -> unique(rows.map { it.item }, "compost ingredients") }
    }

    val compostClassification: Map<Int, Pair<FarmingBinFill, String>> by lazy {
        buildMap {
            fun add(ingredient: CompostIngredient) {
                put(ingredient.item.asRSCM(RSCMType.OBJ), ingredient.fill to ingredient.item)
            }
            val initial = compostIngredients.filter { it.beforeCrops }
            initial.forEach(::add)
            val tomatoes = initial.filter { it.fill == FarmingBinFill.TOMATOES }.map { it.item }.toSet()
            for (crop in FarmingCrops.ALL) {
                val produce = crop.produce ?: continue
                if (produce in tomatoes) continue
                val fill = if (crop.levelReq >= FarmingParams.SUPERCOMPOST_LEVEL_THRESHOLD) {
                    FarmingBinFill.SUPERCOMPOSTABLE
                } else {
                    FarmingBinFill.COMPOSTABLE
                }
                put(produce.asRSCM(RSCMType.OBJ), fill to produce)
            }
            compostIngredients.filterNot { it.beforeCrops }.forEach(::add)
        }
    }

    val doses: List<ConsumableDose> by lazy {
        ordered("farming_consumable_doses").map { row ->
            ConsumableDose(
                row.column("dbcol.farming_consumable_doses:family", StringCodec),
                row.column("dbcol.farming_consumable_doses:dose", IntCodec),
                symbol(RSCMType.OBJ, row.column("dbcol.farming_consumable_doses:item", ObjIdCodec)),
            )
        }.also(::validateDoses)
    }

    private val dosesByFamily: Map<String, List<String>> by lazy {
        doses.groupBy { it.family }.mapValues { (_, entries) -> entries.map { it.item } }
    }

    val compostProducts: Map<FarmingBinProduct, CompostProduct> by lazy {
        ordered("farming_compost_products").map { row ->
            CompostProduct(
                FarmingBinProduct.valueOf(row.column("dbcol.farming_compost_products:product", StringCodec)),
                symbol(RSCMType.OBJ, row.column("dbcol.farming_compost_products:item", ObjIdCodec)),
                row.column("dbcol.farming_compost_products:bin_xp_tenths", IntCodec),
                try {
                    row.column("dbcol.farming_compost_products:application_xp_tenths", IntCodec)
                } catch (_: DbException.MissingColumn) {
                    null
                },
            )
        }.also { rows ->
            require(rows.map { it.product } == FarmingBinProduct.entries)
            unique(rows.map { it.item }, "compost products")
            for (row in rows) {
                require(row.binXpTenths > 0)
                require((row.applicationXpTenths == null) == (row.product == FarmingBinProduct.ROTTEN_TOMATOES))
                require(row.applicationXpTenths == null || row.applicationXpTenths > 0)
            }
        }.associateBy { it.product }
    }

    val compostByObj: Map<Int, Pair<FarmingCompost, String>> by lazy {
        compostProducts.values.filter { it.applicationXp != null }.associate {
            it.item.asRSCM(RSCMType.OBJ) to (FarmingCompost.valueOf(it.product.name) to it.item)
        }
    }

    fun compostXp(tier: FarmingCompost): Double =
        if (tier == FarmingCompost.NONE) 0.0
        else checkNotNull(compostProducts.getValue(FarmingBinProduct.valueOf(tier.name)).applicationXp)

    fun consumableDoses(family: String): List<String> = dosesByFamily.getValue(family)

    fun validate() {
        countSlots
        companions
        farms
        gardeners
        notingRules
        compostIngredients
        doses
        compostProducts
    }

    fun validateCountSlots(rows: List<LeprechaunCountSlot>) {
        unique(rows.map { it.component }, "storage components")
        unique(rows.map { it.item }, "storage items")
        unique(rows.flatMap { it.fields }, "storage count fields")
        for (row in rows) {
            require(row.publishedCap > 0 && row.fields.isNotEmpty())
            require(row.fieldWidths.all { it in 1..30 } && row.fieldWidths.sum() in 1..30)
            RSCM.requireRSCM(RSCMType.COMPONENT, "component.farming_tools_side:${row.component}")
        }
    }

    fun validateGardeners(rows: List<Gardener>) {
        val patches = FarmingPatchRegistry()
        unique(rows.map { it.npc }, "gardener NPCs")
        for (row in rows) {
            require(row.patches.size in 1..2 && (!row.choice || row.patches.size == 2))
            unique(row.patches, "gardener patches")
            require(row.patches.all { patches.byKey(FarmingPatchKey(it)) != null })
        }
    }

    fun validateDoses(rows: List<ConsumableDose>) {
        require(rows.map { it.family }.toSet() == setOf("watering_can", "supercompost_potion"))
        unique(rows.map { it.item }, "consumable dose items")
        for ((family, entries) in rows.groupBy { it.family }) {
            val maximum = if (family == "watering_can") FarmingParams.WATERING_CAN_DOSES else 4
            require(entries.map { it.dose } == (0..maximum).toList())
        }
    }

    private fun ordered(table: String): List<DbHelper> {
        val rows = DbHelper.table("dbtable.$table").sortedBy { it.column("dbcol.$table:order", IntCodec) }
        require(rows.isNotEmpty()) { "Farming service table $table is missing; rebuild the server cache." }
        require(rows.map { it.column("dbcol.$table:order", IntCodec) } == rows.indices.toList()) {
            "Farming service table $table needs unique, contiguous ordering."
        }
        return rows
    }

    private fun unique(values: List<String>, description: String) {
        require(values.size == values.distinct().size && values.all { it.isNotEmpty() }) {
            "Farming $description must be unique and nonempty."
        }
    }

    private fun symbol(type: RSCMType, id: Int): String = RSCM.getReverseMapping(type, id).also {
        when (type) {
            RSCMType.OBJ -> require(ServerCacheManager.getItem(id) != null)
            RSCMType.NPC -> require(ServerCacheManager.getNpc(id) != null)
            RSCMType.VARBIT -> require(ServerCacheManager.getVarbit(id) != null)
            else -> Unit
        }
    }

    data class Companion(val crop: String, val protectsAll: Boolean, val allotments: Set<String>)
    data class Gardener(val npc: String, val choice: Boolean, val patches: List<String>)
    data class NotingRule(val item: String, val allowed: Boolean)
    data class CompostIngredient(val item: String, val fill: FarmingBinFill, val beforeCrops: Boolean)
    data class ConsumableDose(val family: String, val dose: Int, val item: String)
    data class CompostProduct(
        val product: FarmingBinProduct,
        val item: String,
        val binXpTenths: Int,
        val applicationXpTenths: Int?,
    ) {
        val binXp: Double get() = binXpTenths / 10.0
        val applicationXp: Double? get() = applicationXpTenths?.div(10.0)
    }
}
