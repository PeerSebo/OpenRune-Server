package org.rsmod.content.skills.thieving.configs

// Maze safes belong to the Rogues' Den minigame, not the lobby-safe handlers.
object ThievingSafes {
    const val LOBBY_SAFE: String = "loc.roguesden_walldecor_safe"
    const val LOBBY_SAFE_OPEN: String = "loc.roguesden_walldecor_safe_open"

    const val LEVEL_REQ: Int = 50
    const val XP: Double = 70.0

    const val OPEN_TICKS: Int = 5

    const val STETHOSCOPE: String = "obj.roguesden_stethoscope"

    const val CRACK_ANIM: String = "seq.rogue_player_crack_safe_mid"
    const val OPEN_ANIM: String = "seq.rogue_player_crack_safe_end"
    const val CRACK_SOUND: String = "synth.SAFE_CRACK"
    const val OPEN_SOUND: String = "synth.ROGUE_SAFE_OPEN"

    const val TRAP_DAMAGE_LOW: Int = 2

    const val TRAP_DAMAGE_HIGH: Int = 6

    const val CRACK_QUEUE: String = "queue.thieving_safe_crack"
}
