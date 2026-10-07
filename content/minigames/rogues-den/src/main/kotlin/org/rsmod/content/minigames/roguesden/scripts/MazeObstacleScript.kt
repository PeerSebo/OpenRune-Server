package org.rsmod.content.minigames.roguesden.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc5
import org.rsmod.content.minigames.roguesden.configs.RoguesDenMaze
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.disarmTrap
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MazeObstacleScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(RoguesDenMaze.CONTORTION_BARS) { crossContortionBars(it.loc) }
        onOpLoc1(RoguesDenMaze.LEDGE_FLOOR) { crossLedge(it.loc, RoguesDenMaze.LEDGE_FLOOR_COST) }
        onOpLoc1(RoguesDenMaze.LEDGE_WALL) { crossLedge(it.loc, RoguesDenMaze.LEDGE_WALL_COST) }

        for (trap in RoguesDenMaze.SEARCHABLE_TRAPS) {
            onOpLoc5(trap) { searchTrap(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.crossContortionBars(loc: BoundLocInfo) {
        arriveDelay()
        if (stat(RoguesDenMaze.AGILITY_STAT) < RoguesDenMaze.CONTORTION_COST) {
            mes(RoguesDenMaze.AGILITY_DENY)
            return
        }
        statSub(RoguesDenMaze.AGILITY_STAT, RoguesDenMaze.CONTORTION_COST, 0)
        crossMazeObstacle(loc, RoguesDenMaze.CONTORTION_ANIM)
    }

    // TODO: distinguish the final ledge's higher drain by placement.
    private suspend fun ProtectedAccess.crossLedge(loc: BoundLocInfo, cost: Int) {
        arriveDelay()
        if (stat(RoguesDenMaze.AGILITY_STAT) < cost) {
            mes(RoguesDenMaze.AGILITY_DENY)
            return
        }
        statSub(RoguesDenMaze.AGILITY_STAT, cost, 0)
        // TODO: verify the ledge animation; contortion is a placeholder.
        crossMazeObstacle(loc, RoguesDenMaze.CONTORTION_ANIM)
    }

    private fun ProtectedAccess.searchTrap(loc: BoundLocInfo) {
        if (stat(RoguesDenMaze.THIEVING_STAT) < RoguesDenMaze.SEARCH_TRAP_COST) {
            mes(RoguesDenMaze.THIEVING_DENY)
            return
        }
        statSub(RoguesDenMaze.THIEVING_STAT, RoguesDenMaze.SEARCH_TRAP_COST, 0)
        player.disarmTrap(loc.coords)
        mes(RoguesDenMaze.TRAP_DISARM_MESSAGE)
    }
}
