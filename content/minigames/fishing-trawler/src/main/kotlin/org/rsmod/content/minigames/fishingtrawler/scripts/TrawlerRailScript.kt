package org.rsmod.content.minigames.fishingtrawler.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.minigames.fishingtrawler.TrawlerConstants
import org.rsmod.content.minigames.fishingtrawler.TrawlerManager
import org.rsmod.content.minigames.fishingtrawler.TrawlerPhase
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Rail repair replaces a shared loc; hull repairs instead change per-player varbits.
class TrawlerRailScript
@Inject
constructor(
    private val manager: TrawlerManager,
    private val poh: PohManager,
    private val locRepo: LocRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(TrawlerConstants.RAIL_BROKEN_OP) { fixRail(it.loc) }
    }

    private suspend fun ProtectedAccess.fixRail(loc: BoundLocInfo) {
        val voyage = manager.voyageForPlayer(player)
        val playerId = player.uuid
        if (voyage == null || playerId == null || voyage.phase != TrawlerPhase.SAILING) {
            mes("This isn't your trawler to fix.")
            return
        }
        if (!locRepo.findLoc(loc.coords, TrawlerConstants.RAIL_BROKEN_OP)) return
        if (!hasHammer()) {
            mes("You need a hammer to fix the broken rail.")
            return
        }
        faceLoc(loc)

        // TODO: verify rail-repair animation; human_pickupfloor is a silent placeholder.
        anim("seq.human_pickupfloor")
        delay(2)
        if (manager.voyageForPlayer(player) !== voyage ||
            voyage.phase != TrawlerPhase.SAILING || !hasHammer() ||
            !locRepo.findLoc(loc.coords, TrawlerConstants.RAIL_BROKEN_OP)) return

        locRepo.change(loc, TrawlerConstants.RAIL_FIXED, Int.MAX_VALUE)
        voyage.addContribution(playerId, TrawlerConstants.POINTS_RAIL_FIX)
        mes("You fix the broken rail.")
        if (poh.hasHouse(player)) {
            statAdvance("stat.construction", TrawlerConstants.CONSTRUCTION_XP_RAIL_FIX)
        }
    }

    private fun ProtectedAccess.hasHammer(): Boolean =
        listOf("obj.hammer", "obj.imcando_hammer", "obj.imcando_hammer_offhand")
            .any { it in inv || it in worn }
}
