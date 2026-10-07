package org.rsmod.content.areas.wilderness.locs

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WildernessBossPassages : PluginScript() {
    override fun ScriptContext.startup() {
        for (symbol in Passages.map { it.symbol }.distinct()) {
            onOpLoc1(symbol) { usePassage(it.loc) }
        }
    }

    internal suspend fun ProtectedAccess.usePassage(loc: BoundLocInfo) {
        val passage = Passages.firstOrNull { it.symbol.asRSCM() == loc.id && it.source == loc.coords }
            ?: return
        arriveDelay()
        if (passage.ladder) {
            anim("seq.human_reachforladder")
            delay(1)
        }
        telejump(passage.destination, TeleportType.Exempt)
    }

    internal data class Passage(
        val symbol: String,
        val source: CoordGrid,
        val destination: CoordGrid,
        val ladder: Boolean = false,
    )

    internal companion object {
        val Passages = listOf(
            Passage("loc.wildymirrorladdertop2", CoordGrid(3069, 3856), CoordGrid(3069, 10255), true),
            Passage("loc.wildymirrorladder1", CoordGrid(3069, 10256), CoordGrid(3069, 3857), true),
            // Both tombs are on native plane 1; these are not bridge-sunk map placements.
            Passage("loc.wild_vetion_entrance01", CoordGrid(3221, 3787), CoordGrid(3295, 10194, 1)),
            Passage("loc.wild_vetion_singles_entrance01", CoordGrid(3180, 3683), CoordGrid(1887, 11538, 1)),
            Passage("loc.wild_vetion_exit01", CoordGrid(3294, 10190, 1), CoordGrid(3337, 10279)),
            Passage("loc.wild_vetion_exit01", CoordGrid(1886, 11534, 1), CoordGrid(3181, 3683)),
        )
    }
}
