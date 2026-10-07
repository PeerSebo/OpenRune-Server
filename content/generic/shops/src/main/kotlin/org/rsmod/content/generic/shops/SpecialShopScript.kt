package org.rsmod.content.generic.shops

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SpecialShopScript @Inject constructor(
    private val shops: Shops,
    private val operations: ShopOperationMap,
) : PluginScript() {
    private val grace get() = RewardShopData.byKey.getValue("grace")
    private val belona get() = RewardShopData.byKey.getValue("belona")
    private val alry get() = RewardShopData.byKey.getValue("alry")
    private val alryTrained get() = RewardShopData.byKey.getValue("alry_trained")
    private val stock by lazy { RewardShopData.shops.associate { it.key to it.createStock() } }

    override fun ScriptContext.startup() {
        for (stock in listOf(grace, belona, alryTrained)) {
            val prices = stock.items.associate { it.item.asRSCM() to it.price }
            operations.costOf(stock.currency) { prices[it.id] ?: error("Unexpected reward item ${it.id}") }
        }
        bind("npc.rooftops_grace") { grace }
        bind("npc.mguild_rewardseller") { belona }
        bind("npc.fishing_npc_angler") {
            if (vars["varbit.brut_fishing_r"] >= 1) alryTrained else alry
        }
    }

    private fun ScriptContext.bind(merchant: String, stock: ProtectedAccess.() -> RewardShopSpec) {
        onProtectedEvent<NpcUnimplementedEvents.Op3>(merchant.asRSCM()) { open(stock()) }
        onProtectedEvent<NpcUnimplementedEvents.Op1>(merchant.asRSCM()) { browse(it.npc, stock()) }
    }

    private suspend fun ProtectedAccess.browse(npc: Npc, stock: RewardShopSpec) {
        startDialogue(npc) {
            chatNpc(happy, "Would you like to see what I have for sale?")
            if (choice2("Yes please.", true, "No thanks.", false)) open(stock)
        }
    }

    private fun ProtectedAccess.open(stock: RewardShopSpec) {
        shops.open(
            player = player,
            title = stock.title,
            shopInv = this@SpecialShopScript.stock.getValue(stock.key).inventory,
            sideInv = inv,
            currency = stock.currency,
            buyPercentage = stock.buyMultiplier / 10.0,
            sellPercentage = stock.sellMultiplier / 10.0,
            changePercentage = stock.delta / 10.0,
            subtext = Shops.DEFAULT_SUBTEXT,
            allowSelling = stock.allowSelling,
            sellableItems = if (stock.allowSelling) stock.items.mapTo(mutableSetOf()) { it.item.asRSCM() } else emptySet(),
        )
    }
}
