package org.rsmod.content.areas.city.varrock.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BlueMoonInn : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(Bartender) { talkToBartender(it.npc) }

        onOpNpc1(BarbarianWoman) { talkToBarbarian(it.npc) }
        onOpNpc1(Woman) { talkToWoman(it.npc) }
        onOpNpc1(JonnyTheBeard) { talkToJonny(it.npc) }

        onOpLoc1(CellarLadderDown) { climbDown(it.loc) }
        onOpLoc1(CellarLadderUp) { climb(InnCoord, ClimbAnim) }
    }

    private suspend fun ProtectedAccess.talkToBartender(npc: Npc) {
        startDialogue(npc) { bartenderDialogue() }
    }

    private suspend fun Dialogue.bartenderDialogue() {
        chatNpc(happy, "What can I do yer for?")

        val option =
            choice3(
                "A glass of your finest ale please.",
                1,
                "Can you recommend where an adventurer might make his fortune?",
                2,
                "Do you know where I can get some good equipment?",
                3,
            )

        when (option) {
            1 -> buyBeer()
            2 -> {
                chatPlayer(quiz, "Can you recommend where an adventurer might make his fortune?")
                chatNpc(
                    neutral,
                    "There's always work going for them as can swing a sword. Try the folk in the " +
                        "square - someone's always lost something.",
                )
            }
            3 -> {
                chatPlayer(quiz, "Do you know where I can get some good equipment?")
                chatNpc(
                    neutral,
                    "Horvik does armour just up the road, and Lowe sells bows. Varrock's well " +
                        "served for that sort of thing.",
                )
            }
        }
    }

    private suspend fun Dialogue.buyBeer() {
        chatPlayer(neutral, "A glass of your finest ale please.")
        chatNpc(happy, "No problemo. That'll be $BeerPrice coins.")

        if (access.invTotal(access.inv, Coins) < BeerPrice) {
            chatPlayer(sad, "Oh dear, I don't have enough coins.")
            return
        }

        if (access.invDel(access.inv, Coins, BeerPrice).failure) {
            return
        }
        if (access.invAdd(access.inv, Beer, 1).failure) {
            access.invAdd(access.inv, Coins, BeerPrice)
            return
        }

        access.spam("You buy a pint of beer.")
    }

    private suspend fun ProtectedAccess.talkToBarbarian(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "What are you looking at?")
            chatPlayer(neutral, "Nothing, sorry.")
            chatNpc(angry, "That's what I thought.")
        }
    }

    private suspend fun ProtectedAccess.talkToWoman(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Hello there.")

            val ask =
                choice2(
                    "Who's the fellow drinking in the corner?",
                    true,
                    "Nothing, sorry to bother you.",
                    false,
                )
            if (!ask) {
                chatPlayer(neutral, "Nothing, sorry to bother you.")
                return@startDialogue
            }

            chatPlayer(quiz, "Who's the fellow drinking in the corner?")
            chatNpc(
                neutral,
                "That's Dr Harlow. He was somebody once, they say. Now he's mostly here.",
            )
        }
    }

    private suspend fun ProtectedAccess.talkToJonny(npc: Npc) {
        startDialogue(npc) { chatNpc(shifty, "Whatever you want, I'm not interested!") }
    }

    // The shared ladder type serves both the inn cellar and Phoenix hideout; dispatch by placement.
    private suspend fun ProtectedAccess.climbDown(loc: BoundLocInfo) {
        when (loc.coords) {
            CellarLadderCoord -> climb(CellarCoord, ClimbAnim)
            PhoenixLadderCoord ->
                climb(player.coords.translateZ(UndergroundZOffset), PhoenixClimbDownAnim)
        }
    }

    private suspend fun ProtectedAccess.climb(destination: CoordGrid, seq: String) {
        arriveDelay()
        anim(seq)
        delay(1)
        telejump(destination)
    }

    private companion object {
        private const val Bartender = "npc.bluemoon_bartender"
        private const val BarbarianWoman = "npc.barbarianwoman_varrock"
        private const val Woman = "npc.varrock_woman1"
        private const val JonnyTheBeard = "npc.jonny_the_beard"

        private const val CellarLadderDown = "loc.fai_varrock_ladder_deep"
        private const val CellarLadderUp = "loc.fai_varrock_ladder_from_cellar"

        private const val ClimbAnim = "seq.human_reachforladder"

        private const val PhoenixClimbDownAnim = "seq.human_pickupfloor"

        private val CellarCoord = CoordGrid(3232, 9801, 0)
        private val InnCoord = CoordGrid(3232, 3401, 0)

        private val CellarLadderCoord = CoordGrid(3232, 3402, 0)
        private val PhoenixLadderCoord = CoordGrid(3244, 3383, 0)

        private const val UndergroundZOffset = 6400

        private const val Coins = "obj.coins"
        private const val Beer = "obj.beer"

        private const val BeerPrice = 2
    }
}
