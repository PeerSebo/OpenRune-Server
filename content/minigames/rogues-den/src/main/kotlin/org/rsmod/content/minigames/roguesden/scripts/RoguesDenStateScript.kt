package org.rsmod.content.minigames.roguesden.scripts

import jakarta.inject.Inject
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.minigames.roguesden.configs.RoguesDenExit
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.inRoguesDenMaze
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.migrate
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class RoguesDenStateScript @Inject constructor(private val collision: CollisionFlagMap) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            player.migrate()
            finishSession(player)
        }
        onPlayerLogout { finishSession(player) }
    }

    private fun finishSession(player: Player) {
        val coords = player.coords
        val insideMaze = coords.level == 1 && coords.x in 2944..3071 && coords.z in 4992..5119
        if (player.inRoguesDenMaze || insideMaze) {
            RoguesDenExit.exitMaze(player, collision)
        } else {
            RoguesDenExit.removeJewels(player)
            RoguesDenState.clearSession(player)
        }
    }
}
