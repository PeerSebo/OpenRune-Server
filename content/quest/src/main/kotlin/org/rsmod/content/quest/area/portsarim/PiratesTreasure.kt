package org.rsmod.content.quest.area.portsarim

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.BananaRum
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.Casket
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.ChestKey
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.ChestOpenSound
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.Coins
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.Complete
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.KaramjaRum
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.KeyObtained
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.MessageRead
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.PirateMessage
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.QuestKey
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.QuestVarp
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.RumRequested
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.SlicedBananaRum
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class PiratesTreasure :
    QuestScript(
        QuestKey,
        QuestVarp,
        rewards { extra("One-Eyed Hector's Treasure") },
        ItemRewardDisplay(Casket, x = 77, y = 126),
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == Complete) {
            "Pirate's Treasure end state changed: dbrow says ${quest.maxSteps}, script says $Complete"
        }

        onOpNpc1("npc.redbeard_frank") { talkToFrank(it.npc) }

        onOpNpcU("npc.redbeard_frank", KaramjaRum) { useRumOnFrank(it.npc) }
        onOpNpcU("npc.redbeard_frank", BananaRum) { offerSpoiledRum(it.npc, BananaRum) }
        onOpNpcU("npc.redbeard_frank", SlicedBananaRum) { offerSpoiledRum(it.npc, SlicedBananaRum) }
        onOpNpcU("npc.redbeard_frank", Casket) { offerFrankAShare(it.npc) }

        onOpHeld1(Casket) { openCasket() }

        onOpHeld4(KaramjaRum) { drinkRum() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Redbeard Frank</col> in <col=800000>Port Sarim</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "<red>Redbeard Frank</red> knows where a pirate treasure is buried, and he will " +
                    "part with the location for a bottle of <red>Karamjan rum</red>."
            )

            objective(
                "I need to get <red>Redbeard Frank</red> a bottle of rum brewed on <red>Karamja</red>. " +
                    "The customs officer will not let me carry it off the island, so I will have to " +
                    "be sneakier than that."
            ) {
                stageAtLeast(KeyObtained, "I gave Frank his rum, and he gave me a key.")
                    .strike()
                    .finalise()
            }

            objective(
                "Frank's key opens <red>One-Eyed Hector's</red> chest, upstairs in the " +
                    "<red>Blue Moon Inn</red> in <red>Varrock</red>."
            ) {
                visibleWhen { quest.getQuestStage(access.player) >= KeyObtained }
                stageAtLeast(MessageRead, "The chest held a message with a clue in it.")
                    .strike()
                    .finalise()
            }

            objective(
                "The clue points at the statue in <red>Falador Park</red>. I should take a " +
                    "<red>spade</red>, and something to defend myself with."
            ) {
                visibleWhen { quest.getQuestStage(access.player) >= MessageRead }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Redbeard Frank, an old pirate in Port Sarim, offered to tell me where his late " +
                    "captain One-Eyed Hector buried his treasure - in exchange for a bottle of " +
                    "Karamjan rum."
            )
            line(
                "The customs officer on Karamja would not let me leave the island with it, so I " +
                    "took a job loading Luthas' banana crates and shipped the rum out under ten " +
                    "bananas, then took another job at Wydin's food store in Port Sarim to get at " +
                    "the crate again."
            )
            line(
                "Frank traded me Hector's chest key. The chest in the Blue Moon Inn held a clue, " +
                    "and the clue led to Falador Park, where a very angry gardener took exception " +
                    "to my digging."
            )
            line("Under the park I found One-Eyed Hector's casket.")
        }

    private suspend fun ProtectedAccess.talkToFrank(npc: Npc) {
        startDialogue(npc) { frankDialogue(npc) }
    }

    private suspend fun Dialogue.frankDialogue(npc: Npc) {
        chatNpc(happy, "Arr, Matey!")

        when (quest.getQuestStage(player)) {
            PiratesTreasureState.NotStarted -> notStarted(npc)
            RumRequested -> rumRequested(npc)
            else -> afterHandIn(npc)
        }
    }

    private suspend fun Dialogue.notStarted(npc: Npc) {
        val choice =
            choice3(
                "I'm in search of treasure.",
                1,
                "Arr!",
                2,
                "Do you have anything for trade?",
                3,
            )

        when (choice) {
            1 -> offerTheQuest(npc)
            2 -> {
                chatPlayer(happy, "Arr!")
                chatNpc(happy, "Arr!")
            }
            3 -> {
                chatPlayer(quiz, "Do you have anything for trade?")
                chatNpc(short, "Nay, I be no merchant. Just a thirsty old sea dog.")
            }
        }
    }

    private suspend fun Dialogue.offerTheQuest(npc: Npc) {
        chatPlayer(neutral, "I'm in search of treasure.")
        chatNpc(
            happy,
            "Arr, treasure you be after eh? Well I might be able to tell you where to find some... For a price...",
        )
        chatPlayer(quiz, "What sort of price?")
        chatNpc(
            neutral,
            "Well for example if you can get me a bottle of rum... Not just any rum mind...",
        )
        chatNpc(happy, "I'd like some rum made on Karamja Island. There's no rum like Karamja Rum!")

        val start =
            choice2("Yes.", true, "No.", false, title = "Start the Pirate's Treasure quest?")

        if (!start) {
            chatPlayer(neutral, "Not right now.")
            chatNpc(
                happy,
                "Fair enough. I'll still be here and thirsty whenever you feel like helpin' out.",
            )
            return
        }

        chatPlayer(happy, "Ok, I will bring you some Karamja Rum.")
        quest.setQuestStage(access, RumRequested)
        chatNpc(happy, "Yer a saint, although it'll take a miracle to get it off Karamja.")
        explainCustoms(firstTime = true)
    }

    private suspend fun Dialogue.rumRequested(npc: Npc) {
        chatNpc(quiz, "Have ye brought some rum for yer ol' mate Frank?")

        if (player.inv.count(BananaRum) > 0) {
            chatPlayer(happy, "Yes, I've got some.")
            mesbox("Frank takes the bottle from you, then hands it straight back.")
            chatNpc(angry, "Arr - this here rum's got a banana stuck in it!")
            return
        }

        if (player.inv.count(SlicedBananaRum) > 0) {
            chatPlayer(happy, "Yes, I've got some.")
            mesbox("Frank takes the bottle from you, then hands it straight back.")
            chatNpc(angry, "Arr - I don't likes banana in me rum!")
            return
        }

        if (player.inv.count(KaramjaRum) <= 0) {
            chatPlayer(sad, "No, not yet.")
            chatNpc(neutral, "Not surprising, tis no easy task to get it off Karamja.")
            explainCustoms(firstTime = false)
            return
        }

        chatPlayer(happy, "Yes, I've got some.")
        handOverRum(npc)
    }

    private suspend fun Dialogue.explainCustoms(firstTime: Boolean) {
        chatPlayer(quiz, "What do you mean?")
        chatNpc(
            neutral,
            "The Customs office has been clampin' down on the export of spirits. You seem like a " +
                "resourceful young lad, I'm sure ye'll be able to find a way to slip the stuff " +
                "past them.",
        )
        if (firstTime) {
            chatPlayer(happy, "Well I'll give it a shot.")
            chatNpc(happy, "Arr, that's the spirit!")
        } else {
            chatPlayer(happy, "Well I'll give it another shot.")
        }
    }

    private suspend fun ProtectedAccess.useRumOnFrank(npc: Npc) {
        if (quest.getQuestStage(player) != RumRequested) {
            talkToFrank(npc)
            return
        }
        startDialogue(npc) {
            chatPlayer(happy, "Frank, I have some Karamja Rum.")
            handOverRum(npc)
        }
    }

    private suspend fun ProtectedAccess.offerSpoiledRum(npc: Npc, rum: String) {
        startDialogue(npc) {
            chatPlayer(happy, "Frank, I have some Karamja Rum.")
            mesbox("Frank takes the bottle from you, then hands it straight back.")
            if (rum == BananaRum) {
                chatNpc(angry, "Arr - this here rum's got a banana stuck in it!")
            } else {
                chatNpc(angry, "Arr - I don't likes banana in me rum!")
            }
        }
    }

    private suspend fun Dialogue.handOverRum(npc: Npc) {
        chatNpc(
            neutral,
            "Now a deal's a deal, I'll tell ye about the treasure. I used to serve under a pirate " +
                "captain called One-Eyed Hector.",
        )
        chatNpc(
            neutral,
            "Hector were very successful and became very rich. But about a year ago we were " +
                "boarded by the Customs and Excise Agents.",
        )
        chatNpc(
            sad,
            "Hector were killed along with many of the crew, I were one of the few to escape and " +
                "I escaped with this.",
        )

        if (access.invDel(access.inv, KaramjaRum, 1).failure) {
            return
        }
        access.invAdd(access.inv, ChestKey, 1)
        quest.setQuestStage(access, KeyObtained)

        objbox(ChestKey, "Frank happily takes the rum... and hands you a key.")

        chatNpc(
            happy,
            "This be Hector's key. I believe it opens his chest in his old room in the Blue Moon " +
                "Inn in Varrock.",
        )
        chatNpc(happy, "With any luck his treasure will be in there.")

        when (choice2("Ok thanks, I'll go and get it.", 1, "So why didn't you ever get it?", 2)) {
            1 -> chatPlayer(happy, "Ok thanks, I'll go and get it.")
            2 -> {
                chatPlayer(quiz, "So why didn't you ever get it?")
                chatNpc(
                    sad,
                    "I'm not allowed in the Blue Moon Inn. Apparently I'm a drunken trouble maker.",
                )
            }
        }
    }

    private suspend fun Dialogue.afterHandIn(npc: Npc) {
        if (needsReplacementKey(player)) {
            chatPlayer(sad, "I seem to have lost my chest key...")
            chatNpc(
                laugh,
                "Arr, silly you. Fortunately I took the precaution to have another one made.",
            )
            if (access.invAdd(access.inv, ChestKey, 1).success) {
                objbox(ChestKey, "Frank hands you a chest key.")
            }
            return
        }

        when (choice2("Arr!", 1, "Do you have anything for trade?", 2)) {
            1 -> {
                chatPlayer(happy, "Arr!")
                chatNpc(happy, "Arr!")
            }
            2 -> {
                chatPlayer(quiz, "Do you have anything for trade?")
                chatNpc(short, "Nay, I be no merchant. Just a thirsty old sea dog.")
            }
        }
    }

    private fun needsReplacementKey(player: Player): Boolean {
        if (quest.getQuestStage(player) != KeyObtained) {
            return false
        }
        return player.inv.count(ChestKey) == 0 && player.inv.count(PirateMessage) == 0
    }

    private suspend fun ProtectedAccess.offerFrankAShare(npc: Npc) {
        startDialogue(npc) {
            chatPlayer(happy, "I have the treasure, would you like a share?")
            chatNpc(happy, "No lad, you got it fair and square.")
            chatNpc(happy, "You enjoy it. It's what Hector would have wanted.")
        }
    }

    private suspend fun ProtectedAccess.openCasket() {
        val needed = if (inv.count(Coins) > 0) 2 else 3
        if (inv.freeSpace() + 1 < needed) {
            mes("You don't have enough inventory space to open the casket.")
            return
        }

        if (invDel(inv, Casket, 1).failure) {
            return
        }

        mes("You open the casket, and find One-Eyed Hector's treasure.")
        soundSynth(ChestOpenSound)

        invAdd(inv, Coins, CasketCoins)
        invAdd(inv, "obj.gold_ring", 1)
        invAdd(inv, "obj.emerald", 1)
    }

    private suspend fun ProtectedAccess.drinkRum() {
        if (invDel(inv, KaramjaRum, 1).failure) {
            return
        }

        anim(DrinkSeq)
        soundSynth(DrinkSound)

        statHeal("stat.hitpoints", constant = RumHeal, percent = 0)
        statBoost("stat.strength", constant = 1, percent = 5)
        statDrain("stat.attack", constant = 3, percent = 2)

        mes("You drink the rum.")
        mes("You feel slightly reinvigorated...")
        mes("...and slightly dizzy too.")
    }

    private companion object {
        private const val CasketCoins = 450

        private const val DrinkSeq = "seq.human_eat"
        private const val DrinkSound = "synth.LIQUID"

        private const val RumHeal = 5
    }
}
