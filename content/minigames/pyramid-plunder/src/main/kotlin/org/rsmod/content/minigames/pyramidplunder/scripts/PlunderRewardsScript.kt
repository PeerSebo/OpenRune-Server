package org.rsmod.content.minigames.pyramidplunder.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.drops.PlunderDropTables
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlunderRewardsScript @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        val templeton =
            ServerCacheManager.getNpc(PlunderLocs.SIMON_TEMPLETON.asRSCM())
                ?: error("Simon Templeton (npc ${PlunderLocs.SIMON_TEMPLETON}) not found in cache")

        onOpNpc1(templeton) { startDialogue(it.npc) { simonTalk() } }

        PlunderDropTables.ARTEFACTS.forEachIndexed { index, artefact ->
            val price = PRICES[index]
            val obj =
                ServerCacheManager.getItem(artefact.asRSCM(RSCMType.OBJ))
                    ?: error("Unresolved Pyramid Plunder artefact obj: $artefact")
            onOpNpcU(templeton, obj) { sellArtefact(artefact, price) }
        }
    }

    private suspend fun Dialogue.simonTalk() {
        if (access.sellableArtefactCount() <= 0) {
            chatNpc(happy, "G'day, mate. Got any new pyramid artefacts for me?")
            chatPlayer(neutral, "No, I haven't.")
            chatNpc(neutral, "Well, keep my offer in mind if you do find one.")
            return
        }

        chatPlayer(neutral, "I have some interesting artefacts I'd like you to look at.")
        chatNpc(happy, "Bonzer! Let's have a Butcher's mate.")
        chatNpc(happy, "Do you want to flog the lot of 'em?")
        val sell =
            choice2(
                "Yes, show me the money.",
                SELL_ALL,
                "No, let me think about it.",
                DECLINE,
                title = "Select an Option",
            )
        if (sell != SELL_ALL) {
            chatPlayer(neutral, "No, let me think about it.")
            chatNpc(
                neutral,
                "No probs mate. Just give me the items you want to sell and we can do them " +
                    "one by one.",
            )
            return
        }

        chatPlayer(neutral, "Yes, show me the money.")
        val total = access.sellAllArtefacts()
        if (total <= 0) {
            return
        }
        access.mes("Simon Templeton pays you $total coins for your artefacts.")
        chatNpc(happy, "Fair Dinkum mate, Bonzer doing business with you mate.")
    }

    private fun ProtectedAccess.sellableArtefactCount(): Int =
        BULK_SALES.sumOf { (artefact, _) -> inv.count(artefact) }

    private fun ProtectedAccess.sellAllArtefacts(): Int {
        var total = 0
        for ((artefact, price) in BULK_SALES) {
            val have = inv.count(artefact)
            if (have <= 0) {
                continue
            }
            val removed = invDel(inv, artefact, have).completed()
            if (removed <= 0) {
                continue
            }
            total += removed * price
        }
        if (total > 0) {
            invAddOrDrop(objRepo, "obj.coins", total)
        }
        return total
    }

    private fun ProtectedAccess.sellArtefact(artefact: String, price: Int) {
        val deleted = invDel(inv, artefact, 1)
        if (!deleted.success) {
            return
        }
        invAddOrDrop(objRepo, "obj.coins", price)
        mes("Simon Templeton pays you $price coins for the artefact.")
    }

    private companion object {
        // Keep index order aligned with PlunderDropTables.ARTEFACTS.
        val PRICES = intArrayOf(50, 75, 100, 150, 175, 200, 750, 1000, 1250)

        // One Talk-to binding owns both sale categories.
        val BULK_SALES = PlunderDropTables.ARTEFACTS.zip(PRICES.toList()) +
            ("obj.agility_pyramid_gold_pyramid" to 10_000)

        private const val SELL_ALL = 1
        private const val DECLINE = 2
    }
}
