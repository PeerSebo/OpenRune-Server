package org.rsmod.content.minigames.pyramidplunder.configs

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.map.CoordGrid

object PlunderLocs {
    const val OUTER_DOOR_NORTH = "loc.ntk_pyramid_door_north_noanim"
    const val OUTER_DOOR_EAST = "loc.ntk_pyramid_door_east_noanim"
    const val OUTER_DOOR_SOUTH = "loc.ntk_pyramid_door_south_noanim"
    const val OUTER_DOOR_WEST = "loc.ntk_pyramid_door_west_noanim"

    // Door order N/E/S/W matches varbits 2371-2374.
    val OUTER_DOORS: List<String> =
        listOf(OUTER_DOOR_NORTH, OUTER_DOOR_EAST, OUTER_DOOR_SOUTH, OUTER_DOOR_WEST)

    val OUTER_DOORS_OPEN: List<String> =
        listOf(
            "loc.ntk_pyramid_door_north_open_noanim",
            "loc.ntk_pyramid_door_east_open_noanim",
            "loc.ntk_pyramid_door_south_open_noanim",
            "loc.ntk_pyramid_door_west_open_noanim",
        )

    // The spear trap is not a multiloc; separate invisible walls still block ordinary movement.
    const val SPEAR_TRAP = "loc.ntk_speartrap_inmotion"

    val URN_CLOSED: List<String> =
        listOf("loc.ntk_urn1_closed", "loc.ntk_urn2_closed", "loc.ntk_urn3_closed")
    val URN_OPENED: List<String> =
        listOf("loc.ntk_urn1_open", "loc.ntk_urn2_open", "loc.ntk_urn3_open")
    val URN_SNAKE: List<String> =
        listOf("loc.ntk_urn1_snake", "loc.ntk_urn2_snake", "loc.ntk_urn3_snake")
    val URN_SNAKE_CHARMED: List<String> =
        listOf(
            "loc.ntk_urn1_snake_charmed",
            "loc.ntk_urn2_snake_charmed",
            "loc.ntk_urn3_snake_charmed",
        )

    val URN_PARENT_TO_INDEX: Map<Int, Int> =
        mapOf(
            "loc.ntk_urn_type1_multi_1".asRSCM() to 1,
            "loc.ntk_urn_type1_multi_2".asRSCM() to 2,
            "loc.ntk_urn_type1_multi_3".asRSCM() to 3,
            "loc.ntk_urn_type1_multi_4".asRSCM() to 4,
            "loc.ntk_urn_type1_multi_5".asRSCM() to 5,
            "loc.ntk_urn_type2_multi_6".asRSCM() to 6,
            "loc.ntk_urn_type2_multi_7".asRSCM() to 7,
            "loc.ntk_urn_type2_multi_8".asRSCM() to 8,
            "loc.ntk_urn_type2_multi_9".asRSCM() to 9,
            "loc.ntk_urn_type2_multi_10".asRSCM() to 10,
            "loc.ntk_urn_type3_multi_11".asRSCM() to 11,
            "loc.ntk_urn_type3_multi_12".asRSCM() to 12,
            "loc.ntk_urn_type3_multi_13".asRSCM() to 13,
            "loc.ntk_urn_type3_multi_14".asRSCM() to 14,
            "loc.ntk_urn_type3_multi_15".asRSCM() to 15,
        )

    const val CHEST_CLOSED = "loc.ntk_golden_chest_closed"
    const val CHEST_OPEN = "loc.ntk_golden_chest_open"

    const val SARCOPHAGUS_CLOSED = "loc.ntk_sarcophagus"
    const val SARCOPHAGUS_OPEN = "loc.ntk_sarcophagus_open"

    const val TOMB_DOOR_LOCKED = "loc.ntk_tomb_door_noanim"
    const val TOMB_DOOR_UNLOCKED = "loc.ntk_tomb_door_anim"

    val TOMB_DOOR_PARENT_TO_INDEX: Map<Int, Int> =
        mapOf("loc.ntk_tomb_door1".asRSCM() to 1, "loc.ntk_tomb_door2".asRSCM() to 2, "loc.ntk_tomb_door3".asRSCM() to 3, "loc.ntk_tomb_door4".asRSCM() to 4)

    const val TOMB_DOOR_EXIT = "loc.ntk_tomb_door_exit"
    const val ANTECHAMBER_EXIT = "loc.ntk_antechamber_exit"

    val ROOM_LANDINGS: Map<Int, CoordGrid> by lazy {
        PlunderRooms.ALL.associate { it.room to it.landing }
    }

    // Each trap band is two tiles thick; crossing must bypass its static invisible walls.
    enum class TrapAxis {
        X,
        Z,
    }

