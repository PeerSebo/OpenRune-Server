package org.rsmod.content.minigames.roguesden.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld5
import org.rsmod.content.minigames.roguesden.configs.RoguesDenExit
import org.rsmod.content.minigames.roguesden.configs.RoguesDenMaze
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.inRoguesDenMaze
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MysticJewelScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(RoguesDenMaze.MYSTIC_JEWEL) { activate() }
        // Keep the jewel while inside the maze; dropping the only exit item would strand the player.
        onOpHeld5(RoguesDenMaze.MYSTIC_JEWEL) { dropOutsideMazeOnly() }
    }

    private fun ProtectedAccess.activate() {
        if (!player.inRoguesDenMaze) {
            mes("Nothing interesting happens.")
            return
        }
        RoguesDenExit.restoreAndLeaveMazeState(
            player,
            "The mystic jewel glows, and you are whisked out of the maze.",
        )
        telejump(RoguesDenExit.LOBBY_LANDING)
    }

    private fun ProtectedAccess.dropOutsideMazeOnly() {
        if (player.inRoguesDenMaze) {
            mes("You can't drop this while you're inside the maze.")
            return
        }
        if (invDel(inv, RoguesDenMaze.MYSTIC_JEWEL, 1).failure) {
            return
        }
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        objRepo.add(RoguesDenMaze.MYSTIC_JEWEL, player.coords, duration, receiver = player)
    }
}
