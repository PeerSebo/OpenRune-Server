package org.rsmod.content.quest.area.lumbridge

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import dev.openrune.types.aconverted.interf.IfSubType
import kotlin.math.min
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.front
import org.rsmod.api.player.hit.PlayerDefendSounds
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.torso
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.XpRewardLamp
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

internal class IdesOfMilkDialogue(
    private val quest: Quest,
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
    private val alignment: TextAlignment,
    private val instantHitProcessor: InstantPlayerHitProcessor,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
    private val objRepo: ObjRepository,
) {
    private var Player.gillieInformed by intVarBit(IdesOfMilkRefs.VarbitGillieInformed)
    private var Player.sethInformed by intVarBit(IdesOfMilkRefs.VarbitSethInformed)
    private var Player.rewardClaimed by intVarBit(IdesOfMilkRefs.VarbitRewardClaimed)
    private var Player.rewardLampUsed by intVarBit(IdesOfMilkRefs.VarbitRewardLamp)

    fun ScriptContext.register() {
        onOpNpc1(IdesOfMilkRefs.Cassius) { cassius(it.npc) }
        onOpNpc1(IdesOfMilkRefs.Gillie) { gillie(it.npc) }
        onOpNpc1(IdesOfMilkRefs.Seth) { seth(it.npc) }

        onOpLoc1(IdesOfMilkRefs.Shelf) { searchShelf() }

        onOpHeld1(IdesOfMilkRefs.Book) { readBook() }
        onOpHeld1(IdesOfMilkRefs.Sample1) { drinkSampleOne() }
        onOpHeld1(IdesOfMilkRefs.Sample2) { drinkSampleTwo() }
        onOpHeld1(IdesOfMilkRefs.RewardLamp) { rubRewardLamp() }

        onPlayerSoftTimer(IdesOfMilkRefs.TimerMilkSplat) { player.milkSplat() }

        onOpHeld1(IdesOfMilkRefs.CowbellAmuletCharged) {
            cowbellTeleportHeld(it.inventory, it.slot)
        }
        onOpWorn3(IdesOfMilkRefs.CowbellAmuletCharged) { cowbellTeleportWorn() }
        onOpHeld3(IdesOfMilkRefs.CowbellAmuletCharged) {
            cowbellRing(IdesOfMilkRefs.CowbellAmuletCharged)
        }
        onOpHeld3(IdesOfMilkRefs.CowbellAmulet) { cowbellRing(IdesOfMilkRefs.CowbellAmulet) }
        onOpWorn2(IdesOfMilkRefs.CowbellAmuletCharged) {
            cowbellRing(IdesOfMilkRefs.CowbellAmuletCharged)
        }
        onOpWorn2(IdesOfMilkRefs.CowbellAmulet) { cowbellRing(IdesOfMilkRefs.CowbellAmulet) }
        onOpHeld4(IdesOfMilkRefs.CowbellAmuletCharged) { cowbellCheck(it.inventory[it.slot]) }
        onOpWorn4(IdesOfMilkRefs.CowbellAmuletCharged) { cowbellCheck(player.front) }
        onOpHeld5(IdesOfMilkRefs.CowbellAmuletCharged) { cowbellUncharge(it.inventory, it.slot) }
        onOpHeld4(IdesOfMilkRefs.CowbellAmulet) { cowbellCharge(it.inventory, it.slot) }
        onOpHeldU(IdesOfMilkRefs.CowbellAmulet, IdesOfMilkRefs.AirRune) {
            cowbellCharge(inv, it.firstSlot)
        }
        onOpHeldU(IdesOfMilkRefs.CowbellAmuletCharged, IdesOfMilkRefs.AirRune) {
            cowbellCharge(inv, it.firstSlot)
        }
    }

    private suspend fun ProtectedAccess.cassius(npc: Npc) {
        val stage = quest.getQuestStage(player)
        when {
            stage == IdesOfMilkStage.NotStarted -> startDialogue(npc) { cassiusIntro() }
            stage == IdesOfMilkStage.Accepted -> startDialogue(npc) { cassiusIntroFromProfit() }
            stage == IdesOfMilkStage.IntroMilk -> startDialogue(npc) { cassiusIntroFromEmpire() }
            stage < IdesOfMilkStage.Interviewed -> startDialogue(npc) { cassiusReminder() }
            stage < IdesOfMilkStage.BookSnatched -> startDialogue(npc) { cassiusResearchReport() }
            stage < IdesOfMilkStage.SampleOneHeld -> startDialogue(npc) { cassiusBookVerdict() }
            stage < IdesOfMilkStage.SampleOneDrunk ->
                startDialogue(npc) { cassiusSampleOnePending() }
            stage < IdesOfMilkStage.SampleTwoHeld -> startDialogue(npc) { cassiusPermitTask() }
            stage < IdesOfMilkStage.BullSlain -> startDialogue(npc) { cassiusPermitProgress() }
            stage < IdesOfMilkStage.Complete -> startDialogue(npc) { cassiusConfrontation() }
            else -> mesbox("Cassius makes a point of ignoring you.")
        }
    }

    private suspend fun Dialogue.cassiusIntro() {
        chatNpc(happy, "You!")
        chatPlayer(quiz, "Me?")
        chatNpc(happy, "Yes you!")
        chatPlayer(quiz, "Can I... help you?")
        chatNpc(quiz, "That depends. Do you like making money?")
        chatPlayer(neutral, "I'm not averse to making money.")
        chatNpc(quiz, "And do you enjoy... milk?")
        chatPlayer(quiz, "Milk?")
        chatNpc(neutral, "Milk.")
        chatPlayer(neutral, "I can't say I've thought much about it.")
        chatNpc(quiz, "Would you like to think more about it?")

        if (player.combatLevel < IdesOfMilkRefs.RecommendedCombat) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of ${IdesOfMilkRefs.RecommendedCombat}."
            )
        }

        val accept = choice2("Yes.", true, "No.", false, title = "Start The Ides of Milk quest?")
        if (!accept) {
            chatPlayer(neutral, "No, I'm not sure I would like that, thank you.")
            return
        }

        quest.setQuestStage(access, IdesOfMilkStage.Accepted)
        cassiusIntroFromProfit()
    }

    private suspend fun Dialogue.cassiusIntroFromProfit() {
        chatPlayer(quiz, "What am I going to get from thinking more about... milk?")
        chatNpc(happy, "Profit!")
        chatNpc(neutral, "Ten to fifteen percent, performance depending.")
        chatPlayer(quiz, "How are we going to be making that profit? And who even are you anyway?")
        chatNpc(shifty, "Ah, my name is Cassius. I got carried away.")
        chatNpc(shifty, "But, we're going to construct an empire.")
        chatPlayer(quiz, "Of milk?")
        quest.setQuestStage(access, IdesOfMilkStage.IntroMilk)
        chatNpc(shifty, "Of milk.")
        cassiusIntroFromEmpire()
    }

    private suspend fun Dialogue.cassiusIntroFromEmpire() {
        chatPlayer(quiz, "How do you intend to make an empire of milk?")
        chatNpc(neutral, "So, the first step is going to involve you doing a little... research.")
        chatPlayer(confused, "Right...")
        chatNpc(
            shifty,
            "You're going to cross the River Lum, find Gillie and Seth Groats, and learn about " +
                "animal husbandry.",
        )
        chatNpc(
            shifty,
            "Then... Then you're going to cross back to this side of the river, and tell me all " +
                "about how they've made their animals so productive.",
        )
        chatNpc(happy, "It's a perfect plan!")
        chatPlayer(
            quiz,
            "What use is it if I know everything and you don't? Couldn't I make my own... milk " +
                "empire... with that information?",
        )
        chatPlayer(neutral, "If anything I should be cutting you in at ten to fifteen percent.")
        chatNpc(neutral, "Hey, I'm the one supplying the high-quality milk here.")
        quest.setQuestStage(access, IdesOfMilkStage.IntroDone)
        chatNpc(
            shifty,
            "Not to mention, my associate Brutus is already embedded in the Groats operation to " +
                "work a different angle. That will prove most lucrative.",
        )
        chatPlayer(confused, "Right... I guess I'll go and see the Groatses then.")
    }

    private suspend fun Dialogue.cassiusReminder() {
        chatPlayer(quiz, "What was it you needed me to do again?")
        chatNpc(
            neutral,
            "I need you to speak to Gillie and Seth Groats. We need to learn " +
                "everything we can.",
        )
        chatNpc(
            neutral,
            "They live just across the River Lum. Head east from here and you should be able to " +
                "find them.",
        )
    }

    private suspend fun Dialogue.cassiusResearchReport() {
        chatNpc(quiz, "So, how did you get on?")
        chatPlayer(
            happy,
            "I've learned a lot about how the Groats family care for their animals. They say " +
                "the secret is happiness!",
        )
        chatNpc(
            angry,
            "Happiness?! That can't be the only trick they use! Surely you found something else?",
        )

        if (!access.player.inv.contains(IdesOfMilkRefs.Book)) {
            chatPlayer(
                neutral,
                "Now that you mention it, Seth did say he had a book that I... don't have on me " +
                    "right now.",
            )
            chatNpc(
                happy,
                "The legendary Groats book on milk cultivation? I'm most interested! " +
                    "Go fetch it!",
            )
            return
        }

        chatPlayer(
            neutral,
            "I have this book from Seth. He claims it's got his principles of animal husbandry " +
                "in it, if that's of any use?",
        )
        chatNpc(shifty, "Hand it here, quickly!")
        access.invDel(access.inv, IdesOfMilkRefs.Book, 1)
        quest.setQuestStage(access, IdesOfMilkStage.BookSnatched)
        objbox(IdesOfMilkRefs.Book, ObjboxZoom, "Cassius snatches the book from your hands.")
        cassiusBookVerdict()
    }

    private suspend fun Dialogue.cassiusBookVerdict() {
        chatNpc(quiz, "Now, this is most curious.")
        chatNpc(
            neutral,
            "The idea that a happy animal will do what you want is frankly absurd. What if they " +
                "choose to not produce. Are you supposed to respect that?",
        )
        chatNpc(
            shifty,
            "However, the writings on the systems of taxation are fascinating. I couldn't " +
                "imagine Seth was smart enough to put this together.",
        )
        chatPlayer(quiz, "So, do you think this is enough to start our milk empire?")
        chatNpc(
            neutral,
            "No, no. Not quite yet. First of all, you haven't tried our milk yet, " + "have you?",
        )
        chatPlayer(worried, "I don't think I have, no. You already have some milk?")
        chatNpc(neutral, "Don't look so worried. Here, have a try.")

        if (access.invAdd(access.inv, IdesOfMilkRefs.Sample1).failure) {
            chatNpc(neutral, "I'll give you some more once you've got some space.")
            return
        }
        quest.setQuestStage(access, IdesOfMilkStage.SampleOneHeld)
        objbox(IdesOfMilkRefs.Sample1, ObjboxZoom, "Cassius hands you a milk sample to try.")
    }

    private suspend fun Dialogue.cassiusSampleOnePending() {
        if (access.player.inv.contains(IdesOfMilkRefs.Sample1)) {
            chatNpc(quiz, "Have you given that sample a try yet?")
            return
        }
        chatPlayer(sad, "I've managed to lose that sample of milk...")
        if (access.invAdd(access.inv, IdesOfMilkRefs.Sample1).failure) {
            chatNpc(neutral, "I'll give you some more once you've got some space.")
            return
        }
        objbox(
            IdesOfMilkRefs.Sample1,
            ObjboxZoom,
            "Cassius glares as he hands you another sample of milk.",
        )
    }

    private suspend fun Dialogue.cassiusPermitTask() {
        chatNpc(quiz, "So, what do you think? The milk is fantastic, isn't it?")
        chatPlayer(shifty, "It wasn't what I was expecting, certainly.")
        chatNpc(
            happy,
            "We're going to change the lactic landscape! Before we do though, I have another " +
                "task for you.",
        )
        chatPlayer(angry, "Now what do you need?")
        chatNpc(neutral, "We need... a permit.")
        chatPlayer(quiz, "Shouldn't you have got a permit before making milk?")
        chatNpc(neutral, "The permit is to sell milk, not to make milk!")
        chatPlayer(quiz, "Fine, where are we going to get this permit?")
        chatNpc(
            neutral,
            "From the Duke of Lumbridge! Where else? You need to bring him our milk and I'm " +
                "sure he'll give us our permit without fuss.",
        )
        chatPlayer(sad, "Are you sure having the Duke drink our milk is a good idea?")
        chatNpc(
            shifty,
            "Of course! Soon the whole of Lumbridge will be drinking my, I mean our " + "milk!",
        )
        if (access.invAdd(access.inv, IdesOfMilkRefs.Sample2).failure) {
            chatNpc(
                neutral,
                "It looks like you don't have space to carry this. You'll have to grab it from " +
                    "me when you have room.",
            )
            return
        }
        quest.setQuestStage(access, IdesOfMilkStage.SampleTwoHeld)
        objbox(IdesOfMilkRefs.Sample2, ObjboxZoom, "Cassius hands you a milk sample.")
    }

    private suspend fun Dialogue.cassiusPermitProgress() {
        val stage = quest.getQuestStage(access.player)
        chatNpc(quiz, "Has the Duke given us our permit yet?")

        val sampleStillNeeded = stage < IdesOfMilkStage.SampleTwoDrunk
        if (sampleStillNeeded && !access.player.inv.contains(IdesOfMilkRefs.Sample2)) {
            chatPlayer(
                quiz,
                "Not yet, no. I could do with another sample of milk though, if you have one " +
                    "spare?",
            )
            chatNpc(neutral, "I should have one somewhere here. Just a moment.")
            if (access.invAdd(access.inv, IdesOfMilkRefs.Sample2).failure) {
                chatNpc(
                    neutral,
                    "It looks like you don't have space to carry this. You'll have to grab it " +
                        "from me when you have room.",
                )
                return
            }
            objbox(IdesOfMilkRefs.Sample2, ObjboxZoom, "Cassius hands you a milk sample.")
            return
        }

        if (stage < IdesOfMilkStage.GillieJudging) {
            chatPlayer(neutral, "Not yet, no, but I am working on it!")
        } else {
            chatPlayer(neutral, "It's become rather complicated, but I am working it out!")
        }
    }

    private suspend fun Dialogue.cassiusConfrontation() {
        chatPlayer(neutral, "Cassius, I don't think we'll be getting our business permit.")
        chatNpc(quiz, "Why? What's happened?")
        chatPlayer(sad, "Brutus is dead.")
        chatNpc(quiz, "Dead?")
        chatNpc(quiz, "How could this have happened?")
        chatPlayer(neutral, "So the Duke asked me to... and then Gillie said that...")
        chatNpc(angry, "You?!")
        chatNpc(angry, "Don't tell me you killed Brutus and ruined my- our, plan?")
        chatPlayer(
            neutral,
            "In my defence, I had no idea that was Brutus! You made it sound like he was a " +
                "business partner.",
        )
        chatPlayer(
            neutral,
            "Not to mention, apparently his aggression was ruining Seth's herd's milk. This " +
                "should be a good thing!",
        )
        chatNpc(angry, "Ruining the milk? The unique flavour was what I was trying to sell!")
        chatNpc(angry, "You should leave. I can't even look at you.")
        access.midiSong("midi.stop_music")
        quest.completeQuest(access)
    }

    private suspend fun ProtectedAccess.gillie(npc: Npc) {
        val stage = quest.getQuestStage(player)
        when {
            stage < IdesOfMilkStage.DukeConsulted -> startDialogue(npc) { gillieStandard() }
            stage < IdesOfMilkStage.GillieJudging -> startDialogue(npc) { gillieSampleRequest() }
            stage < IdesOfMilkStage.SampleTwoDrunk -> startDialogue(npc) { gillieDrinkNudge() }
            stage < IdesOfMilkStage.BullTaskGiven -> startDialogue(npc) { gilliePostDrink() }
            stage < IdesOfMilkStage.BullSlain -> startDialogue(npc) { gillieBullNudge() }
            stage < IdesOfMilkStage.ToldGillie -> startDialogue(npc) { gillieReport() }
            stage < IdesOfMilkStage.Complete -> startDialogue(npc) { gillieThanks() }
            else -> startDialogue(npc) { gilliePostQuest() }
        }
    }

    private suspend fun Dialogue.gillieStandard() {
        chatNpc(happy, "Hello, I'm Gillie the Milkmaid. What can I do for you?")

        val stage = quest.getQuestStage(access.player)
        val questOption = stage in IdesOfMilkStage.IntroDone until IdesOfMilkStage.DukeConsulted
        val choice =
            if (questOption) {
                choice4(
                    "Who are you?",
                    1,
                    "Can you tell me how to milk a cow?",
                    2,
                    "Can you share what makes your cows so productive?",
                    3,
                    "I'm fine, thanks.",
                    4,
                )
            } else {
                choice3(
                    "Who are you?",
                    1,
                    "Can you tell me how to milk a cow?",
                    2,
                    "I'm fine, thanks.",
                    4,
                )
            }

        when (choice) {
            1 -> {
                chatNpc(
                    happy,
                    "My name is Gillie Groats. My father is a farmer and I milk the " +
                        "cows for him.",
                )
                chatPlayer(quiz, "Do you have any buckets of milk spare?")
                chatNpc(
                    neutral,
                    "I'm afraid not. We need all of our milk to sell to market, but you can milk " +
                        "the cow yourself if you need the milk.",
                )
                chatPlayer(happy, "Thanks.")
            }
            2 -> {
                chatPlayer(quiz, "So how do you get milk from a cow then?")
                chatNpc(happy, "It's very easy. First you need an empty bucket to hold the milk.")
                chatNpc(neutral, "Then find a dairy cow to milk - you can't milk just any cow.")
                chatPlayer(quiz, "How do I find a dairy cow?")
                chatNpc(
                    neutral,
                    "They are easy to spot - they are dark brown and white, unlike beef cows, " +
                        "which are light brown and white. We also tether them to a post to stop " +
                        "them wandering around all over the place.",
                )
                chatNpc(neutral, "There are a couple very near, in this field.")
                chatNpc(
                    neutral,
                    "Then just milk the cow and your bucket will fill with tasty, " +
                        "nutritious milk.",
                )
            }
            3 -> gillieInterview()
            4 -> chatPlayer(neutral, "I'm fine, thanks.")
        }
    }

    private suspend fun Dialogue.gillieInterview() {
        chatPlayer(quiz, "Can you share what makes your cows so productive?")
        chatNpc(happy, "Absolutely! The secret is happiness.")
        chatPlayer(quiz, "Happiness?")
        chatNpc(
            happy,
            "The cows make the best milk when they're happy, and to be honest, they " +
                "make too much!",
        )
        chatNpc(
            happy,
            "I'm always glad that adventurers come by and help with milking them. I wouldn't be " +
                "able to keep up otherwise.",
        )
        chatPlayer(quiz, "That can't be everything though, can it?")
        chatNpc(
            neutral,
            "Come to think of it, I'm sure my father keeps a few tricks up his sleeve. Why " +
                "don't you go and ask him?",
        )

        val sethDone = access.player.sethInformed == 1
        access.player.gillieInformed = 1
        if (sethDone && quest.getQuestStage(access.player) == IdesOfMilkStage.IntroDone) {
            quest.setQuestStage(access, IdesOfMilkStage.Interviewed)
        }

        if (!sethDone) {
            chatPlayer(quiz, "That would be Seth, wouldn't it?")
            chatNpc(
                happy,
                "It would be! I'm sure he'll be happy to share some information with " + "you.",
            )
            chatPlayer(happy, "I'll go and speak to him now, thank you!")
        } else {
            chatPlayer(
                happy,
                "I've already had a chance to talk to him. He did indeed have some tricks to " +
                    "share!",
            )
            chatNpc(
                happy,
                "That's good. You must be ready to care for your own cows at this " + "point!",
            )
            chatPlayer(
                happy,
                "If you think so, then I must be. Thank you for your help, I'll make sure that " +
                    "knowledge is put to good use.",
            )
        }
    }

    private suspend fun Dialogue.gillieSampleRequest() {
        chatNpc(quiz, "Hiya, did you need something else from me?")
        chatPlayer(
            neutral,
            "I did. I've been trying to secure a business permit from the Duke, but he said " +
                "that as it is a milk-related business venture, I should speak to you instead.",
        )
        chatNpc(
            happy,
            "The Duke is a good man, directing you to me! I've been running the milk side of " +
                "our farm for a while now.",
        )
        chatNpc(quiz, "Were you thinking of being a competitor to us then?")

        if (!access.player.inv.contains(IdesOfMilkRefs.Sample2)) {
            chatPlayer(
                neutral,
                "I can prove we wouldn't be if you could just try this sample " + "of...",
            )
            chatNpc(quiz, "Sample of?")
            chatPlayer(
                sad,
                "I seem to have misplaced my sample of milk. I'll be back when I " + "have it.",
            )
            return
        }

        chatPlayer(
            neutral,
            "I think if you were to try this sample here you'd see we wouldn't be competing.",
        )
        chatNpc(quiz, "You want me to drink a vial of mysterious liquid?")
        chatPlayer(neutral, "Well, it's not mysterious, it's milk!")
        chatNpc(
            shifty,
            "No, I think you should drink it, I'll know the quality of it from your " + "reaction.",
        )
        chatNpc(shifty, "It is just milk after all.")
        chatPlayer(quiz, "Would it convince you to help me with my permit?")
        quest.setQuestStage(access, IdesOfMilkStage.GillieJudging)
        chatNpc(happy, "It definitely won't hurt your chances.")
    }

    private suspend fun Dialogue.gillieDrinkNudge() {
        chatNpc(quiz, "Have you tried that milk sample yet?")
        chatPlayer(sad, "Not yet. I really don't want to, but I guess I must.")
    }

    private suspend fun Dialogue.gilliePostDrink() {
        chatNpc(sad, "That looked like a horrible experience.")
        chatPlayer(neutral, "It wasn't pleasant.")
        chatNpc(quiz, "Where did you even get that milk?")
        chatPlayer(
            neutral,
            "My business partner, Cassius. I don't know where he gets it from " +
                "originally though.",
        )
        chatNpc(
            quiz,
            "Cassius... I don't recognise the name. He's not mentioned anything about " +
                "its source?",
        )
        chatPlayer(
            neutral,
            "Nothing, but he apparently has enough of a source to start a business. I'm mostly " +
                "trying to be helpful.",
        )
        chatPlayer(
            quiz,
            "If the milk's bad, you're not going to recommend us for a permit, are " + "you?",
        )
        chatNpc(neutral, "With how bad it looks, you wouldn't be any competition to us.")
        chatNpc(
            quiz,
            "However, I wonder if I'd be doing a public service by protecting anyone " +
                "else from it.",
        )
        chatPlayer(quiz, "Is there anything more I could do to convince you?")
        chatNpc(
            neutral,
            "Now that you mention it, I've had an incredibly aggressive bull disrupting my herd " +
                "lately. I've got him caught in the pen over there.",
        )
        quest.setQuestStage(access, IdesOfMilkStage.BullTaskGiven)
        chatNpc(
            quiz,
            "Perhaps you'd be interested in dealing with him? I'd owe you a fairly " +
                "large favour.",
        )
        chatPlayer(quiz, "A roughly permit sized favour?")
        chatNpc(neutral, "Absolutely.")
    }

    private suspend fun Dialogue.gillieBullNudge() {
        chatNpc(quiz, "How are you getting on with the bull?")
        chatPlayer(neutral, "I'm getting there! He looks quite tough though.")
    }

    private suspend fun Dialogue.gillieReport() {
        chatNpc(
            happy,
            "You succeeded! I'll let the Duke know that you're all good to start a " +
                "milk business.",
        )
        chatPlayer(sad, "I don't think that's going to be necessary anymore.")
        chatNpc(quiz, "Oh? What happened in that field?")
        chatPlayer(sad, "That bull was Cassius' other business partner, Brutus.")
        chatPlayer(
            neutral,
            "I'm sure he won't be feeling particularly friendly once I let him " + "know.",
        )
        chatNpc(quiz, "That bull was one of the business partners?")
        chatNpc(
            quiz,
            "I wonder if his aggression is why Cassius' herd are producing such bad " + "milk...",
        )
        chatPlayer(quiz, "I'm starting to think Cassius doesn't even have his own herd.")
        chatNpc(quiz, "You think he's been stealing milk?")
        chatPlayer(sad, "I'm not sure where else he would've been getting it from.")
        quest.setQuestStage(access, IdesOfMilkStage.ToldGillie)
        chatNpc(
            happy,
            "Why don't you go and talk to him about it! He may appreciate knowing " +
                "the Brutus was bad for business.",
        )
    }

    private suspend fun Dialogue.gillieThanks() {
        chatNpc(
            happy,
            "Thank you once again for handling Brutus, he was causing quite a " +
                "problem for my herd.",
        )
        chatNpc(
            neutral,
            "And, if you haven't yet spoken to Cassius, you should probably let him know what's " +
                "transpired.",
        )
    }

    private suspend fun Dialogue.gilliePostQuest() {
        if (access.player.rewardClaimed == 0) {
            chatNpc(happy, "Oh, just the person I wanted to see! I have something for you.")
            if (access.invAdd(access.inv, IdesOfMilkRefs.CowbellAmulet).failure) {
                chatNpc(sad, "Ah, your inventory is full. Can you make some room?")
                return
            }
            access.player.rewardClaimed = 1
            chatNpc(
                happy,
                "This is for helping out with that bull. Our cows are much happier " + "these days.",
            )
            objbox(
                IdesOfMilkRefs.CowbellAmuletCharged,
                ObjboxZoom,
                "Gillie hands you an amulet with a bell on it.",
            )
            chatNpc(happy, "One of our local milk enjoyers gave us some enchanted cow bells.")
            chatNpc(happy, "They seem to be quite versatile, enjoy!")
            chatPlayer(happy, "Wow, thank you! I'll make sure to check it out.")
            if (access.invAdd(access.inv, IdesOfMilkRefs.RewardLamp).failure) {
                chatNpc(
                    sad,
                    "I also have something else for you but your inventory is full. " +
                        "Can you make some room?",
                )
                return
            }
            chatNpc(happy, "I also have this for you!")
            objbox(IdesOfMilkRefs.RewardLamp, ObjboxZoom, "Gillie hands you a magical lamp.")
            chatPlayer(happy, "Oh, thank you!")
            return
        }

        val carriesLamp = access.player.inv.contains(IdesOfMilkRefs.RewardLamp)
        if (access.player.rewardLampUsed == 0 && !carriesLamp) {
            chatNpc(happy, "I have this for you!")
            if (access.invAdd(access.inv, IdesOfMilkRefs.RewardLamp).failure) {
                chatNpc(sad, "Ah, your inventory is full. Can you make some room?")
                return
            }
            objbox(IdesOfMilkRefs.RewardLamp, ObjboxZoom, "Gillie hands you a magical lamp.")
            chatPlayer(happy, "Oh, thank you!")
            return
        }

        val carriesAmulet =
            access.player.inv.contains(IdesOfMilkRefs.CowbellAmulet) ||
                access.player.inv.contains(IdesOfMilkRefs.CowbellAmuletCharged) ||
                access.player.worn.contains(IdesOfMilkRefs.CowbellAmulet) ||
                access.player.worn.contains(IdesOfMilkRefs.CowbellAmuletCharged)
        if (!carriesAmulet) {
            chatPlayer(sad, "I've lost that amulet you gave me...")
            chatNpc(happy, "Oh, no worries, here's another.")
            if (access.invAdd(access.inv, IdesOfMilkRefs.CowbellAmulet).failure) {
                chatNpc(sad, "Ah, your inventory is full. Can you make some room?")
                return
            }
            objbox(
                IdesOfMilkRefs.CowbellAmuletCharged,
                ObjboxZoom,
                "Gillie hands you an amulet with a bell on it.",
            )
            chatPlayer(happy, "Thank you so much!")
            chatNpc(happy, "No problem.")
            return
        }

        gillieStandard()
    }

    private suspend fun ProtectedAccess.rubRewardLamp() {
        val stat = XpRewardLamp.choose(this, selectable = CombatLampStats) ?: return
        if (!player.inv.contains(IdesOfMilkRefs.RewardLamp)) {
            return
        }
        invDel(inv, IdesOfMilkRefs.RewardLamp, 1)
        player.rewardLampUsed = 1
        val granted = statAdvance(stat, RewardLampXp, globalRate = 1.0)
        soundSynth(SynthLampReward)
        mesbox(
            "<col=0000ff>Your wish has been granted!</col> You have been awarded $granted " +
                "${XpRewardLamp.displayName(stat)} experience!"
        )
    }

    private val cowbellVarObj by lazy {
        ServerCacheManager.getVarObj(IdesOfMilkRefs.VarObjCowbellCharges.asRSCM(RSCMType.VAROBJ))
    }

    private fun cowbellCharges(obj: InvObj?): Int {
        val varobj = cowbellVarObj ?: return 0
        return obj?.vars?.getBits(varobj.bits) ?: 0
    }

    private suspend fun ProtectedAccess.cowbellTeleportHeld(inventory: Inventory, slot: Int) {
        val obj = inventory[slot] ?: return
        cowbellTeleport(obj) { updated -> inventory[slot] = updated }
    }

    private suspend fun ProtectedAccess.cowbellTeleportWorn() {
        val obj = player.front ?: return
        cowbellTeleport(obj) { updated ->
            player.front = updated
            rebuildAppearance()
        }
    }

    // Consume the teleport charge before suspending so disconnecting mid-cast cannot duplicate charges.
    private suspend fun ProtectedAccess.cowbellTeleport(obj: InvObj, update: (InvObj) -> Unit) {
        val varobj = cowbellVarObj ?: return
        val charges = obj.vars.getBits(varobj.bits)
        if (charges <= 0) {
            update(InvObj(IdesOfMilkRefs.CowbellAmulet, vars = obj.vars))
            return
        }

        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            mes(denial)
            return
        }

        val left = charges - 1
        val newVars = obj.vars.withBits(varobj.bits, left)
        val updated =
            if (left == 0) {
                InvObj(IdesOfMilkRefs.CowbellAmulet, vars = newVars)
            } else {
                obj.copy(vars = newVars)
            }
        update(updated)

        player.clearMapFlag()
        anim(IdesOfMilkRefs.SeqCowbellTeleport)
        spotanim(IdesOfMilkRefs.SpotanimCowbellTeleport, slot = 1)
        soundSynth(
            IdesOfMilkRefs.SynthCowbellTeleportCast,
            delay = IdesOfMilkRefs.SynthCowbellTeleportCastDelay,
        )

        delay(IdesOfMilkRefs.CowbellTeleportDelay)

        telejump(IdesOfMilkCoords.CowbellTeleport)
        spotanim(IdesOfMilkRefs.SpotanimCowbellTeleport, slot = 1)
        rebuildAppearance()
        soundSynth(
            IdesOfMilkRefs.SynthCowbellTeleportArrive,
            delay = IdesOfMilkRefs.SynthCowbellTeleportArriveDelay,
        )

        delay(IdesOfMilkRefs.CowbellTeleportResetDelay)
        resetAnim()
    }

    private suspend fun ProtectedAccess.cowbellRing(obj: String) {
        for (cycles in IdesOfMilkRefs.SynthCowbellRingDelays) {
            soundSynth(IdesOfMilkRefs.SynthCowbellRing, delay = cycles)
        }
        objbox(obj, "You ring the bell.")
    }

    private fun ProtectedAccess.cowbellCheck(obj: InvObj?) {
        val charges = cowbellCharges(obj)
        mes("The amulet has $charges ${plural(charges, "charge")}.")
    }

    private suspend fun ProtectedAccess.cowbellCharge(inventory: Inventory, slot: Int) {
        val varobj = cowbellVarObj ?: return
        val obj = inventory[slot] ?: return
        val curr = obj.vars.getBits(varobj.bits)
        if (curr >= IdesOfMilkRefs.CowbellMaxCharges) {
            mes("The amulet is already fully charged.")
            return
        }

        val runes = invTotal(inv, IdesOfMilkRefs.AirRune)
        if (runes <= 0) {
            mes("You need air runes to charge the amulet.")
            return
        }

        val cap = min(IdesOfMilkRefs.CowbellMaxCharges - curr, runes)
        val requested = min(countDialog("How many charges do you want to apply? (Up to $cap)"), cap)
        if (requested <= 0) {
            return
        }
        val live = inventory[slot]
        if (live == null || live.id != obj.id) {
            return
        }
        val liveCurr = live.vars.getBits(varobj.bits)
        val granted = min(requested, IdesOfMilkRefs.CowbellMaxCharges - liveCurr)
        if (granted <= 0) {
            mes("The amulet is already fully charged.")
            return
        }
        if (invDel(inv, IdesOfMilkRefs.AirRune, granted).failure) {
            return
        }

        val total = liveCurr + granted
        val newVars = live.vars.withBits(varobj.bits, total)
        inventory[slot] =
            if (liveCurr == 0) {
                InvObj(IdesOfMilkRefs.CowbellAmuletCharged, vars = newVars)
            } else {
                live.copy(vars = newVars)
            }

        val text =
            "You add $granted air ${plural(granted, "rune")} to your amulet. It now has " +
                "$total ${plural(total, "charge")}."
        soundSynth(IdesOfMilkRefs.SynthCowbellCharge)
        objbox(IdesOfMilkRefs.CowbellAmuletCharged, text)
    }

    private suspend fun ProtectedAccess.cowbellUncharge(inventory: Inventory, slot: Int) {
        val varobj = cowbellVarObj ?: return
        val obj = inventory[slot] ?: return
        val charges = obj.vars.getBits(varobj.bits)
        inventory[slot] =
            InvObj(IdesOfMilkRefs.CowbellAmulet, vars = obj.vars.withBits(varobj.bits, 0))
        if (charges <= 0) {
            return
        }
        invAddOrDrop(objRepo, IdesOfMilkRefs.AirRune, charges)

        val text =
            "You uncharge your amulet by removing $charges air ${plural(charges, "rune")} from it."
        soundSynth(IdesOfMilkRefs.SynthCowbellUncharge)
        doubleobjbox(IdesOfMilkRefs.CowbellAmulet, IdesOfMilkRefs.AirRune, text)
    }

    private fun plural(count: Int, singular: String): String =
        if (count == 1) singular else "${singular}s"

    private suspend fun ProtectedAccess.seth(npc: Npc) {
        val stage = quest.getQuestStage(player)
        when {
            stage == IdesOfMilkStage.NotStarted || stage >= IdesOfMilkStage.Complete ->
                startDialogue(npc) { chatNpc(happy, "M'arnin'....going to milk me cowsies!") }
            player.sethInformed == 0 -> startDialogue(npc) { sethInterview() }
            else -> startDialogue(npc) { sethAgain() }
        }
    }

    private suspend fun Dialogue.sethInterview() {
        chatNpc(quiz, "Well well, what can I do for you?")
        chatPlayer(
            quiz,
            "I was wondering if you could teach me a little about how you're so successful with " +
                "your animals.",
        )
        chatNpc(quiz, "Lookin' to get into the business, eh?")
        chatPlayer(neutral, "Something along those lines.")
        chatNpc(happy, "Well, always happy to help another budding farmer out!")
        chatPlayer(quiz, "Great! So where do I start?")
        chatNpc(neutral, "Would always recommend me Groats Principles as yer basics.")
        chatPlayer(quiz, "Groats Principles?")
        chatNpc(happy, "Aye. First one is that happy animals makes happy farmers.")
        chatNpc(
            neutral,
            "Second of me principles is to never take more than you need. Greed'll stop you in " +
                "your tracks.",
        )
        chatNpc(
            shifty,
            "The last principle: never let the taxman know yer earnings. Money should be spent " +
                "on the animals!",
        )
        chatPlayer(quiz, "You've found a way to get around the taxes?")
        chatNpc(shifty, "A way or two.")

        val gillieDone = access.player.gillieInformed == 1
        access.player.sethInformed = 1
        if (gillieDone && quest.getQuestStage(access.player) == IdesOfMilkStage.IntroDone) {
            quest.setQuestStage(access, IdesOfMilkStage.Interviewed)
        }
        chatNpc(
            neutral,
            "Anyway, feel free to grab me book on the topic from the shelf over " + "there.",
        )
        chatPlayer(neutral, "Okay, thank you for the help.")
    }

    private suspend fun Dialogue.sethAgain() {
        chatNpc(quiz, "Well well, what can I do for you?")
        chatPlayer(
            quiz,
            "I was wondering if you could teach me a little about how you're so successful with " +
                "your animals.",
        )
        chatNpc(neutral, "Mmm we already had this talk. Go read me book on the shelf over there.")
        if (access.player.inv.contains(IdesOfMilkRefs.Book)) {
            chatPlayer(
                neutral,
                "I've actually got it here, I'll take it away and give it a read somewhere a " +
                    "little more peaceful.",
            )
        } else {
            chatPlayer(neutral, "I'll go grab it, thanks.")
        }
    }

    private suspend fun ProtectedAccess.searchShelf() {
        val stage = quest.getQuestStage(player)
        val bookAvailable =
            player.sethInformed == 1 &&
                stage < IdesOfMilkStage.BookSnatched &&
                !player.inv.contains(IdesOfMilkRefs.Book)

        if (!bookAvailable) {
            mes(random.pick(ShelfFlavour))
            return
        }

        if (invAdd(inv, IdesOfMilkRefs.Book).failure) {
            mes("You find Seth's book on the shelf, but don't have room to take it with you.")
            return
        }
        soundSynth(IdesOfMilkRefs.SynthPick2)
        objbox(IdesOfMilkRefs.Book, ObjboxZoom, "You find Seth's book of principles on the shelf.")
    }

    private suspend fun ProtectedAccess.readBook() {
        var spread = 0
        while (true) {
            ifOpenMain("interface.book")
            ifSetText("component.book:title", "The Groats Principles")
            ifSetEvents("component.book:close_button", -1..-1, IfEvent.Op1)
            renderBookSpread(spread)
            val input = pauseButton()
            spread =
                when {
                    input.isComponentType("component.book:page_right_button") && spread == 0 -> 1
                    input.isComponentType("component.book:page_left_button") && spread == 1 -> 0
                    else -> return
                }
        }
    }

    private fun ProtectedAccess.renderBookSpread(spread: Int) {
        val left = if (spread == 0) BookSpread1Left else BookSpread2Left
        val right = if (spread == 0) BookSpread1Right else BookSpread2Right

        ifSetText("component.book:page_left_number", "${spread * 2 + 1}")
        ifSetText("component.book:page_right_number", "${spread * 2 + 2}")
        for (i in 1..BookLinesPerPage) {
            ifSetText("component.book:page_left_text_$i", left.getOrElse(i - 1) { "" })
            ifSetText("component.book:page_right_text_$i", right.getOrElse(i - 1) { "" })
            ifSetHide("component.book:page_left_model_$i", hide = true)
            ifSetHide("component.book:page_right_model_$i", hide = true)
        }

        ifSetHide("component.book:page_left_button", hide = spread == 0)
        ifSetHide("component.book:page_right_button", hide = spread == 1)
        ifSetEvents("component.book:page_left_button", -1..-1, IfEvent.PauseButton)
        ifSetEvents("component.book:page_right_button", -1..-1, IfEvent.PauseButton)
    }

    // Use a soft timer for the story hit; queues would wait for access or close the dialogue.
    private suspend fun ProtectedAccess.drinkSampleOne() {
        if (quest.getQuestStage(player) != IdesOfMilkStage.SampleOneHeld) {
            return
        }
        if (invDel(inv, IdesOfMilkRefs.Sample1, 1).failure) {
            return
        }
        quest.setQuestStage(this, IdesOfMilkStage.SampleOneDrunk)
        soundSynth(IdesOfMilkRefs.SynthLiquid)
        anim(IdesOfMilkRefs.SeqDrink)
        chatPlayerNp(sadMesAnim, "Oh, that's not good milk.")
        player.softTimer(IdesOfMilkRefs.TimerMilkSplat, SampleSplatDelay)
        delay(SampleFollowupDelay)
        startDialogue { chatPlayer(sad, "That's really not good milk.") }

        val cassius = findNearby(IdesOfMilkRefs.Cassius) ?: return
        startDialogue(cassius) { cassiusPermitTask() }
    }

    // Apply the story hit immediately without protection modifiers; keep the visual splat in god mode.
    private fun Player.milkSplat() {
        clearSoftTimer(IdesOfMilkRefs.TimerMilkSplat)
        val lethal = hitpoints <= 1
        val hit =
            takeInstantHit(
                type = HitType.Typeless,
                damage = if (lethal) 0 else 1,
                processor = instantHitProcessor,
                hitmark = hitmark_groups.poison_damage,
                modifier = NoopPlayerHitModifier,
            )
        if (adminGodMode) {
            showHitmark(hit.hitmark)
        }
        val sound =
            PlayerDefendSounds.resolve(
                lefthand = lefthand?.let { ServerCacheManager.getItem(it.id) },
                torso = torso?.let { ServerCacheManager.getItem(it.id) },
                damage = 1,
                bodyType = appearance.bodyType,
                random = random,
            )
        soundSynth(sound, delay = HitSoundClientDelay)
        combatPlayDefendAnim(clientDelay = 0)
    }

    private suspend fun ProtectedAccess.drinkSampleTwo() {
        val stage = quest.getQuestStage(player)
        if (stage < IdesOfMilkStage.GillieJudging) {
            startDialogue {
                chatPlayer(worried, "I don't think I'm ready to drink another vial of this stuff.")
                chatPlayer(neutral, "I should find someone else to give it to.")
            }
            return
        }
        if (stage != IdesOfMilkStage.GillieJudging) {
            return
        }

        val gillie = findNearby(IdesOfMilkRefs.Gillie)
        if (gillie == null) {
            startDialogue {
                chatPlayer(neutral, "I shouldn't drink this unless I have Gillie " + "nearby.")
            }
            return
        }

        if (invDel(inv, IdesOfMilkRefs.Sample2, 1).failure) {
            return
        }
        quest.setQuestStage(this, IdesOfMilkStage.SampleTwoDrunk)
        soundSynth(IdesOfMilkRefs.SynthLiquid)
        anim(IdesOfMilkRefs.SeqDrink)
        startDialogue { chatPlayer(sad, "Oh, that's worse milk than before.") }
        startDialogue(gillie) { gilliePostDrink() }
    }

    suspend fun Dialogue.dukeBranch(): Boolean {
        val stage = quest.getQuestStage(access.player)
        if (stage !in IdesOfMilkStage.SampleTwoHeld until IdesOfMilkStage.GillieJudging) {
            return false
        }

        if (stage >= IdesOfMilkStage.DukeConsulted) {
            chatPlayer(quiz, "Hi, what did you want me to do for the permit again?")
            chatNpc(
                neutral,
                "Go speak to Gillie Groats. She seems to handle the milk business in Lumbridge. " +
                    "Maybe she'll get you set up under their permit.",
            )
            chatPlayer(happy, "Thanks.")
            return true
        }

        chatPlayer(neutral, "Greetings. I come seeking a business permit.")
        chatNpc(quiz, "Oh wonderful! What sort of business are you looking to start?")
        chatPlayer(happy, "I'm looking to get into the milk trade!")
        chatNpc(
            quiz,
            "Milk? Well, the Groats family still have their permit, and I can't start " +
                "stepping on their toes.",
        )
        chatPlayer(neutral, "If you'd just try this milk...")
        chatNpc(
            neutral,
            "I really couldn't possibly give you a permit without their approval. This is " +
                "really their territory.",
        )
        quest.setQuestStage(access, IdesOfMilkStage.DukeConsulted)
        chatNpc(
            quiz,
            "Why not speak to Gillie Groats? She seems to handle the milk side of the business " +
                "now. Maybe she'll get you set up under their permit.",
        )
        chatPlayer(quiz, "You're the Duke. Couldn't you just grant a permit?")
        chatNpc(neutral, "No, no. It's really quite important you speak to Gillie about this.")
        chatPlayer(quiz, "Aren't you the highest power in this land?")
        chatNpc(
            shifty,
            "Don't go digging too deeply into that. Even I have some... commitments " +
                "that I have to keep to.",
        )
        chatPlayer(
            confused,
            "But you're the Duke, you shouldn't need the approval of a business entity to grant " +
                "a permit.",
        )
        chatPlayer(quiz, "Not to mention, can't they just refuse to have any competitors?")
        chatNpc(
            shifty,
            "Well absolutely, but I'm sure you'll find they're more than reasonable " +
                "about things.",
        )
        chatNpc(sad, "Entirely. Completely reasonable.")
        return true
    }

    private fun ProtectedAccess.findNearby(type: String): Npc? =
        npcRepo.findAll(ZoneKey.from(player.coords), zoneRadius = 1).firstOrNull { it.isType(type) }

    private fun ProtectedAccess.chatPlayerNp(mesanim: MesAnimType, text: String) {
        val page = alignment.generateChatPageList(text).first()
        val lineHeight = alignment.chatLineHeight(page.lineCount)
        val chatanim = ServerCacheManager.getAnim(mesanim.variant(page.lineCount)) ?: return

        player.mes("${player.displayName}|${page.text}", ChatType.Dialogue)
        chatModalUnclamp = constants.modal_fixedwidthandheight
        ClientScripts.topLevelChatboxResetBackground(player)
        ifOpenSub("interface.chat_right", "component.chatbox:chatmodal", IfSubType.Modal)
        ifSetPlayerHead("component.chat_right:head")
        ifSetAnim("component.chat_right:head", chatanim)
        ifSetText("component.chat_right:name", player.displayName)
        ifSetText("component.chat_right:text", page.text)
        ClientScripts.ifSetTextAlign(
            player,
            "component.chat_right:text",
            alignH = 1,
            alignV = 1,
            lineHeight,
        )
        ifSetEvents("component.chat_right:continue", -1..-1)
        ifSetText("component.chat_right:continue", "")
    }

    private var ProtectedAccess.chatModalUnclamp by intVarBit("varbit.chatmodal_unclamp")

    private fun MesAnimType.variant(lines: Int): Int =
        when (lines) {
            1 -> len1
            2 -> len2
            3 -> len3
            else -> len4
        }

    private val sadMesAnim by lazy {
        ServerCacheManager.getMesAnim("mesanim.sad".asRSCM(RSCMType.MESANIM))
            ?: error("mesanim.sad has no definition in the SERVER cache.")
    }

    private companion object {
        const val ObjboxZoom = 400

        const val SampleSplatDelay = 3

        const val SampleFollowupDelay = 5

        const val HitSoundClientDelay = 20

        val ShelfFlavour =
            arrayOf(
                "You find a bucket on the shelf, as you'd expect a farmer to have.",
                "You find a loose egg on the shelf, and fortunately avoid knocking it to the " +
                    "floor.",
                "Hidden behind the piles of junk on the shelf, you find a small farmer's hat " +
                    "with Gillie's name written on it.",
            )

        val CombatLampStats = 0..6

        const val RewardLampXp = 1000.0

        const val SynthLampReward = "synth.FOUND_GEM"
    }

    private val BookSpread1Left =
        listOf(
            "The Groats Principles.",
            "",
            "Principle One.",
            "",
            "Happy animals make happy",
            "farmers. It is crucial to",
            "ensure your animals have",
            "everything they could need:",
            "space, food, water. Without",
            "those essentials met, you'll",
            "spend your time running",
            "around trying to make them",
            "happy and healthy. Not to",
            "mention, unhappy animals",
            "produce worse quality",
        )

    private val BookSpread1Right =
        listOf(
            "produce!",
            "",
            "The Groats name lives and",
            "dies on its reputation, and",
            "that reputation relies on",
            "producing the highest quality",
            "milk and eggs.",
            "",
            "Principle Two.",
            "",
            "Never take more than you",
            "need. Greed will rot not only",
            "your soul, but your land.",
            "Your animals will remain",
            "healthier when not pushed to",
        )

    private val BookSpread2Left =
        listOf(
            "their limits.",
            "",
            "Principle Three.",
            "",
            "Always squirrel money away",
            "from the tax collector. It's",
            "not a good citizen that avoids",
            "all of their taxes, but a smart",
            "one keeps a little back. The",
            "more you can invest into",
            "your farm and your animals,",
            "the more you can give back",
            "later on in your life.",
            "",
            "",
        )

    private val BookSpread2Right = List(BookLinesPerPage) { "" }
}

private const val BookLinesPerPage = 15
