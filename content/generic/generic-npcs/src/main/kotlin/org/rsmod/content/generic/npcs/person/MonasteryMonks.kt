package org.rsmod.content.generic.npcs.person

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MonasteryMonks : PluginScript() {
    override fun ScriptContext.startup() {
        for (symbol in HealingMonks) {
            onOpNpc1(symbol) { startDialogue(it.npc) { conversation(abbot = false) } }
        }
        onOpNpc1("npc.abbot_langley") {
            startDialogue(it.npc) { conversation(abbot = true) }
        }
        onOpLoc1("loc.monasteryladder") {
            if (vars[Membership] == 0 || player.basePrayerLvl < RequiredPrayer) {
                startDialogue {
                    abbot("The upper floor is reserved for members of our order.")
                    joinOrder()
                }
                return@onOpLoc1
            }
            arriveDelay()
            anim(RSCM.getReverseMapping(RSCMType.SEQ, it.type.param(params.climb_anim).id))
            delay(1)
            telejump(coords.translateLevel(1))
        }
    }

    private suspend fun Dialogue.conversation(abbot: Boolean) {
        chatNpc(neutral, "Greetings traveller.")
        val choice = if (abbot && vars[Membership] == 0) {
            choice3(Heal, 1, Location, 2, "May I go upstairs?", 3)
        } else {
            choice2(Heal, 1, Location, 2)
        }
        when (choice) {
            1 -> heal(abbot)
            2 -> {
                chatPlayer(quiz, Location)
                chatNpc(happy, "The peace and quiet suit us. Even here, many travellers " +
                    "come to us seeking refuge or treatment for their wounds.")
            }
            3 -> {
                chatPlayer(quiz, "May I go upstairs?")
                abbot("The upper floor is reserved for members of our order.")
                joinOrder()
            }
        }
    }

    private suspend fun Dialogue.heal(abbot: Boolean) {
        chatPlayer(quiz, Heal)
        if (!abbot && (vars["varbit.this_is_a_pvp_world"] != 0 || vars["varbit.deadman_mode"] != 0)) {
            chatNpc(sad, "I'm unable to heal you on this world.")
            return
        }
        chatNpc(neutral, "Ok.")
        if (player.hitpoints < player.baseHitpointsLvl) {
            val amount = if (abbot) 4 + player.hitpoints * 12 / 100 else 2 + player.hitpoints / 5
            player.statHeal("stat.hitpoints", amount, 0)
        }
        mesbox("The ${if (abbot) "abbot" else "monk"} rests his hands upon your head, easing your wounds.")
    }

    private suspend fun Dialogue.joinOrder() {
        if (!choice2("Could I become a member?", true, "I'll stay downstairs.", false)) {
            chatPlayer(neutral, "I'll stay downstairs.")
            return
        }
        chatPlayer(quiz, "Could I become a member?")
        if (player.basePrayerLvl < RequiredPrayer) {
            abbot("You must deepen your devotion before joining us.")
            mesbox("Level 31 Prayer is required to join the order.")
            return
        }
        abbot("Your devotion is clear. You are welcome to join our order.")
        vars[Membership] = 1
    }

    private suspend fun Dialogue.abbot(text: String) {
        if (npc == null) chatNpcSpecific("Abbot Langley", "npc.abbot_langley", neutral, text)
        else chatNpc(neutral, text)
    }

    internal companion object {
        val HealingMonks = listOf("npc.monk", "npc.entrana_monk", "npc.hosidius_monk")
        const val Membership = "varbit.monastery_enter"
        const val RequiredPrayer = 31
        private const val Heal = "Can you heal me? I'm injured."
        private const val Location = "Why is this place so isolated?"
    }
}
