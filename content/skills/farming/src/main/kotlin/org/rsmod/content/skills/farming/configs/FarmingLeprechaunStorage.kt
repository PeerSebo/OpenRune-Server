package org.rsmod.content.skills.farming.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.bits

// Native interfaces 125/126 read varbit-backed counts directly; stock is not a shop inventory.
data class LeprechaunCountSlot(
    val component: String,
    val item: String,
    val fields: List<String>,
    val publishedCap: Int,
) {
    private val widths: List<Int> by lazy {
        fields.map { field ->
            val varbit =
                ServerCacheManager.getVarbit(field.asRSCM(RSCMType.VARBIT))
                    ?: error("Missing varbit: $field")
            varbit.bits.last - varbit.bits.first + 1
        }
    }

    val rawFieldCapacity: Int by lazy { (1 shl widths.sum()) - 1 }

    val capacity: Int by lazy { minOf(rawFieldCapacity, publishedCap) }

    val fieldWidths: List<Int>
        get() = widths
}

object FarmingLeprechaunSlots {
    const val GARDENING_TROWEL: String = FarmingParams.GARDENING_TROWEL

    val COUNT_SLOTS: List<LeprechaunCountSlot>
        get() = FarmingServiceData.countSlots

    val RAKE: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("rake")

    val DIBBER: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("dibber")

    val SPADE: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("spade")

    val SECATEURS: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("secateurs")

    val TROWEL: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("trowel")

    val BUCKET: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("bucket")

    val COMPOST: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("compost")

    val SUPERCOMPOST: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("supercompost")

    val ULTRACOMPOST: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("ultracompost")

    val PLANT_CURE: LeprechaunCountSlot
        get() = FarmingServiceData.countSlotsByComponent.getValue("plantcure")

    // Magic secateurs share the regular secateurs component but use a separate ownership bit.
    const val MAGIC_SECATEURS_VARBIT: String = "varbit.farming_tools_fairysecateurs"

    const val WATERING_CAN_VARBIT: String = "varbit.farming_tools_wateringcan"

    // Clientscript 1063 encodes absent/empty/filled bottomless bucket states separately.
    const val BOTTOMLESS_BUCKET_TYPE_VARBIT: String = "varbit.farming_tools_bottomless_bucket_type"

    const val BOTTOMLESS_BUCKET_QUANTITY_VARBIT: String =
        "varbit.farming_tools_bottomless_bucket_quantity"

    const val BOTTOMLESS_BUCKET_EMPTY: String = "obj.bottomless_compost_bucket"
    const val BOTTOMLESS_BUCKET_FILLED: String = "obj.bottomless_compost_bucket_filled"

    const val SELECTED_QUANTITY_VARBIT: String = "varbit.farming_tools_selectedquantity"
}
