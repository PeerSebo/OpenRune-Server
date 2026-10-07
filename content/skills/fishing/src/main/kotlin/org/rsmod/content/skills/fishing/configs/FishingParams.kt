package org.rsmod.content.skills.fishing.configs

object FishingParams {

    const val ROLL_TICKS = 5

    const val ANIM_TICKS = 5

    const val CUT_TICKS = 3

    const val CUT_QUEUE = "queue.fishing_barbarian_cut"

    const val OFFCUTS_CUT_QUEUE = "queue.fishing_offcuts_cut"

    const val MOVE_MIN_TICKS = 250
    const val MOVE_MAX_TICKS = 530

    const val ANGLER_MOVE_MIN_TICKS = 8
    const val ANGLER_MOVE_MAX_TICKS = 830

    const val INFERNAL_EEL_MOVE_MIN_TICKS = 100
    const val INFERNAL_EEL_MOVE_MAX_TICKS = 300

    const val MINNOW_MOVE_TICKS = 25

    const val SPOT_SMALLNET = "content.fishing_spot_smallnet"
    const val SPOT_ROD = "content.fishing_spot_rod"
    const val SPOT_CAGE = "content.fishing_spot_cage"
    const val SPOT_BIGNET = "content.fishing_spot_bignet"
    const val SPOT_BARBARIAN = "content.fishing_spot_barbarian"
    const val SPOT_MONKFISH = "content.fishing_spot_monkfish"
    const val SPOT_ANGLERFISH = "content.fishing_spot_anglerfish"

    const val SPOT_SLIMY_EEL = "content.fishing_spot_slimy_eel"

    const val SPOT_CAVEFISH = "content.fishing_spot_cavefish"
    const val SPOT_LAVA_EEL = "content.fishing_spot_lava_eel"
    const val SPOT_INFERNAL_EEL = "content.fishing_spot_infernal_eel"
    const val SPOT_DARK_CRAB = "content.fishing_spot_dark_crab"
    const val SPOT_KARAMBWANJI = "content.fishing_spot_karambwanji"
    const val SPOT_KARAMBWAN = "content.fishing_spot_karambwan"
    const val SPOT_MINNOW = "content.fishing_spot_minnow"

    // TODO: exempt static Isle of Souls and Prifddinas spawns within otherwise moving spot groups.
    val SPOT_GROUPS =
        listOf(
            SPOT_SMALLNET,
            SPOT_ROD,
            SPOT_CAGE,
            SPOT_BIGNET,
            SPOT_BARBARIAN,
            SPOT_MONKFISH,
            SPOT_ANGLERFISH,
            SPOT_MINNOW,
            SPOT_INFERNAL_EEL,
        )

    fun moveWindow(group: String): IntRange =
        when (group) {
            SPOT_ANGLERFISH -> ANGLER_MOVE_MIN_TICKS..ANGLER_MOVE_MAX_TICKS
            SPOT_MINNOW -> MINNOW_MOVE_TICKS..MINNOW_MOVE_TICKS
            SPOT_INFERNAL_EEL -> INFERNAL_EEL_MOVE_MIN_TICKS..INFERNAL_EEL_MOVE_MAX_TICKS
            else -> MOVE_MIN_TICKS..MOVE_MAX_TICKS
        }

    const val HARPOON_CONTENT = "content.fishing_harpoon"

    const val SMALL_NET = "obj.net"
    const val BIG_NET = "obj.big_net"
    const val FISHING_ROD = "obj.fishing_rod"
    const val FLY_FISHING_ROD = "obj.fly_fishing_rod"
    const val LOBSTER_POT = "obj.lobster_pot"
    const val FISHING_BAIT = "obj.fishing_bait"
    const val FEATHER = "obj.feather"

    const val BARBARIAN_ROD = "obj.brut_fishing_rod"

    const val SANDWORMS = "obj.piscarilius_sandworms"

    const val OILY_ROD = "obj.oily_fishing_rod"

    const val ICE_GLOVES = "obj.ice_gloves"
    const val SMITHS_GLOVES_I = "obj.smithing_uniform_gloves_ice"

    const val DARK_FISHING_BAIT = "obj.wilderness_fishing_bait"

    const val KNIFE = "obj.knife"

    const val FISH_OFFCUTS = "obj.brut_fish_cuts"
    const val ROE = "obj.brut_roe"
    const val CAVIAR = "obj.brut_caviar"

    val BARBARIAN_BAITS = listOf(FISH_OFFCUTS, FISHING_BAIT, FEATHER, ROE, CAVIAR)

    const val HARPOON_ANIM_CARRIED = "seq.human_harpoon"

    // Sequence 6702 contains skeletal knife sounds; an additional synth would duplicate them.
    const val CUT_ANIM = "seq.brut_human_knifeuse"

    const val BARB_FISHING_VARBIT = "varbit.brut_fishing_r"

    // These are local progress values; native stage values remain unverified.
    const val BARB_STAGE_NOT_STARTED = 0
    const val BARB_STAGE_TAUGHT = 1
    const val BARB_STAGE_CAUGHT = 2
    const val BARB_STAGE_REPORTED = 3
}
