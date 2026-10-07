package org.rsmod.content.minigames.fishingtrawler.scripts

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// The boarding plank is bridge-sunk from archive plane 1 to runtime 0; deck exit stays on 1.
class TrawlerBoardingScript : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GANGPLANK) { board(it.loc) }
        onOpLoc1(GANGPLANK_OFF) { disembark(it.loc) }
        onOpNpc1(DOCK_MURPHY) { talkToMurphy(it.npc) }
    }

    private suspend fun ProtectedAccess.board(plank: BoundLocInfo) {
        if (plank.coords.z != GANGPLANK_Z || plank.coords.x !in GANGPLANK_ON_X) {
            return
        }
        arriveDelay()
        delay(1)
        telejump(DECK_LANDING)
    }

    private suspend fun ProtectedAccess.disembark(plank: BoundLocInfo) {
        if (plank.coords.z != GANGPLANK_Z || plank.coords.x != GANGPLANK_OFF_X) {
            return
        }
        arriveDelay()
        delay(1)
        telejump(PIER_LANDING)
    }

    private suspend fun ProtectedAccess.talkToMurphy(npc: Npc) {
        startDialogue(npc) { opening() }
    }

    private suspend fun Dialogue.opening() {
        chatPlayer(happy, "Good day to you Sir.")
        chatNpc(happy, "Well hello my brave adventurer.")
        chatPlayer(quiz, "What are you up to?")
        chatNpc(neutral, "Getting ready to go fishing of course. There's no time to waste!")
        chatNpc(
            neutral,
            "I've got all the supplies I need from the shop at the end of the pier. They sell " +
                "good rope, hammers and axes although their bailing buckets aren't too effective.",
        )
        mainMenu()
    }

    private suspend fun Dialogue.mainMenu() {
        val choice =
            choice3(
                "What fish do you catch?",
                1,
                "Your boat doesn't look too safe.",
                2,
                "Could I help?",
                3,
            )
        when (choice) {
            1 -> fishCaught()
            2 -> boatSafety()
            3 -> couldIHelp()
        }
    }

    private suspend fun Dialogue.fishCaught() {
        chatPlayer(quiz, "What fish do you catch?")
        chatNpc(
            happy,
            "I get all sorts, anything that lies on the sea bed, you never know what you're " +
                "going to get until you pull up the net!",
        )
        mainMenu()
    }

    private suspend fun Dialogue.boatSafety() {
        chatPlayer(worried, "Your boat doesn't look too safe.")
        chatNpc(neutral, "That's because it's not, the darn thing's full of holes.")
        chatPlayer(quiz, "Oh, so I suppose you can't go out for a while?")
        chatNpc(
            laugh,
            "Oh no, I don't let a few holes stop an experienced sailor like me. I could sail " +
                "these seas in a barrel, I'll be going out soon enough.",
        )
        mainMenu()
    }

    private suspend fun Dialogue.couldIHelp() {
        chatPlayer(quiz, "Could I help?")
        chatNpc(
            happy,
            "Well of course you can! I'll warn you though, the seas are merciless and without " +
                "fishing experience you won't catch much.",
        )
        chatNpc(neutral, "You need a fishing level of 15 or above to catch any fish on the trawler.")
        chatNpc(neutral, "On occasions the net rips, so you'll need some rope to repair it.")
        chatNpc(
            neutral,
            "Repairing the net is difficult in the harsh conditions so you'll find it easier " +
                "with a higher crafting level.",
        )
        chatPlayer(neutral, "Right...ok.")
        chatNpc(neutral, "There's also a slight problem with leaks.")
        chatPlayer(shocked, "Leaks?!")
        chatNpc(neutral, "Nothing some swamp paste won't fix...")
        chatPlayer(quiz, "Swamp paste?")
        chatNpc(neutral, "Also, there's the small matter of the Kraken.")
        chatPlayer(shocked, "You have got to be kidding me?")
        chatNpc(
            neutral,
            "Occupational hazard. I usually whack it with an axe a few time and fix the damage " +
                "with hammer once it's gone.",
        )
        chatNpc(quiz, "Oh, and one more thing... I hope you're a good swimmer?")
        helpMenu()
    }

    private suspend fun Dialogue.helpMenu() {
        val choice =
            choice3(
                "Actually, I think I'll leave it.",
                1,
                "I'll be fine, let's go.",
                2,
                "What's swamp paste?",
                3,
            )
        when (choice) {
            1 -> leaveIt()
            2 -> letsGo()
            3 -> swampPaste()
        }
    }

    private suspend fun Dialogue.leaveIt() {
        chatPlayer(neutral, "Actually, I think I'll leave it.")
        chatNpc(angry, "Bloomin' land lovers!!!")
    }

    private suspend fun Dialogue.letsGo() {
        chatPlayer(happy, "I'll be fine, let's go.")
        chatNpc(
            happy,
            "Aye aye! Meet me on board the trawler. I just need to get a few things together.",
        )
    }

    private suspend fun Dialogue.swampPaste() {
        chatPlayer(quiz, "What's swamp paste?")
        chatNpc(neutral, "Swamp tar mixed with flour which is then heated over a fire.")
        chatPlayer(quiz, "Where can I find swamp tar?")
        chatNpc(
            neutral,
            "Unfortunately the only supply of swamp tar is in the swamps below Lumbridge.",
        )
    }

    private companion object {
        private const val GANGPLANK = "loc.trawler_gangplank"

        private const val GANGPLANK_OFF = "loc.trawler_gangplankoff"

        private const val DOCK_MURPHY = "npc.murphy"

        // Compare x/z only: boarding archive and runtime planes differ.
        private const val GANGPLANK_Z = 3170
        private val GANGPLANK_ON_X = 2674..2675
        private const val GANGPLANK_OFF_X = 2673

        private val DECK_LANDING = CoordGrid(2672, 3170, 1)

        // Pier archive plane 1 is bridge-sunk to runtime 0 (auditMapPlanes).
        private val PIER_LANDING = CoordGrid(2676, 3170, 0)
    }
}
