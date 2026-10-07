package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseAgilityLvl
import org.rsmod.api.player.stat.baseThievingLvl
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ConditionalShopkeeperScript @Inject constructor(private val shops: Shops) : PluginScript() {
    private val martinStock = mutableMapOf<Pair<String, Boolean>, Inventory>()

    override fun ScriptContext.startup() {
        val martin = "npc.roguesden_trader".asRSCM()
        onProtectedEvent<NpcUnimplementedEvents.Op1>(martin) { browseMartin(it.npc) }
        onProtectedEvent<NpcUnimplementedEvents.Op3>(martin) { openMartin() }

        val noterazzo = "npc.noterazzo".asRSCM()
        onProtectedEvent<NpcUnimplementedEvents.Op1>(noterazzo) {
            startDialogue(it.npc) {
                chatNpc(happy, "Would you like to see what I have for sale?")
                if (choice2("Yes please.", true, "No thanks.", false)) {
                    shops.openConfigured(player, it.npc, "inv.taxfree")
                }
            }
        }
        onProtectedEvent<NpcUnimplementedEvents.Op3>(noterazzo) {
            shops.openConfigured(player, it.npc, "inv.taxfree")
        }
    }

    private suspend fun ProtectedAccess.browseMartin(npc: Npc) {
        if (!canTradeWithMartin()) return
        startDialogue(npc) {
            chatNpc(happy, "Would you like to browse my lost and found?")
            if (choice2("Yes please.", true, "No thanks.", false)) openMartin()
        }
    }

    private fun ProtectedAccess.canTradeWithMartin(): Boolean {
        if (player.baseThievingLvl >= 99 ||
            player.baseThievingLvl >= 50 && player.baseAgilityLvl >= 50) return true
        mes("You need level 50 Agility and Thieving, or level 99 Thieving, to trade with Martin.")
        return false
    }

    private fun ProtectedAccess.openMartin() {
        if (!canTradeWithMartin()) return
        val inventory = when {
            player.baseThievingLvl < 99 -> "inv.roguesden_shop"
            player.hasAnotherMasteredSkill() -> "inv.roguesden_shop_skillcape_trimmed"
            else -> "inv.roguesden_shop_skillcape"
        }
        val stock = martinStock.getOrPut(inventory to player.isAnyIronman) {
            Inventory.create(inventory)
        }
        val unsellable = setOf(
            "obj.roguesden_stethoscope", "obj.skillcape_thieving",
            "obj.skillcape_thieving_trimmed", "obj.skillcape_thieving_hood",
        ).mapTo(mutableSetOf()) { it.asRSCM() }
        val hood = listOf(InvObj("obj.skillcape_thieving_hood"))
        shops.open(
            player = player,
            title = stock.type.name,
            shopInv = stock,
            sideInv = inv,
            buyPercentage = stock.type.buyMultiplier / 10.0,
            sellPercentage = stock.type.sellMultiplier / 10.0,
            changePercentage = stock.type.delta / 10.0,
            subtext = Shops.DEFAULT_SUBTEXT,
            unsellableItems = unsellable,
            purchaseExtras = mapOf(
                "obj.skillcape_thieving".asRSCM() to hood,
                "obj.skillcape_thieving_trimmed".asRSCM() to hood,
            ),
        )
    }

    private fun Player.hasAnotherMasteredSkill(): Boolean =
        ServerCacheManager.getStats().values.any {
            val skill = RSCM.getReverseMapping(RSCMType.STAT, it.id)
            skill != "stat.thieving" && statBase(skill) >= 99
        }
}
