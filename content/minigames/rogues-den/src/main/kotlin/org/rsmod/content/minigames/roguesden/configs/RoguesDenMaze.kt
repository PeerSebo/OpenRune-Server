package org.rsmod.content.minigames.roguesden.configs

object RoguesDenMaze {
    const val AGILITY_REQ: Int = 50

    const val THIEVING_REQ: Int = 50

    const val AGILITY_STAT: String = "stat.agility"
    const val THIEVING_STAT: String = "stat.thieving"
    const val PRAYER_STAT: String = "stat.prayer"

    const val ROGUES_DEN_REGEN_SOURCE: String = "rogues_den_maze"

    const val MAZE_MASTER: String = "npc.roguesden_mazemaster"
    const val MAZE_MASTER_NAME: String = "Brian O'Richard"
    const val MAZE_ENTRANCE: String = "loc.roguesden_mazeentrance"
    const val MYSTIC_JEWEL: String = "obj.roguesden_gem"

    const val MAZE_ENTRANCE_OPEN: String = "loc.roguesden_obstacle_blocking_door_noentry"

    const val MAZE_ENTRANCE_OPEN_SOUND: String = "synth.PORTCULLIS_OPEN"

    const val DENY_NO_ACCESS: String =
        "And where do you think you're going? A little too eager I think. Come and talk to me " +
            "before you go wandering around in there."

    const val DENY_ITEMS: String =
        "Tut tut tut, now you know you're not allowed to take anything except that jewel in " +
            "with you."

    const val DENY_EXIT: String = "The doorway is blocked - you'll have to find another way back."

    const val DENY_LEVEL: String =
        "You need an Agility level of $AGILITY_REQ and a Thieving level of $THIEVING_REQ to " +
            "enter the maze."

    const val JEWEL_GRANT_MES: String =
        "Brian O'Richard gives you a jewel as you enter the maze."

    const val CONTORTION_BARS: String = "loc.roguesden_obstacle_contortion_bars"
    const val CONTORTION_COST: Int = 5
    const val CONTORTION_ANIM: String = "seq.roguesden_contortion"

    const val LEDGE_FLOOR: String = "loc.roguesden_obstacle_floor_balancing_ledge"
    const val LEDGE_FLOOR_COST: Int = 4

    // TODO: identify the final floor ledge placement before applying its seven-level drain.
    const val LEDGE_FLOOR_FINAL_COST_TODO: Int = 7

    const val LEDGE_WALL: String = "loc.roguesden_obstacle_wall_hanging_ledge"
    const val LEDGE_WALL_COST: Int = 4

    const val AGILITY_DENY: String = "You don't have enough Agility skill left!"

    const val TRAP_FLOOR_SPIKES: String = "loc.roguesden_trap_floor_spikes"
    const val TRAP_FLOOR_SPRING: String = "loc.roguesden_trap_floor_springtile"
    const val TRAP_FLOOR_EXPLOSION: String = "loc.roguesden_trap_floor_explosion"
    const val TRAP_WALL_SPIKES: String = "loc.roguesden_trap_wall_spikes"
    const val TRAP_WALL_GAS: String = "loc.roguesden_trap_wall_poison_gas"
    const val TRAP_WALL_CRUSHER: String = "loc.roguesden_trap_wall_crusher"

    const val SEARCH_TRAP_COST: Int = 1

    const val THIEVING_DENY: String = "You don't have enough Thieving skill left!"

    const val TRAP_DISARM_MESSAGE: String = "You carefully disarm the trap."

    val SEARCHABLE_TRAPS: Set<String> =
        setOf(
            TRAP_FLOOR_SPIKES,
            TRAP_FLOOR_SPRING,
            TRAP_FLOOR_EXPLOSION,
            TRAP_WALL_SPIKES,
            TRAP_WALL_GAS,
            TRAP_WALL_CRUSHER,
        )

    const val TRAP_FLOOR_SCYTHEBLADE: String = "loc.roguesden_trap_floor_scytheblade"

    const val TRAP_FLOOR_PENDULUM: String = "loc.roguesden_trap_floor_pendulum"
    const val PENDULUM_COST: Int = 3

    const val PENDULUM_JUMP_ANIM: String = "seq.human_spot_jump"

    const val PENDULUM_JUMP_SOUND: String = "synth.ROGUE_PENDULUM_HIT"

    // The jump loops indefinitely; queue its reset because the coordinate hook has no protected coroutine.
    const val PENDULUM_JUMP_QUEUE: String = "queue.roguesden_pendulum_jump"

    const val TRAP_FLOOR_TIMEDBLADE: String = "loc.roguesden_trap_floor_timedblade"
    const val FLOOR_BLADE_COST: Int = 3

    const val TRAP_WALL_SCYTHEBLADE: String = "loc.roguesden_trap_wall_scytheblade"
    const val TRAP_WALL_SAW: String = "loc.roguesden_trap_wall_saw"

    const val WALL_BLADE_SAW_COST: Int = FLOOR_BLADE_COST

    const val HAZARD_EJECT_MESSAGE: String = "You are caught by the trap and thrown out of the maze!"

    const val ROGUE_HELM: String = "obj.roguesden_helm"
    const val ROGUE_BODY: String = "obj.roguesden_body"
    const val ROGUE_LEGS: String = "obj.roguesden_legs"
    const val ROGUE_GLOVES: String = "obj.roguesden_gloves"
    const val ROGUE_BOOTS: String = "obj.roguesden_boots"
    const val ROGUE_KIT: String = "obj.roguesden_kit"
    const val ROGUE_CRATE: String = "obj.roguesden_crate"

    const val CHISEL: String = "obj.chisel"
    const val ROPE: String = "obj.rope"
    const val TINDERBOX: String = "obj.tinderbox"
    const val SPADE: String = "obj.spade"
}
