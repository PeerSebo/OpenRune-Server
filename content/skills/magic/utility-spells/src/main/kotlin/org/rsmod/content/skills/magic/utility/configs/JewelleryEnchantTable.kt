package org.rsmod.content.skills.magic.utility.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.SpotCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.SeqIdCodec
import dev.openrune.types.dbcol.SynthIdCodec
import dev.openrune.types.dbcol.column

internal enum class EnchantJewelleryShape {
    Ring,
    Necklace,
    Amulet,
    Bracelet,
}

internal data class EnchantJewelleryRow(
    val input: String,
    val product: String,
    val shape: EnchantJewelleryShape,
)

internal enum class EnchantTier {
    Lvl1,
    Lvl2,
    Lvl3,
    Lvl4,
    Lvl5,
    Lvl6,
    Lvl7;

    private val definition get() = JewelleryEnchantData.tiers.getValue(name)
    val spellObj get() = definition.spellObj
    val amuletSeq get() = definition.amuletSeq
    val amuletSpotanim get() = definition.amuletSpotanim
    val amuletSound get() = definition.amuletSound
    val ringSound get() = definition.ringSound
    val rows get() = definition.rows

    fun findRow(inputInternal: String): EnchantJewelleryRow? =
        rows.firstOrNull { it.input == inputInternal }
}

private object JewelleryEnchantData {
    val tiers: Map<String, TierDefinition> by lazy {
        val tiers = DbHelper.table("dbtable.jewellery_enchant_tiers")
            .sortedBy { it.column("dbcol.jewellery_enchant_tiers:tier_order", IntCodec) }
        require(tiers.map { it.column("dbcol.jewellery_enchant_tiers:tier_order", IntCodec) } == tiers.indices.toList())
        val names = tiers.map { it.column("dbcol.jewellery_enchant_tiers:tier", StringCodec) }
        require(names == EnchantTier.entries.map { it.name }) {
            "Jewellery enchant tiers are missing or out of order; rebuild the server cache."
        }
        val products = DbHelper.table("dbtable.jewellery_enchant_products")
            .groupBy { it.column("dbcol.jewellery_enchant_products:tier", StringCodec) }
        require(products.keys == names.toSet()) { "Jewellery enchant products have missing or unknown tiers." }
        tiers.associate { row ->
            val name = row.column("dbcol.jewellery_enchant_tiers:tier", StringCodec)
            val productRows = products.getValue(name)
                .sortedBy { it.column("dbcol.jewellery_enchant_products:product_order", IntCodec) }
            require(productRows.map { it.column("dbcol.jewellery_enchant_products:product_order", IntCodec) } == productRows.indices.toList())
            val rows = productRows.map { product ->
                EnchantJewelleryRow(
                    input = RSCM.getReverseMapping(RSCMType.OBJ, product.column("dbcol.jewellery_enchant_products:input", ObjIdCodec)),
                    product = RSCM.getReverseMapping(RSCMType.OBJ, product.column("dbcol.jewellery_enchant_products:product", ObjIdCodec)),
                    shape = EnchantJewelleryShape.valueOf(product.column("dbcol.jewellery_enchant_products:shape", StringCodec)),
                )
            }
            require(rows.distinctBy { it.input }.size == rows.size)
            name to TierDefinition(
                spellObj = RSCM.getReverseMapping(RSCMType.OBJ, row.column("dbcol.jewellery_enchant_tiers:spell", ObjIdCodec)),
                amuletSeq = RSCM.getReverseMapping(RSCMType.SEQ, row.column("dbcol.jewellery_enchant_tiers:amulet_seq", SeqIdCodec)),
                amuletSpotanim = RSCM.getReverseMapping(RSCMType.SPOTANIM, row.column("dbcol.jewellery_enchant_tiers:amulet_spotanim", SpotCodec).id),
                amuletSound = RSCM.getReverseMapping(RSCMType.SYNTH, row.column("dbcol.jewellery_enchant_tiers:amulet_sound", SynthIdCodec)),
                ringSound = RSCM.getReverseMapping(RSCMType.SYNTH, row.column("dbcol.jewellery_enchant_tiers:ring_sound", SynthIdCodec)),
                rows = rows,
            )
        }
    }

    data class TierDefinition(
        val spellObj: String,
        val amuletSeq: String,
        val amuletSpotanim: String,
        val amuletSound: String,
        val ringSound: String,
        val rows: List<EnchantJewelleryRow>,
    )
}
