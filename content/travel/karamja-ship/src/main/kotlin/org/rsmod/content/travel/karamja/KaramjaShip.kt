package org.rsmod.content.travel.karamja

import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KaramjaShip : PluginScript() {

    override fun ScriptContext.startup() {
        // Bind both multinpc parents and leaves: interaction dispatch resolves the leaf first.
        for (sailor in PortSarimSailors) {
            onOpNpc1(sailor) { talkToSailor(it.npc) }
            onOpNpc3(sailor) { boardFor(MusaPointDock, "Musa Point") }
        }

        for (officer in CustomsOfficers) {
            onOpNpc1(officer) { talkToCustoms(it.npc) }
            onOpNpc3(officer) { customsBoarding() }
        }

        onPlayerCoordsChanged { player.breakRumOnTeleport(lastKnownCoords) }
    }

    private suspend fun ProtectedAccess.talkToSailor(npc: Npc) {
        startDialogue(npc) {
            chatNpc(
                happy,
                "Hello there. Do you want to go on a trip to Karamja? We can take you to Musa " +
                    "Point for only $Fare coins.",
            )
            val board = choice2("Yes please.", true, "No thank you.", false)
            if (!board) {
                chatPlayer(neutral, "No thank you.")
                return@startDialogue
            }
            chatPlayer(happy, "Yes please.")
            access.boardFor(MusaPointDock, "Musa Point")
        }
    }

    private suspend fun ProtectedAccess.talkToCustoms(npc: Npc) {
        startDialogue(npc) { customsDialogue() }
    }

    private suspend fun Dialogue.customsDialogue() {
        chatNpc(neutral, "Can I help you?")

        when (
            choice2(
                "Can I journey on this ship?",
                1,
                "Does Karamja have any unusual customs then?",
                2,
            )
        ) {
            1 -> requestPassage()
            2 -> {
                chatPlayer(quiz, "Does Karamja have any unusual customs then?")
                chatNpc(bored, "I'm not that sort of customs officer.")
            }
        }
    }

    private suspend fun Dialogue.requestPassage() {
        chatPlayer(quiz, "Can I journey on this ship?")
        chatNpc(neutral, "You need to be searched before you can board.")

        var asked = false
        while (true) {
            val choice =
                if (asked) {
                    choice2(
                        "Search away. I have nothing to hide.",
                        1,
                        "You're not putting your hands on my things!",
                        2,
                    )
                } else {
                    choice3(
                        "Why?",
                        0,
                        "Search away. I have nothing to hide.",
                        1,
                        "You're not putting your hands on my things!",
                        2,
                    )
                }

            when (choice) {
                0 -> {
                    chatPlayer(quiz, "Why?")
                    chatNpc(
                        neutral,
                        "Because Asgarnia has banned the import of intoxicating spirits.",
                    )
                    asked = true
                }
                1 -> {
                    chatPlayer(happy, "Search away. I have nothing to hide.")
                    submitToSearch()
                    return
                }
                else -> {
                    chatPlayer(angry, "You're not putting your hands on my things!")
                    chatNpc(short, "You're not getting on this ship then.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.submitToSearch() {
        if (access.confiscateRum()) {
            chatNpc(angry, "Aha, trying to smuggle rum are we?")
            chatPlayer(shifty, "Umm... it's for personal use?")
            access.mes("The customs officer confiscates your rum.")
            access.mes("You will need to find some way to smuggle it off the island...")
            return
        }

        chatNpc(
            neutral,
            "Well you've got some odd stuff, but it's all legal. Now you need to pay a boarding " +
                "charge of $Fare coins.",
        )

        val pay = choice2("Okay.", true, "Oh, I'll not bother then.", false)
        if (!pay) {
            chatPlayer(neutral, "Oh, I'll not bother then.")
            return
        }

        chatPlayer(happy, "Okay.")
        if (access.inv.count(Coins) < Fare) {
            chatPlayer(sad, "Oh dear, I don't seem to have enough money.")
            return
        }
        access.boardFor(PortSarimDock, "Port Sarim")
    }

    private suspend fun ProtectedAccess.customsBoarding() {
        if (confiscateRum()) {
            mes("The customs officer confiscates your rum.")
            mes("You will need to find some way to smuggle it off the island...")
            return
        }
        boardFor(PortSarimDock, "Port Sarim")
    }

    private fun ProtectedAccess.confiscateRum(): Boolean {
        if (inv.count(KaramjaRum) <= 0) {
            return false
        }
        invDel(inv, KaramjaRum, inv.count(KaramjaRum))
        return true
    }

    // Fade transparency is 255 -> 0 for darkening, opposite to the canoe script.
    private suspend fun ProtectedAccess.boardFor(dock: CoordGrid, name: String) {
        if (inv.count(Coins) < Fare) {
            mes("You don't have enough coins for the boarding charge.")
            return
        }

        if (invDel(inv, Coins, Fare).failure) {
            return
        }

        mes("You pay the boarding charge of $Fare coins.")
        mes("You board the ship and sail to $name.")

        midiJingle(SailingJingle)
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FadeDuration,
        )
        clearHealthHud()

        delay(CrossingTicks)

        telejump(dock)

        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = FadeDuration,
        )
        minimapReset()
        clearHealthHud()

        delay(1)
        closeFadeOverlay()
    }

    // Running moves two tiles per tick; only longer jumps count as teleporting with rum.
    private fun Player.breakRumOnTeleport(from: CoordGrid) {
        if (from == CoordGrid.NULL || from.chebyshevDistance(coords) <= MaxMoveStep) {
            return
        }
        if (inv.count(KaramjaRum) <= 0) {
            return
        }
        invDel(inv, KaramjaRum, inv.count(KaramjaRum))
        mes("Your Karamjan rum gets broken and spilled.")
    }

    private companion object {
        private val PortSarimSailors =
            listOf(
                "npc.seaman_lorris",
                "npc.seaman_lorris_1op",
                "npc.seaman_thresnor",
                "npc.seaman_thresnor_1op",
                "npc.captain_tobias",
                "npc.captain_tobias_1op",
            )

        private val CustomsOfficers = listOf("npc.customs_officer", "npc.customs_officer_1op")

        /** Tiles a player can cover in one tick on foot: 1 walking, 2 running. */
        private const val MaxMoveStep = 2

        private const val KaramjaRum = "obj.karamja_rum"
        private const val Coins = "obj.coins"

        private const val Fare = 30

        private val MusaPointDock = CoordGrid(2956, 3146, 0)

        private val PortSarimDock = CoordGrid(3029, 3217, 0)

        private const val CrossingTicks = 5

        private const val FadeDuration = 50

        private const val SailingJingle = 171
    }
}
