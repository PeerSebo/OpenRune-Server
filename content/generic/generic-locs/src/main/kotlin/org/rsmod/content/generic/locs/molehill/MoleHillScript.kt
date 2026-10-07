package org.rsmod.content.generic.locs.molehill

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.mechanics.dig.DigSites
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.table.LightSourcesRow
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MoleHillScript @Inject constructor(private val players: PlayerList) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.mole_hill_spade") { enterPrivateLair() }
        DigSites.register("giant_mole.public", MOLE_HILLS) {
            if (!player.holdsLitLightSource()) {
                mes("It's going to be dark down there, I should bring a light source.")
                delay(1)
                resetAnim()
                return@register true
            }
            delay(1)
            resetAnim()
            telejump(CAVE_ENTRANCE)
            true
        }
        onOpLoc5("loc.mole_hill") {
            arriveDelay()
            val count = players.count { !it.staffInvisible && inPublicCave(it.coords) }
            mes("There ${if (count == 1) "is 1 player" else "are $count players"} inside the Mole Hole.")
        }
        onOpLoc1("loc.mole_rope_02") {
            arriveDelay()
            anim("seq.emote_climbing_rope")
            delay(2)
            telejump(SURFACE_EXIT)
            resetAnim()
        }
    }

    private suspend fun ProtectedAccess.enterPrivateLair() {
        arriveDelay()

        if (!player.holdsLitLightSource()) {
            mes("It's going to be dark down there, I should bring a light source.")
            return
        }

        // TODO: Add private-instance entry, confirmation and discounted fees.
        mes(constants.dm_default, ChatType.Engine)
    }

    private fun Player.holdsLitLightSource(): Boolean =
        LightSourcesRow.all().any { it.lit in inv || it.lit in worn }

    private companion object {
        val MOLE_HILLS = listOf(
            CoordGrid(2984, 3387), CoordGrid(2987, 3387), CoordGrid(2989, 3378),
            CoordGrid(2996, 3377), CoordGrid(2999, 3375), CoordGrid(3005, 3376),
        )
        val CAVE_ENTRANCE = CoordGrid(1752, 5237)
        val SURFACE_EXIT = CoordGrid(2983, 3387)
        fun inPublicCave(tile: CoordGrid): Boolean =
            tile.level == 0 && tile.x in 1728..1791 && tile.z in 5120..5247
    }
}
