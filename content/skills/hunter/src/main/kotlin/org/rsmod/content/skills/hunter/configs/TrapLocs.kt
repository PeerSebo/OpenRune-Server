package org.rsmod.content.skills.hunter.configs

object TrapLocs {

    const val SNARE_SET: String = "loc.hunting_ojibway_trap"

    const val SNARE_FAILING: String = "loc.hunting_ojibway_trap_failing"

    const val SNARE_BROKEN: String = "loc.hunting_ojibway_trap_broken"

    fun snareTrapping(suffix: String): String = "loc.hunting_ojibway_trap_trapping_$suffix"

    fun snareFull(suffix: String): String = "loc.hunting_ojibway_trap_full_$suffix"

    const val BOX_SET: String = "loc.hunting_boxtrap_empty"

    const val BOX_FAILING: String = "loc.hunting_boxtrap_failing"

    const val BOX_FAILED: String = "loc.hunting_boxtrap_failed"

    fun boxTrapping(suffix: String, direction: String): String =
        "loc.hunting_boxtrap_trapping_${suffix}_$direction"

    fun boxFull(suffix: String): String = "loc.hunting_boxtrap_full_$suffix"

    fun saplingBare(colour: String): String = "loc.hunting_sapling_up_$colour"

    fun saplingSetting(colour: String): String = "loc.hunting_sapling_setting_$colour"

    fun saplingBent(colour: String): String = "loc.hunting_sapling_set_$colour"

    fun saplingArmed(colour: String): String = "loc.hunting_sapling_net_set_$colour"

    fun saplingFailing(colour: String): String = "loc.hunting_sapling_failing_$colour"

    // Swamp states use the green suffix only for catching/full variants.
    fun saplingCatching(catchSuffix: String): String = "loc.hunting_sapling_catching_$catchSuffix"

    fun saplingFull(catchSuffix: String): String = "loc.hunting_sapling_full_$catchSuffix"

    fun saplingFailed(colour: String): String = "loc.hunting_sapling_failed_$colour"

    const val DEADFALL_BOULDER: String = "loc.hunting_deadfall_boulder"

    const val DEADFALL_SETTING: String = "loc.hunting_deadfall_setting"

    const val DEADFALL_SET: String = "loc.hunting_deadfall_trap"

    const val DEADFALL_FAILING: String = "loc.hunting_deadfall_failing"

    fun deadfallTrapping(suffix: String): String = "loc.hunting_deadfall_trapping_$suffix"

    fun deadfallFull(suffix: String): String = "loc.hunting_deadfall_full_$suffix"
}
