package org.rsmod.content.minigames.fishingtrawler.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.minigames.fishingtrawler.TrawlerConstants
import org.rsmod.content.minigames.fishingtrawler.TrawlerManager
import org.rsmod.content.minigames.fishingtrawler.TrawlerPhase
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Each hull base slot transforms to the same leak loc; resolve the original slot before changing its varbit.
class TrawlerHullScript
@Inject
constructor(
    private val manager: TrawlerManager,
    private val poh: PohManager,
) : PluginScript() {

    private val swampPaste by lazy {
        checkNotNull(ServerCacheManager.getItem(TrawlerConstants.SWAMP_PASTE_ITEM.asRSCM(RSCMType.OBJ))) {
            "Missing item def for ${TrawlerConstants.SWAMP_PASTE_ITEM}"
        }
    }

    override fun ScriptContext.startup() {
        onOpLoc1(TrawlerConstants.HULL_LEAK_TRANSFORM_LOC) { fillLeak(it.loc, it.vis) }
    }

    private suspend fun ProtectedAccess.fillLeak(baseLoc: BoundLocInfo, visibleLoc: BoundLocInfo) {
        val voyage = manager.voyageForPlayer(player)
        val playerId = player.uuid
        if (voyage == null || playerId == null || voyage.phase != TrawlerPhase.SAILING) {
            mes("This isn't your trawler to fix.")
            return
        }
        val slot = baseLoc.id - TrawlerConstants.BASE_HULL_LOC.asRSCM(RSCMType.LOC)
        if (slot !in voyage.leakState.indices ||
            voyage.leakState[slot] != TrawlerConstants.HULL_STATE_LEAKING) {
            return
        }
        if (swampPaste !in inv) {
            mes("You need some swamp paste to fill this leak.")
            return
        }
        faceLoc(visibleLoc)
        // TODO: verify hull-repair animation; human_pickupfloor is a silent placeholder.
        anim("seq.human_pickupfloor")
        delay(2)
        if (manager.voyageForPlayer(player) !== voyage ||
            voyage.phase != TrawlerPhase.SAILING ||
            voyage.leakState[slot] != TrawlerConstants.HULL_STATE_LEAKING) return

        val delTx = player.invDel(inv, obj = swampPaste.id, count = 1)
        if (!delTx.success) {
            return
        }

        manager.patchLeak(voyage, slot, mapClock)
        voyage.addContribution(playerId, TrawlerConstants.POINTS_LEAK_FILL)
        mes("You fill the leak with swamp paste.")
        if (poh.hasHouse(player)) {
            statAdvance("stat.construction", TrawlerConstants.CONSTRUCTION_XP_LEAK_FILL)
        }
    }
}
