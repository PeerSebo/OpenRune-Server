package org.rsmod.content.quest.area.icemountain

import org.rsmod.api.config.constants
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.ShieldOfArrav
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.checkQuestPointRequirement
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

internal class BelowIceMountainDialogue(
    private val quest: Quest,
    private val cutscene: BelowIceMountainCutscene,
    private val objRepo: ObjRepository,
    private val shieldOfArrav: ShieldOfArrav,
) {

    fun ScriptContext.register() {
        onOpNpc1(BelowIceMountainRefs.WillowPath) { startDialogue(it.npc) { willowPath() } }
        onOpNpc1(BelowIceMountainRefs.WillowEntrance) { startDialogue(it.npc) { willowEntrance() } }
        onOpNpc1(BelowIceMountainRefs.WillowLeaf) { willowLeaf(it.npc) }

        onOpNpc1(BelowIceMountainRefs.CheckalVillage) { startDialogue(it.npc) { checkalVillage() } }
        onOpNpc1(BelowIceMountainRefs.CheckalEntrance) {
            startDialogue(it.npc) { checkalEntrance() }
        }
        onOpNpc1(BelowIceMountainRefs.CheckalLeaf) { checkalLeaf(it.npc) }

        onOpNpc1(BelowIceMountainRefs.Atlas) { startDialogue(it.npc) { atlas() } }

        onOpNpc1(BelowIceMountainRefs.MarleyEdgeville) { startDialogue(it.npc) { marley() } }
        onOpNpc1(BelowIceMountainRefs.MarleyEntrance) { startDialogue(it.npc) { marleyEntrance() } }
        onOpNpc1(BelowIceMountainRefs.MarleyLeaf) { marleyLeaf(it.npc) }

        onOpNpc1(BelowIceMountainRefs.BurntofPub) { startDialogue(it.npc) { burntofPub() } }
        onOpNpc1(BelowIceMountainRefs.BurntofEntrance) {
            startDialogue(it.npc) { burntofEntrance() }
        }
        onOpNpc1(BelowIceMountainRefs.BurntofLeaf) { burntofLeaf(it.npc) }
        onOpNpc1(BelowIceMountainRefs.BurntofNoDrinkLeaf) { burntofLeaf(it.npc) }

        onOpNpc1(BelowIceMountainRefs.Cook) { startDialogue(it.npc) { cookDialogue() } }
        onOpNpc1(BelowIceMountainRefs.Charlie) { startDialogue(it.npc) { charlieTheTramp() } }

        onOpNpc1(BelowIceMountainRefs.RamarnoEntrance) { startDialogue(it.npc) { ramarno() } }
        onOpNpc1(BelowIceMountainRefs.RamarnoEntranceLeaf) { startDialogue(it.npc) { ramarno() } }
        onOpNpc1(BelowIceMountainRefs.RamarnoWorkshop) { startDialogue(it.npc) { ramarno() } }
        onOpNpc1(BelowIceMountainRefs.RamarnoWorkshopLeaf) { startDialogue(it.npc) { ramarno() } }

        onOpNpc1(BelowIceMountainRefs.WillowDesert) { startDialogue(it.npc) { willowDesert() } }
        onOpNpc1(BelowIceMountainRefs.CheckalDesert) { startDialogue(it.npc) { checkalDesert() } }
        onOpNpc1(BelowIceMountainRefs.MarleyDesert) { startDialogue(it.npc) { marleyDesert() } }
        onOpNpc1(BelowIceMountainRefs.BurntofDesert) { startDialogue(it.npc) { burntofDesert() } }

        onOpHeldU(BelowIceMountainRefs.Knife, BelowIceMountainRefs.Bread) {
            makeSteakSandwich(it.secondSlot)
        }
    }

    private suspend fun ProtectedAccess.willowLeaf(npc: Npc) {
        val entrance = quest.getQuestStage(player) >= BelowIceMountainStage.FirstRecruited
        startDialogue(npc) { if (entrance) willowEntrance() else willowPath() }
    }

    private suspend fun ProtectedAccess.checkalLeaf(npc: Npc) {
        val entrance = bimCheckal >= BelowIceMountainTracks.Recruited
        startDialogue(npc) { if (entrance) checkalEntrance() else checkalVillage() }
    }

    private suspend fun ProtectedAccess.marleyLeaf(npc: Npc) {
        val entrance = bimMarley >= BelowIceMountainTracks.Recruited
        startDialogue(npc) { if (entrance) marleyEntrance() else marley() }
    }

    private suspend fun ProtectedAccess.burntofLeaf(npc: Npc) {
        val entrance = bimBurntof >= BelowIceMountainTracks.Recruited
        startDialogue(npc) { if (entrance) burntofEntrance() else burntofPub() }
    }

    private suspend fun Dialogue.willowPath() {
        when {
            quest.getQuestStage(player) >= BelowIceMountainStage.CrewNamed -> willowDirections()
            quest.getQuestStage(player) >= BelowIceMountainStage.Started -> willowBriefing()
            else -> willowOpener()
        }
    }

    private suspend fun Dialogue.willowOpener() {
        chatPlayer(quiz, "Hello there. What are you doing all the way out here?")
        chatNpc(shifty, "Just... looking around.")
        chatPlayer(quiz, "Oh, did you lose something?")
        chatNpc(shifty, "No... I found something. A door.")
        chatPlayer(quiz, "A door? I see doors all the time. What makes this one so special?")
        chatNpc(
            shifty,
            "It's buried beneath a tonne of rocks. Could be the entrance to some ancient ruins...",
        )
        chatPlayer(shocked, "Wow! Ancient ruins? Are you an archaeologist or something?")
        chatNpc(quiz, "Archaeologist? Me? Hmm...")
        chatNpc(
            happy,
            "Of course I am! I'm the greatest archaeologist of our time. The name's Willow! Have " +
                "you not heard of me?",
        )
        chatPlayer(quiz, "I'm afraid I haven't. Are you looking for a way into the ruins?")
        chatNpc(sad, "You bet I am, but I don't think there's any other way in.")
        chatPlayer(quiz, "Oh dear. Do you have another plan to get in?")
        chatNpc(
            neutral,
            "Potentially, but I won't be able to do it on my own. Nor can I leave the area. If " +
                "I'm not here to guard the entrance, some other archaeologist might come along " +
                "and steal the glory from me!",
        )
        chatNpc(
            quiz,
            "Maybe you could give me a hand? There could be untold riches inside the ruins!",
        )

        if (!access.checkQuestPointRequirement(quest)) {
            return
        }

        val accept =
            choice2("Yes.", true, "No.", false, title = "Start the Below Ice Mountain quest?")
        if (!accept) {
            chatPlayer(neutral, "Seems like you're in a bit of a pickle. Have fun with that.")
            return
        }

        quest.setQuestStage(access, BelowIceMountainStage.Started)
        willowBriefing()
    }

    private suspend fun Dialogue.willowBriefing() {
        chatPlayer(
            neutral,
            "A chance to uncover ancient ruins doesn't happen every day! Of course I'll help! " +
                "I'm ${player.displayName} by the way. How do you plan to get in?",
        )
        chatNpc(
            neutral,
            "The entrance is covered in rocks and rubble - I don't think mining them will work.",
        )
        chatNpc(
            quiz,
            "We'll need something stronger. Who knows what could be inside the ruins. There could " +
                "be traps, or even monsters!",
        )
        chatNpc(shifty, "No, I think we'll need to get my old crew...")
        chatNpc(
            shifty,
            "...I mean, excavation team together. But you'll need to convince them to help us. " +
                "We didn't part on the best of terms.",
        )
        chatPlayer(quiz, "What happened?")

        if (quest.getQuestStage(player) < BelowIceMountainStage.AskedWhatHappened) {
            quest.setQuestStage(access, BelowIceMountainStage.AskedWhatHappened)
        }

        chatNpc(shifty, "We had a falling out after a, uhm... dig, went wrong.")
        chatPlayer(neutral, "Where can I find your old team, and how will they help?")
        chatNpc(
            neutral,
            "To get into the ruins we'll need to blow up all the rocks blocking the entrance.",
        )
        chatPlayer(
            quiz,
            "That doesn't sound too safe. What if we damage the ruins? Surely it's better to go " +
                "slower and preserve as much as we can?",
        )
        chatNpc(
            angry,
            "No! We need to get in there as soon as possible! Burntof the dwarf will do it. He's " +
                "a master with explosives.",
        )
        chatNpc(
            quiz,
            "From what I can remember, he's a fan of his drink. His favourite pub being the " +
                "Rising Sun Inn, in Falador - I'd look there first.",
        )
        chatNpc(
            neutral,
            "After we've blown the rocks away, we'll need someone to move all the rubble. " +
                "Someone strong.",
        )
        chatPlayer(happy, "I could do it! I'm quite strong myself.")
        chatNpc(
            laugh,
            "Ha ha ha! I don't think so. We need a real strongman - someone with muscles. An old " +
                "friend of mine, Checkal, should be able to do all the heavy lifting.",
        )
        chatPlayer(sad, "Oh okay... Where can I find him?")
        chatNpc(
            quiz,
            "Hmm... We had quite a rough falling out when we last spoke, but I heard he's " +
                "hanging around Barbarian Village.",
        )
        chatNpc(
            neutral,
            "Lastly, if my hunch is correct, this place will be covered in traps. We'll need my " +
                "pal Marley to disarm them. He's the best thief I know. Been doing it for years.",
        )
        chatNpc(
            neutral,
            "I have no idea where he is these days. But his cousin Charlie in Varrock may know. " +
                "You should talk to him. He's normally somewhere near the Blue Moon Inn.",
        )

        if (quest.getQuestStage(player) < BelowIceMountainStage.CrewNamed) {
            quest.setQuestStage(access, BelowIceMountainStage.CrewNamed)
        }

        chatPlayer(
            neutral,
            "You have a rather interesting group of friends... but if they get us into the ruins, " +
                "I'm all for it.",
        )
        chatNpc(
            neutral,
            "Good luck. Once you've recruited one of the team, I'll meet them at the entrance. " +
                "It's on the west side of the mountain.",
        )
    }

    private suspend fun Dialogue.willowDirections() {
        chatNpc(quiz, "Back again? What can I help you with?")
        while (true) {
            val choice =
                choice5(
                    "Where can I find Marley?",
                    1,
                    "Where can I find Checkal?",
                    2,
                    "Where can I find Burntof?",
                    3,
                    "Where can I find the entrance to the ruins?",
                    4,
                    "Nothing, thanks.",
                    5,
                )
            when (choice) {
                1 -> {
                    chatPlayer(neutral, "Where can I find Marley?")
                    chatNpc(
                        neutral,
                        "Marley is an elusive one, to be honest. I have no idea where to find " +
                            "him. You should try talk to his cousin Charlie near the Blue Moon " +
                            "Inn in Varrock. Maybe he will know.",
                    )
                    chatNpc(quiz, WillowAnyoneElse)
                }
                2 -> {
                    chatPlayer(neutral, "Where can I find Checkal?")
                    chatNpc(
                        neutral,
                        "Last I heard Checkal was hanging around Barbarian Village. Maybe you " +
                            "should head there to find him?",
                    )
                    chatNpc(quiz, WillowAnyoneElse)
                }
                3 -> {
                    chatPlayer(neutral, "Where can I find Burntof?")
                    chatNpc(
                        neutral,
                        "Burntof likes to hang out at the Rising Sun Inn located in Falador.",
                    )
                    chatNpc(quiz, WillowAnyoneElse)
                }
                4 -> {
                    chatPlayer(neutral, "Where can I find the entrance to the ruins?")
                    chatNpc(
                        neutral,
                        "On the western side of Ice Mountain. There's no way up on this side so " +
                            "you'll need to head east of here first and go around the dwarf " +
                            "colony to get there.",
                    )
                    chatPlayer(neutral, "Okay.")
                }
                else -> {
                    chatPlayer(neutral, "Nothing, thanks.")
                    chatNpc(
                        neutral,
                        "Hurry up! It's not exactly exciting standing out here all day.",
                    )
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.willowEntrance() {
        when {
            quest.getQuestStage(player) >= BelowIceMountainStage.ReadyToEnter -> willowReadyAgain()
            quest.getQuestStage(player) >= BelowIceMountainStage.AllRecruited -> willowGathered()
            else -> willowDirections()
        }
    }

    private suspend fun Dialogue.willowGathered() {
        chatPlayer(
            neutral,
            "That's everyone gathered. Interesting company for an archaeologist to keep...",
        )
        chatNpc(shifty, "*Shrugs* How many archaeologists do you actually know?")
        chatPlayer(neutral, "Well I-")
        chatNpc(
            neutral,
            "Exactly. So who are you to judge? Anyway, we're all here and ready to break in... " +
                "Um, I mean excavate... the entrance.",
        )

        quest.setQuestStage(access, BelowIceMountainStage.ReadyToEnter)

        chatNpc(quiz, "Are you ready? Who knows what's inside the ruins?")
        offerToEnter()
    }

    private suspend fun Dialogue.willowReadyAgain() {
        chatNpc(quiz, "Are you finally ready to crack these ruins open?")
        offerToEnter()
    }

    private suspend fun Dialogue.offerToEnter() {
        val enter = choice2("Yes.", true, "No.", false, title = "Head into the ruins?")
        if (!enter) {
            chatPlayer(neutral, "I need a little bit more time to prepare.")
            chatNpc(neutral, "Hurry up then! We've waited long enough.")
            return
        }
        chatPlayer(neutral, "I'm ready as I'll ever be. Let's head in.")
        access.closeChat()
        cutscene.enterRuins(access)
    }

    private suspend fun Dialogue.checkalVillage() {
        when {
            bimCheckal >= BelowIceMountainTracks.Recruited -> checkalEntrance()
            bimCheckal >= BelowIceMountainTracks.CheckalAskedToFlex -> checkalAwaitsFlex()
            bimCheckal >= BelowIceMountainTracks.CheckalTrainingDone -> checkalJudges()
            bimCheckal >= BelowIceMountainTracks.CheckalSentToAtlas -> checkalRemindsOfAtlas()
            quest.isQuestNotStarted(player) -> checkalBeforeTheQuest()
            else -> checkalFirstMeeting()
        }
    }

    private suspend fun Dialogue.checkalBeforeTheQuest() {
        mesbox("Checkal stares at you intensely.")
        chatNpc(angry, "You best not be here to start any trouble, $sirOrMadam.")
        chatPlayer(neutral, "Wouldn't dream of it.")
    }

    private suspend fun Dialogue.checkalFirstMeeting() {
        chatPlayer(
            neutral,
            "Hello there. I'm ${player.displayName} and I'm looking for a man named Checkal.",
        )
        chatNpc(neutral, "That would be me, $sirOrMadam. What can I do for you?")
        chatPlayer(neutral, "I was told to come find you. Willow has found a new digsi...")
        chatNpc(
            angry,
            "Stop right there, $sirOrMadam. You can tell Willow she can do the heavy lifting " +
                "herself.",
        )
        chatPlayer(quiz, "And why is that?")
        chatNpc(
            angry,
            "Willow does not appreciate the amount of work that goes into building my physique. " +
                "I would not help her lift a shrimp.",
        )
        chatNpc(
            neutral,
            "I take great pride in these muscles and it hurts me to the core to hear such insults!",
        )
        chatPlayer(
            neutral,
            "But we need your help! She's found a new archaeological digsite and needs you to " +
                "help clear the entrance.",
        )
        chatNpc(quiz, "Archaeological digsite? Why is Willow interested in starting a digsite?")
        chatPlayer(
            neutral,
            "She said there could be some ancient ruins, even an untouched hoard of gold and " +
                "treasure!",
        )
        chatNpc(neutral, "Ah that makes sense. *Sighs* You poor $ladOrLass...")
        chatPlayer(quiz, "What do you mean?")
        chatNpc(
            neutral,
            "Nothing. Look, I will help you. But first you must prove your strength to me.",
        )

        if (access.statBase("stat.strength") >= MaxStrength) {
            chatPlayer(
                neutral,
                "Well Checkal, I don't mean to brag, but there are few people in Gielinor " +
                    "stronger than me.",
            )
            chatNpc(quiz, "Hmm... I will see about that.")
            chatNpc(neutral, "Yes... you do have tremendous muscle mass.")
            chatNpc(neutral, "Your legs are strong as steel, too.")
            chatNpc(
                neutral,
                "Well ${player.displayName}, I have to say you are the definition of strength.",
            )
            chatPlayer(neutral, "I do try.")
            chatNpc(
                neutral,
                "But a strongman doesn't stop when they reach the top. They always push " +
                    "themselves.",
            )
        } else {
            chatPlayer(quiz, "How can I do that?")
        }

        chatNpc(
            neutral,
            "I would ask you to train with my old friend and trainer, Atlas, one of the strongest " +
                "barbarians I know. You can find him in the hall north of here.",
        )

        access.bimCheckal = BelowIceMountainTracks.CheckalSentToAtlas

        chatNpc(
            neutral,
            "If you can survive his rigorous training program, I shall offer you my help.",
        )
        chatPlayer(quiz, "How do you know he'll train me?")
        chatNpc(neutral, "Tell him I sent you and he will train you.")
        chatPlayer(neutral, "Alright then, I'll go speak to Atlas. Farewell.")
    }

    private suspend fun Dialogue.checkalRemindsOfAtlas() {
        chatPlayer(quiz, "I seem to have forgotten where Atlas is. Can you remind me?")
        chatNpc(neutral, "Atlas can be found in the hall to the north of us.")
        chatPlayer(neutral, "Thanks.")
        chatNpc(neutral, "No problem at all.")
    }

    private suspend fun Dialogue.checkalJudges() {
        chatNpc(neutral, "So have you completed your training session with Atlas?")
        chatPlayer(neutral, "Just about, but it was a real struggle.")
        chatNpc(
            happy,
            "It always is, but I can see you are standing taller and looking stronger already.",
        )
        chatNpc(
            happy,
            "Now, show me your muscles and I will judge if you're ready to receive my help.",
        )

        access.bimCheckal = BelowIceMountainTracks.CheckalAskedToFlex
        mesbox(FlexPrompt)
    }

    private suspend fun Dialogue.checkalAwaitsFlex() {
        chatPlayer(quiz, "What did you want me to do again?")
        chatNpc(neutral, "I want you to prove your strength by showing me your muscles!")
        mesbox(FlexPrompt)
    }

    // The queued dialogue has no clicked NPC; use chatNpcSpecific rather than chatNpc.
    internal suspend fun Dialogue.checkalAnswersFlex() {
        chatNpcSpecific(
            CheckalName,
            BelowIceMountainRefs.CheckalVillage,
            happy,
            "My goodness, those muscles are exquisite. I will definitely help anyone who shows " +
                "that much dedication to their physique.",
        )
        chatPlayer(
            happy,
            "Fantastic! Willow said she'd meet you by the entrance, on the west side of Ice " +
                "Mountain.",
        )
        chatNpcSpecific(
            CheckalName,
            BelowIceMountainRefs.CheckalVillage,
            happy,
            "Thank you my friend, I will meet you there.",
        )

        access.recruitCrewman { bimCheckal = BelowIceMountainTracks.Recruited }

        chatPlayer(neutral, access.recruitSignOff(CheckalSignOff))
    }

    private suspend fun Dialogue.checkalEntrance() {
        chatPlayer(quiz, "Do you think you'll be able to handle all these rocks?")
        chatNpc(neutral, "Should not be a problem. This challenge is easy for a strongman.")
        chatPlayer(sad, "I wish I could help...")
        chatNpc(
            neutral,
            "Do not worry my friend. If you stay dedicated and keep working everyday, you'll be " +
                "able to achieve anything you dream of.",
        )
        chatPlayer(happy, "Thanks Checkal, I will.")
        chatNpc(
            neutral,
            "One last thing - be careful when we go inside. You never know what will happen.",
        )
    }

    private suspend fun Dialogue.atlas() {
        when {
            bimCheckal >= BelowIceMountainTracks.CheckalTrainingDone -> atlasRepeat()
            bimCheckal >= BelowIceMountainTracks.CheckalTrainingOffered -> atlasAsksAgain()
            else -> atlasFirstMeeting()
        }
    }

    private suspend fun Dialogue.atlasFirstMeeting() {
        chatNpc(angry, "What do you want, little $manOrWoman?")
        chatPlayer(
            neutral,
            "I've just come to talk to you. Checkal said you could train me. He said you're the " +
                "strongest barbarian he knows.",
        )
        chatNpc(angry, "Oh did he now? What makes you think I will train you?")
        chatPlayer(worried, "I... Um...")
        chatNpc(angry, "Come on then! Out with it already!")
        chatPlayer(worried, "Well uh, he said he wouldn't...")
        chatNpc(laugh, "I'm just pulling your leg! Any friend of Checkal is a friend of mine.")
        chatPlayer(sad, "Please don't scare me like that.")
        chatNpc(
            neutral,
            "It's a force of habit. It comes with the barbarian lifestyle. But to apologise, " +
                "I'll give you a free lesson!",
        )
        chatPlayer(quiz, "Oh really? How much would this usually cost?")
        chatNpc(neutral, "Around 25,000 coins per session.")
        chatPlayer(shocked, "That's a lot of money!")

        access.bimCheckal = BelowIceMountainTracks.CheckalTrainingOffered

        chatNpc(
            neutral,
            "No price is too much for a good workout. Are you ready to start your training session?",
        )
        offerWorkout()
    }

    private suspend fun Dialogue.atlasAsksAgain() {
        chatNpc(neutral, "You're back. Would you like to start that training session now?")
        offerWorkout()
    }

    private suspend fun Dialogue.offerWorkout() {
        val start =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = "Would you like to start your training session?",
            )
        if (!start) {
            chatPlayer(neutral, "Not right now, I am a bit busy.")
            chatNpc(neutral, "No problem. Come back when you are ready.")
            return
        }
        access.closeChat()
        cutscene.workout(access)
    }

    private suspend fun Dialogue.atlasRepeat() {
        chatNpc(neutral, "Well done, little one. You've proven your worth.")
        chatPlayer(quiz, "Can I do it again?")
        chatNpc(quiz, "What? Why?")
        chatPlayer(neutral, "It was fun, I'd like to do it again.")
        chatNpc(
            neutral,
            "You're the first person to ask me that ${player.displayName}. I suppose I could " +
                "train you again, but I will have to charge you the full 25,000 coins this time.",
        )
        val again = choice2("Yes", true, "No", false)
        if (!again) {
            return
        }
        chatPlayer(neutral, "Let's do this, I'm ready.")
        if (!access.player.invTakeFee(WorkoutFee)) {
            chatNpc(
                neutral,
                "Hold it there little one, you don't have enough money to pay for the lesson.",
            )
            chatPlayer(sad, "Oh, sorry I was too excited...")
            chatNpc(neutral, "No worries friend, come back when you can afford the session.")
            return
        }
        chatNpc(neutral, "It's not going to be any easier my friend!")
        access.closeChat()
        cutscene.workout(access)
    }

    private suspend fun Dialogue.marley() {
        when {
            quest.isQuestCompleted(player) -> marleyAfterQuest()
            bimMarley >= BelowIceMountainTracks.Recruited -> marleyEntrance()
            bimMarley >= BelowIceMountainTracks.MarleyWantsSandwich -> marleyWaitsForSandwich()
            else -> marleyFirstMeeting()
        }
    }

    private suspend fun Dialogue.marleyFirstMeeting() {
        chatPlayer(neutral, "Hey there. Would you happen to be Marley?")
        chatNpc(
            shifty,
            "I could be. Depends on who's asking? If you're with the Varrock City Guard, I had " +
                "nothing to do with the robbery at the museum. I don't even know what I'd do with " +
                "some Wizard robes.",
        )
        chatPlayer(quiz, "Uhm, no? What robbery? Wizard robes?")
        chatNpc(shifty, "Robbery? Sorry, pal, first I've heard of a robbery.")
        chatPlayer(
            neutral,
            "Look, I'm not here with the city guard, Willow sent me to find you. She needs your " +
                "help.",
        )
        chatNpc(quiz, "Willow? Hmm... I'm afraid I don't recall a Willow.")
        chatPlayer(neutral, "Are you sure? She said you were old pals.")
        chatNpc(neutral, "Nope, I'm sure. Only Willow I know of is a type of tree.")
        chatNpc(quiz, "But, then again, my memory is a bit hazy, due to how hungry I am.")
        chatPlayer(neutral, "You're too hungry... to remember your friend?")
        chatNpc(neutral, "Yep... been a while since I had a nice hot meal.")
        chatPlayer(
            angry,
            "*Sighs*... would you like me to get you something to eat, to help jog your memory?",
        )
        chatNpc(happy, "Oh what a kind offer! I couldn't say no.")
        chatPlayer(angry, "Fine, I'll be back in a...")
        chatNpc(
            happy,
            "Woah woah woah, hold your horses there friend! I can't just eat anything. I have " +
                "very specific dietary requirements.",
        )
        chatPlayer(angry, "...")
        chatNpc(
            happy,
            "Don't be like that! I don't want nothing fancy, just a Steak sandwich from the Blue " +
                "Moon Inn, in Varrock.",
        )

        access.bimMarley = BelowIceMountainTracks.MarleyWantsSandwich

        chatPlayer(angry, "Why don't you go and get one then?")
        chatNpc(
            happy,
            "Slight problem there, friend. I've been barred and the chef won't make me another " +
                "one. Something about stealing from punters. I can't remember...",
        )
        chatPlayer(angry, "*Sighs*... Fine. If I go get you this sandwich, will you help Willow?")
        chatNpc(
            happy,
            "Of course! Oh, by the way, don't bother with the bartender. Just talk to the chef. " +
                "Trust me.",
        )
        chatPlayer(angry, "Alright. I'll be back momentarily with your sandwich.")
    }

    private suspend fun Dialogue.marleyWaitsForSandwich() {
        if (access.invTotal(access.inv, BelowIceMountainRefs.SteakSandwich) <= 0) {
            chatNpc(quiz, "Have you got the sandwich yet?")
            chatPlayer(neutral, "I'm still working on it. Where did you say you got them from?")
            chatNpc(happy, "The Blue Moon Inn, in Varrock. They have the best cook around!")
            chatPlayer(neutral, "I'll go speak to them, then.")
            return
        }

        chatPlayer(happy, "Hey Marley. I've managed to get your Steak sandwich.")
        chatNpc(happy, "Ah, just in time for my afternoon snack. Pass it over.")

        if (access.invDel(access.inv, BelowIceMountainRefs.SteakSandwich, 1).failure) {
            return
        }

        access.bimMarley = BelowIceMountainTracks.MarleyFed
        mesbox("You give the steak sandwich to Marley.")
        access.marleyEats()

        chatNpc(happy, "Aaah... not quite as good as the usual, but it hit the spot.")
        chatPlayer(angry, "Are you finally done?")
        chatNpc(
            happy,
            "Yes $sirOrMadam I am. I'll honour my side of the deal and head off to find Willow.",
        )
        chatPlayer(neutral, "Finally! You can find her on the west side of Ice Mountain.")
        chatNpc(happy, "Excellent! I'll be on my way then.")

        access.recruitCrewman { bimMarley = BelowIceMountainTracks.Recruited }

        chatPlayer(neutral, access.recruitSignOff(MarleySignOff))
    }

    private suspend fun Dialogue.marleyEntrance() {
        if (quest.isQuestCompleted(player)) {
            marleyAfterQuest()
            return
        }
        chatPlayer(neutral, "Hello again Marley.")
        chatNpc(happy, "Ah, my new favourite chef. Have you got another sandwich for me?")
        chatPlayer(angry, "No!")
        chatNpc(happy, "Woah! Alright there, calm down. Was only a joke.")
        chatPlayer(angry, "I don't find being your errand boy very funny.")
        chatNpc(happy, "You need to lighten up a little, my friend.")
    }

    private suspend fun Dialogue.marleyAfterQuest() {
        chatPlayer(neutral, "Hello again, Marley.")
        chatNpc(happy, "${player.displayName} my old pal! How are you doing?")
    }

    private suspend fun Dialogue.cookDialogue() {
        val questBranch =
            bimMarley >= BelowIceMountainTracks.MarleyWantsSandwich &&
                bimMarley < BelowIceMountainTracks.Recruited

        if (!questBranch) {
            chatNpc(neutral, "What do you want? I'm a little busy here, so make it quick!")
            cookStandardOptions()
            return
        }

        val recipeKnown = bimMarley >= BelowIceMountainTracks.MarleyRecipeKnown
        val hasSandwich = access.invTotal(access.inv, BelowIceMountainRefs.SteakSandwich) > 0

        val questOption =
            when {
                hasSandwich -> "I've managed to make myself a Steak sandwich."
                recipeKnown -> "I forgot the recipe to make a Steak sandwich."
                else -> "I was wondering if you'd be able to make me a Steak sandwich?"
            }

        val choice = choice4(questOption, 1, CookSellFood, 2, CookFreeFood, 3, CookLeave, 4)

        when (choice) {
            1 ->
                when {
                    hasSandwich -> cookInspectsSandwich()
                    recipeKnown -> cookRepeatsRecipe()
                    else -> cookGivesRecipe()
                }
            2 -> cookSellsFood()
            3 -> cookRefusesFreeFood()
            else -> cookInsulted()
        }
    }

    private suspend fun Dialogue.cookStandardOptions() {
        when (choice3(CookSellFood, 1, CookFreeFood, 2, CookLeave, 3)) {
            1 -> cookSellsFood()
            2 -> cookRefusesFreeFood()
            else -> cookInsulted()
        }
    }

    private suspend fun Dialogue.cookGivesRecipe() {
        chatPlayer(quiz, "I was wondering if you'd be able to make me a Steak sandwich?")
        chatNpc(
            quiz,
            "Hmm... we don't get many food orders in 'ere. Last person to order one of them was " +
                "barred for thievin'.",
        )
        chatPlayer(shifty, "Oh... uhm... no. It's for me?")
        chatNpc(quiz, "Really?")
        chatPlayer(sad, "No... it's for Marley...")
        chatNpc(angry, "I knew it! I'm not making another meal for that thievin' git! Clear off.")
        chatPlayer(quiz, "At least tell me why you hate him so much?")
        chatNpc(
            sad,
            "I know what it's like, not knowin' where your next meals going to come from, so I " +
                "let him eat 'ere for free.",
        )
        chatNpc(
            angry,
            "The minute I turn my back, he's caught red-handed trying to pickpocket one of our " +
                "patrons. We gave him the boot there 'n' then.",
        )
        chatPlayer(
            neutral,
            "Well, fair enough, but it's just one sandwich. He's not even in Varrock anymore. " +
                "He's moved off to Edgeville.",
        )
        chatNpc(
            angry,
            "No chance mate, it's the principle. I won't make food for such a low class criminal...",
        )
        chatPlayer(
            neutral,
            "Please, I really need Marley's help and he'll only offer it if I get him a Steak " +
                "sandwich.",
        )
        chatNpc(quiz, "Well, I suppose if I give you the recipe, you can make it yourself.")
        chatPlayer(happy, "Great! I can't imagine it's that hard to make.")
        chatNpc(angry, "Watch your mouth! Making a Steak sandwich is a delicate art!")
        chatPlayer(sad, "Sorry! Of course it is. What's the recipe then?")
        chatNpc(
            neutral,
            "To start, you need to get some prime beef from a cow. The fields around Lumbridge " +
                "are a great source. Cook it up - medium rare is perfect. Don't cook it for too " +
                "long or you'll burn it...",
        )
        chatNpc(
            neutral,
            "Then, you take some bread. I recommend making it fresh, but to each their own. " +
                "Slice it in half and slide the beautifully cooked beef between the two halves...",
        )

        access.bimMarley = BelowIceMountainTracks.MarleyRecipeKnown

        chatNpc(
            neutral,
            "And hey presto, you've got yourself a delicious, perfectly made Steak sandwich.",
        )
        chatPlayer(shocked, "Wow... is that it? Some beef between a loaf of bread?")
        chatNpc(
            angry,
            "It's harder to make than it sounds! Now get out before I whack you one for being so " +
                "rude.",
        )
    }

    private suspend fun Dialogue.cookRepeatsRecipe() {
        chatPlayer(quiz, "I forgot the recipe to make a Steak sandwich.")
        chatNpc(
            angry,
            "For Saradomin's sake! Take some bread, cut it in half and stick some cooked Steak " +
                "inside, done.",
        )
        chatPlayer(happy, "Oh yeah, thank you!")
    }

    private suspend fun Dialogue.cookInspectsSandwich() {
        chatPlayer(happy, "I've managed to make myself a Steak sandwich.")
        chatNpc(neutral, "Let me take a look at it.")
        mesbox("You show your Steak sandwich to the cook.")
        chatNpc(laugh, "Ha ha ha, you call this a sandwich!?")
        chatPlayer(sad, "Well, I tried my best...")
        chatNpc(neutral, "I'm sure you did. It'll be enough for Marley, anyway.")
    }

    private suspend fun Dialogue.cookSellsFood() {
        chatPlayer(quiz, CookSellFood)
        chatNpc(
            neutral,
            "I suppose I could sell you some cabbage, if you're willing to pay for it. Cabbage is " +
                "good for you.",
        )
        if (access.inv.freeSpace() < 1) {
            chatPlayer(sad, "Oh, I haven't got enough space to carry it.")
            chatNpc(angry, "Why are you asking me to sell you food if you can't carry it? Go away!")
            return
        }
        if (access.invTotal(access.inv, BelowIceMountainRefs.Coins) < CabbagePrice) {
            chatPlayer(sad, "Oh, I haven't got any money.")
            chatNpc(
                angry,
                "Why are you asking me to sell you food if you haven't got any money? Go away!",
            )
            return
        }
        val buy =
            choice2("Alright, I'll buy a cabbage.", true, "No thanks, I don't like cabbage.", false)
        if (!buy) {
            chatPlayer(neutral, "No thanks, I don't like cabbage.")
            chatNpc(angry, "Bah! People these days only appreciate junk food.")
            return
        }
        chatPlayer(neutral, "Alright, I'll buy a cabbage.")
        if (!access.player.invTakeFee(CabbagePrice)) {
            return
        }
        access.invAddOrDrop(objRepo, BelowIceMountainRefs.Cabbage)
        chatNpc(neutral, "It's a deal. Now, make sure you eat it all up. Cabbage is good for you.")
    }

    private suspend fun Dialogue.cookRefusesFreeFood() {
        chatPlayer(quiz, CookFreeFood)
        chatNpc(quiz, "Can you give me any free money?")
        chatPlayer(quiz, "Why should I give you free money?")
        chatNpc(quiz, "Why should I give you free food?")
        chatPlayer(neutral, "Oh, forget it.")
    }

    private suspend fun Dialogue.cookInsulted() {
        chatPlayer(neutral, CookLeave)
        chatNpc(
            angry,
            "How dare you? I put a lot of effort into cleaning this kitchen. My daily sweat and " +
                "elbow-grease keep this kitchen clean!",
        )
        chatPlayer(shocked, "Ewww!")
        chatNpc(angry, "Oh, just leave me alone.")
    }

    private suspend fun Dialogue.charlieTheTramp() {
        chatNpc(sad, "Spare some change, guv?")

        val options = mutableListOf<Pair<String, suspend Dialogue.() -> Unit>>()

        if (quest.isQuestInProgress(player) && bimMarley < BelowIceMountainTracks.Recruited) {
            options +=
                "I'm looking for a man named Marley. Have you seen him?" to
                    {
                        charlieMarleyBranch()
                    }
        }
        options += "Who are you?" to { charlieWhoAreYou() }
        if (shieldOfArrav.charlieBlackArmAskVisible(access)) {
            options +=
                "I'm looking for the Black Arm Gang." to { shieldOfArrav.charlieBlackArmAsk(this) }
        }
        if (shieldOfArrav.charliePhoenixAskVisible(access)) {
            options +=
                "Any idea where the Phoenix Gang are based?" to
                    {
                        shieldOfArrav.charliePhoenixAsk(this)
                    }
        }
        options += "Okay, here you go." to { charlieGiveCoin() }
        options +=
            "Sorry, I haven't got any." to
                {
                    chatPlayer(neutral, "Sorry, I haven't got any.")
                    chatNpc(neutral, "Thanks anyway.")
                }
        options +=
            "Go get a job!" to
                {
                    chatPlayer(angry, "Go get a job!")
                    chatNpc(angry, "You startin? I hope your nose falls off!")
                }

        val pick = charlieMenu(options.map { it.first })
        options[pick].second(this)
    }

    private suspend fun Dialogue.charlieMenu(labels: List<String>): Int =
        when (labels.size) {
            4 -> choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3)
            5 -> choice5(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, labels[4], 4)
            else ->
                choice6(
                    labels[0],
                    0,
                    labels[1],
                    1,
                    labels[2],
                    2,
                    labels[3],
                    3,
                    labels[4],
                    4,
                    labels[5],
                    5,
                )
        }

    private suspend fun Dialogue.charlieWhoAreYou() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(
            neutral,
            "Charles. Charles E. Trampin' at your service. Now, about that change you were going " +
                "to give me...",
        )
    }

    private suspend fun Dialogue.charlieGiveCoin() {
        chatPlayer(neutral, "Okay, here you go.")
        if (access.invDel(access.inv, "obj.coins", 1).failure) {
            chatPlayer(sad, "Oh wait. I haven't got any, sorry.")
            chatNpc(neutral, "Thanks anyway.")
            return
        }
        chatNpc(happy, "Hey, thanks a lot!")
        val followUp =
            choice2(
                "No problem.",
                false,
                "Don't I get some sort of quest hint or something now?",
                true,
            )
        if (!followUp) {
            chatPlayer(neutral, "No problem.")
            return
        }
        chatPlayer(quiz, "So...don't I get some sort of quest hint or something now?")
        chatNpc(quiz, "Huh? What do you mean? That wasn't why I asked you for money.")
        chatNpc(sad, "I just need to eat...")
    }

    private suspend fun Dialogue.charlieMarleyBranch() {
        chatPlayer(quiz, "I'm looking for a man named Marley. Have you seen him?")
        chatNpc(happy, "Ah Marley, my cousin. He's always getting himself into trouble!")
        chatPlayer(quiz, "Oh really? Do you know where I could find him?")
        chatNpc(
            neutral,
            "Hmm... it's been a while since I've seen him. Last I heard he got caught in the " +
                "middle of a robbery and had to flee Varrock.",
        )
        chatNpc(
            neutral,
            "Something to do with trying to steal a set of Wizard robes from the museum.",
        )
        chatPlayer(shocked, "Great, so I'm dealing with a criminal!")
        chatNpc(
            neutral,
            "Afraid so. Marley is no stranger to the authorities... Knowing him, he's probably " +
                "hanging out in Edgeville ruins till the attention dies down. I'd try there.",
        )
        chatPlayer(neutral, "Perfect. I'll go look for him.")
    }

    private suspend fun Dialogue.burntofPub() {
        when {
            bimBurntof >= BelowIceMountainTracks.Recruited -> burntofEntrance()
            bimBurntof >= BelowIceMountainTracks.BurntofGameOffered -> burntofGameAgain()
            bimBurntof >= BelowIceMountainTracks.BurntofDrinking -> burntofRecruitmentTalk()
            bimBurntof >= BelowIceMountainTracks.BurntofWantsDrink -> burntofWantsBeer()
            else -> burntofOpener()
        }
    }

    private suspend fun Dialogue.burntofOpener() {
        chatNpc(drunk, "Buy ush a drink.")
        chatPlayer(quiz, "Are you Burntof?")

        access.bimBurntof = BelowIceMountainTracks.BurntofWantsDrink

        chatNpc(drunk, "I might be, I might not. Buy ush a drink and find out *hic*.")

        if (findDrink() != null) {
            chatPlayer(neutral, "It just so happens I've got a drink you can have.")
            chatNpc(drunk, "Giv' it 'ere then.")
            burntofTakesDrink()
            return
        }

        chatPlayer(neutral, "Fine... I'll get you a beer.")
        chatNpc(drunk, "Thanksh a bunch.")
    }

    private suspend fun Dialogue.burntofWantsBeer() {
        chatNpc(drunk, "Where'sh my beer?")
        if (findDrink() == null) {
            chatPlayer(neutral, "I'll get right on it.")
            return
        }
        chatPlayer(neutral, "Here you go.")
        burntofTakesDrink()
    }

    private suspend fun Dialogue.burntofTakesDrink() {
        val drink = findDrink() ?: return
        if (access.invDel(access.inv, drink, 1).failure) {
            return
        }

        access.bimBurntof = BelowIceMountainTracks.BurntofDrinking
        mesbox("You give Burntof the drink.")

        when (drink) {
            BelowIceMountainRefs.DwarvenStout ->
                chatNpc(drunk, "Ah! A $ladOrLass of fhine taste *hic*. I love Dwarven shtout!")
            BelowIceMountainRefs.MindBomb ->
                chatNpc(drunk, "No messhing around with you! Only the strong stuff, I like it!")
            else -> chatNpc(drunk, "That'sh good shtuff.")
        }

        burntofRecruitmentTalk()
    }

    private suspend fun Dialogue.burntofRecruitmentTalk() {
        chatPlayer(quiz, "So, are you Burntof?")
        chatNpc(drunk, "Yesh I am *hic*. What can thish dwarf do for you?")
        chatPlayer(neutral, "Willow sent me. She needs your help with a job.")
        chatNpc(drunk, "Willow? Now that'sh a name I drink to forget!")
        chatPlayer(quiz, "What do you mean?")
        chatNpc(
            drunk,
            "Willow alwaysh insishted on changing me waysh. I knows explosivesh. Just because I " +
                "lost a couple of me fingersh a few yearsh back doesn't mean I'll do it again *hic*.",
        )
        chatNpc(
            drunk,
            "I knew the exhact amount of dyn*hic*amite needed to blow that rich fool's shafe. We " +
                "woulda been shet for life!",
        )
        chatPlayer(quiz, "I thought you were a team of archaeologists?")
        chatNpc(drunk, "Archaeologishts? Shure, if that's what you want to call ush?!")
        chatPlayer(
            neutral,
            "Willow sent me to get you. She's found a new digsite and needs your expert knowledge " +
                "on explosives.",
        )
        chatNpc(drunk, "Hmmm... *hic*")

        access.bimBurntof = BelowIceMountainTracks.BurntofGameOffered

        chatNpc(
            drunk,
            "Tell you what. If you beat me in a friendly game of rock paper s*hic*cissorsh, I'll " +
                "join the team.",
        )
        chatNpc(drunk, "*Whispers* We'll have the game upshtairs away from everyone.")
        access.closeChat()
        cutscene.rockPaperScissors(access)
    }

    private suspend fun Dialogue.burntofGameAgain() {
        chatNpc(drunk, "*Whispers* We'll have the game upshtairs away from everyone.")
        access.closeChat()
        cutscene.rockPaperScissors(access)
    }

    private suspend fun Dialogue.burntofEntrance() {
        chatPlayer(quiz, "You managed to make your way over to the ruins then?")
        chatNpc(drunk, "Yush, I did, why's that shuch a *hic* shurprise?")
        chatPlayer(neutral, "I thought you would pass out on your way.")
        chatNpc(
            drunk,
            "Dwarshves have a very good shense of direction and we can *hic* hold our beer very " +
                "well.",
        )
        chatPlayer(
            quiz,
            "Are you sure you'll be able to handle the dynamite in your current state?",
        )
        chatNpc(drunk, "Hah! I could blow thish rubble away with one hand and me eyesh closed.")
    }

    private fun Dialogue.findDrink(): String? =
        BurntofDrinks.firstOrNull { access.invTotal(access.inv, it) > 0 }

    private suspend fun Dialogue.ramarno() {
        when (access.camdozaalRamarnoIntro) {
            0 -> ramarnoFirstMeeting()
            1 -> ramarnoProposal()
            else -> ramarnoQuestions()
        }
    }

    internal suspend fun Dialogue.ramarnoFirstMeeting() {
        chatPlayer(shocked, "Wow. I honestly didn't expect to see a dwarf in these ruins.")
        chatNpcSpecific(RamarnoName, RamarnoHead, shocked, "I can't believe it... ")
        chatPlayer(neutral, "Yeah, exactly.")
        chatNpcSpecific(
            RamarnoName,
            RamarnoHead,
            shocked,
            "It's been so long since I've heard another person's voice.",
        )
        chatNpcSpecific(RamarnoName, RamarnoHead, quiz, "How did you find me?")
        chatPlayer(neutral, "To be honest, it was all a bit of an accident.")
        chatNpcSpecific(RamarnoName, RamarnoHead, quiz, "What do you mean?")
        chatPlayer(
            neutral,
            "There was a gang of... well, I thought were archaeologists, but it turned out they " +
                "were grave robbers.",
        )
        chatPlayer(
            neutral,
            "I helped them clear the entrance to these ruins and we worked our way inside, only " +
                "to wake up a monster! They ran off and left me to defeat it.",
        )
        chatNpcSpecific(RamarnoName, RamarnoHead, confused, "That wasn't a monster...")
        chatPlayer(angry, "Well it looked menacing enough!")
        chatNpcSpecific(
            RamarnoName,
            RamarnoHead,
            confused,
            "That was one of the guardians of this once great city.",
        )
        chatPlayer(quiz, "City?")
        chatNpcSpecific(
            RamarnoName,
            RamarnoHead,
            happy,
            "Yes! Camdozaal, home of the Imcando Dwarves.",
        )
        chatNpcSpecific(
            RamarnoName,
            RamarnoHead,
            sad,
            "These halls once bustled with life, but that was a long time ago. I'm the only one " +
                "left now. The name's Ramarno.",
        )
        chatPlayer(
            neutral,
            "Well, Ramarno, I'm ${player.displayName}, and you have me at a loss for words! I'd " +
                "have never even imagined there would be a whole city down here.",
        )
        chatNpcSpecific(RamarnoName, RamarnoHead, sad, "Not much of a city anymore, I suppose.")
        chatPlayer(
            sad,
            "I guess I didn't help with that. Sorry about breaking into your home and killing your guardian.",
        )
        chatNpcSpecific(
            RamarnoName,
            RamarnoHead,
            neutral,
            "We have plenty more. No real harm done. That said, I do think I might have a way for " +
                "you to make up for it. There'll likely be something in it for you as well.",
        )
        chatNpcSpecific(
            RamarnoName,
            RamarnoHead,
            neutral,
            "My workshop is just north of here. Meet me there and we'll talk further.",
        )

        access.camdozaalRamarnoIntro = 1
        mesbox("Ramarno departs for his workshop.")
    }

    private suspend fun Dialogue.ramarnoProposal() {
        chatPlayer(neutral, "Hello again.")
        chatNpc(neutral, "Ah, ${player.displayName}. You made it.")
        chatPlayer(quiz, "So, tell me about this proposal of yours.")
        chatNpc(
            sad,
            "Camdozaal is a shadow of what it once was. We Imcando Dwarves were the finest " +
                "artisans in the world, but now I am all that remains. Today, you'll only find " +
                "echoes of our once great city.",
        )
        chatNpc(
            sad,
            "But that which was once great can still be great again! We Imcando Dwarves know that " +
                "better than most. There is hope for the future of Camdozaal. But I need help.",
        )
        chatPlayer(quiz, "What do you need?")
        chatNpc(
            neutral,
            "We have a Sacred Forge. You will find none other like it in all the world. Some of " +
                "the greatest weapons of legend started life here.",
        )
        chatNpc(
            neutral,
            "The Forge could be the key to Camdozaal's future, but it's going out. If it does, I " +
                "fear it will never be lit again. I can no longer gather the fuel it needs. But " +
                "perhaps you can?",
        )
        chatPlayer(quiz, "Fuel?")
        chatNpc(
            neutral,
            "Barronite. You'll only find it in these ruins. If you can gather some for me, I can " +
                "use it to keep the Forge lit. In return, I'll let you take what you want from " +
                "our Great Vault.",
        )
        chatNpc(
            neutral,
            "I no longer have use for anything in the Vault. Besides, material possessions mean " +
                "nothing compared to the fire of the Sacred Forge.",
        )
        chatPlayer(
            quiz,
            "So I find barronite in the ruins and bring it to you? Doesn't sound too difficult. " +
                "Where should I start?",
        )

        access.camdozaalRamarnoIntro = 2

        chatNpc(
            neutral,
            "Anywhere you like. The ruins are filled with barronite, so anything you do here " +
                "should yield some. If you need more information on specific things you can do, " +
                "just let me know.",
        )
        ramarnoOptions()
    }

    private suspend fun Dialogue.ramarnoQuestions() {
        chatPlayer(neutral, "Hello again.")
        chatNpc(
            neutral,
            "Ah ${player.displayName}. I'm sure you have plenty of questions. I will do my best " +
                "to answer them.",
        )
        ramarnoOptions()
    }

    private suspend fun Dialogue.ramarnoOptions() {
        while (true) {
            val choice =
                choice4(
                    "What can I do here?",
                    1,
                    "What happened to the other Imcando Dwarves?",
                    2,
                    "Can I use the Forge?",
                    3,
                    "I'll see you later.",
                    4,
                )
            when (choice) {
                1 -> ramarnoWhatCanIDo()
                2 -> ramarnoLostKin()
                3 -> ramarnoForge()
                else -> {
                    chatPlayer(neutral, "I'll see you later.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.ramarnoWhatCanIDo() {
        chatPlayer(quiz, "What can I do here?")
        chatNpc(
            neutral,
            "As you can see, Camdozaal isn't the city it once was. That said, there are still a " +
                "few things around here that might interest you. There's the golem workshop, the " +
                "barronite mine and the offering altar.",
        )
        val topic =
            choice4(
                "Tell me more about the golem workshop.",
                1,
                "Tell me more about the barronite mine.",
                2,
                "Tell me more about the offering altar.",
                3,
                "Let's talk about something else.",
                4,
            )
        when (topic) {
            1 -> {
                chatPlayer(quiz, "Tell me more about the golem workshop.")
                chatNpc(
                    neutral,
                    "You'll find it to the east. It was once the forefront of our technological " +
                        "advances. Sadly, it was also one of the major factors that led to our doom.",
                )
                chatPlayer(quiz, "What were you doing there?")
                chatNpc(
                    neutral,
                    "Camdozaal was built upon the ruins of an older civilization. Little trace of " +
                        "them remained by the time we arrived. However, when building the city, " +
                        "ancient golems were discovered.",
                )
                chatNpc(
                    neutral,
                    "These golems were very crude. They were mostly made from clay, of all " +
                        "things. We used our advanced Smithing knowledge to improve their form. " +
                        "Eventually, we were able to get them working again.",
                )
                chatNpc(
                    neutral,
                    "We kept pushing with the golems. Many of them we imbued with magical " +
                        "energies. We started with mind and body energy, but eventually moved on " +
                        "to chaos as well.",
                )
                chatNpc(
                    sad,
                    "Alas, the influence of chaos had grave results. We lost control of many. " +
                        "Those that became corrupt had to be locked away, but by then it was " +
                        "already too late.",
                )
                chatNpc(
                    sad,
                    "The barbarians had learnt of our experimentation, and were determined to " +
                        "destroy all traces of it.",
                )
                chatNpc(
                    neutral,
                    "Ironically, locking them away meant the golems were among the few things to " +
                        "survive the barbarians. If you fancy a bit of combat, you might want to " +
                        "go take a look.",
                )
                chatNpc(
                    neutral,
                    "They're largely made of barronite now, so they're a good source of the stuff.",
                )
                chatNpc(
                    neutral,
                    "Their cores are also very interesting. Apparently, the dwarves of the golem " +
                        "workshop once used old cores to craft runes. Maybe you could do the same.",
                )
            }
            2 -> {
                chatPlayer(quiz, "Tell me more about the barronite mine.")
                chatNpc(
                    neutral,
                    "You'll find it to the north west. Unsurprisingly, it's the best place to " +
                        "find barronite in the city.",
                )
                chatNpc(
                    neutral,
                    "At one time, our miners would work all through the day there to gather the " +
                        "barronite needed to power the Sacred Forge. That was a long time ago.",
                )
                chatNpc(
                    neutral,
                    "If you're lucky, you might find other things while mining there. We were not " +
                        "the first to build a home beneath this mountain. Sometimes traces of an " +
                        "older civilization can be found.",
                )
                chatPlayer(quiz, "What kind of traces?")
                chatNpc(
                    neutral,
                    "You might find some ancient relics encased within barronite. If you do, you " +
                        "can use the anvils here to crack open the deposits.",
                )
                chatNpc(
                    neutral,
                    "The relics are of no interest to me. Maybe some of your race would find them interesting.",
                )
            }
            3 -> {
                chatPlayer(quiz, "Tell me more about the offering altar.")
                chatNpc(
                    neutral,
                    "You'll find it to the south west. It's not something that ever interested " +
                        "me, but most of my kind were followers of Saradomin - the god of order " +
                        "and wisdom.",
                )
                chatNpc(
                    neutral,
                    "Many of my kin would visit the altar to make offerings in his name.",
                )
                chatNpc(
                    neutral,
                    "First, they'd catch fish from the nearby reservoir. Next, they'd ritually " +
                        "prepare them at tables by the altar. Finally, they'd use the altar to " +
                        "offer the fish.",
                )
                chatNpc(
                    neutral,
                    "There's plenty of barronite to be found within the reservoir, so it might be " +
                        "worth checking out. Be warned though: worshipping gods never did my kind " +
                        "any good.",
                )
            }
            else -> {
                chatPlayer(neutral, "Let's talk about something else.")
                chatNpc(neutral, "Very well.")
            }
        }
    }

    private suspend fun Dialogue.ramarnoLostKin() {
        chatPlayer(quiz, "What happened to the other Imcando Dwarves?")
        chatNpc(
            sad,
            "Ah... It's a tough tale to tell. Even though it's been many years since the last of " +
                "my kin died out, the memory is still fresh.",
        )
        chatPlayer(neutral, "You don't have to tell me if the memory hurts too much.")
        chatNpc(neutral, "No, I will. The legacy of my kin must live on.")
        chatNpc(
            neutral,
            "As you can see around you, Camdozaal was once a great city. Alive with the hum of " +
                "activity. We dug deep into the mountain and reaped the rewards.",
        )
        chatNpc(
            neutral,
            "Our methods would often differ from other dwarves. Whereas they just used " +
                "traditional smithing techniques, we would combine them with magic as well.",
        )
        chatNpc(
            sad,
            "Alas, that was ultimately our undoing. We learned of a horde of barbarians " +
                "pillaging across the land, hunting down and killing anyone who dared use magic.",
        )
        chatNpc(
            sad,
            "We underestimated them. We believed our weapons would be enough to keep us safe. We were wrong.",
        )
        chatNpc(
            sad,
            "The barbarians brought ruin upon Camdozaal. Most of my kind died as the city " +
                "collapsed around them. Only a few of us survived until the end. Now, only I remain.",
        )
        chatPlayer(
            neutral,
            "The world has changed a lot, Ramarno. Magic is practiced freely. Those barbarians " +
                "failed to wipe it from Gielinor.",
        )
        chatNpc(
            sad,
            "I'm glad they didn't win, I suppose. But it doesn't change much for me. I am still the last Imcando Dwarf.",
        )
        chatPlayer(
            neutral,
            "I'm sorry Ramarno. I will do my best to help ensure the legacy of your kin lives on.",
        )
        chatNpc(
            neutral,
            "Thank you, ${player.displayName}. That gives me some hope. If you have any more " +
                "questions, do not hesitate to ask.",
        )
    }

    private suspend fun Dialogue.ramarnoForge() {
        chatPlayer(quiz, "Can I use the Forge?")
        chatNpc(
            neutral,
            "I'm afraid not. The Sacred Forge is like none other in existence. Only an Imcando " +
                "Dwarf could use it correctly.",
        )
        chatPlayer(quiz, "What makes it so special?")
        chatNpc(
            neutral,
            "Truth be told, I don't fully know. It's far older than I am. It was supposedly built in the earliest days of the city.",
        )
        chatNpc(
            neutral,
            "What I do know is that it's been imbued with powerful magic - some said that of the " +
                "gods themselves. The Forge powers the entire city, or what's left of it at least.",
        )
        chatNpc(
            neutral,
            "I've been doing my best to keep it lit over the years. It's not a job I'm capable of " +
                "any more though. That's why I'm hoping you can help.",
        )
        chatNpc(
            neutral,
            "Bring me any barronite you find. In return, I can channel enough power from the " +
                "Forge into the city or even open the Great Vault.",
        )
    }

    private suspend fun Dialogue.willowDesert() {
        chatPlayer(quiz, "So this is where you scampered off to after the monster awoke?")
        chatNpc(
            neutral,
            "Look ${player.displayName}, it was a simple misunderstanding, that's all...",
        )
        chatPlayer(angry, "Oh really? You threatening to kill me was just a misunderstanding?")
        chatNpc(
            neutral,
            "Things got a little heated down there, but we all made it out alive, right?",
        )
        chatPlayer(angry, "Barely... I had to defeat that monster on my own!")
        chatPlayer(quiz, "What are you doing out here, anyway?")
        chatNpc(
            shifty,
            "We thought you'd rat us out to the authorities, so we came out here to hide.",
        )
        chatNpc(
            shifty,
            "Plus, we heard there are some sealed tombs in the desert that haven't been touched " +
                "in hundreds of years...",
        )
        chatPlayer(neutral, "Ah, of course... scoping out your next grave to rob.")
    }

    private suspend fun Dialogue.checkalDesert() {
        chatNpc(happy, "${player.displayName}, I am so glad to see you are safe.")
        chatPlayer(neutral, "Hello, Checkal. Why didn't you tell me about Willow?")
        chatPlayer(sad, "I thought we were friends...")
        chatNpc(
            sad,
            "I am sorry ${player.displayName}. I do respect you and your love for the muscle. " +
                "But sadly I needed the money. These muscles don't pay the bills on their own.",
        )
        chatPlayer(neutral, "Oh, I understand...")
        chatNpc(
            neutral,
            "Please do not let this incident divert you from your path of becoming a strongman! " +
                "I truly believe in you.",
        )
    }

    private suspend fun Dialogue.marleyDesert() {
        chatPlayer(happy, "Wow, the desert really suits you Marley.")
        chatNpc(happy, "${player.displayName} my old pal! How are you doing?")
        chatPlayer(
            neutral,
            "Better now I don't have to deal with you. Enjoying life in the desert?",
        )
        chatNpc(
            sad,
            "Not really. I'm not suited to such harsh climates. No steak sandwiches here either...",
        )
        chatPlayer(
            happy,
            "Oh what a shame! Poor Marley's going to starve as he doesn't have anyone to make " +
                "his sandwiches... So sad.",
        )
    }

    private suspend fun Dialogue.burntofDesert() {
        chatPlayer(
            quiz,
            "How you holding up Burntof? Must have had quite the fright when you woke that monster.",
        )
        chatNpc(drunk, "You're right $ladOrLass. It did give me a little fright.")
        chatNpc(drunk, "But I saw some interesting things down in the ruins...")
        chatPlayer(quiz, "What do you mean?")
        chatNpc(
            drunk,
            "Some of the markings on the wall look very familiar to me. I've seen them before.",
        )
        chatPlayer(
            neutral,
            "Well, when I defeated the monster, an Imcando dwarf opened the barred door...",
        )
        chatNpc(
            shocked,
            "An Imcando dwarf! I thought they were all but extinct! You've found something truly " +
                "amazing ${player.displayName}!",
        )
        chatNpc(drunk, "I wish I had a drink to celebrate, but this desert is bone dry.")
    }

    private suspend fun ProtectedAccess.makeSteakSandwich(breadSlot: Int) {
        if (invTotal(inv, BelowIceMountainRefs.CookedMeat) <= 0) {
            mes(constants.dm_default, ChatType.Engine)
            return
        }
        if (invDel(inv, BelowIceMountainRefs.Bread, 1, slot = breadSlot).failure) {
            return
        }
        val replaced =
            invReplace(inv, BelowIceMountainRefs.CookedMeat, 1, BelowIceMountainRefs.SteakSandwich)
        if (replaced.failure) {
            invAdd(inv, BelowIceMountainRefs.Bread, 1)
            return
        }
        mes("You combine the cooked meat and bread to make a Steak sandwich!")
    }

    private companion object {
        private const val CheckalName = "Checkal"

        private const val RamarnoName = "Ramarno"

        private const val RamarnoHead = BelowIceMountainRefs.RamarnoEntrance

        private const val FlexPrompt =
            "Show your strength by <col=ef1020>flexing</col> your muscles."

        private const val CheckalSignOff =
            "What an exhausting workout. I hope the others aren't as much effort to recruit."

        private const val MarleySignOff =
            "Marley is certainly an interesting character. Hopefully the next team member is more " +
                "respectable."

        private const val WillowAnyoneElse = "Need help finding anyone else?"

        private const val CookSellFood = "Can you sell me any food?"
        private const val CookFreeFood = "Can you give me any free food?"
        private const val CookLeave = "I don't want anything from this horrible kitchen."

        private const val CabbagePrice = 1

        private const val WorkoutFee = 25_000

        private const val MaxStrength = 99

        private val BurntofDrinks =
            listOf(
                BelowIceMountainRefs.DwarvenStout,
                BelowIceMountainRefs.MindBomb,
                BelowIceMountainRefs.AsgarnianAle,
                BelowIceMountainRefs.Beer,
            )
    }
}

internal fun ProtectedAccess.closeChat() {
    ifCloseSub("interface.chat_left")
    ifCloseSub("interface.chat_right")
}

internal suspend fun ProtectedAccess.recruitCrewman(write: ProtectedAccess.() -> Unit) {
    fadeOverlay(
        startColour = 0,
        startTransparency = 255,
        endColour = 0,
        endTransparency = 0,
        clientDuration = RecruitFadeDuration,
    )
    delay(RecruitFadeTicks)
    write()
    checkCrewRecruited()
    fadeOverlay(
        startColour = 0,
        startTransparency = 0,
        endColour = 0,
        endTransparency = 255,
        clientDuration = RecruitFadeDuration,
    )
    delay(RecruitFadeTicks)
    closeFadeOverlayNow()
}

internal fun ProtectedAccess.checkCrewRecruited() {
    val quest = Quest.get(BelowIceMountainRefs.QuestKey) ?: return
    val stage = quest.getQuestStage(player)
    val recruited = BelowIceMountainTracks.Recruited
    val all = bimCheckal >= recruited && bimMarley >= recruited && bimBurntof >= recruited

    when {
        all && stage < BelowIceMountainStage.AllRecruited ->
            quest.setQuestStage(this, BelowIceMountainStage.AllRecruited)
        !all && stage < BelowIceMountainStage.FirstRecruited ->
            quest.setQuestStage(this, BelowIceMountainStage.FirstRecruited)
    }
}

internal fun ProtectedAccess.recruitSignOff(whileRecruiting: String): String {
    val recruited = BelowIceMountainTracks.Recruited
    val all = bimCheckal >= recruited && bimMarley >= recruited && bimBurntof >= recruited
    return if (all) LastRecruitSignOff else whileRecruiting
}

internal suspend fun ProtectedAccess.marleyEats() {
    repeat(EatSynthCount) {
        soundSynth(EatSound)
        delay(EatSynthGap)
    }
}

private const val LastRecruitSignOff =
    "Well, that's everyone. I guess I should head off the entrance on the west side of Ice Mountain."

private const val RecruitFadeDuration = 50

private const val RecruitFadeTicks = 2

private const val EatSound = "synth.EAT"

private const val EatSynthCount = 3

private const val EatSynthGap = 2
