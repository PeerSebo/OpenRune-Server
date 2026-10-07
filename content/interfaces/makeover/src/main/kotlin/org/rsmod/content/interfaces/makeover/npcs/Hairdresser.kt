package org.rsmod.content.interfaces.makeover.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hat
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.content.interfaces.makeover.MakeoverPage
import org.rsmod.content.interfaces.makeover.openMakeover
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Hairdresser @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(HAIRDRESSER) { startDialogue(it.npc) { hairdresser() } }
        onOpNpc1(EMMA) { startDialogue(it.npc) { hairdresser(emma = true) } }

        for (npc in listOf(HAIRDRESSER, EMMA)) {
            onOpNpc4(npc) { offerMakeover(MakeoverPage.Hair) }
            onOpNpc5(npc) { offerMakeover(MakeoverPage.FacialHair) }
        }
    }

    private fun ProtectedAccess.offerMakeover(page: MakeoverPage) {
        if (player.hat != null) {
            mes("Please take off your hat before changing your hair.")
            return
        }
        openMakeover(page)
    }

    private suspend fun Dialogue.hairdresser(emma: Boolean = false) {
        chatNpc(
            happy,
            if (emma) {
                "Hello. Let's get to business. I got scissors. You got hair. Want to do this?"
            } else {
                "Good afternoon. In need of a haircut are we? Currently we're offering this " +
                    "service for free!"
            },
        )

        val choice =
            choice3(
                "I'd like a haircut please.",
                HAIRCUT,
                "I'd like a shave please.",
                SHAVE,
                "No thank you.",
                DECLINE,
            )

        when (choice) {
            HAIRCUT -> {
                chatPlayer(happy, "I'd like a haircut please.")
                chatNpc(
                    happy,
                    "Please select the hairstyle you would like from this brochure. I'll even " +
                        "throw in a free recolour.",
                )
                access.offerMakeover(MakeoverPage.Hair)
            }

            SHAVE -> {
                chatPlayer(happy, "I'd like a shave please.")
                access.offerMakeover(MakeoverPage.FacialHair)
            }

            DECLINE -> {
                chatPlayer(neutral, "No thank you.")
                chatNpc(neutral, "Very well. Come back if you change your mind.")
            }
        }
    }

    private companion object {
        const val HAIRDRESSER = "npc.hairdresser"
        const val EMMA = "npc.hairdresser_boutique"

        const val HAIRCUT = 1
        const val SHAVE = 2
        const val DECLINE = 3
    }
}
