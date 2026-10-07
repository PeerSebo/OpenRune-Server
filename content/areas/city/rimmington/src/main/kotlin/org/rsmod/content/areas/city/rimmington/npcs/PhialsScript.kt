package org.rsmod.content.areas.city.rimmington.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import kotlin.math.min
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PhialsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(PHIALS) { startDialogue(it.npc) { phials() } }
        onOpNpcU(PHIALS) { startDialogue(it.npc) { exchange(it.invSlot, it.objType) } }
    }

    private suspend fun Dialogue.phials() {
        chatNpc(
            quiz,
            "Hello. Do you wish me to exchange banknotes for you? I charge only $FEE coins for " +
                "each banknote.",
        )
        when (choice3("Yes please.", 1, "Who are you?", 2, "No thanks.", 3)) {
            1 -> askForNotes()
            2 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(
                    neutral,
                    "I am Phials. I can exchange banknotes for real items. Do you want any " +
                        "banknotes converted?",
                )
                if (choice2("Yes please.", true, "No thanks.", false)) {
                    askForNotes()
                } else {
                    chatPlayer(bored, "No thanks.")
                }
            }
            else -> chatPlayer(bored, "No thanks.")
        }
    }

    private suspend fun Dialogue.askForNotes() {
        chatPlayer(happy, "Yes please.")
        chatNpc(happy, "Hand me the banknotes you wish me to exchange.")
    }

    private suspend fun Dialogue.exchange(invSlot: Int, noteType: ItemServerType) {
        if (!noteType.isCert) {
            chatNpc(neutral, "Sorry, I can only exchange banknotes.")
            return
        }
        val held = access.inv[invSlot]?.takeIf { it.isType(noteType) }?.count ?: return
        val name = access.ocUncert(noteType).name
        val choice =
            choice5(
                "Exchange 1: $FEE coins.",
                1,
                "Exchange 5: ${5 * FEE} coins.",
                5,
                "Exchange All: ${held.toLong() * FEE} coins.",
                held,
                "Exchange X.",
                -1,
                "Cancel.",
                0,
                title = "Exchange $name?",
            )
        val requested = if (choice == -1) access.countDialog() else choice
        if (requested <= 0) {
            return
        }
        val item = access.exchangeNotes(invSlot, noteType, min(requested, held)) ?: return
        objbox(item, zoom = 400, "Phials converts your banknotes.")
    }

    internal fun ProtectedAccess.exchangeNotes(
        invSlot: Int,
        noteType: ItemServerType,
        count: Int,
    ): String? {
        val held = inv[invSlot]?.takeIf { it.isType(noteType) }?.count ?: return null
        val affordable = inv.count("obj.coins") / FEE
        if (affordable == 0) {
            mes("You don't have enough coins to pay for that.")
            return null
        }
        val item = ocUncert(noteType)
        val maximum = minOf(count, held, affordable, inv.freeSpace() + 2)
        for (amount in maximum downTo 1) {
            val exchange = player.invTransaction(inv) {
                val inventory = select(inv)
                delete { from = inventory; obj = "obj.coins".asRSCM(); strictCount = amount * FEE }
                delete { from = inventory; obj = noteType.id; strictCount = amount; strictSlot = invSlot }
                insert { into = inventory; obj = item.id; strictCount = amount }
            }
            if (exchange.success) return item.internalName
        }
        mes("You don't have enough inventory space.")
        return null
    }

    private companion object {
        const val PHIALS = "npc.uncerter_rimmington"
        const val FEE = 5
    }
}
