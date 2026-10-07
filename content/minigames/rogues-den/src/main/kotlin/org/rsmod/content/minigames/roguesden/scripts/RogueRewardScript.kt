package org.rsmod.content.minigames.roguesden.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.minigames.roguesden.configs.RoguesDenMaze
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO: replace random crate/kit rewards with their native choice menus.
class RogueRewardScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(RoguesDenMaze.ROGUE_CRATE) { searchCrate() }
        onOpHeld1(RoguesDenMaze.ROGUE_KIT) { buildKit() }
    }

    private fun ProtectedAccess.searchCrate() {
        if (invDel(inv, RoguesDenMaze.ROGUE_CRATE, 1).failure) {
            return
        }
        val reward = random.pick(CRATE_REWARDS)
        invAdd(inv, reward, 1)
        mes("You search the crate and find " + rewardName(reward) + ".")
    }

    private fun ProtectedAccess.buildKit() {
        if (invDel(inv, RoguesDenMaze.ROGUE_KIT, 1).failure) {
            return
        }
        val reward = random.pick(KIT_REWARDS)
        invAdd(inv, reward, 1)
        mes("You use the kit to make " + rewardName(reward) + ".")
    }

    private fun rewardName(obj: String): String =
        when (obj) {
            RoguesDenMaze.ROGUE_HELM -> "a rogue mask"
            RoguesDenMaze.ROGUE_BODY -> "a rogue top"
            RoguesDenMaze.ROGUE_LEGS -> "rogue trousers"
            RoguesDenMaze.ROGUE_GLOVES -> "rogue gloves"
            RoguesDenMaze.ROGUE_BOOTS -> "rogue boots"
            RoguesDenMaze.ROGUE_KIT -> "a rogue kit"
            RoguesDenMaze.CHISEL -> "a chisel"
            RoguesDenMaze.ROPE -> "a rope"
            RoguesDenMaze.TINDERBOX -> "a tinderbox"
            RoguesDenMaze.SPADE -> "a spade"
            else -> "an item"
        }

    private companion object {
        val CRATE_REWARDS =
            listOf(
                RoguesDenMaze.ROGUE_HELM,
                RoguesDenMaze.ROGUE_BODY,
                RoguesDenMaze.ROGUE_LEGS,
                RoguesDenMaze.ROGUE_GLOVES,
                RoguesDenMaze.ROGUE_BOOTS,
                RoguesDenMaze.ROGUE_KIT,
            )

        val KIT_REWARDS =
            listOf(
                RoguesDenMaze.CHISEL,
                RoguesDenMaze.ROPE,
                RoguesDenMaze.TINDERBOX,
                RoguesDenMaze.SPADE,
            )
    }
}
