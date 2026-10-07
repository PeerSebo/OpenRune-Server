package org.rsmod.content.areas.city.falador

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpcU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Emily @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((npc, name) in Barmaids) {
            onApNpc1(npc) { apTalkToBarmaid(it.npc, name) }
            onOpNpc1(npc) { talkToBarmaid(it.npc, name) }
            onOpNpcU(npc, "obj.beer_glass") { startDialogue(it.npc) { sellGlasses() } }
            onApNpcU(
                checkNotNull(ServerCacheManager.getNpc(npc.asRSCM())),
                checkNotNull(ServerCacheManager.getItem("obj.beer_glass".asRSCM())),
            ) {
                if (isWithinApRange(it.npc, distance = CounterApRange)) {
                    startDialogue(it.npc) { sellGlasses() }
                }
            }
        }
    }

    // Narrow AP range while retaining the route; the interaction must retry as the player approaches.
    private suspend fun ProtectedAccess.apTalkToBarmaid(npc: Npc, name: String) {
        if (!isWithinApRange(npc, distance = CounterApRange)) {
            return
        }
        talkToBarmaid(npc, name)
    }

    private suspend fun ProtectedAccess.talkToBarmaid(npc: Npc, name: String): Unit =
        startDialogue(npc) { barmaid(name) }

    private suspend fun Dialogue.barmaid(name: String) {
        chatNpcNoTurn(happy, "Heya! What can I get you?")
        chatPlayer(quiz, "What ales are you serving?")
        chatNpcNoTurn(
            happy,
            "Well, we've got Asgarnian Ale, Wizard's Mind Bomb and " +
                "Dwarven Stout, all for only 3 coins.",
        )
        val choice = choice4(AsgarnianAle, 1, MindBomb, 2, DwarvenStout, 3, NoneOfThose, 4)
        when (choice) {
            1 -> buyAle(name, happy, AsgarnianAle, "obj.asgarnian_ale", "You buy an Asgarnian Ale.")
            2 ->
                buyAle(
                    name,
                    happy,
                    MindBomb,
                    "obj.wizards_mind_bomb",
                    "You buy a Wizard's Mind Bomb.",
                )
            3 -> buyAle(name, quiz, DwarvenStout, "obj.dwarven_stout", "You buy a Dwarven Stout.")
            4 -> chatPlayer(neutral, NoneOfThose)
        }
    }

    private suspend fun Dialogue.buyAle(
        name: String,
        orderAnim: MesAnimType,
        orderLine: String,
        obj: String,
        purchaseMessage: String,
    ) {
        chatPlayer(orderAnim, orderLine)
        if (!player.invTakeFee(fee = AlePrice)) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        player.spam(purchaseMessage)
        player.invAddOrDrop(objRepo, obj)
        chatPlayer(happy, "Thanks, $name.")
    }

    private suspend fun Dialogue.sellGlasses() {
        chatPlayer(neutral, "I'd like to sell my empty beer glasses.")
        chatNpcNoTurn(happy, "I can pay two coins for each empty beer glass.")
        if (!choice2("Okay, sure.", true, "No thanks.", false)) return

        access.ifClose()
        val count = player.inv.count("obj.beer_glass")
        if (count == 0) return
        val exchange = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            delete(inventory, "obj.beer_glass".asRSCM(), count)
            add(inventory, "obj.coins".asRSCM(), count * GlassPrice)
        }
        if (exchange.failure) {
            access.mes("You don't have enough room to receive the coins.")
            return
        }
        chatNpcNoTurn(happy, "There you go.")
        chatPlayer(happy, "Thanks!")
    }

    private companion object {
        private val Barmaids = mapOf(
            "npc.risingsun_barmaid" to "Emily",
            "npc.risingsun_barmaid2" to "Kaylee",
            "npc.risingsun_barmaid3" to "Tina",
        )

        private const val AlePrice = 3
        private const val GlassPrice = 2
        private const val CounterApRange = 4

        private const val AsgarnianAle = "One Asgarnian Ale, please."
        private const val MindBomb = "I'll try the Mind Bomb."
        private const val DwarvenStout = "Can I have a Dwarven Stout?"
        private const val NoneOfThose = "I don't feel like any of those."
    }
}
