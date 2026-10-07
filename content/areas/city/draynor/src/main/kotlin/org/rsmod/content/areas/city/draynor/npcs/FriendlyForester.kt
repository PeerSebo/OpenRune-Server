package org.rsmod.content.areas.city.draynor.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseWoodcuttingLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.interfaces.omnishop.openOmnishop
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FriendlyForester : PluginScript() {
    private var Player.metForester by intVarBit("varbit.forestry_forester_met")

    override fun ScriptContext.startup() {
        onOpNpc1("npc.forestry_forester") { startDialogue(it.npc) { forester() } }
        onOpNpc3("npc.forestry_forester") { openOmnishop(FORESTRY_SHOP) }
    }

    private suspend fun Dialogue.forester() {
        chatPlayer(neutral, "Hello there.")
        if (player.metForester == 0) {
            chatNpc(happy, "Greetings! Have you come to talk forestry?")
            chatPlayer(quiz, "Forestry?")
            chatNpc(
                happy,
                "That's right! In this day and age, we need those trained in the way of the " +
                    "forester now more than ever!",
            )
            chatPlayer(quiz, "Way of the forester? What do you mean?")
            chatNpc(
                happy,
                "You know, helping the woodland flourish with your fellow foresters! And of " +
                    "course, drinking plenty of tea to fuel yourself while you do so!",
            )
            chatPlayer(quiz, "Sounds interesting. How can I get started?")
            if (player.baseWoodcuttingLvl < FORESTRY_LEVEL) {
                chatNpc(
                    happy,
                    "Well, I'd recommend spending some more time in the forests before " +
                        "embarking on your forestry journey. Come back when you can fell a " +
                        "mighty oak!",
                )
                chatPlayer(happy, "I'll do that. Thanks for the information.")
                chatNpc(happy, "Good luck out there!")
                return
            }
            player.metForester = 1
            chatNpc(
                happy,
                "First, you'll need a forestry kit. Once you have one, all you need to do is chop " +
                    "some trees while you have it on you. The woodland will do the rest!",
            )
            chatNpc(
                happy,
                "Oh, and if you find any anima-infused bark while tending to the forests, I'll " +
                    "gladly trade with you. My shop has all sorts of useful items.",
            )
            chatPlayer(confused, "That's all rather vague...")
            chatNpc(
                happy,
                "It is, but I find it's better to let the forests do the talking. You'll " +
                    "understand once you get going. Speaking of which, would you like one of " +
                    "those kits now? I have them free of charge in my shop.",
            )
            chatPlayer(happy, "Yes please.")
            chatNpc(happy, "Excellent!")
            access.openOmnishop(FORESTRY_SHOP)
            return
        }
        chatNpc(happy, "Hello again! How goes your forestry journey?")
        val topic =
            menu(
                "I'd like to trade with you." to 0,
                "Could you tell me more about forestry?" to 1,
                "Pretty good, thanks." to 2,
            )
        when (topic) {
            0 -> {
                chatPlayer(neutral, "I'd like to trade with you.")
                chatNpc(happy, "Of course!")
                access.openOmnishop(FORESTRY_SHOP)
            }
            1 -> {
                chatPlayer(quiz, "Could you tell me more about forestry?")
                chatNpc(
                    happy,
                    "It's very simple. All you need is a forestry kit. I have them free of " +
                        "charge in my shop. Once you have one, just chop some trees while you " +
                        "have it on you. The woodland will do the rest!",
                )
                chatNpc(
                    happy,
                    "Oh, and if you find any anima-infused bark while tending to the forests, " +
                        "I'll gladly trade with you. My shop has all sorts of useful items.",
                )
                chatNpc(happy, "Speaking of which, would you like to trade now?")
                if (menu("Yes please." to true, "I'm good, thanks." to false)) {
                    chatPlayer(happy, "Yes please.")
                    chatNpc(happy, "Excellent!")
                    access.openOmnishop(FORESTRY_SHOP)
                } else {
                    chatPlayer(neutral, "I'm good, thanks.")
                    chatNpc(happy, "Very well.")
                }
            }
            else -> {
                chatPlayer(happy, "Pretty good, thanks.")
                chatNpc(happy, "I'm glad to hear it.")
            }
        }
    }

    private companion object {
        const val FORESTRY_LEVEL = 15
        const val FORESTRY_SHOP = "dbrow.forestry_shop"
    }
}
