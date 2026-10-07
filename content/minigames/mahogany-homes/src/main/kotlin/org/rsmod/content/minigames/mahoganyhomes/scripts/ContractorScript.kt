package org.rsmod.content.minigames.mahoganyhomes.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.poh.pohHouseLocation
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesHouse
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesManager
import org.rsmod.content.minigames.mahoganyhomes.rewards.MahoganyHomesShop
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Contractor ops depend on con_contract_discussed; bind the multinpc children for each state.
class ContractorScript
@Inject
constructor(
    private val shops: Shops,
    private val shopOps: ShopOperationMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        MahoganyHomesShop.registerCostOverride(shopOps)

        for (contractor in CONTRACTORS) {
            for (npc in contractor.npcSymbols) {
                onOpNpc1(npc) { talk(contractor) }
                onOpNpc3(npc) { contract(contractor) }
                onOpNpc4(npc) { lastTierContract(contractor) }
                if (contractor.hasRewardsShop) {
                    onOpNpc5(npc) { rewards() }
                }
            }
        }
    }

    private fun ProtectedAccess.talk(contractor: Contractor) {
        if (MahoganyHomesManager.hasActiveContract(player)) {
            mes("You already have an active Mahogany Homes contract - come back when it's done.")
            return
        }
        if (player.vars[DISCUSSED_VARBIT] == 0) {
            VarPlayerIntMapSetter.set(player, DISCUSSED_VARBIT, 1)
            mes(
                "I'm ${contractor.name}, a Mahogany Homes contractor. If you own a player-owned " +
                    "house, I can offer you Construction contracts for carpenter points.",
            )
            return
        }
        mes("Ask me for a Contract, or for my Last-tier contract to repeat your last tier.")
    }

    private fun ProtectedAccess.contract(contractor: Contractor) {
        if (MahoganyHomesManager.hasActiveContract(player)) {
            mes("You already have an active Mahogany Homes contract - come back when it's done.")
            return
        }
        if (player.pohHouseLocation == 0) {
            mes("You need to own a player-owned house before I can offer you a contract.")
            return
        }

        val house = MahoganyHomesManager.rollAndAssign(player, { n -> random.of(n) }, reusePreviousTier = false)
        if (house == null) {
            mes("I don't have any contracts you're eligible for right now.")
            return
        }
        val tier = MahoganyHomesManager.currentTier(player)
        mes(
            "Your contract: ${tier.name.lowercase().replaceFirstChar(Char::uppercase)} tier, at " +
                "${contractor.houseDescription(house)}.",
        )
    }

    private fun ProtectedAccess.lastTierContract(contractor: Contractor) {
        if (MahoganyHomesManager.hasActiveContract(player)) {
            mes("You already have an active Mahogany Homes contract - come back when it's done.")
            return
        }
        if (player.pohHouseLocation == 0) {
            mes("You need to own a player-owned house before I can offer you a contract.")
            return
        }

        val house = MahoganyHomesManager.rollAndAssign(player, { n -> random.of(n) }, reusePreviousTier = true)
        if (house == null) {
            mes("I don't have any contracts you're eligible for right now.")
            return
        }
        val tier = MahoganyHomesManager.currentTier(player)
        mes(
            "Same tier as last time: ${tier.name.lowercase().replaceFirstChar(Char::uppercase)}, at " +
                "${contractor.houseDescription(house)}.",
        )
    }

    private fun ProtectedAccess.rewards() {
        MahoganyHomesShop.open(shops, player)
    }

    private class Contractor(
        public val name: String,
        public val npcSymbols: List<String>,
        public val hasRewardsShop: Boolean,
    ) {
        fun houseDescription(house: MahoganyHomesHouse): String =
            "${house.name.lowercase().replaceFirstChar(Char::uppercase)}'s house"
    }

    private companion object {
        const val DISCUSSED_VARBIT = "varbit.con_contract_discussed"

        val CONTRACTORS =
            listOf(
                Contractor(
                    name = "Amy",
                    npcSymbols = listOf("npc.con_contractor", "npc.con_contractor_1op", "npc.con_contractor_3op"),
                    hasRewardsShop = true,
                ),
                Contractor(
                    name = "Marlo",
                    npcSymbols =
                        listOf(
                            "npc.con_contractor_varrock",
                            "npc.con_contractor_varrock_1op",
                            "npc.con_contractor_varrock_2op",
                        ),
                    hasRewardsShop = false,
                ),
                Contractor(
                    name = "Ellie",
                    npcSymbols =
                        listOf(
                            "npc.con_contractor_ardougne",
                            "npc.con_contractor_ardougne_1op",
                            "npc.con_contractor_ardougne_2op",
                        ),
                    hasRewardsShop = false,
                ),
                Contractor(
                    name = "Angelo",
                    npcSymbols =
                        listOf(
                            "npc.con_contractor_hosidius",
                            "npc.con_contractor_hosidius_1op",
                            "npc.con_contractor_hosidius_2op",
                        ),
                    hasRewardsShop = false,
                ),
            )
    }
}
