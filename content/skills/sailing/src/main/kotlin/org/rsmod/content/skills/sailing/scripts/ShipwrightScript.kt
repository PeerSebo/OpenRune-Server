package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseSailingLvl
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc5
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.skills.sailing.BoatOwnershipService
import org.rsmod.content.skills.sailing.SAILING_COINS
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class ShipwrightScript @Inject constructor(private val ownership: BoatOwnershipService) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (jim in listOf("npc.junior_jim", "npc.junior_jim_4ops")) {
            onOpNpc3(jim) { startDialogue(it.npc) { buyBoat(SailingDocks.THE_PANDEMONIUM) } }
            onOpNpc5(jim) { startDialogue(it.npc) { recoverBoat(SailingDocks.THE_PANDEMONIUM) } }
        }
        val shipwrights = PORTS.map { port -> port to requireDock(port) }
        for ((port, dock) in shipwrights) {
            val npc = "npc.sailing_shipwright_$port"
            // Use approach reach across the counter; routing to adjacency cannot reach the shipwright.
            onApNpc1(npc) {
                if (isWithinApRange(it.npc, distance = SHIPWRIGHT_COUNTER_RANGE)) {
                    startDialogue(it.npc) { shipwrightMenu(dock) }
                }
            }
            onApNpc3(npc) {
                if (isWithinApRange(it.npc, distance = SHIPWRIGHT_COUNTER_RANGE)) {
                    startDialogue(it.npc) { buyBoat(dock) }
                }
            }
            onApNpc5(checkNotNull(ServerCacheManager.getNpc(npc.asRSCM()))) {
                if (isWithinApRange(it.npc, distance = SHIPWRIGHT_COUNTER_RANGE)) {
                    startDialogue(it.npc) { recoverBoat(dock) }
                }
            }
            onOpNpc1(npc) { startDialogue(it.npc) { shipwrightMenu(dock) } }
            onOpNpc3(npc) { startDialogue(it.npc) { buyBoat(dock) } }
            onOpNpc5(npc) { startDialogue(it.npc) { recoverBoat(dock) } }
        }

        for (seb in SEB_NPCS) {
            onOpNpc1(seb) { startDialogue(it.npc) { sebMenu() } }
            onOpNpc3(seb) { startDialogue(it.npc) { sebTrade() } }
            onOpNpc4(seb) { startDialogue(it.npc) { repairBoat() } }
        }
    }

    private fun requireDock(port: String): SailingDocks.SailingDock {
        val dbrow = "dbrow.sailing_dock_$port"
        return requireNotNull(SailingDocks.byDbRow(dbrow)) {
            "Shipwright port '$port' has no matching dock '$dbrow' in SailingDocks.ALL."
        }
    }

    private suspend fun Dialogue.shipwrightMenu(dock: SailingDocks.SailingDock) {
        chatNpc(happy, "Welcome to ${dock.row.niceName}. What can I do for you?")
        val choice =
            choice4(
                "I'd like to buy a boat.",
                BUY,
                "I'd like my boat brought here.",
                RECOVER,
                "Can you customise my boat?",
                CUSTOMISE,
                "Never mind.",
                CANCEL,
            )
        when (choice) {
            BUY -> buyBoat(dock)
            RECOVER -> recoverBoat(dock)
            CUSTOMISE -> customiseBoat()
            else -> Unit
        }
    }

    private suspend fun Dialogue.buyBoat(dock: SailingDocks.SailingDock) {
        if (ownership.freeSlot(player) == null) {
            val next = ownership.nextSlotLevel(player)
            if (next == null) {
                chatNpc(neutral, ShipwrightMessages.MAX_BOATS)
            } else {
                chatNpc(neutral, ShipwrightMessages.needBaseLevelForSlot(next))
            }
            return
        }

        val available = ownership.purchasableBoats(player)
        if (available.isEmpty()) {
            val cheapest = SailingBoats.all.minOf { it.levelRequired }
            chatNpc(neutral, ShipwrightMessages.needBaseLevelForBoat(cheapest))
            return
        }

        val picked =
            pickPaged(ShipwrightMessages.PICK_BOAT_TITLE, available) {
                "${it.name} - ${SailingBoats.purchasePrice(it).formatAmount} coins"
            } ?: return

        val price = SailingBoats.purchasePrice(picked)
        val confirmed =
            choice2(
                "Yes, please.",
                true,
                "No, thanks.",
                false,
                title = "Buy ${picked.inlineName} for ${price.formatAmount} coins?",
            )
        if (!confirmed) {
            return
        }

        // Revalidate ownership and levels after the confirmation suspends.
        val slot = ownership.freeSlot(player)
        if (slot == null || player.baseSailingLvl < picked.levelRequired) {
            chatNpc(confused, ShipwrightMessages.CANNOT_SELL_NOW)
            return
        }

        if (access.invDel(access.inv, SAILING_COINS, price).failure) {
            chatNpc(neutral, ShipwrightMessages.NOT_ENOUGH_COINS)
            return
        }
        ownership.registerPurchase(player, slot, picked, dock)
        chatNpc(happy, "${picked.name} is yours. She's moored at ${dock.row.niceName}.")
    }

    private suspend fun Dialogue.customiseBoat() {
        chatNpc(neutral, ShipwrightMessages.CUSTOMISE_USE_OP)
    }

    private suspend fun Dialogue.recoverBoat(dock: SailingDocks.SailingDock) {
        val owned = ownership.ownedBoats(player)
        if (owned.isEmpty()) {
            chatNpc(neutral, ShipwrightMessages.NO_BOATS)
            return
        }
        val recoverable = owned.filter { it.dockId != dock.row.dockId || it.isSunk }
        if (recoverable.isEmpty()) {
            chatNpc(neutral, ShipwrightMessages.ALREADY_MOORED_HERE)
            return
        }

        val picked =
            pickPaged(ShipwrightMessages.PICK_RECOVER_TITLE, recoverable) { boat ->
                val fee = ownership.recoveryCost(player, boat.slot).formatAmount
                val where = boat.dock?.let { it.row.niceName } ?: ShipwrightMessages.LOST_AT_SEA
                "${boat.displayName} ($where) - $fee coins"
            } ?: return

        val quoted = ownership.recoveryCost(player, picked.slot)
        val confirmed =
            choice2(
                "Yes, please.",
                true,
                "No, thanks.",
                false,
                title = "Recover ${picked.inlineName} for ${quoted.formatAmount} coins?",
            )
        if (!confirmed) {
            return
        }

        // Recompute the fee from current fitted parts after selection.
        val current = ownership.owned(player, picked.slot)
        if (current == null || current.boat.typeId != picked.boat.typeId) {
            chatNpc(confused, ShipwrightMessages.BOAT_GONE)
            return
        }
        val fee = ownership.recoveryCost(player, picked.slot)
        if (fee > 0 && access.invDel(access.inv, SAILING_COINS, fee).failure) {
            chatNpc(neutral, ShipwrightMessages.NOT_ENOUGH_COINS)
            return
        }
        ownership.moveTo(player, picked.slot, dock)
        ownership.repairFully(player, picked.slot)
        chatNpc(happy, "${current.displayName} is waiting for you at ${dock.row.niceName}.")
    }

    private suspend fun Dialogue.sebMenu() {
        chatNpc(happy, "Welcome to the Shipyard. Ship taken a beating?")
        val choice =
            choice3(
                "Can you repair my boat?",
                REPAIR,
                "What do you sell?",
                TRADE,
                "Never mind.",
                CANCEL,
            )
        when (choice) {
            REPAIR -> repairBoat()
            TRADE -> sebTrade()
            else -> Unit
        }
    }

    // TODO(sailing-shop): pack the shipyard manager's stock before enabling Trade.
    private suspend fun Dialogue.sebTrade() {
        chatNpc(neutral, ShipwrightMessages.TRADE_TODO)
    }

    // TODO(sailing-bank-payment): allow repair fees to be paid from the bank.
    private suspend fun Dialogue.repairBoat() {
        val damaged = ownership.ownedBoats(player).filter { it.missingHp > 0 }
        if (damaged.isEmpty()) {
            chatNpc(neutral, ShipwrightMessages.NOTHING_TO_REPAIR)
            return
        }

        val picked =
            pickPaged(ShipwrightMessages.PICK_REPAIR_TITLE, damaged) { boat ->
                val cost = ownership.repairCost(player, boat.slot).formatAmount
                "${boat.displayName} (${boat.hp}/${boat.maxHp}) - $cost coins"
            } ?: return

        val quoted = ownership.repairCost(player, picked.slot)
        val confirmed =
            choice2(
                "Yes, please.",
                true,
                "No, thanks.",
                false,
                title = "Repair ${picked.inlineName} for ${quoted.formatAmount} coins?",
            )
        if (!confirmed) {
            return
        }

        val current = ownership.owned(player, picked.slot)
        if (current == null || current.missingHp <= 0) {
            chatNpc(confused, ShipwrightMessages.NOTHING_TO_REPAIR)
            return
        }
        val cost = ownership.repairCost(player, picked.slot)
        if (cost > 0 && access.invDel(access.inv, SAILING_COINS, cost).failure) {
            chatNpc(neutral, ShipwrightMessages.NOT_ENOUGH_COINS)
            return
        }
        ownership.repairFully(player, picked.slot)
        chatNpc(happy, "Good as new. Try to keep her that way.")
    }

    private suspend fun <T : Any> Dialogue.pickPaged(
        title: String,
        items: List<T>,
        label: (T) -> String,
    ): T? {
        var offset = 0
        while (true) {
            val page = items.drop(offset).take(PAGE_SIZE)
            if (page.isEmpty()) {
                return null
            }
            val more = offset + PAGE_SIZE < items.size
            val picked =
                choice5(
                    page.getOrNull(0)?.let(label) ?: SKIP,
                    page.getOrNull(0),
                    page.getOrNull(1)?.let(label) ?: SKIP,
                    page.getOrNull(1),
                    page.getOrNull(2)?.let(label) ?: SKIP,
                    page.getOrNull(2),
                    page.getOrNull(3)?.let(label) ?: SKIP,
                    page.getOrNull(3),
                    if (more) "More options..." else "Never mind.",
                    null,
                    title = title,
                )
            if (picked != null) {
                return picked
            }
            if (!more) {
                return null
            }
            offset += PAGE_SIZE
        }
    }

    private companion object {
        private val PORTS: List<String> =
            listOf(
                "port_sarim",
                "musa_point",
                "catherby",
                "brimhaven",
                "port_khazard",
                "lands_end",
                "port_piscarilius",
                "civitas_illa_fortis",
                "rellekka",
                "aldarin",
                "corsair_cove",
                "port_roberts",
                "red_rock",
                "deepfin_point",
                "prifddinas",
            )

        private val SEB_NPCS: List<String> =
            listOf("npc.sailing_shipyard_manager", "npc.sailing_shipyard_manager_vis")

        private const val PAGE_SIZE = 4

        private const val SKIP = "-"

        private const val BUY = 1
        private const val RECOVER = 2
        private const val CUSTOMISE = 3
        private const val REPAIR = 4
        private const val TRADE = 5
        private const val CANCEL = 6
    }
}

