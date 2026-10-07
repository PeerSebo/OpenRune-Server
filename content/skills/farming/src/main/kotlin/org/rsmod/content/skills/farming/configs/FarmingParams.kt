package org.rsmod.content.skills.farming.configs

import org.rsmod.api.farming.FarmingBinProduct
import org.rsmod.api.farming.FarmingCompost

object FarmingParams {
    const val FARMING_STAT: String = "stat.farming"

    const val PATCH_CATEGORY: String = "category.farming_patch"

    const val QUEUE_RAKE: String = "queue.farming_rake"
    const val QUEUE_PLANT: String = "queue.farming_plant"
    const val QUEUE_HARVEST: String = "queue.farming_harvest"
    const val QUEUE_WATER: String = "queue.farming_water"
    const val QUEUE_CURE: String = "queue.farming_cure"
    const val QUEUE_CLEAR: String = "queue.farming_clear"
    const val QUEUE_COMPOST: String = "queue.farming_compost"
    const val QUEUE_CHECK_HEALTH: String = "queue.farming_check_health"

    const val QUEUE_CHOP: String = "queue.farming_chop"

    const val QUEUE_SEEDLING_GROW: String = "queue.farming_seedling_grow"

    const val QUEUE_FILL_POT: String = "queue.farming_fill_pot"

    const val QUEUE_WATER_SEEDLING: String = "queue.farming_water_seedling"

    const val ACTION_CADENCE_TICKS: Int = 3

    const val RAKE: String = "obj.rake"
    const val SEED_DIBBER: String = "obj.dibber"
    const val SPADE: String = "obj.spade"
    const val SECATEURS: String = "obj.secateurs"

    const val MAGIC_SECATEURS: String = "obj.fairy_enchanted_secateurs"
    const val GARDENING_TROWEL: String = "obj.gardening_trowel"
    const val PLANT_CURE: String = "obj.plant_cure"

    const val EMPTY_VIAL: String = "obj.vial_empty"

    val WATERING_CANS: List<String>
        get() = FarmingServiceData.consumableDoses("watering_can")

    const val WATERING_CAN_DOSES: Int = 8

    const val BUCKET_COMPOST: String = "obj.bucket_compost"
    const val BUCKET_SUPERCOMPOST: String = "obj.bucket_supercompost"
    const val BUCKET_ULTRACOMPOST: String = "obj.bucket_ultracompost"
    const val BUCKET_EMPTY: String = "obj.bucket_empty"

    val XP_COMPOST: Double
        get() = FarmingServiceData.compostXp(FarmingCompost.COMPOST)
    val XP_SUPERCOMPOST: Double
        get() = FarmingServiceData.compostXp(FarmingCompost.SUPERCOMPOST)
    val XP_ULTRACOMPOST: Double
        get() = FarmingServiceData.compostXp(FarmingCompost.ULTRACOMPOST)

    const val SUPERCOMPOST_LEVEL_THRESHOLD: Int = 47

    const val VOLCANIC_ASH: String = "obj.fossil_volcanic_ash"

    const val ASH_PER_BIN: Int = 25

    const val ASH_PER_BIG_BIN: Int = 50

    val SUPERCOMPOST_POTION_DOSES: List<String>
        get() = FarmingServiceData.consumableDoses("supercompost_potion")

    // Sequence 7699 has no baked audio and loops; reset it after the action.
    const val SEQ_ULTRACOMPOST_MAKE: String = "seq.ultracompost_make"

    val XP_BIN_COMPOST: Double
        get() = FarmingServiceData.compostProducts.getValue(FarmingBinProduct.COMPOST).binXp
    val XP_BIN_SUPERCOMPOST: Double
        get() = FarmingServiceData.compostProducts.getValue(FarmingBinProduct.SUPERCOMPOST).binXp
    val XP_BIN_ULTRACOMPOST: Double
        get() = FarmingServiceData.compostProducts.getValue(FarmingBinProduct.ULTRACOMPOST).binXp

    val XP_BIN_ROTTEN_TOMATO: Double
        get() = FarmingServiceData.compostProducts.getValue(FarmingBinProduct.ROTTEN_TOMATOES).binXp

    const val QUEUE_BIN_TAKE: String = "queue.farming_bin_take"

    const val BIN_TAKE_CADENCE_TICKS: Int = 4

    const val WEEDS: String = "obj.weeds"

    const val XP_PER_WEED: Double = 4.0

    const val SECATEURS_SUCCESS_OUT_OF_128: Int = 96

    const val PLANT_CURE_SUCCESS_OUT_OF_128: Int = 128

    // TODO: implement the gardener's paid stump-removal option.
    const val STUMP_REMOVAL_COST: Int = 200

    const val SEQ_RAKING: String = "seq.farming_raking"

    const val SEQ_SEED_DIBBING: String = "seq.farming_seed_dibbing"

    const val SEQ_WATERING: String = "seq.farming_watering"

    const val SEQ_PLANT_CURE: String = "seq.farming_plant_cure"

    const val SEQ_PICKING: String = "seq.picking_low"

    const val SEQ_TROWEL_DIGGING: String = "seq.farming_trowel_digging"

    const val SEQ_SPADE_DIG: String = "seq.human_dig"

    const val SEQ_COMPOST_POUR: String = "seq.farming_pour_water"

    const val SYNTH_RAKING: String = "synth.FARMING_RAKING"
    const val SYNTH_DIBBING: String = "synth.FARMING_DIBBING"
    const val SYNTH_WATERING: String = "synth.FARMING_WATERING"
    const val SYNTH_PLANT_CURE: String = "synth.FARMING_PLANTCURE"
    const val SYNTH_PICK: String = "synth.FARMING_PICK"
    const val SYNTH_COMPOST: String = "synth.FARMING_COMPOST"
    const val SYNTH_PRUNE: String = "synth.FARMING_PRUNE"
    const val SYNTH_PLANT_SCARECROW: String = "synth.FARMING_PLANT_SCARECROW"

    const val SYNTH_FILL_PLANT_POT: String = "synth.FARMING_FILLPLANTPOT"

    const val SYNTH_DIG_SPADE: String = "synth.DIGSPADE"

    const val EMPTY_PLANT_POT: String = "obj.plantpot_empty"

    const val FILLED_PLANT_POT: String = "obj.plantpot_compost"

    val FILLED_PLANT_POTS: List<String> =
        listOf(FILLED_PLANT_POT, "obj.plantpot_supercompost")

    const val SEEDLING_TO_SAPLING_MINUTES: Int = 1

    const val VARP_SEEDLING_READY: String = "varp.farming_seedling_ready"

    const val SEEDLING_QUEUE_TICKS: Int = SEEDLING_TO_SAPLING_MINUTES * 100

    const val AXE_CONTENT: String = "content.woodcutting_axe"
}
