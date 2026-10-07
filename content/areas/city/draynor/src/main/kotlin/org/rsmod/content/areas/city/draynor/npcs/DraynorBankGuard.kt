package org.rsmod.content.areas.city.draynor.npcs

import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorBankGuard : PluginScript() {
    private var Player.bankJob by intVarBit("varbit.wom_bankjob")

    override fun ScriptContext.startup() {
        onOpNpc1("npc.wom_bankguard") { startDialogue(it.npc) { bankGuard() } }
    }

    private suspend fun Dialogue.bankGuard() {
        chatNpc(neutral, "Yes?")
        when {
            player.bankJob >= BANKJOB_WATCHED -> bankGuardAfterRecording()
            player.bankJob == BANKJOB_KNOWN -> {
                chatPlayer(quiz, "Do you have any idea who robbed the bank yet?")
                bankGuardSuspect()
            }
            else -> bankGuardIntro()
        }
    }

    private suspend fun Dialogue.bankGuardIntro() {
        var deposited = false
        while (true) {
            val options = buildList {
                if (!deposited) add("Can I deposit my stuff here?" to GuardTopic.Deposit)
                add("That wall doesn't look very good." to GuardTopic.Wall)
                if (deposited) add(GUARD_LEAVE to GuardTopic.Leave)
                else add("Sorry, I don't want anything." to GuardTopic.Nothing)
            }
            when (menu(options, title = GUARD_MENU)) {
                GuardTopic.Deposit -> {
                    deposited = true
                    chatPlayer(quiz, "Hello. Can I deposit my stuff here?")
                    chatNpc(neutral, "No. I'm a security guard, not a bank clerk.")
                }
                GuardTopic.Wall -> return bankGuardWall()
                GuardTopic.Leave -> return bankGuardLeave()
                GuardTopic.Nothing -> {
                    chatPlayer(neutral, "Sorry, I don't want anything.")
                    chatNpc(neutral, "Ok.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.bankGuardWall() {
        chatPlayer(quiz, "That wall doesn't look very good.")
        chatNpc(neutral, "No, it doesn't.")
        val tell =
            menu(
                "Are you going to tell me what happened?" to true,
                GUARD_LEAVE to false,
                title = GUARD_MENU,
            )
        if (!tell) return bankGuardLeave()
        chatPlayer(quiz, "Are you going to tell me what happened?")
        chatNpc(neutral, "I could do.")
        chatPlayer(happy, "Ok, go on!")
        chatNpc(neutral, "Someone smashed the wall when they were robbing the bank.")
        if (player.bankJob < BANKJOB_KNOWN) player.bankJob = BANKJOB_KNOWN
        chatPlayer(shocked, "Someone's robbed the bank?")
        chatNpc(neutral, "Yes.")
        chatPlayer(shocked, "But... was anyone hurt? Did they get anything valuable?")
        chatNpc(neutral, "Yes, but we were able to get more staff and mend the wall easily enough.")
        chatNpc(neutral, "The Bank has already replaced all the stolen items that belonged to customers.")
        chatPlayer(shocked, "Oh, good... but the bank staff got hurt?")
        chatNpc(neutral, "Yes, but the new ones are just as good.")
        chatPlayer(neutral, "You're not very nice, are you?")
        chatNpc(neutral, "No-one's expecting me to be nice.")
        chatPlayer(confused, "Anyway... So, someone's robbed the bank?")
        chatNpc(neutral, "Yes.")
        chatPlayer(quiz, "Do you know who did it?")
        bankGuardSuspect()
    }

    private suspend fun Dialogue.bankGuardSuspect() {
        chatNpc(
            neutral,
            "We are fairly sure we know who the robber was. The security recording was damaged " +
                "in the attack, but it still shows his face clearly enough.",
        )
        chatPlayer(quiz, "You've got a security recording?")
        chatNpc(neutral, "Yes. Our insurers insisted that we install a magical scrying orb.")
        var askedWho = false
        while (true) {
            val options = buildList {
                add("Can I see the recording?" to true)
                if (askedWho) add(GUARD_LEAVE to false) else add("So who was the robber?" to null)
            }
            when (menu(options, title = GUARD_MENU)) {
                true -> return offerRecording()
                false -> return bankGuardLeave()
                null -> {
                    askedWho = true
                    chatPlayer(happy, "So who was the robber?")
                    chatNpc(neutral, "I can't disclose that information.")
                }
            }
        }
    }

    private suspend fun Dialogue.offerRecording() {
        chatPlayer(quiz, "Can I see the recording?")
        chatNpc(neutral, "I suppose so. But it's quite long.")
        val watch =
            menu(
                "That's ok, show me the recording." to true,
                "Thanks, maybe another day." to false,
                title = GUARD_MENU,
            )
        if (!watch) {
            chatPlayer(neutral, "Thanks, maybe another day.")
            chatNpc(neutral, "Ok.")
            return
        }
        chatPlayer(neutral, "That's ok, show me the recording.")
        chatNpc(
            neutral,
            "Alright... The bank's magical playback device will feed the recorded images into " +
                "your mind. Just shut your eyes.",
        )
        watchRecording()
    }

    private suspend fun Dialogue.watchRecording() {
        mesbox("You close your eyes and watch the recording...")
        player.bankJob = BANKJOB_WATCHED
        mesbox("End of recording.")
    }

    private suspend fun Dialogue.bankGuardAfterRecording() {
        val topic =
            menu(
                "The robber in the recording looked familiar." to 0,
                "Can I see that recording again, please?" to 1,
                "Sorry, I don't want anything." to 2,
                title = GUARD_MENU,
            )
        when (topic) {
            0 -> {
                chatPlayer(neutral, "The robber in the recording looked familiar.")
                chatNpc(neutral, "Oh, you recognised him too?")
                chatPlayer(neutral, "Yes, and I can tell you where he lives.")
                chatNpc(neutral, "Thanks, but we already know where to find our suspect.")
                chatPlayer(quiz, "So are you going to have him arrested?")
                chatNpc(
                    neutral,
                    "We're certainly keeping an eye on him. I'm afraid I can't give you any " +
                        "more details about the case.",
                )
                chatPlayer(neutral, "Fair enough.")
            }
            1 -> {
                chatPlayer(quiz, "Can I see that recording again, please?")
                chatNpc(neutral, "I'd like you to pay me 50 gp first.")
                if (access.inv.count(COINS) < RECORDING_FEE) {
                    chatPlayer(sad, "I'm not carrying that much.")
                    chatNpc(neutral, "Oh well, maybe another day.")
                    return
                }
                val pay =
                    menu("Ok, here's 50 gp." to true, "Thanks, maybe another day." to false)
                if (!pay) {
                    chatPlayer(neutral, "Thanks, maybe another day.")
                    chatNpc(neutral, "Ok.")
                    return
                }
                chatPlayer(neutral, "Ok, here's 50 gp.")
                if (access.invDel(access.inv, COINS, RECORDING_FEE).failure) return
                watchRecording()
            }
            else -> {
                chatPlayer(neutral, "Sorry, I don't want anything.")
                chatNpc(neutral, "Ok.")
            }
        }
    }

    private suspend fun Dialogue.bankGuardLeave() {
        chatPlayer(neutral, GUARD_LEAVE)
        chatNpc(neutral, "Good day, ${if (isLad()) "sir" else "ma'am"}.")
    }

    private enum class GuardTopic {
        Deposit,
        Wall,
        Leave,
        Nothing,
    }

    private fun Dialogue.isLad(): Boolean = player.appearance.bodyType == Constants.bodytype_a

    private companion object {
        const val COINS = "obj.coins"
        const val RECORDING_FEE = 50
        const val BANKJOB_KNOWN = 1
        const val BANKJOB_WATCHED = 2
        const val GUARD_MENU = "What would you like to say?"
        const val GUARD_LEAVE = "Alright, I'll stop bothering you now."
    }
}
