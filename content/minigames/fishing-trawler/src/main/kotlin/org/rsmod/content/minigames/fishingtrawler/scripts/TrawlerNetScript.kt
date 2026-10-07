package org.rsmod.content.minigames.fishingtrawler.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseFishingLvl
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.minigames.fishingtrawler.TrawlerCatchTable
import org.rsmod.content.minigames.fishingtrawler.TrawlerConstants
import org.rsmod.content.minigames.fishingtrawler.TrawlerManager
import org.rsmod.content.minigames.fishingtrawler.TrawlerPhase
import org.rsmod.content.minigames.fishingtrawler.TrawlerRewardData
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Nets expose Inspect only; repairs use rope-on-loc. Reward claim shares this op.
class TrawlerNetScript
@Inject
constructor(
    private val manager: TrawlerManager,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        TrawlerRewardData.load()
        onOpLocU(TrawlerConstants.NET_LEFT, TrawlerConstants.ROPE_ITEM) { repairNet(it.vis) }
        onOpLocU(TrawlerConstants.NET_RIGHT, TrawlerConstants.ROPE_ITEM) { repairNet(it.vis) }
        onOpLoc1(TrawlerConstants.NET_REWARD) { collectReward() }
    }

    private suspend fun ProtectedAccess.repairNet(loc: BoundLocInfo) {
        val voyage = manager.voyageForPlayer(player)
        val playerId = player.uuid
        if (voyage == null || playerId == null || voyage.phase != TrawlerPhase.SAILING) {
            mes("This isn't your trawler to fix.")
            return
        }
        if (TrawlerConstants.ROPE_ITEM !in inv) return
        faceLoc(loc)

        // TODO: verify net-repair animation; human_pickupfloor is a silent placeholder.
        anim("seq.human_pickupfloor")
        delay(2)
        if (manager.voyageForPlayer(player) !== voyage ||
            voyage.phase != TrawlerPhase.SAILING || TrawlerConstants.ROPE_ITEM !in inv) return

        // Net-repair success endpoints are provisional.
        val rate =
            SkillingSuccessRate.successRate(
                low = NET_REPAIR_LOW,
                high = NET_REPAIR_HIGH,
                level = player.craftingLvl,
                maxLevel = TrawlerCatchTable.MAX_LEVEL,
            )
        if (random.randomDouble() >= rate) {
            mes("You fail to repair the net.")
            return
        }

        val delTx = invDel(inv, TrawlerConstants.ROPE_ITEM, count = 1)
        if (!delTx.success) {
            return
        }
        voyage.addContribution(playerId, TrawlerConstants.POINTS_NET_REPAIR)
        mes("You repair the net with the rope.")
        statAdvance("stat.crafting", TrawlerConstants.CRAFTING_XP_NET_REPAIR)
    }

    private suspend fun ProtectedAccess.collectReward() {
        val voyage = manager.voyageForPlayer(player)
        val playerId = player.uuid
        if (voyage == null || playerId == null) {
            mes("There's no trawler catch to collect here.")
            return
        }
        val pending = voyage.pendingFishReward[playerId] ?: 0
        if (pending <= 0) {
            mes("You check the net, but there's nothing left here for you.")
            return
        }
        if (player.baseFishingLvl < TrawlerConstants.MIN_FISHING_LEVEL_FOR_REWARD) {
            mes(
                "You need a Fishing level of ${TrawlerConstants.MIN_FISHING_LEVEL_FOR_REWARD} " +
                    "to claim a reward from the trawler.",
            )
            return
        }
        val contribution = voyage.contribution[playerId] ?: 0
        if (contribution < TrawlerConstants.MIN_CONTRIBUTION_TO_CLAIM) {
            mes("You didn't contribute enough to the voyage to earn a share of the catch.")
            return
        }

        voyage.pendingFishReward[playerId] = 0
        var junk = 0
        repeat(pending) {
            val fish = rollFish()
            if (fish != null) {
                invAddOrDrop(objRepo, fish.item, count = 1)
                statAdvance("stat.fishing", fish.xpTenths / 10.0)
            } else {
                junk++
                statAdvance("stat.fishing", TrawlerCatchTable.JUNK_XP_TENTHS / 10.0)
            }
        }
        mes("You haul in your share of the catch - $pending fish!")
        if (junk > 0) {
            spam("Some of the haul was just junk.")
        }

        rollAnglerPiece()
        rollHeronPet(contribution)
    }

    private fun ProtectedAccess.rollFish(): TrawlerCatchTable.Fish? {
        val level = player.baseFishingLvl
        for (fish in TrawlerCatchTable.CASCADE) {
            if (level < fish.reqLevel) {
                continue
            }
            val rate =
                SkillingSuccessRate.successRate(
                    TrawlerCatchTable.SUCCESS_LOW,
                    TrawlerCatchTable.SUCCESS_HIGH,
                    level,
                    TrawlerCatchTable.MAX_LEVEL,
                )
            if (random.randomDouble() < rate) {
                return fish
            }
        }
        return null
    }

    private fun ProtectedAccess.rollAnglerPiece() {
        if (random.of(TrawlerConstants.ANGLER_PIECE_CHANCE) != 0) {
            return
        }
        val candidates = TrawlerRewardData.anglerPieces.filterNot { it in inv || it in worn }
        val piece = random.pickOrNull(candidates) ?: return
        invAddOrDrop(objRepo, piece, count = 1)
        mes("<col=ff0000>You find a piece of the angler's outfit in the net!</col>")
    }

    private fun ProtectedAccess.rollHeronPet(contribution: Int) {
        val span = TrawlerConstants.MAX_CONTRIBUTION - TrawlerConstants.MIN_CONTRIBUTION_TO_CLAIM
        val progress = (contribution - TrawlerConstants.MIN_CONTRIBUTION_TO_CLAIM).coerceIn(0, span)
        val chanceRange = TrawlerConstants.HERON_CHANCE_MIN_CONTRIB - TrawlerConstants.HERON_CHANCE_FULL_METER
        val chance =
            (TrawlerConstants.HERON_CHANCE_MIN_CONTRIB - (progress * chanceRange / span))
                .coerceIn(TrawlerConstants.HERON_CHANCE_FULL_METER, TrawlerConstants.HERON_CHANCE_MIN_CONTRIB)
        if (random.of(chance) != 0) {
            return
        }
        if (invAddOrDrop(objRepo, HERON_PET_OBJ, count = 1)) {
            spam("You have a funny feeling like you're being followed.")
        } else {
            spam("You feel something weird sneaking into your backpack.")
        }
    }

    private companion object {
        const val NET_REPAIR_LOW = 100
        const val NET_REPAIR_HIGH = 220
        const val HERON_PET_OBJ = "obj.skillpetfish"
    }
}
