package org.rsmod.content.generic.npcs.security

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CountCheck : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.count_check") { startDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc) = startDialogue(npc) { greeting() }

    private suspend fun Dialogue.greeting() {
        chatNpc(
            happy,
            "Ahahahaha! I am Count Check, the renowned security expert. Would you like me to " +
                "check your account to see if it is secure?",
        )
        menu()
    }

    private suspend fun Dialogue.menu() {
        val choice =
            choice5(
                "Check my account, Count Check!",
                1,
                "Can you give me any advice?",
                2,
                "Where can I learn more about security?",
                3,
                "Who are you?",
                4,
                "I'll see you another time.",
                5,
            )
        when (choice) {
            1 -> checkAccount()
            2 -> advice()
            3 -> whereToLearn()
            4 -> whoAreYou()
            5 -> chatPlayer(neutral, "I'll see you another time.")
        }
    }

    private suspend fun Dialogue.checkAccount() {
        chatPlayer(happy, "Check my account, Count Check!")
        access.mes(
            "To set a Bank PIN, talk to a banker and <col=ef1020>ask to check your PIN " +
                "settings</col>."
        )
        chatNpc(
            sad,
            "You do not have a Bank PIN, so you fail my checks! Talk to a banker to set a PIN " +
                "before we meet again.",
        )
    }

    private suspend fun Dialogue.advice() {
        val count = npc
        chatPlayer(quiz, "Can you give me any advice?")
        chatNpc(happy, "I can! Count with the Count as I count the steps to securing your account:")
        count?.say("ONE!")
        chatNpc(happy, "ONE... unique password!")
        chatNpc(
            neutral,
            "Never use the same password for different accounts, or on different websites. If it " +
                "got leaked, all of your accounts would become vulnerable at once!",
        )
        count?.say("TWO!")
        chatNpc(happy, "TWO... step verification for your email account!")
        chatNpc(
            neutral,
            "Good email providers let you set up two-step verification, maybe by generating " +
                "security codes on a second device, or by sending codes to your phone. Make sure " +
                "you've enabled this.",
        )
        count?.say("THREE!")
        chatNpc(happy, "THREE... Um... Use a Jagex Account. I can see you already do this.")
        chatPlayer(quiz, "What's that got to do with THREE?")
        chatNpc(
            angry,
            "Look, I'd got something for ONE, TWO and FOUR, and I like counting, okay? Don't " +
                "interrupt my flow!",
        )
        chatPlayer(neutral, "...")
        count?.say("FOUR!")
        chatNpc(happy, "FOUR... digit Bank PIN!")
        chatNpc(neutral, "Next time you visit a bank, talk to a banker and ask to check your PIN settings.")
        chatNpc(happy, "Muhahahahhaa! Such fun it is to count!")
    }

    private suspend fun Dialogue.whereToLearn() {
        chatPlayer(quiz, "Where can I learn more about security?")
        chatNpc(
            happy,
            "The Stronghold of Security, in Barbarian Village. There is much to learn there. But " +
                "while you are here, may I check your account for you?",
        )
    }

    private suspend fun Dialogue.whoAreYou() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(
            neutral,
            "As you may see, I am a vampyre, from Morytania. I left there when I heard the " +
                "terrible news and now I choose to spend my days warning others of the perils " +
                "that lay in wait for them.",
        )
        chatPlayer(quiz, "The perils?")
        chatNpc(
            sad,
            "Yes, be warned! The travellers of these lands face danger from outside. Accounts can " +
                "be stolen, and when that happens one can lose everything one has worked for.",
        )
        chatNpc(
            neutral,
            "I've left my vampyric ways behind me, and now I spend my days letting everyone know " +
                "of this terrible danger.",
        )
        chatNpc(
            happy,
            "In return for my dedication, I have been blessed with the ability to check if " +
                "adventurers are ready to defend themselves against these account thieves.",
        )
    }
}
