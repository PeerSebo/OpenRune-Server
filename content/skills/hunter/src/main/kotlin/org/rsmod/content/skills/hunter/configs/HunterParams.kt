package org.rsmod.content.skills.hunter.configs

object HunterParams {
    const val HUNTER_STAT: String = "stat.hunter"

    const val BIRD_SNARE_OBJ: String = "obj.hunting_ojibway_bird_snare"
    const val BOX_TRAP_OBJ: String = "obj.hunting_box_trap"
    const val ROPE: String = "obj.rope"

    const val SMALL_FISHING_NET: String = "obj.net"

    const val KNIFE: String = "obj.knife"

    const val FLETCHING_KNIFE: String = "obj.fletching_knife"

    val KNIVES: List<String> = listOf(KNIFE, FLETCHING_KNIFE)

    const val LOGS: String = "obj.logs"

    val DEADFALL_LOGS: List<String> =
        listOf(
            LOGS,
            "obj.achey_tree_logs",
            "obj.oak_logs",
            "obj.willow_logs",
            "obj.teak_logs",
            "obj.maple_logs",
            "obj.mahogany_logs",
            "obj.yew_logs",
            "obj.magic_logs",
        )

    fun consumedTools(family: TrapFamily): List<String> =
        when (family) {
            TrapFamily.BIRD_SNARE -> listOf(BIRD_SNARE_OBJ)
            TrapFamily.BOX_TRAP -> listOf(BOX_TRAP_OBJ)
            TrapFamily.NET_TRAP -> listOf(ROPE, SMALL_FISHING_NET)
            TrapFamily.DEADFALL -> listOf(LOGS)
        }

    fun recoveredTools(family: TrapFamily): List<String> =
        when (family) {
            TrapFamily.BIRD_SNARE -> listOf(BIRD_SNARE_OBJ)
            TrapFamily.BOX_TRAP -> listOf(BOX_TRAP_OBJ)
            TrapFamily.NET_TRAP -> listOf(ROPE, SMALL_FISHING_NET)
            TrapFamily.DEADFALL -> emptyList()
        }

    const val LAY_QUEUE: String = "queue.hunter_lay_trap"
    const val CHECK_QUEUE: String = "queue.hunter_check_trap"

    const val LAY_TRAP_ANIM: String = "seq.human_laytrap"

    const val LAY_NET_ANIM: String = "seq.hunting_setting_sapling_trap"

    const val CHECK_TRAP_ANIM: String = "seq.human_hunting_dismantle_net"

    const val CHECK_BOX_ANIM: String = "seq.hunting_setting_trap_small"

    const val SNARE_LAY_SYNTH: String = "synth.HUNTING_SETSNARE"
    const val BOX_LAY_SYNTH: String = "synth.HUNTING_LAYBOXTRAP"
    const val NET_LAY_SYNTH: String = "synth.HUNTING_SET_TWITCHNET"
    const val DEADFALL_LAY_SYNTH: String = "synth.HUNTING_SETDEADFALL"

    const val DISMANTLE_SYNTH: String = "synth.HUNTING_DISMANTLE"
    const val TAKE_CREATURE_SYNTH: String = "synth.HUNTING_TAKE_CREATURE"

    const val SNARE_SPRING_SYNTH: String = "synth.HUNTING_NOOSE"

    const val SNARE_CAUGHT_SYNTH: String = "synth.HUNTING_BIRDCAUGHT"
    const val BOX_SPRING_SYNTH: String = "synth.HUNTING_BOXTRAP"
    const val NET_SPRING_SYNTH: String = "synth.HUNTING_TWITCHNET"
    const val DEADFALL_SPRING_SYNTH: String = "synth.HUNTING_DEADFALL"

    const val ESCAPE_SYNTH: String = "synth.HUNTING_SMOKEPUFF_ESCAPE"

    const val BOX_CAUGHT_SYNTH: String = "synth.CHINCHOMPA_TRAPPED"

    const val TRAP_SOUND_RADIUS: Int = 5

    // The three-tick cadence and two-tile attraction radius are verified only for box traps.
    const val ATTEMPT_INTERVAL_TICKS: Int = 3

    const val TRAP_RADIUS: Int = 2

    const val TRANSITION_TICKS: Int = 2

    const val LAY_TICKS: Int = 3

    const val CHECK_TICKS: Int = 2

    // Trap expiry/escape and prey respawn durations are provisional.
    const val SET_LIFETIME_TICKS: Int = 500

    const val CAUGHT_LIFETIME_TICKS: Int = 200

    const val FAILED_LIFETIME_TICKS: Int = 200

    const val PREY_RESPAWN_TICKS: Int = 50

    const val DEADFALL_TRAP_CAP: Int = 2

    const val DEADFALL_LOG_PRESERVE_CHANCE: Int = 75

    const val DEADFALL_FIRST_ATTEMPT_TICKS: Int = 8

    fun maxConcurrentTraps(
        level: Int,
        inWilderness: Boolean,
        wildernessBonusEligible: Boolean,
    ): Int {
        val base =
            when {
                level >= 80 -> 5
                level >= 60 -> 4
                level >= 40 -> 3
                else -> 2
            }
        return if (inWilderness && wildernessBonusEligible) base + 1 else base
    }
}
