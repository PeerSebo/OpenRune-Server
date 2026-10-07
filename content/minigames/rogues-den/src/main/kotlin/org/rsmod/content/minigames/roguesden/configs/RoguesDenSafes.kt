package org.rsmod.content.minigames.roguesden.configs

object RoguesDenSafes {
    const val MAZE_SAFE: String = "loc.roguesden_walldecor_mazesafe"
    const val MAZE_SAFE_OPEN: String = "loc.roguesden_walldecor_safe_open"

    const val SAFE_CRACK_ANIM: String = "seq.rogue_player_crack_safe_mid"
    const val SAFE_OPEN_ANIM: String = "seq.rogue_player_crack_safe_end"
    const val SAFE_CRACK_SOUND: String = "synth.SAFE_CRACK"
    const val SAFE_OPEN_SOUND: String = "synth.ROGUE_SAFE_OPEN"

    const val SAFE_OPEN_TICKS: Int = 5

    // Commit via a queue; an interrupted delay must not discard loot/XP.
    const val SAFE_CRACK_QUEUE: String = "queue.roguesden_maze_safe_crack"

    const val XP_PER_DRAINED_LEVEL: Double = 12.0
}
