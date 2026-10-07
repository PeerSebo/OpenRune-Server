package org.rsmod.content.minigames.fishingtrawler.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.minigames.fishingtrawler.TrawlerCatchTable
import org.rsmod.content.minigames.fishingtrawler.TrawlerConstants
import org.rsmod.content.minigames.fishingtrawler.TrawlerManager
import org.rsmod.content.minigames.fishingtrawler.TrawlerPhase
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TrawlerTentacleScript
@Inject
constructor(private val manager: TrawlerManager) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(TrawlerConstants.TENTACLE_NPC) { chop(it.npc) }
    }

    private suspend fun ProtectedAccess.chop(npc: Npc) {
        val voyage = manager.voyageForPlayer(player)
        val playerId = player.uuid
        if (voyage == null || playerId == null || voyage.phase != TrawlerPhase.SAILING ||
            voyage.tentacleNpc !== npc) {
            return
        }
        val axe = findAxe()
        if (axe == null) {
            mes("You need an axe to chop the tentacle.")
            return
        }

        val tier = tierOf(axe)
        faceEntitySquare(npc)
        // Sequence 8954 has baked skeletal audio; do not send a duplicate synth.
        anim(tier.anim)
        delay(2)

        if (manager.voyageForPlayer(player) !== voyage ||
            voyage.phase != TrawlerPhase.SAILING || voyage.tentacleNpc !== npc) {
            return
        }

        // Tentacle success endpoints are provisional; only axe/level dependence is established.
        val low = (40 + tier.rank * 6).coerceAtMost(200)
        val high = (140 + tier.rank * 9).coerceAtMost(250)
        val rate =
            SkillingSuccessRate.successRate(low, high, player.woodcuttingLvl, TrawlerCatchTable.MAX_LEVEL)
        if (random.randomDouble() >= rate) {
            mes("You swing at the tentacle, but fail to land a solid hit.")
            return
        }

        voyage.addContribution(playerId, TrawlerConstants.POINTS_TENTACLE_CHOP)
        statAdvance("stat.woodcutting", TrawlerConstants.WOODCUTTING_XP_TENTACLE_CHOP)
        mes("You hack at the tentacle and it recoils back into the sea!")
        manager.despawnTentacle(voyage)
    }

    private fun ProtectedAccess.findAxe(): InvObj? {
        val worn = player.righthand?.takeIf { getInvObj(it).isContentType(AXE_CONTENT) }
        if (worn != null) {
            return worn
        }
        return inv.filterNotNull { getInvObj(it).isContentType(AXE_CONTENT) }.firstOrNull()
    }

    private fun tierOf(axe: InvObj): AxeTier {
        val type = getInvObj(axe)
        return AXE_TIERS[type.internalName] ?: AxeTier("seq.human_trawler_hack_axe_bronze", 0)
    }

    private data class AxeTier(val anim: String, val rank: Int)

    private companion object {
        const val AXE_CONTENT = "content.woodcutting_axe"

        val AXE_TIERS =
            mapOf(
                "obj.bronze_axe" to AxeTier("seq.human_trawler_hack_axe_bronze", 0),
                "obj.iron_axe" to AxeTier("seq.human_trawler_hack_axe_iron", 1),
                "obj.steel_axe" to AxeTier("seq.human_trawler_hack_axe_steel", 2),
                "obj.black_axe" to AxeTier("seq.human_trawler_hack_axe_black", 3),
                "obj.mithril_axe" to AxeTier("seq.human_trawler_hack_axe_mithril", 4),
                "obj.adamant_axe" to AxeTier("seq.human_trawler_hack_axe_adamant", 5),
                "obj.rune_axe" to AxeTier("seq.human_trawler_hack_axe_rune", 6),
                "obj.gilded_axe" to AxeTier("seq.human_trawler_hack_axe_gilded", 7),
                "obj.dragon_axe" to AxeTier("seq.human_trawler_hack_axe_dragon", 8),
                "obj.dragon_axe_or" to AxeTier("seq.human_trawler_hack_axe_dragon", 8),
                "obj.crystal_axe" to AxeTier("seq.human_trawler_hack_axe_crystal", 9),
                "obj.infernal_axe" to AxeTier("seq.human_trawler_hack_axe_infernal", 10),
            )
    }
}
