package org.rsmod.content.skills.fishing.scripts

import org.rsmod.api.player.feet
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.torso
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MinnowRowboat : PluginScript() {

    override fun ScriptContext.startup() {
        onOpContentLoc1("content.minnow_rowboat_to_island") { toIsland() }
        onOpContentLoc1("content.minnow_rowboat_to_guild") { toGuild() }
    }

    private suspend fun ProtectedAccess.toIsland() {
        if (player.fishingLvl < LEVEL_REQ) {
            mes("You need a Fishing level of $LEVEL_REQ to fish on the island.")
            return
        }
        if (!hasFullAngler()) {
            mes(
                "You must be wearing a full angler's outfit before Kylie will let you onto the island."
            )
            return
        }
        arriveDelay()
        telejump(ISLAND)
    }

    private suspend fun ProtectedAccess.toGuild() {
        arriveDelay()
        telejump(GUILD)
    }

    private fun ProtectedAccess.hasFullAngler(): Boolean =
        player.hat.isAnglerPiece("obj.trawler_reward_hat", "obj.spirit_angler_hat") &&
            player.torso.isAnglerPiece("obj.trawler_reward_top", "obj.spirit_angler_top") &&
            player.legs.isAnglerPiece("obj.trawler_reward_legs", "obj.spirit_angler_legs") &&
            player.feet.isAnglerPiece("obj.trawler_reward_boots", "obj.spirit_angler_boots")

    private fun InvObj?.isAnglerPiece(regular: String, spirit: String): Boolean =
        this != null && (isType(regular) || isType(spirit))

    private companion object {
        private const val LEVEL_REQ = 82

        private val ISLAND = CoordGrid(2614, 3411, 0)
        private val GUILD = CoordGrid(2599, 3424, 0)
    }
}
