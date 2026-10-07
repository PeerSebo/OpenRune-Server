package org.rsmod.content.quest.area.portsarim

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.Banana
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.BananaRum
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.CRATE_BANANAS
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.CRATE_RUM
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.Coins
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.CrateCapacity
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.CratePayment
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.KaramjaRum
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.LUTHAS_EMPLOYED
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.PutDownSound
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.SlicedBananaRum
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.TakeFromContainerSeq
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.crateBananas
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.crateHasRum
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.isLuthasEmployee
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PiratesTreasureKaramja : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.luthas") { talkToLuthas(it.npc) }

        onOpLoc1(Crate) { searchCrate() }
        onOpLoc2(Crate) { fillCrate() }

        onOpLocU(Crate, KaramjaRum) { stashRum() }
        onOpLocU(Crate, Banana) { packOneBanana() }
        onOpLocU(Crate, BananaRum) {
            arriveDelay()
            mes("Pirates don't usually take banana with their Karamjan rum.")
        }
        onOpLocU(Crate, SlicedBananaRum) {
            arriveDelay()
            mes("Pirates don't usually take sliced banana with their Karamjan rum.")
        }
    }

    private suspend fun ProtectedAccess.talkToLuthas(npc: Npc) {
        startDialogue(npc) { luthasDialogue(npc) }
    }

    private suspend fun Dialogue.luthasDialogue(npc: Npc) {
        if (!isLuthasEmployee(player)) {
            chatNpc(happy, "Hello I'm Luthas, I run the banana plantation here.")
            when (
                choice2(
                    "Could you offer me employment on your plantation?",
                    1,
                    "That customs officer is annoying isn't she?",
                    2,
                )
            ) {
                1 -> askForWork()
                2 -> customsGossip()
            }
            return
        }

        if (crateBananas(player) >= CrateCapacity) {
            payForCrate(npc)
            return
        }

        chatNpc(quiz, "How are you getting on with that crate?")
        when (
            choice3(
                "I'm still working on it.",
                1,
                "So where are these bananas going to be delivered to?",
                2,
                "That customs officer is annoying isn't she?",
                3,
            )
        ) {
            1 -> chatPlayer(neutral, "I'm still working on it.")
            2 -> whereDoTheyGo()
            3 -> customsGossip()
        }
    }

    private suspend fun Dialogue.askForWork() {
        chatPlayer(quiz, "Could you offer me employment on your plantation?")
        chatNpc(
            happy,
            "Yes, I can sort something out. There's a crate ready to be loaded onto the ship.",
        )
        chatNpc(
            neutral,
            "You wouldn't believe the demand for bananas from Wydin's shop over in Port Sarim. I " +
                "think this is the third crate I've shipped him this month..",
        )
        chatNpc(happy, "If you could fill it up with bananas, I'll pay you 30 gold.")
        LUTHAS_EMPLOYED.set(player, true)
    }

    private suspend fun Dialogue.customsGossip() {
        chatPlayer(quiz, "That customs officer is annoying isn't she?")
        chatNpc(neutral, "Well I know her pretty well. She doesn't cause me any trouble any more.")
        chatNpc(
            happy,
            "She doesn't even search my export crates any more. She knows they only contain bananas.",
        )
        chatPlayer(quiz, "Really? How interesting. Whereabouts do you send those to?")
        chatNpc(
            neutral,
            "There is a little shop over in Port Sarim that buys them up by the crate. I believe " +
                "it is run by a man called Wydin.",
        )
    }

    private suspend fun Dialogue.whereDoTheyGo() {
        chatPlayer(quiz, "So where are these bananas going to be delivered to?")
        chatNpc(neutral, "I sell them to Wydin who runs a grocery store in Port Sarim.")
    }

    private suspend fun Dialogue.payForCrate(npc: Npc) {
        chatPlayer(happy, "I've filled a crate with bananas.")
        chatNpc(happy, "Well done, here's your payment.")

        access.invAdd(access.inv, Coins, CratePayment)
        access.mes("Luthas hands you $CratePayment coins.")

        CRATE_BANANAS.set(player, 0)

        when (
            choice3(
                "Will you pay me for another crate full?",
                1,
                "Thank you, I'll be on my way",
                2,
                "So where are these bananas going to be delivered to?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Will you pay me for another crate full?")
                chatNpc(happy, "Yes certainly.")
                chatNpc(
                    neutral,
                    "If you go outside you should see the old crate has been loaded on to the " +
                        "ship, and there is another empty crate in its place.",
                )
            }
            2 -> chatPlayer(happy, "Thank you, I'll be on my way.")
            3 -> whereDoTheyGo()
        }
    }

    private suspend fun ProtectedAccess.searchCrate() {
        arriveDelay()

        val bananas = crateBananas(player)
        val rum = crateHasRum(player)

        if (rum && bananas == 0) {
            mes(
                "There is some rum in here, although with no bananas to cover it. It is a little obvious."
            )
            return
        }

        if (bananas >= CrateCapacity) {
            mes("The crate is full of bananas.")
        } else {
            val plural = if (bananas == 1) "banana" else "bananas"
            mes("The crate has $bananas $plural inside.")
        }

        if (rum) {
            mes("There is also some rum stashed in here too.")
        }
    }

    private suspend fun ProtectedAccess.fillCrate() {
        arriveDelay()

        if (!isLuthasEmployee(player)) {
            mes("Why would I want to do that?")
            return
        }

        if (crateBananas(player) >= CrateCapacity) {
            mes("The crate is already full.")
            return
        }

        val carried = inv.count(Banana)
        if (carried <= 0) {
            mes("You have no bananas to put in the crate.")
            return
        }

        val room = CrateCapacity - crateBananas(player)
        val packed = minOf(carried, room)
        if (invDel(inv, Banana, packed).failure) {
            return
        }

        CRATE_BANANAS.set(player, crateBananas(player) + packed)
        anim(TakeFromContainerSeq)
        soundSynth(PutDownSound)
        mesbox("You pack all your bananas into the crate.")
    }

    private suspend fun ProtectedAccess.packOneBanana() {
        arriveDelay()

        if (!isLuthasEmployee(player)) {
            mes("Why would I want to do that?")
            return
        }

        if (crateBananas(player) >= CrateCapacity) {
            mes("The crate is already full.")
            return
        }

        if (invDel(inv, Banana, 1).failure) {
            return
        }

        CRATE_BANANAS.set(player, crateBananas(player) + 1)
        anim(TakeFromContainerSeq)
        soundSynth(PutDownSound)
        mes("You pack a banana into the crate.")
    }

    private suspend fun ProtectedAccess.stashRum() {
        arriveDelay()

        if (crateHasRum(player)) {
            mes("There's already some rum in here...")
            return
        }

        if (invDel(inv, KaramjaRum, 1).failure) {
            return
        }

        CRATE_RUM.set(player, true)
        anim(TakeFromContainerSeq)
        soundSynth(PutDownSound)
        mesbox("You stash the rum in the crate.")
    }

    private companion object {
        private const val Crate = "loc.bananacrate"
    }
}
