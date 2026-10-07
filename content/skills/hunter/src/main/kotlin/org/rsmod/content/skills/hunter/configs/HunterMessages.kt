package org.rsmod.content.skills.hunter.configs

object HunterMessages {
    fun levelRequirement(level: Int, action: String): String =
        "You need a Hunter level of $level to $action."

    const val SET_BIRD_SNARE: String = "set a bird snare"
    const val SET_BOX_TRAP: String = "set a box trap"
    const val SET_NET_TRAP: String = "set a net trap"
    const val SET_DEADFALL: String = "set a deadfall trap"

    fun maxTraps(max: Int): String =
        "You may set up only $max traps at a time at your Hunter level."

    fun deadfallCap(max: Int): String =
        if (max == 1) {
            "You can only manage one deadfall trap at a time."
        } else {
            "You can only manage $max deadfall traps at a time."
        }

    const val LAY_START: String = "You begin setting up the trap."

    const val TILE_OCCUPIED: String = "You can't set a trap here."
    const val NEED_ROPE_AND_NET: String =
        "You need a rope and a small fishing net to set this trap."
    const val NEED_KNIFE_AND_LOGS: String = "You need a knife and some logs to set this trap."
    const val NOT_YOUR_TRAP: String = "This isn't your trap."

    const val INVESTIGATE_SET: String = "This trap has been set and is waiting for something."

    const val TRAP_COLLAPSED: String = "One of your traps has collapsed."
    const val CREATURE_ESCAPED: String = "The creature in your trap escaped."
    const val DISMANTLED: String = "You dismantle the trap."
    const val NET_TRAP_SNAPPED: String =
        "The tree snaps back, dropping your rope and net on the ground."

    const val BUILD_BIRD_HOUSE: String = "build this bird house"

    const val BIRDHOUSE_NO_HOUSE: String = "You need a bird house to place here."

    const val BIRDHOUSE_BUILT: String = "You place the bird house."
    const val BIRDHOUSE_OCCUPIED: String = "There is already a bird house here."

    const val BIRDHOUSE_NEED_SEEDS: String =
        "You need 10 low-level seeds or 5 higher-level seeds to fill a bird house."

    const val BIRDHOUSE_BAD_SEED: String = "You can't put that in a bird house."
    const val BIRDHOUSE_SEEDED: String = "You fill the bird house with seeds."
    const val BIRDHOUSE_ALREADY_SEEDED: String = "This bird house already has seeds in it."

    const val BIRDHOUSE_DISMANTLED_EARLY: String =
        "You dismantle the bird house before it has caught anything."

    const val BIRDHOUSE_EMPTIED: String = "You empty the bird house and collect the loot."

    const val BIRDHOUSE_RESET: String = "You empty and reset the bird house."

    const val NET_NO_NET: String = "You need a butterfly net to catch this."
    const val NET_NO_BUTTERFLY_JAR: String = "You need an empty butterfly jar to catch this."
    const val NET_NO_IMPLING_JAR: String = "You need an empty impling jar to catch this."
    const val NET_CATCH_FAILED: String = "You fail to catch it."
    const val NET_TARGET_GONE: String = "It got away."

    private const val VOWELS: String = "aeiou"

    fun caught(creature: String): String {
        val name = creature.lowercase()
        val article = if (name.isNotEmpty() && name[0] in VOWELS) "an" else "a"
        return "You've caught $article $name."
    }

    const val IMPLING_CAUGHT: String =
        "You manage to catch the impling and squeeze it into a jar."
}
