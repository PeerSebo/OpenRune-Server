package org.rsmod.content.generic.npcs.death

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DeathsOffice : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.death_office_death") { startDialogue(it.npc) }
        onOpLoc1("loc.death_coffer_access") { coffer() }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc) = startDialogue(npc) { death() }

    private suspend fun ProtectedAccess.coffer() {
        arriveDelay()
        mes(
            "Death will not accept items worth less than 10,000 coins per " +
                "<col=ef1020>individual item</col>."
        )
    }

    private suspend fun Dialogue.death() {
        chatNpc(
            neutral,
            "So, mortal... you have died, as all mortals do. As you have suffered the loss of " +
                "some items on this occasion, I have brought you here to explain for you how " +
                "that works.",
        )
        chatPlayer(quiz, "I lost items? Can I get them back?")
        explainGravestones()
        menu()
    }

    private suspend fun Dialogue.menu() {
        val choice =
            choice5(
                "Tell me about gravestones again.",
                1,
                "How do I pay a gravestone fee?",
                2,
                "How long do I have to return to my gravestone?",
                3,
                "How do I know what will happen to my items when I die?",
                4,
                "I think I'm done here.",
                5,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "Tell me about gravestones again.")
                explainGravestones(again = true)
                menu()
            }
            2 -> {
                payFee()
                menu()
            }
            3 -> {
                howLong()
                menu()
            }
            4 -> {
                itemsKeptOnDeath()
                menu()
            }
            5 -> {
                chatPlayer(neutral, "I think I'm done here.")
                chatNpc(
                    neutral,
                    "Very well. Once you pass through my portal, you will have 15 minutes to " +
                        "reach your gravestone.",
                )
            }
        }
    }

    private suspend fun Dialogue.explainGravestones(again: Boolean = false) {
        chatNpc(
            neutral,
            (if (again) "Gladly... " else "Let me explain... ") +
                "Across most of the land of Gielinor, when you die, some of the items you're " +
                "carrying are dropped.",
        )
        mesbox(
            "A gravestone appears on the ground <col=9f0000>near where you <col=9f0000>died</col>. " +
                "You can go there and retrieve your items by clicking on it."
        )
        mesbox(
            "Your gravestone would be marked with an arrow to help you spot it. A grave icon " +
                "appears on the <col=9f0000>world map</col> too, if your grave is in a mapped area."
        )
        mesbox(
            "Gravestones charge a <col=9f0000>small fee</col> to return especially valuable items, " +
                "but most things are returned free of charge. Conveniently, a gravestone can take " +
                "its fee <col=9f0000>straight from your bank</col>."
        )
        mesbox(
            "A gravestone does not last forever. After <col=9f0000>about 15 minutes</col>, a " +
                "gravestone will collapse. Anything in it will be sent here, to me."
        )
        chatNpc(
            neutral,
            "I will look after such items, <col=9f0000>without a time limit</col>. However, I do " +
                "charge more for my services. It would be cheaper to retrieve them from the " +
                "gravestone directly, if you can reach it in time.",
        )
    }

    private suspend fun Dialogue.payFee() {
        chatPlayer(quiz, "How do I pay a gravestone fee?")
        chatNpc(
            neutral,
            "Cash is always acceptable. Also, a gravestone can take it directly from your bank, " +
                "in case you didn't have enough with you.",
        )
        chatNpc(
            neutral,
            "Besides that, take a look at my Coffer here. You can bring your unwanted " +
                "possessions, and sacrifice them into my Coffer. I'll count their value against " +
                "any future gravestone fees you're charged.",
        )
        mesbox(
            "Death's Coffer won't accept items worth less than <col=9f0000>10,000 coins per " +
                "item</col> - Death doesn't want cheap junk! But more valuable items can be " +
                "sacrificed to pay down future reclamation fees."
        )
    }

    private suspend fun Dialogue.howLong() {
        chatPlayer(quiz, "How long do I have to return to my gravestone?")
        mesbox(
            "A gravestone lasts for <col=9f0000>at least 15 minutes</col> before it collapses. " +
                "However, that timer <col=9f0000>pauses if you become <col=9f0000>inactive</col>, " +
                "in case you have lost your connection."
        )
        mesbox(
            "A timer <col=9f0000>on your head-up display</col> shows the remaining time before " +
                "your gravestone will collapse."
        )
    }

    private suspend fun Dialogue.itemsKeptOnDeath() {
        chatPlayer(quiz, "How do I know what will happen to my items when I die?")
        chatNpc(
            neutral,
            "There is a menu to predict these things for you. Take a look at your Worn Items " +
                "side-panel...",
        )
        mesbox(
            "The <col=9f0000>Items Kept on Death</col> menu tells you roughly what your items " +
                "will do when you next die, including <col=9f0000>estimating gravestone fees</col>."
        )
    }
}
