package org.rsmod.content.quest.area.varrock

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.varrock.DemonSlayerProgress.Companion.BonesRequired
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

internal class DemonSlayerDialogue(
    private val quest: Quest,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val cutscenes: DemonSlayerCutscenes,
    private val scenery: DemonSlayerScenery,
) {
    private val progress = DemonSlayerProgress(quest)
    private val traibornAsked = progress.traibornAsked
    private val traibornBones = progress.traibornBones
    private val traibornKeyGiven = progress.traibornKeyGiven

    private val visionSoundTicks = quest.temporaryInt("VISION_SOUND", "varp.demon_slayer_vision_sound")

    private val visionAris = quest.attribute<Npc?>("VISION_ARIS", default = null, temp = true)

    private val arisAsked = progress.arisAsked

    fun bonesOwed(player: Player): Int = progress.bonesOwed(player)

    fun ScriptContext.register() {
        onOpNpc1(DemonSlayerRefs.Aris) { aris(it.npc) }
        onOpNpc1(DemonSlayerRefs.Prysin) { prysin(it.npc) }
        onOpNpc1(DemonSlayerRefs.Rovin) { rovin(it.npc) }
        onOpNpc1(DemonSlayerRefs.Traiborn) { traiborn(it.npc) }

        onPlayerSoftTimer(VisionSoundTimer) { player.visionSoundLoop() }
    }

    private suspend fun ProtectedAccess.aris(npc: Npc) {
        npcPlayerFaceClose(npc)
        faceSquare(npc.coords)

        startDialogue(npc) {
            when {
                quest.isQuestNotStarted(player) -> arisOpener()
                // TODO: Add Aris's post-quest dialogue.
                quest.isQuestCompleted(player) ->
                    chatNpc(
                        neutral,
                        "Hello young one. Cross my palm with silver and the future will be " +
                            "revealed to you.",
                    )
                else -> arisReturning()
            }
        }
    }

    private suspend fun Dialogue.arisOpener() {
        chatNpc(
            neutral,
            "Hello young one. Cross my palm with silver and the future will be revealed to you.",
        )

        if (access.invTotal(access.inv, DemonSlayerRefs.Coins) < ArisFee) {
            chatPlayer(sad, "Oh dear. I don't have any money.")
            return
        }

        val accept = choice2("Yes.", true, "No.", false, title = "Start the Demon Slayer quest?")
        if (!accept) {
            chatPlayer(neutral, "No, I don't believe in that stuff.")
            chatNpc(neutral, "Ok suit yourself.")
            return
        }

        chatPlayer(neutral, "Okay, here you go.")
        access.invDel(access.inv, DemonSlayerRefs.Coins, ArisFee)

        arisVision(checkNotNull(npc) { "Aris dialogue requires an npc." })
        arisQuestions()
    }

    private suspend fun Dialogue.arisVision(aris: Npc) {
        aris.noneMode()
        aris.faceSquare(DemonSlayerCoords.CrystalBallTable)
        aris.anim(DemonSlayerRefs.SeqArisAttain)
        aris.spotanim(DemonSlayerRefs.SpotanimBallStart)
        access.animateCrystalBall(DemonSlayerRefs.SeqCrystalBallLeave)
        access.soundSynth(DemonSlayerRefs.SynthCrystalBallStart)
        access.startVisionSound(aris)

        var restored = false
        try {
            chatNpcNoTurn(
                happy,
                "Come closer, and listen carefully to what the future holds for you, as I peer " +
                    "into the swirling mists of the crystal ball.",
            )
            chatNpcNoTurn(neutral, "I can see images forming. I can see you.")
            chatNpcNoTurn(
                confused,
                "You are holding a very impressive looking sword. I'm sure I recognise that " +
                    "sword...",
            )
            chatNpcNoTurn(confused, "There is a big dark shadow appearing now.")

            access.stopVisionSound()
            aris.anim(DemonSlayerRefs.SeqArisReturn)
            aris.spotanim(DemonSlayerRefs.SpotanimBallEnd)
            access.animateCrystalBall(DemonSlayerRefs.SeqCrystalBallReturn)
            access.soundSynth(DemonSlayerRefs.SynthCrystalBallEnd)
            chatNpcNoTurn(worried, "Aaargh!")

            chatPlayer(quiz, "Are you all right?")
            chatNpc(worried, "It's Delrith! Delrith is coming!")
            restored = true
        } finally {
            if (!restored) {
                access.stopVisionSound()
                aris.playerFaceClose(player)
            }
        }
        chatPlayer(worried, "Who's Delrith?")
        chatNpc(sad, "Delrith...")
        chatNpc(neutral, "Delrith is a powerful demon.")
        chatNpc(
            worried,
            "Oh! I really hope he didn't see me looking at him through my crystal ball!",
        )
        chatNpc(
            sad,
            "He tried to destroy this city 150 years ago. He was stopped just in time by the " +
                "great hero Wally.",
        )
        chatNpc(
            sad,
            "Using his magic sword Silverlight, Wally managed to trap the demon in the stone " +
                "circle just south of this city.",
        )
        chatNpc(
            shocked,
            "Ye gods! Silverlight was the sword you were holding in my vision! You are the one " +
                "destined to stop the demon this time.",
        )
    }

    private suspend fun Dialogue.arisQuestions() {
        while (true) {
            val asked = arisAsked.get(player)
            val remaining = ArisQuestionOrder.filter { asked and (1 shl it) == 0 }
            if (remaining.size < 2) {
                arisFlashback()
                return
            }

            val shown = remaining.take(3)
            val picked =
                if (shown.size == 2) {
                    choice2(
                        ArisQuestions.getValue(shown[0]),
                        shown[0],
                        ArisQuestions.getValue(shown[1]),
                        shown[1],
                        title = ArisOptionsTitle,
                    )
                } else {
                    choice3(
                        ArisQuestions.getValue(shown[0]),
                        shown[0],
                        ArisQuestions.getValue(shown[1]),
                        shown[1],
                        ArisQuestions.getValue(shown[2]),
                        shown[2],
                        title = ArisOptionsTitle,
                    )
                }
            arisAsked.set(player, asked or (1 shl picked))

            when (picked) {
                ArisHowToFight -> {
                    chatPlayer(quiz, "How am I meant to fight a demon who can destroy cities?!")
                    chatNpc(
                        neutral,
                        "If you face Delrith while he is still weak from being summoned, and use " +
                            "the correct weapon, you will not find the task too arduous.",
                    )
                    chatNpc(
                        neutral,
                        "Do not fear. If you follow the path of the great hero Wally, then you " +
                            "are sure to defeat the demon.",
                    )
                }
                ArisWhereIsHe -> {
                    chatPlayer(happy, "Okay, where is he? I'll kill him for you!")
                    chatNpc(laugh, "Ah, the overconfidence of the young!")
                    chatNpc(
                        neutral,
                        "Delrith can't be harmed by ordinary weapons. You must face him using " +
                            "the same weapon that Wally used.",
                    )
                }
                ArisWallyName -> {
                    chatPlayer(bored, "Wally doesn't sound a very heroic name.")
                    chatNpc(
                        neutral,
                        "Yes I know. Maybe that is why history doesn't remember him. However he " +
                            "was a very great hero.",
                    )
                    chatNpc(
                        neutral,
                        "Who knows how much pain and suffering Delrith would have brought forth " +
                            "without Wally to stop him!",
                    )
                    chatNpc(
                        neutral,
                        "It looks like you are going to need to perform similar heroics.",
                    )
                }
                else -> {
                    arisFlashback()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.arisFlashback() {
        chatPlayer(quiz, "So how did Wally kill Delrith?")
        cutscenes.wallyFlashback(access)

        chatNpc(sad, "Delrith will come forth from the stone circle again.")
        chatNpc(
            sad,
            "I would imagine an evil sorcerer is already starting on the rituals to summon " +
                "Delrith as we speak.",
        )
        arisAdvice()
    }

    private suspend fun Dialogue.arisAdvice() {
        while (true) {
            val picked =
                choice4(
                    ArisQuestions.getValue(ArisHowToFight),
                    ArisHowToFight,
                    ArisQuestions.getValue(ArisWhereIsHe),
                    ArisWhereIsHe,
                    "What is the magical incantation?",
                    ArisIncantation,
                    "Where can I find Silverlight?",
                    ArisWhereSilverlight,
                    title = ArisOptionsTitle,
                )

            when (picked) {
                ArisHowToFight -> {
                    chatPlayer(quiz, "How am I meant to fight a demon who can destroy cities?!")
                    chatNpc(
                        neutral,
                        "If you face Delrith while he is still weak from being summoned, and use " +
                            "the correct weapon, you will not find the task too arduous.",
                    )
                    chatNpc(
                        neutral,
                        "Do not fear. If you follow the path of the great hero Wally, then you " +
                            "are sure to defeat the demon.",
                    )
                }
                ArisWhereIsHe -> {
                    chatPlayer(happy, "Okay, where is he? I'll kill him for you!")
                    chatNpc(laugh, "Ah, the overconfidence of the young!")
                    chatNpc(
                        neutral,
                        "Delrith can't be harmed by ordinary weapons. You must face him using " +
                            "the same weapon that Wally used.",
                    )
                }
                ArisIncantation -> arisRepeatsIncantation()
                else -> {
                    chatPlayer(quiz, "Where can I find Silverlight?")
                    chatNpc(
                        neutral,
                        "Silverlight has been passed down through Wally's descendants. I believe " +
                            "it is currently in the care of one of the King's knights called Sir " +
                            "Prysin.",
                    )
                    chatNpc(
                        neutral,
                        "He shouldn't be too hard to find. He lives in the royal palace in this " +
                            "city. Tell him Aris sent you.",
                    )
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.arisRepeatsIncantation() {
        chatPlayer(quiz, "What is the magical incantation?")
        chatNpc(neutral, "Oh yes, let me think a second...")

        if (!DemonSlayerIncantation.isRolled(player)) {
            DemonSlayerIncantation.roll(player, random)
        }

        chatNpc(
            neutral,
            "Alright, I think I've got it now, it goes.... " +
                "${DemonSlayerIncantation.recite(player)} Have you got that?",
        )
        chatPlayer(neutral, "I think so, yes.")
    }

    private suspend fun Dialogue.arisReturning() {
        when {
            access.carryingObj(DemonSlayerRefs.Silverlight) -> {
                chatNpc(neutral, "How goes the quest?")
                chatPlayer(neutral, "I have the sword now. I just need to kill the demon, I think.")
                chatNpc(neutral, "Yep, that's right.")
            }
            quest.getQuestStage(player) >= DemonSlayerStage.KeysNamed -> {
                chatNpc(neutral, "How goes the quest?")
                chatPlayer(
                    sad,
                    "I found Sir Prysin. Unfortunately I haven't got the sword yet. He's made it " +
                        "complicated for me.",
                )
                chatNpc(neutral, "Ok, hurry, we haven't much time.")
            }
            else -> {
                chatNpc(neutral, "Greetings. How goes thy quest?")
                chatPlayer(neutral, "I'm still working on it.")
                chatNpc(neutral, "Well if you need any advice I'm always here, young one.")
            }
        }
        arisAdvice()
    }

    private fun ProtectedAccess.animateCrystalBall(seq: String) {
        val table = findCrystalBallTable() ?: return
        locAnim(worldRepo, table, seq)
    }

    private fun ProtectedAccess.findCrystalBallTable(): LocInfo? {
        val type = DemonSlayerRefs.CrystalBallTable.asRSCM(RSCMType.LOC)
        val zone = ZoneKey.from(DemonSlayerCoords.CrystalBallTable)
        val byType = locRepo.findAll(zone).firstOrNull { it.id == type }
        return byType ?: locRepo.findAll(DemonSlayerCoords.CrystalBallTable).firstOrNull()
    }

    // Use a soft timer: a soft queue would close the dialogue before running.
    private fun ProtectedAccess.startVisionSound(aris: Npc) {
        visionSoundTicks.set(player, VisionSoundMaxTicks)
        visionAris.set(player, aris)
        softTimer(VisionSoundTimer, 1)
    }

    private fun ProtectedAccess.stopVisionSound() {
        visionSoundTicks.set(player, 0)
        visionAris.clear(player)
        clearSoftTimer(VisionSoundTimer)
    }

    private fun Player.visionSoundLoop() {
        val remaining = visionSoundTicks.get(this)
        if (remaining <= 0) {
            clearSoftTimer(VisionSoundTimer)
            visionAris.clear(this)
            return
        }
        visionSoundTicks.set(this, remaining - 1)
        worldRepo.soundArea(
            DemonSlayerCoords.CrystalBallSound,
            DemonSlayerRefs.SynthCrystalBallAnim,
            radius = VisionSoundRadius,
        )
        val aris = visionAris.getOrNull(this)
        if (aris != null && aris.isSlotAssigned) {
            aris.spotanim(DemonSlayerRefs.SpotanimBallMiddle)
        }
    }

    private suspend fun ProtectedAccess.prysin(npc: Npc) {
        npcPlayerFaceClose(npc)
        faceSquare(npc.coords)

        var handover = false
        startDialogue(npc) {
            when {
                // TODO: Add Sir Prysin's post-quest Silverlight replacement for 500 coins.
                quest.isQuestCompleted(player) -> prysinFirstMeeting(offerQuestOption = false)
                quest.getQuestStage(player) >= DemonSlayerStage.KeysNamed ->
                    handover = prysinReturning()
                else -> prysinFirstMeeting(offerQuestOption = !quest.isQuestNotStarted(player))
            }
        }

        if (handover) {
            scenery.giveSilverlight(this, npc)
        }
    }

    private suspend fun Dialogue.prysinFirstMeeting(offerQuestOption: Boolean) {
        chatNpc(neutral, "Hello, who are you?")

        val picked =
            if (offerQuestOption) {
                choice3(
                    "I am a mighty adventurer. Who are you?",
                    1,
                    "I'm not sure, I was hoping you could tell me.",
                    2,
                    "Aris said I should come and talk to you.",
                    3,
                )
            } else {
                choice2(
                    "I am a mighty adventurer. Who are you?",
                    1,
                    "I'm not sure, I was hoping you could tell me.",
                    2,
                )
            }

        when (picked) {
            1 -> {
                chatPlayer(neutral, "I am a mighty adventurer, who are you?")
                chatNpc(neutral, "I am Sir Prysin. A bold and famous knight of the realm.")
                return
            }
            2 -> {
                chatPlayer(neutral, "I was hoping you could tell me.")
                chatNpc(neutral, "Well I've never met you before.")
                return
            }
        }

        chatPlayer(neutral, "Aris said I should come and talk to you.")
        chatNpc(
            neutral,
            "Aris? Is she still alive? I remember her from when I was pretty young. Well what do " +
                "you need to talk to me about?",
        )

        val wantsSilverlight =
            choice2("I need to find Silverlight.", true, "Yes, she is still alive.", false)
        if (!wantsSilverlight) {
            chatPlayer(neutral, "Yes she is still alive. She lives right outside the castle!")
            chatNpc(
                neutral,
                "Oh, is that the same Aris? I would have thought she would have died by now. She " +
                    "was pretty old when I was a lad.",
            )
            chatNpc(neutral, "Anyway, what can I do for you?")
        }

        chatPlayer(neutral, "I need to find Silverlight.")
        chatNpc(neutral, "What do you need to find that for?")
        chatPlayer(neutral, "I need it to fight Delrith.")
        chatNpc(
            neutral,
            "Delrith? I thought the world was rid of him, thanks to my great-grandfather.",
        )

        val ballSays =
            choice2(
                "Well, Aris' crystal ball seems to think otherwise.",
                true,
                "He's back and unfortunately I've got to deal with him.",
                false,
            )
        if (ballSays) {
            chatPlayer(neutral, "Well Aris' crystal ball seems to think otherwise.")
            chatNpc(neutral, "Well if the ball says so, I'd better help you.")
        } else {
            chatPlayer(sad, "He's back and unfortunately I've got to deal with him.")
            chatNpc(
                neutral,
                "You don't look up to much. I suppose Silverlight may be good enough to carry you " +
                    "through though.",
            )
        }

        chatNpc(neutral, "The problem is getting Silverlight.")
        chatPlayer(sad, "You mean you don't have it?")
        chatNpc(
            neutral,
            "Oh I do have it, but it is so powerful that the king made me put it in a special box " +
                "which needs three different keys to open it. That way it won't fall into the " +
                "wrong hands.",
        )

        val demand = choice2("So give me the keys!", true, "And why is this a problem?", false)
        if (demand) {
            chatPlayer(angry, "So give me the keys!")
            chatNpc(sad, "Um, well, it's not so easy.")
        } else {
            chatPlayer(quiz, "And why is this a problem?")
        }

        prysinNamesTheKeys()
        prysinKeyOptions()
    }

    private suspend fun Dialogue.prysinNamesTheKeys() {
        chatNpc(
            neutral,
            "I kept one of the keys. I gave the other two to other people for safe keeping.",
        )
        chatNpc(neutral, "One I gave to Rovin, the captain of the palace guard.")
        chatNpc(neutral, "I gave the other to the wizard Traiborn.")

        if (quest.getQuestStage(player) < DemonSlayerStage.KeysNamed) {
            quest.setQuestStage(access, DemonSlayerStage.KeysNamed)
        }
    }

    private suspend fun Dialogue.prysinKeyOptions() {
        var askedDrain = false

        while (true) {
            val picked =
                if (askedDrain) {
                    choice4(
                        "So what does the drain lead to?",
                        4,
                        "Where can I find Captain Rovin?",
                        2,
                        "Where does the wizard live?",
                        3,
                        "Well I'd better go key hunting.",
                        5,
                    )
                } else {
                    choice4(
                        "Can you give me your key?",
                        1,
                        "Where can I find Captain Rovin?",
                        2,
                        "Where does the wizard live?",
                        3,
                        "Well I'd better go key hunting.",
                        5,
                    )
                }

            when (picked) {
                1 -> {
                    chatPlayer(quiz, "Can you give me your key?")
                    chatNpc(shifty, "Um.... ah....")
                    chatNpc(sad, "Well there's a problem there as well.")
                    chatNpc(
                        sad,
                        "I managed to drop the key in the drain just outside the palace kitchen. " +
                            "It is just inside and I can't reach it.",
                    )
                    askedDrain = true
                }
                4 -> {
                    chatPlayer(quiz, "So what does the drain connect to?")
                    chatNpc(
                        neutral,
                        "It is the drain for the drainpipe running from the sink in the kitchen " +
                            "down to the palace sewers.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Where can I find Captain Rovin?")
                    chatNpc(
                        neutral,
                        "Captain Rovin lives at the top of the guards' quarters in the north-west " +
                            "wing of this palace.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Where does the wizard live?")
                    chatNpc(neutral, "Wizard Traiborn?")
                    chatNpc(
                        neutral,
                        "He is one of the wizards who lives in the tower on the little island " +
                            "just off the south coast. I believe his quarters are on the first " +
                            "floor of the tower.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Well I'd better go key hunting.")
                    chatNpc(neutral, "Ok, goodbye.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.prysinReturning(): Boolean {
        val given = access.silverlightCaseOpened
        if (given) {
            chatNpc(neutral, "Have you sorted that demon out yet?")
            if (
                access.carryingObj(DemonSlayerRefs.Silverlight) ||
                    access.invTotal(access.bank, DemonSlayerRefs.Silverlight) > 0
            ) {
                chatPlayer(neutral, "No, not yet.")
                chatNpc(
                    neutral,
                    "Well get on with it. He'll be pretty powerful when he gets to full strength.",
                )
                return false
            }
            chatPlayer(sad, "Not yet. And I, um, lost Silverlight.")
            chatNpc(
                neutral,
                "Yes, I know, someone returned it to me. Take better care of it this time.",
            )
            access.invAddOrDrop(objRepo, DemonSlayerRefs.Silverlight)
            return false
        }

        chatNpc(neutral, "So how are you doing with getting the keys?")

        val held = DemonSlayerKeys.filter { access.carryingObj(it) }
        when (held.size) {
            3 -> {
                chatPlayer(happy, "I've got all three keys!")
                chatNpc(happy, "Excellent! Now I can give you Silverlight.")
                return true
            }
            1,
            2 -> chatPlayer(neutral, held.keyReport())
            else -> {
                chatPlayer(neutral, "I haven't found any of them yet.")
                val remind =
                    choice2(
                        "Can you remind me where all the keys were again?",
                        true,
                        "I'm still looking.",
                        false,
                    )
                if (!remind) {
                    chatPlayer(neutral, "I'm still looking.")
                    chatNpc(neutral, "Ok, tell me when you've got them all.")
                    return false
                }
                chatPlayer(neutral, "Can you remind me where all the keys were again?")
            }
        }

        prysinNamesTheKeys()
        prysinKeyOptions()
        return false
    }

    private fun List<String>.keyReport(): String {
        val traiborn = DemonSlayerRefs.Key1 in this
        val rovin = DemonSlayerRefs.Key2 in this
        val drain = DemonSlayerRefs.Key3 in this
        return when {
            traiborn && rovin -> "I've got the keys from Wizard Traiborn and Captain Rovin."
            rovin && drain ->
                "I've got the key from Captain Rovin and the one that you dropped down the drain."
            traiborn && drain ->
                "I've got the key from Wizard Traiborn and the one that you dropped down the drain."
            rovin -> "I've got the key from Captain Rovin."
            traiborn -> "I've got the key from Wizard Traiborn."
            else -> "I've got the key which you dropped down the drain."
        }
    }

    private suspend fun ProtectedAccess.rovin(npc: Npc) {
        npcPlayerFaceClose(npc)
        faceSquare(npc.coords)
        startDialogue(npc) { rovinConversation() }
    }

    private suspend fun Dialogue.rovinConversation() {
        chatNpc(angry, "What are you doing up here? Only the palace guards are allowed up here.")

        val opening =
            choice3(
                "I am one of the palace guards.",
                1,
                "What about the King?",
                2,
                "Yes I know, but this is important.",
                3,
            )
        if (opening != 3) {
            // TODO: Add Rovin's replies to the two joke options.
            chatPlayer(
                neutral,
                if (opening == 1) "I am one of the palace guards." else "What about the King?",
            )
            return
        }

        chatPlayer(neutral, "Yes, I know, but this is important.")
        chatNpc(neutral, "Ok, I'm listening. Tell me what's so important.")

        val topic =
            choice3(
                "There's a demon who wants to invade this city.",
                1,
                "Erm I forgot.",
                2,
                "The castle has just received its ale delivery.",
                3,
            )
        if (topic != 1) {
            // TODO: Add Rovin's replies to the two joke options.
            chatPlayer(
                neutral,
                if (topic == 2) "Erm I forgot."
                else "The castle has just received its ale delivery.",
            )
            return
        }

        chatPlayer(neutral, "There's a demon who wants to invade the city.")

        if (rovinAlreadyGaveKey()) {
            chatNpc(neutral, "Yes, you said before, haven't you killed it yet?")
            chatPlayer(
                neutral,
                "Well I'm going to use the powerful sword Silverlight, which I believe you have " +
                    "one of the keys for?",
            )
            if (access.carryingObj(DemonSlayerRefs.Key2)) {
                chatNpc(neutral, "I already gave you my key. Check your pockets.")
            } else {
                chatNpc(
                    neutral,
                    "I already gave you my key. Maybe you left it somewhere. Have you checked " +
                        "your bank account?",
                )
            }
            return
        }

        chatNpc(neutral, "Is it a powerful demon?")

        val powerful = choice2("Not really.", false, "Yes, very.", true)
        if (!powerful) {
            chatPlayer(neutral, "Not really.")
            chatNpc(
                neutral,
                "Well, I'm sure the palace guards can deal with it, then. Thanks for the " +
                    "information.",
            )
            return
        }

        chatPlayer(sad, "Yes, very.")
        chatNpc(
            worried,
            "As good as the palace guards are, I don't know if they're up to taking on a very " +
                "powerful demon.",
        )

        val mine =
            choice2(
                "Yeah, the palace guards are rubbish!",
                false,
                "It's not them who are going to fight the demon, it's me.",
                true,
            )
        if (!mine) {
            chatPlayer(laugh, "Yeah, the palace guards are rubbish!")
            chatNpc(neutral, "Yeah, they're--")
            chatNpc(angry, "Wait! How dare you insult the palace guards? Get out of my sight!")
            return
        }

        chatPlayer(neutral, "It's not them who are going to fight the demon, it's me.")
        chatNpc(shocked, "What, all by yourself? How are you going to do that?")
        chatPlayer(
            neutral,
            "I'm going to use the powerful sword Silverlight, which I believe you have one of " +
                "the keys for?",
        )
        chatNpc(quiz, "Yes, I do. But why should I give it to you?")

        while (true) {
            val why =
                choice3(
                    "Fortune-teller Aris said I was destined to kill the demon.",
                    1,
                    "Otherwise the demon will destroy the city!",
                    2,
                    "Sir Prysin said you would give me the key.",
                    3,
                )
            when (why) {
                1 -> {
                    chatPlayer(
                        neutral,
                        "Fortune-teller Aris said I was destined to kill the demon.",
                    )
                    chatNpc(
                        angry,
                        "A fortune-teller? Destiny? I don't believe in that stuff. I got where I " +
                            "am today by hard work, not by destiny! Why should I care what that " +
                            "mad old fortune-teller says?",
                    )
                }
                2 -> {
                    chatPlayer(neutral, "Otherwise the demon will destroy the city!")
                    chatNpc(
                        angry,
                        "You can't fool me! How do I know you haven't just made that story up to " +
                            "get my key?",
                    )
                }
                else -> {
                    rovinGivesKey()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.rovinGivesKey() {
        chatPlayer(neutral, "Sir Prysin said you would give me the key.")
        chatNpc(
            angry,
            "Oh, he did, did he? Well I don't report to Sir Prysin, I report directly to the king!",
        )
        chatNpc(
            angry,
            "I didn't work my way up through the ranks of the palace guards so I could take " +
                "orders from an ill-bred moron who only has his job because his great-grandfather " +
                "was a hero with a silly name!",
        )

        while (true) {
            val picked =
                choice3(
                    "Why did he give you one of the keys then?",
                    1,
                    "Fortune-teller Aris said I was destined to kill the demon.",
                    2,
                    "Otherwise the demon will destroy the city!",
                    3,
                )
            when (picked) {
                2 -> {
                    chatPlayer(
                        neutral,
                        "Fortune-teller Aris said I was destined to kill the demon.",
                    )
                    chatNpc(
                        angry,
                        "A fortune-teller? Destiny? I don't believe in that stuff. I got where I " +
                            "am today by hard work, not by destiny! Why should I care what that " +
                            "mad old fortune-teller says?",
                    )
                }
                3 -> {
                    chatPlayer(neutral, "Otherwise the demon will destroy the city!")
                    chatNpc(
                        angry,
                        "You can't fool me! How do I know you haven't just made that story up to " +
                            "get my key?",
                    )
                }
                else -> break
            }
        }

        chatPlayer(quiz, "Why did he give you one of the keys then?")
        chatNpc(
            laugh,
            "Only because the king ordered him to! The king couldn't get Sir Prysin to part with " +
                "his precious ancestral sword, but he made him lock it up so he couldn't lose it.",
        )
        chatNpc(
            quiz,
            "I got one key and I think some wizard got another. Now what happened to the third one?",
        )
        chatPlayer(laugh, "Sir Prysin dropped it down a drain!")
        chatNpc(laugh, "Ha ha ha! The idiot!")
        chatNpc(
            happy,
            "Okay, I'll give you the key, just so that it's you that kills the demon and not Sir " +
                "Prysin!",
        )

        access.invAddOrDrop(objRepo, DemonSlayerRefs.Key2)
        objbox(DemonSlayerRefs.Key2, "Captain Rovin hands you a key.")
    }

    private fun Dialogue.rovinAlreadyGaveKey(): Boolean =
        access.carryingObj(DemonSlayerRefs.Key2) ||
            access.invTotal(access.bank, DemonSlayerRefs.Key2) > 0 ||
            access.silverlightCaseOpened

    private suspend fun ProtectedAccess.traiborn(npc: Npc) {
        npcPlayerFaceClose(npc)
        faceSquare(npc.coords)
        startDialogue(npc) { traibornConversation() }
    }

    private suspend fun Dialogue.traibornConversation() {
        chatNpc(confused, "Ello young thingummywut.")

        val demonSlayer =
            choice2("Talk about Demon Slayer.", true, "Talk about something else.", false)
        if (!demonSlayer) {
            // TODO: Add Traiborn's non-quest dialogue.
            return
        }

        when {
            traibornHasHandedOverKey() -> {
                chatNpc(
                    confused,
                    "Don't you have somewhere to be, young thingummywut? You still have that key " +
                        "you asked me for.",
                )
                chatPlayer(neutral, "You're right. I've got a demon to slay.")
            }
            traibornKeyGiven.get(player) -> {
                chatNpc(confused, "Ello young thingummywut.")
                chatPlayer(sad, "I've lost the key you gave to me.")
                chatNpc(
                    confused,
                    "Yes I know, it was returned to me. If you want it back you're going to have " +
                        "to collect another 25 sets of bones.",
                )
                traibornKeyGiven.set(player, false)
                traibornBones.set(player, 0)
            }
            traibornAsked.get(player) -> traibornBoneHandIn()
            else -> traibornFirstAsk()
        }
    }

    private suspend fun Dialogue.traibornFirstAsk() {
        chatPlayer(neutral, "I need to get a key given to you by Sir Prysin.")
        chatNpc(confused, "Sir Prysin? Who's that? What would I want his key for?")

        while (true) {
            val picked =
                choice3(
                    "He told me you were looking after it for him.",
                    1,
                    "He's one of the King's knights.",
                    2,
                    "Well, have you got any keys knocking around?",
                    3,
                )
            when (picked) {
                1 -> {
                    chatPlayer(neutral, "He told me you were looking after it for him.")
                    chatNpc(
                        confused,
                        "That wasn't very clever of him. I'd lose my head if it wasn't screwed " +
                            "on. Go and tell him to find someone else to look after his valuables " +
                            "in future.",
                    )
                    val tellHim =
                        choice2(
                            "Okay, I'll go and tell him that.",
                            true,
                            "Well, have you got any keys knocking around?",
                            false,
                        )
                    if (tellHim) {
                        chatPlayer(neutral, "Okay, I'll go and tell him that.")
                        chatNpc(happy, "Oh that's great, if it wouldn't be too much trouble.")
                        continue
                    }
                }
                2 -> {
                    chatPlayer(neutral, "He's one of the King's knights.")
                    chatNpc(
                        confused,
                        "Say, I remember one of the King's knights. He had nice shoes...",
                    )
                    chatNpc(
                        confused,
                        "...and didn't like my homemade spinach rolls. Would you like a spinach roll?",
                    )
                    val wantsRoll =
                        choice2(
                            "Yes please.",
                            true,
                            "Well, have you got any keys knocking around?",
                            false,
                        )
                    if (wantsRoll) {
                        chatPlayer(happy, "Yes please.")
                        access.invAddOrDrop(objRepo, SpinachRoll)
                        objbox(
                            SpinachRoll,
                            "Traiborn digs around in the pockets of his robes. After a few " +
                                "moments he triumphantly presents you with a spinach roll.",
                        )
                        chatPlayer(happy, "Thank you very much.")
                        continue
                    }
                }
            }
            break
        }

        traibornOffersRitual()
    }

    private suspend fun Dialogue.traibornOffersRitual() {
        chatPlayer(neutral, "Well, have you got any keys knocking around?")
        chatNpc(
            confused,
            "Now you come to mention it, yes I do have a key. It's in my special closet of " +
                "valuable stuff. Now how do I get into that?",
        )
        chatNpc(
            confused,
            "I sealed it using one of my magic rituals. So it would make sense that another " +
                "ritual would open it again.",
        )
        chatPlayer(neutral, "So do you know what ritual to use?")
        chatNpc(neutral, "Let me think a second.")
        chatNpc(
            confused,
            "Yes a simple drazier style ritual should suffice. Hmm, main problem with that is " +
                "I'll need 25 sets of bones. Now where am I going to get hold of something like " +
                "that?",
        )

        val help =
            choice2(
                "Hmm, that's too bad. I really need that key.",
                false,
                "I'll get the bones for you.",
                true,
            )
        if (!help) {
            chatPlayer(sad, "Hmm, that's too bad. I really need that key.")
            chatNpc(sad, "Ah well, sorry I couldn't be any more help.")
            return
        }

        chatPlayer(neutral, "I'll help get the bones for you.")
        chatNpc(happy, "Ooh that would be very good of you.")
        chatPlayer(neutral, "Okay, I'll speak to you when I've got some bones.")
        traibornAsked.set(player, true)
    }

    private suspend fun Dialogue.traibornBoneHandIn() {
        chatNpc(confused, "How are you doing finding bones?")

        val carried = access.invTotal(access.inv, DemonSlayerRefs.Bones)
        if (carried <= 0) {
            chatPlayer(sad, "I haven't got any at the moment.")
            chatNpc(neutral, "Nevermind, keep working on it.")
            return
        }

        chatPlayer(happy, "I have some bones.")
        chatNpc(confused, "Give 'em here then.")

        val alreadyGiven = traibornBones.get(player)
        val accepted = minOf(carried, BonesRequired - alreadyGiven)
        access.invDel(access.inv, DemonSlayerRefs.Bones, accepted)
        val total = alreadyGiven + accepted
        traibornBones.set(player, total)

        val setWord = if (accepted == 1) "set" else "sets"
        mesbox("You give Traiborn $accepted $setWord of bones.")

        if (total < BonesRequired) {
            chatPlayer(neutral, "That's all of them.")
            chatNpc(confused, "I still need ${BonesRequired - total} more.")
            chatPlayer(neutral, "Ok, I'll keep looking.")
            return
        }

        chatNpc(happy, "Hurrah! That's all $BonesRequired sets of bones.")
        traibornRitual()
    }

    private suspend fun Dialogue.traibornRitual() {
        chatNpc(
            confused,
            "Wings of dark and colour too, Spreading in the morning dew; Locked away I have a " +
                "key; Return it now, please, unto me.",
        )
        access.soundSynth(DemonSlayerRefs.SynthBoneSpell)

        scenery.traibornWardrobe(access)

        // Record key ownership immediately after delivery, before suspending for its object dialogue.
        access.invAddOrDrop(objRepo, DemonSlayerRefs.Key1)
        traibornKeyGiven.set(player, true)

        objbox(DemonSlayerRefs.Key1, "Traiborn hands you a key.")

        chatPlayer(happy, "Thank you very much.")
        chatNpc(happy, "Not a problem for a friend of Sir What's-his-face.")
    }

    private fun Dialogue.traibornHasHandedOverKey(): Boolean =
        traibornKeyGiven.get(player) &&
            (access.carryingObj(DemonSlayerRefs.Key1) ||
                access.invTotal(access.bank, DemonSlayerRefs.Key1) > 0 ||
                access.silverlightCaseOpened)

    private companion object {
        const val ArisFee = 1

        const val SpinachRoll = "obj.spinach_roll"

        const val ArisOptionsTitle = "What would you like to say?"

        const val VisionSoundTimer = "timer.demonslayer_crystal_ball"

        const val VisionSoundRadius = 5

        const val VisionSoundMaxTicks = 200

        const val ArisHowToFight = 1
        const val ArisWhereIsHe = 2
        const val ArisWallyName = 3
        const val ArisFlashbackQuestion = 4
        const val ArisIncantation = 5
        const val ArisWhereSilverlight = 6

        val ArisQuestionOrder =
            listOf(ArisHowToFight, ArisWhereIsHe, ArisWallyName, ArisFlashbackQuestion)

        val ArisQuestions =
            mapOf(
                ArisHowToFight to "How am I meant to fight a demon who can destroy cities?",
                ArisWhereIsHe to "Okay, where is he? I'll kill him for you!",
                ArisWallyName to "Wally doesn't sound like a very heroic name.",
                ArisFlashbackQuestion to "So how did Wally kill Delrith?",
            )

        val DemonSlayerKeys =
            listOf(DemonSlayerRefs.Key1, DemonSlayerRefs.Key2, DemonSlayerRefs.Key3)
    }
}

internal val ProtectedAccess.silverlightCaseOpened: Boolean
    get() = vars[DemonSlayerRefs.VarbitSilverlightCase] != 0
