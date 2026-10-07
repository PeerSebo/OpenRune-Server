package org.rsmod.content.areas.city.falador.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FaladorBarsScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onCounterTalk("npc.partyroom_barmaid") { npc -> startDialogue(npc) { megan(npc) } }
        onCounterTalk("npc.partyroom_barmaid2") { startDialogue(it) { lucy() } }
    }

    private suspend fun Dialogue.megan(npc: Npc) {
        chatNpc(happy, "Hi! I'm Megan. Welcome to the Party Room!")
        when (
            choice3(
                "One beer please Megan!",
                1,
                "Can you dance, Megan?",
                2,
                "Do you have any news?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "One beer please Megan!")
                partyRoomBeer("Megan")
            }
            2 -> {
                chatPlayer(quiz, "Can you dance, Megan?")
                chatNpc(happy, "Can I dance?!")
                chatNpc(laugh, "CAN I dance?!!")
                chatPlayer(happy, "Dance with me Megan!")
                danceTogether(npc)
            }
            else -> {
                chatPlayer(quiz, "Do you have any news?")
                chatNpc(
                    neutral,
                    "Not at the moment. I've heard that the known world is expanding as new places " +
                        "are discovered.",
                )
                chatNpc(happy, "These are exciting times indeed!")
            }
        }
    }

    private suspend fun Dialogue.lucy() {
        chatNpc(happy, "Hi! I'm Lucy. Welcome to the Party Room!")
        chatPlayer(happy, "One beer please Lucy!")
        partyRoomBeer("Lucy")
    }

    private suspend fun Dialogue.partyRoomBeer(barmaid: String) {
        chatNpc(happy, "Coming right up! That's two gold please.")
        if (!player.invTakeFee(BEER_PRICE)) {
            chatPlayer(sad, "I'm sorry but I don't have enough money!")
            return
        }
        player.invAddOrDrop(objRepo, "obj.beer")
        objbox("obj.beer", "$barmaid has given you a beer.")
    }

    private suspend fun Dialogue.danceTogether(npc: Npc) {
        access.anim(SEQ_DANCE)
        npc.anim(SEQ_DANCE)
        access.delay(DANCE_CYCLES)
        access.anim(SEQ_CHEER)
        npc.anim(SEQ_CHEER)
        access.delay(CHEER_CYCLES)
        access.anim(SEQ_BOW)
        npc.anim(SEQ_BOW)
    }

    private companion object {
        const val BEER_PRICE = 2

        const val SEQ_DANCE = "seq.emote_dance"
        const val SEQ_CHEER = "seq.emote_cheer"
        const val SEQ_BOW = "seq.emote_bow"
        const val DANCE_CYCLES = 5
        const val CHEER_CYCLES = 3
    }
}
