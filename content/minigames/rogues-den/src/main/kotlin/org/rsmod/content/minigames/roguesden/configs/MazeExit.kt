package org.rsmod.content.minigames.roguesden.configs

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.StatRegenPrevention
import org.rsmod.api.player.stat.statRestore
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.clearDisarmedTraps
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.inRoguesDenMaze
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

object RoguesDenExit {
    val LOBBY_LANDING: CoordGrid = CoordGrid(3049, 4976, 1)

    fun restoreAndLeaveMazeState(player: Player, message: String? = null) {
        removeJewels(player)
        player.statRestore(RoguesDenMaze.AGILITY_STAT)
        player.statRestore(RoguesDenMaze.THIEVING_STAT)
        player.statRestore(RoguesDenMaze.PRAYER_STAT)

        player.inRoguesDenMaze = false
        player.clearDisarmedTraps()
        StatRegenPrevention.removeSource(player, RoguesDenMaze.ROGUES_DEN_REGEN_SOURCE)

        if (message != null) {
            player.mes(message)
        }
    }

    fun removeJewels(player: Player) {
        for (slot in player.inv.indices) {
            if (player.inv[slot]?.isType(RoguesDenMaze.MYSTIC_JEWEL) == true) {
                player.inv[slot] = null
            }
        }
    }

    fun ensureSingleJewel(player: Player) {
        val existing = player.inv.indices.firstOrNull {
            player.inv[it]?.isType(RoguesDenMaze.MYSTIC_JEWEL) == true
        }
        removeJewels(player)
        val slot = existing ?: player.inv.indices.first { player.inv[it] == null }
        player.inv[slot] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)
    }

    fun exitMaze(player: Player, collision: CollisionFlagMap, message: String? = null) {
        restoreAndLeaveMazeState(player, message)
        PathingEntityCommon.telejump(player, collision, LOBBY_LANDING)
    }
}