    data class TrapBand(val axis: TrapAxis, val lo: Int, val hi: Int) {
        fun crossDestination(from: CoordGrid, landing: CoordGrid): CoordGrid? {
            val pos = if (axis == TrapAxis.X) from.x else from.z
            val landingPos = if (axis == TrapAxis.X) landing.x else landing.z
            val entryHigh = landingPos > hi
            val needsCrossing = if (entryHigh) pos >= lo else pos <= hi
            if (!needsCrossing) {
                return null
            }
            val destPos = if (entryHigh) lo - 1 else hi + 1
            return if (axis == TrapAxis.X) CoordGrid(destPos, from.z, from.level)
            else CoordGrid(from.x, destPos, from.level)
        }
    }

    val TRAP_BANDS: Map<Int, TrapBand> by lazy {
        PlunderRooms.ALL.associate { it.room to it.trapBand }
    }

    val ANTECHAMBER_LANDINGS: List<CoordGrid> =
        listOf(
            CoordGrid(1934, 4420, 3),
            CoordGrid(1934, 4450, 2),
            CoordGrid(1968, 4420, 2),
            CoordGrid(1968, 4450, 3),
        )

    const val GUARDIAN_CHAMBER = 0

    val EXIT_LANDING: CoordGrid = CoordGrid(3288, 2801, 0)

    const val MUMMY_GUARDIAN = "npc.ntk_mummy_guardian"
    const val SCARAB_SWARM = "npc.ntk_scarab_swarm"

    val MUMMIES: List<String> =
        listOf(
            "npc.ntk_mummy_1",
            "npc.ntk_mummy_2",
            "npc.ntk_mummy_3",
            "npc.ntk_mummy_4",
            "npc.ntk_mummy_5",
        )
    const val SIMON_TEMPLETON = "npc.agility_pyramid_simon"

    const val SNAKE_CHARM = "obj.snake_flute"
    const val LOCKPICK = "obj.lockpick"

    const val SEQ_SEARCH = "seq.human_pickuptable"
    const val SEQ_PICK_STAGE2 = "seq.human_scythe_block"
    const val SEQ_URN_SEARCH = "seq.urn_search"
    const val SEQ_URN_BIT = "seq.urn_search_bit"
    const val SEQ_URN_SUCCESS = "seq.urn_search_success"
    const val SEQ_STUMBLE_BACK = "seq.human_stumble_back"
    const val SEQ_SARCOPHAGUS_PUSH = "seq.ntk_tomb_push_loop"
    const val SEQ_SARCOPHAGUS_OPEN = "seq.ntk_tomb_open"

    // These sequences target loc skeletons; sending them as player animations can crash the client.
    const val SEQ_TOMB_DOOR_SWING = "seq.ntk_tomb_door"
    const val SEQ_SARC_READY = "seq.ntk_sarcophagi_anim_ready"
    const val SEQ_SARC_ANIM = "seq.ntk_sarcophagi_anim"

    const val SPOT_STUNNED = "spotanim.stunned_thieving"

    const val SYNTH_OUTER_SEARCH = "synth.NTK_STONE_DOOR_LOCKED"
    const val SYNTH_PICK = "synth.STONE_DOOR_LOCKED"
    const val SYNTH_PICK_STAGE2 = "synth.MUMMY_ATTACK"
    const val SYNTH_DOOR_SWING = "synth.COFFIN_OPEN"
    const val SYNTH_DEAD_END_1 = "synth.TORAG_BREASTPLATE_03"
    const val SYNTH_DEAD_END_2 = "synth.TORAG_BREASTPLATE_04"
    const val SYNTH_TRAP_DISARM = "synth.DISARM_TRAP"
    const val SYNTH_TRAP_FAIL = "synth.SPEARTRAP"
    const val SYNTH_CHEST_OPEN = "synth.NTK_CHEST_OPEN"
    const val SYNTH_JAR_OPEN = "synth.NTK_JAR_OPEN"
    const val SYNTH_JAR_SNAKE = "synth.NTK_JAR_WITH_SNAKE"
    const val SYNTH_SNAKE_ATTACK = "synth.SNAKE_ATTACK"
    const val SYNTH_SARCOPHAGUS_OPEN = "synth.NTK_SARCOPHAGUS_OPEN"
    const val SYNTH_SCARABS = "synth.SCARABS_APPEAR"
    const val SYNTH_PUNCH = "synth.WALL_CRUSHED"
    const val SYNTH_PUNCH_HIT = "synth.HUMAN_HIT_02"

    // The overlay belongs to active run state, not the area; idle antechambers have none.
    const val OVERLAY_INTERFACE = "interface.ntk_overlay"
}
