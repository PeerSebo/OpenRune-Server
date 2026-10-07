package org.rsmod.content.areas.city.draynor

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Diango : PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerLogin { DiangoState.migrate(player) }
        onOpNpc4(NpcId) { checkHolidayItems(it.npc) }
    }

    private suspend fun ProtectedAccess.checkHolidayItems(npc: Npc) =
        startDialogue(npc) {
            if (DiangoState.claimedBananaHat(player)) {
                alreadyHaveEverything()
            } else {
                offerBananaHat()
            }
        }

    private suspend fun Dialogue.offerBananaHat() {
        chatNpc(
            happy,
            "Howdy there partner! You came at just the right time! I've got something for ya!",
        )
        chatPlayer(happy, "Oh really? What is it?")
        chatNpc(
            happy,
            "Well, I did some rooting around in my house and found this really neat hat.",
        )
        chatPlayer(quiz, "A hat? What sort of hat...")
        chatNpc(happy, "A banana hat! Would you like it?")

        val accept =
            choice2(
                "Sure, I'll take the hat.",
                true,
                "No thanks, I don't want it right now.",
                false,
            )
        if (!accept) {
            return
        }
        chatPlayer(happy, "Sure, I'll take the hat.")

        if (access.invAdd(access.inv, BananaHat).failure) {
            return
        }
        access.mes("You've received a holiday item: <col=ef1020>Banana hat</col>")
        DiangoState.setClaimedBananaHat(player, true)
        chatNpc(happy, "There you go amigo, enjoy!")
    }

    private suspend fun Dialogue.alreadyHaveEverything() {
        chatNpc(happy, "It looks as if you already have everything!")
    }

    private companion object {
        private const val NpcId = "npc.aprilfoolshorsesalesman"

        private const val BananaHat = "obj.osb8_banana_hat"
    }
}
