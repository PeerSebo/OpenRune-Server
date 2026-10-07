package org.rsmod.content.minigames.roguesden.configs

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

object RoguesDenState {
    var Player.inRoguesDenMaze by boolVarp("varp.rogues_den_in_maze")
    private var Player.mazeAccess by boolVarp("varp.rogues_den_maze_access")
    private var Player.migrated by boolVarp("varp.rogues_den_state_migrated")

    private val disarmedTraps =
        AttributeKey<MutableSet<CoordGrid>>(resetOnDeath = true, temp = true)

    private val legacyAccess = AttributeKey<Boolean>(persistenceKey = "roguesden_spoken_to_brian")

    fun Player.disarmedTrap(tile: CoordGrid): Boolean = attr[disarmedTraps]?.contains(tile) == true

    fun Player.disarmTrap(tile: CoordGrid) {
        attr.getOrPut(disarmedTraps) { mutableSetOf() }.add(tile)
    }

    fun Player.clearDisarmedTraps() {
        attr.remove(disarmedTraps)
    }

    var Player.grantedMazeAccess: Boolean
        get() {
            migrate()
            return mazeAccess
        }
        set(value) {
            mazeAccess = value
            migrated = true
            attr.remove(legacyAccess)
        }

    fun Player.migrate() {
        if (!migrated) {
            if ("varp.rogues_den_maze_access" !in vars) mazeAccess = attr[legacyAccess] == true
            migrated = true
        }
        attr.remove(legacyAccess)
    }

    fun clearSession(player: Player) {
        player.inRoguesDenMaze = false
        player.clearDisarmedTraps()
    }
}
