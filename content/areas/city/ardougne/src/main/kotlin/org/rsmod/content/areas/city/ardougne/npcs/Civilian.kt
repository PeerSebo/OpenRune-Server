package org.rsmod.content.areas.city.ardougne.npcs

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.other.pets.cats.CatPet
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Civilian @Inject constructor(private val exchange: CatExchange) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(BLONDE) { talk(it.npc) { blonde() } }
        onOpNpc1(BROWN) { talk(it.npc) { brown() } }
        onOpNpc1(BALD) { talk(it.npc) { bald() } }
        for (npc in listOf(BLONDE, BROWN, BALD)) {
            onOpNpcU(npc) { useItem(it.npc, it.objType) }
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc, intro: suspend Dialogue.() -> Unit) =
        startDialogue(npc) {
            intro()
            when {
                hasAdultCat(player) -> offerCat()
                WITCHS_CAT in player.inv -> witchsCat()
                else -> chatPlayer(neutral, "No, you're right, you don't see many around.")
            }
        }

    private suspend fun Dialogue.blonde() {
        chatPlayer(happy, "Hello there.")
        chatNpc(bored, "Oh hello, I'm sorry, I'm a bit worn out.")
        chatPlayer(quiz, "Busy day?")
        chatNpc(
            angry,
            "Oh, It's those mice! They're everywhere! What I really need is a cat. But they're hard to " +
                "come by nowadays.",
        )
    }

    private suspend fun Dialogue.brown() {
        chatPlayer(happy, "Hi there.")
        chatNpc(happy, "Good day to you traveller.")
        chatPlayer(quiz, "What are you up to?")
        chatNpc(bored, "Chasing mice as usual! It's all I seem to do nowadays.")
        chatPlayer(quiz, "You must waste a lot of time?")
        chatNpc(neutral, "Yes, but what can you do? It's not like there's many cats around here!")
    }

    private suspend fun Dialogue.bald() {
        chatPlayer(happy, "Hello there.")
        chatNpc(bored, "I'm a bit busy to talk right now, sorry.")
        chatPlayer(quiz, "Why? What are you doing?")
        chatNpc(angry, "Trying to kill these mice! What I really need is a cat!")
    }

    private suspend fun Dialogue.offerCat() {
        val sell = choice2("I have a cat that I could sell.", true, "Nope, they're not easy to get hold of.", false)
        if (!sell) {
            chatPlayer(neutral, "Nope, they're not easy to get hold of.")
            return
        }
        chatPlayer(happy, "I have a cat that I could sell.")
        chatNpc(quiz, "You don't say, is that it ?")
        chatPlayer(happy, "Say hello to a real mouse killer!")
        chatNpc(happy, "Hmmm, not bad, not bad at all. Looks like it's a lively one.")
        chatPlayer(worried, "Erm...kind of...")
        chatNpc(neutral, "I don't have much in the way of money. I do have these!")
        mesbox("The peasant shows you a sack of Death Runes.")
        chatNpc(
            happy,
            "The dwarves bring them from the mine for us. Tell you what, I'll give you ${runes(player)} " +
            "Death Runes for the cat.",
        )
        val deal = choice2("Nope, I'm not parting for that.", false, "Ok then, you've got a deal.", true)
        if (!deal) {
            chatPlayer(neutral, "Nope, I'm not parting for that.")
            chatNpc(angry, "Well, I'm not giving you any more!")
            return
        }
        chatPlayer(happy, "Ok then, you've got a deal.")
        if (exchange.sellFirstAvailable(player)) {
            mesbox("You hand over the cat. You are given ${runes(player)} Death Runes.")
            chatNpc(happy, "Great, thanks for that!")
        } else {
            chatNpc(neutral, "You need a grown cat and enough room for the runes.")
        }
    }

    private suspend fun Dialogue.witchsCat() {
        chatPlayer(happy, "I have a cat...look!")
        chatNpc(bored, "Hmmm...doesn't look like it's seen daylight in years. That's not going to catch any mice!")
    }

    private suspend fun ProtectedAccess.useItem(npc: Npc, obj: ItemServerType) {
        if (obj.internalName == WITCHS_CAT) {
            startDialogue(npc) { witchsCat() }
            return
        }
        val cat = CatPet.fromObj(obj.id)
        if (cat == null) {
            mes("Nothing interesting happens.")
            return
        }
        if (cat.isKitten) {
            mes("Nothing interesting happens.")
            return
        }
        val runes = runes(player)
        if (!exchange.sellHeld(player, cat)) {
            mes("You need a grown cat and enough room for the runes.")
            return
        }
        startDialogue(npc) {
            mesbox("You hand over the cat. You are given $runes Death Runes.")
            chatNpc(happy, "Great, thanks for that!")
            chatPlayer(happy, "That's ok, take care.")
        }
    }

    private fun hasAdultCat(player: Player): Boolean {
        val following = exchange.following(player)
        return following?.isKitten == false || CatPet.all.any { !it.isKitten && it.obj in player.inv }
    }

    private fun runes(player: Player): Int = exchange.runes(player)

    private companion object {
        const val BLONDE = "npc.wantcat1"
        const val BROWN = "npc.wantcat2"
        const val BALD = "npc.wantcat3"
        const val WITCHS_CAT = "obj.cavewitchcat"
    }
}
