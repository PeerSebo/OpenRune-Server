package org.rsmod.content.other.grandexchange

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.ironman.isSoloIronman
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc4
import org.rsmod.api.script.onApNpc5
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onOpContentLoc3
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GrandExchangeScript @Inject constructor(private val service: GeService) : PluginScript() {
    private val counterRange = 2

    override fun ScriptContext.startup() {
        service.load()
        onEvent<GameLifecycle.EndCycle> { service.tick() }
        onEvent<GameLifecycle.Shutdown> { service.save() }
        onPlayerLogin { service.onLogin(player) }
        onPlayerLogout { service.save() }

        onOpLoc1(GeConfig.LOC_BOOTH_EXCHANGE) { service.open(this) }
        onOpLoc3(GeConfig.LOC_BOOTH_EXCHANGE) { openCollectionBox() }
        onOpLoc2(GeConfig.LOC_BOOTH_BANK) { tryOpenBank() }
        onOpLoc3(GeConfig.LOC_BOOTH_BANK) { openCollectionBox() }
        onOpContentLoc3("content.bank_booth") { openCollectionBox() }
        for (clerk in GeConfig.CLERKS) {
            onApNpc1(clerk) {
                if (isWithinApRange(it.npc, counterRange)) talkToClerk(it.npc)
            }
            onApNpc3(clerk) {
                if (isWithinApRange(it.npc, counterRange)) service.open(this)
            }
            onApNpc4(clerk) {
                if (isWithinApRange(it.npc, counterRange)) service.openHistory(this)
            }
            onApNpc5(checkNotNull(ServerCacheManager.getNpc(clerk.asRSCM()))) {
                if (isWithinApRange(it.npc, counterRange)) mes("Item sets are not available on this world.")
            }
            onOpNpc1(clerk) { talkToClerk(it.npc) }
            onOpNpc3(clerk) { service.open(this) }
            onOpNpc4(clerk) { service.openHistory(this) }
            onOpNpc5(clerk) { mes("Item sets are not available on this world.") }
        }
        for ((npc, goods) in GeConfig.EXPERTS) {
            onOpNpc1(npc) { talkToExpert(it.npc, goods) }
            onOpNpc3(npc) { checkPrice(it.npc) }
        }

        onIfOpen(GeConfig.OFFERS) { service.onOffersOpened(player) }
        onIfClose(GeConfig.OFFERS) { service.onOffersClosed(player) }
        onIfOpen(GeConfig.COLLECT) { service.onCollectionBoxOpened(player) }
        onIfClose(GeConfig.COLLECT) { service.onCollectionBoxClosed(player) }

        for (slot in 0 until GeConfig.SLOT_COUNT) {
            onIfModalButton(GeConfig.indexSlot(slot)) {
                when (it.comsub) {
                    GeConfig.INDEX_CHILD_VIEW -> service.viewSlot(this, slot)
                    GeConfig.INDEX_CHILD_BUY -> service.startBuy(this, slot)
                    GeConfig.INDEX_CHILD_SELL -> service.startSell(this, slot)
                }
            }
        }
        onIfModalButton(GeConfig.COMP_BACK) { service.back(player) }
        onIfModalButton(GeConfig.COMP_HISTORY) { service.openHistory(this) }
        onIfModalButton(GeConfig.COMP_SIDE_ITEMS) {
            if (it.op == IfButtonOp.Op10) {
                service.examineInventory(player, it.comsub)
            } else {
                service.offerFromInventory(this, it.comsub)
            }
        }

        onIfModalButton(GeConfig.COMP_SETUP) { setupButton(it.comsub, it.op) }
        onIfModalButton(GeConfig.COMP_SETUP_CONFIRM) { service.confirm(this) }

        onIfModalButton(GeConfig.COMP_DETAILS_STATUS) {
            when (it.comsub) {
                GeConfig.DETAILS_ABORT -> service.abort(player)
                GeConfig.DETAILS_MODIFY -> service.modify(this)
            }
        }
        onIfModalButton(GeConfig.COMP_DETAILS_COLLECT) {
            val box =
                GeConfig.collectBoxOf(
                    it.comsub,
                    GeConfig.DETAILS_COLLECT_ITEMS_CHILD,
                    GeConfig.DETAILS_COLLECT_COINS_CHILD,
                ) ?: return@onIfModalButton
            if (it.op == IfButtonOp.Op10) {
                service.examineBox(player, slot = null, box = box)
            } else {
                service.collectSelected(player, box, collectMode(it.op))
            }
        }

        onIfModalButton(GeConfig.COMP_COLLECT_ALL) {
            when (it.comsub) {
                GeConfig.COLLECT_ALL_BUTTON -> service.collectAll(player, toBank = it.op == IfButtonOp.Op2)
                GeConfig.REPEAT_OFFER_BUTTON -> service.repeatOffer(this)
            }
        }

        for (slot in 0 until GeConfig.SLOT_COUNT) {
            onIfModalButton(GeConfig.collectSlot(slot)) {
                val box =
                    GeConfig.collectBoxOf(
                        it.comsub,
                        GeConfig.COLLECT_SLOT_ITEMS_CHILD,
                        GeConfig.COLLECT_SLOT_COINS_CHILD,
                    ) ?: return@onIfModalButton
                if (it.op == IfButtonOp.Op10) {
                    service.examineBox(player, slot, box)
                } else {
                    service.collectSlot(player, slot, box, collectMode(it.op))
                }
            }
        }
        onIfModalButton(GeConfig.COMP_COLLECT_INV) { service.collectAll(player, toBank = false) }
        onIfModalButton(GeConfig.COMP_COLLECT_BANK) { service.collectAll(player, toBank = true) }

        onIfModalButton(GeConfig.COMP_HISTORY_EXCHANGE) { service.open(this) }
        onIfModalButton(GeConfig.COMP_HISTORY_LIST) {
            val trade = service.historyTrade(player, it.comsub) ?: return@onIfModalButton
            if (it.op == IfButtonOp.Op10) {
                service.examineBox(player, slot = null, box = GeConfig.COLLECT_BOX_ITEMS)
            } else {
                service.buyAgain(this, trade)
            }
        }
    }

    private suspend fun ProtectedAccess.setupButton(comsub: Int, op: IfButtonOp) {
        when (comsub) {
            GeConfig.SETUP_CHOOSE_ITEM -> service.chooseItem(this)
            GeConfig.SETUP_QTY_MINUS_1 -> service.adjustQuantity(player, -1)
            GeConfig.SETUP_QTY_PLUS_1_MOBILE,
            GeConfig.SETUP_QTY_PLUS_1 -> service.adjustQuantity(player, 1)
            GeConfig.SETUP_QTY_PLUS_10 -> service.adjustQuantity(player, 10)
            GeConfig.SETUP_QTY_PLUS_100 -> service.adjustQuantity(player, 100)
            GeConfig.SETUP_QTY_PLUS_1K_OR_ALL -> service.setAllOrThousand(player)
            GeConfig.SETUP_QTY_ENTER -> service.enterQuantity(this)
            GeConfig.SETUP_PRICE_MINUS_1 -> service.adjustPrice(player, -1)
            GeConfig.SETUP_PRICE_PLUS_1 -> service.adjustPrice(player, 1)
            GeConfig.SETUP_PRICE_MINUS_5PCT -> service.adjustPricePercent(player, -5)
            GeConfig.SETUP_PRICE_GUIDE -> service.guidePrice(player)
            GeConfig.SETUP_PRICE_ENTER -> service.enterPrice(this)
            GeConfig.SETUP_PRICE_PLUS_5PCT -> service.adjustPricePercent(player, 5)
            GeConfig.SETUP_PRICE_MINUS_CUSTOM ->
                if (op == IfButtonOp.Op2) service.enterCustomPercent(this)
                else service.adjustPricePercent(player, -service.customPercent(player))
            GeConfig.SETUP_PRICE_PLUS_CUSTOM ->
                if (op == IfButtonOp.Op2) service.enterCustomPercent(this)
                else service.adjustPricePercent(player, service.customPercent(player))
        }
    }

    internal fun collectMode(op: IfButtonOp): GeCollectionMode =
        when (op) {
            IfButtonOp.Op1 -> GeCollectionMode.Default
            IfButtonOp.Op2 -> GeCollectionMode.Item
            IfButtonOp.Op3 -> GeCollectionMode.Bank
            else -> GeCollectionMode.Default
        }

    private fun ProtectedAccess.openCollectionBox() {
        ifOpenMainModal(GeConfig.COLLECT)
    }

    private suspend fun ProtectedAccess.talkToClerk(npc: Npc) {
        startDialogue(npc, faceFar = true) {
            chatNpc(happy, "Welcome to the Grand Exchange! Would you like to see your offers?")
            if (player.isSoloIronman) {
                chatNpc(neutral, "As an Ironman you can only use the Exchange to buy bonds, mind you.")
            }
            val choice =
                choice3(
                    "Yes, show me my offers.",
                    ClerkChoice.Offers,
                    "Show me my trade history.",
                    ClerkChoice.History,
                    "No thanks.",
                    ClerkChoice.Nothing,
                )
            when (choice) {
                ClerkChoice.Offers -> {
                    chatPlayer(happy, "Yes, show me my offers.")
                    service.open(access)
                }
                ClerkChoice.History -> {
                    chatPlayer(neutral, "Show me my trade history.")
                    service.openHistory(access)
                }
                ClerkChoice.Nothing -> {
                    chatPlayer(neutral, "No thanks.")
                    chatNpc(happy, "Come back any time!")
                }
            }
        }
    }

    private enum class ClerkChoice {
        Offers,
        History,
        Nothing,
    }

    private suspend fun ProtectedAccess.talkToExpert(npc: Npc, goods: String) {
        startDialogue(npc, faceFar = true) {
            chatNpc(happy, "Hello there. I keep an eye on the going rate for $goods here at the Grand Exchange.")
            val check = choice2("Can you check a price for me?", true, "No thanks.", false)
            if (check) {
                chatPlayer(quiz, "Can you check a price for me?")
                priceCheck()
            } else {
                chatPlayer(neutral, "No thanks.")
            }
        }
    }

    private suspend fun ProtectedAccess.checkPrice(npc: Npc) {
        startDialogue(npc, faceFar = true) { priceCheck() }
    }

    private suspend fun Dialogue.priceCheck() {
        val item = access.objDialog("Which item would you like a guide price for?", stockMarketRestriction = true)
        chatNpc(neutral, service.guidePriceText(item))
    }
}
