package org.rsmod.content.generic.shops

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RoavarScript : PluginScript() {
    override fun ScriptContext.startup() {
        val roavar = "npc.werewolfinnkeeper".asRSCM()
        onProtectedEvent<NpcUnimplementedEvents.Op1>(roavar) { browse(it.npc) }
        onProtectedEvent<NpcUnimplementedEvents.Op3>(roavar) { buyMead() }
        onProtectedEvent<NpcUnimplementedEvents.Ap1>(roavar) {
            if (isWithinApRange(it.npc, 2)) browse(it.npc)
        }
        onProtectedEvent<NpcUnimplementedEvents.Ap3>(roavar) {
            if (isWithinApRange(it.npc, 2)) buyMead()
        }
    }

    private suspend fun ProtectedAccess.browse(npc: Npc) = startDialogue(npc) {
        chatNpc(happy, "Would you like a drink? Moonlight mead is five coins.")
        if (choice2("Yes please.", true, "No thanks.", false)) {
            access.ifClose()
            buyMead()
        }
    }

    private fun ProtectedAccess.buyMead() {
        if (inv.count("obj.coins") < 5) {
            mes("You need 5 coins to buy a glass of moonlight mead.")
            return
        }
        val transaction = player.invTransaction(inv) {
            val inventory = select(inv)
            delete(inventory, "obj.coins".asRSCM(), 5)
            add(inventory, "obj.moonlight_mead".asRSCM(), 1)
        }
        if (transaction.failure) {
            mes("You don't have enough room in your inventory.")
        } else {
            spam("You buy a glass of moonlight mead.")
        }
    }
}