internal const val SHIPWRIGHT_COUNTER_RANGE: Int = 2

private object ShipwrightMessages {
    const val MAX_BOATS: String = "You already own as many boats as anyone's allowed."

    const val NOT_ENOUGH_COINS: String = "You don't have enough coins."

    const val CANNOT_SELL_NOW: String = "I'm sorry, I can't sell you a boat right now."

    const val NO_BOATS: String = "You don't own a boat yet."

    const val ALREADY_MOORED_HERE: String = "All your boats are already moored here."

    const val BOAT_GONE: String = "That boat isn't yours any more."

    const val LOST_AT_SEA: String = "lost at sea"

    const val NOTHING_TO_REPAIR: String = "There's nothing wrong with your boats."

    const val CUSTOMISE_USE_OP: String =
        "Not here - pick 'Customise-boat' and I'll send you along to the Shipyard."

    const val TRADE_TODO: String = "I've nothing to sell you at the moment."

    const val PICK_BOAT_TITLE: String = "Which boat would you like?"

    const val PICK_RECOVER_TITLE: String = "Which boat should I bring here?"

    const val PICK_REPAIR_TITLE: String = "Which boat should I repair?"

    fun needBaseLevelForSlot(level: Int): String =
        "You need a Sailing level of $level to own another boat."

    fun needBaseLevelForBoat(level: Int): String =
        "You need a Sailing level of $level before I'll sell you anything."
}
