package org.rsmod.content.quest.area.alkharid

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Beer
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.BeersToDrunk
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.BlondWig
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.DealWithGuard
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Gathering
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.GuardDrunk
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.GuardHadOneBeer
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.KeliTiedUp
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.KeyPrint
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.PinkSkirt
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.PrinceFreed
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.PrincesKey
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Rope
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.SkinPaste
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.SoftClay
import org.rsmod.content.quest.manager.QuestVar
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PrinceAliRescueJail @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    private val quest
        get() = PrinceAliRescueState.quest()

    override fun ScriptContext.startup() {
        onOpNpc1("npc.leela") { talkToLeela(it.npc) }

        onOpNpc1("npc.lady_keli") { talkToKeli(it.npc) }
        onOpNpcU("npc.lady_keli", Rope) { tieUpKeli() }

        onOpNpc1("npc.joe") { talkToJoe(it.npc) }
        onOpNpc1("npc.prince_ali_prison") { talkToPrince(it.npc) }

        onOpLoc1(CellDoor) { openCellDoor() }
        onOpLocU(CellDoor, PrincesKey) { openCellDoor() }
    }

    private suspend fun ProtectedAccess.talkToLeela(npc: Npc) {
        startDialogue(npc) { leelaDialogue() }
    }

    private suspend fun Dialogue.leelaDialogue() {
        val stage = quest.getQuestStage(player)

        when {
            stage >= PrinceFreed -> {
                chatNpc(
                    happy,
                    "You did it! Prince Ali is safe again. You should head back to Al Kharid - " +
                        "Chancellor Hassan will want to thank you himself.",
                )
            }
            stage >= KeliTiedUp -> {
                chatNpc(
                    neutral,
                    "Keli is out of the way and the guard is no use to anyone. Go and get the " +
                        "Prince out.",
                )
            }
            stage >= GuardDrunk -> leelaGuardDrunk()
            stage >= DealWithGuard -> leelaGuardStill()
            stage >= Gathering -> leelaBriefing()
            else -> {
                chatNpc(shifty, "Move along. I'm working.")
            }
        }
    }

    private suspend fun Dialogue.leelaBriefing() {
        if (!access.metLeela()) {
            access.markMetLeela()
            chatPlayer(happy, "You must be Leela. Your father sent me to help rescue Prince Ali.")
            chatNpc(
                neutral,
                "Yes, he sent word ahead that you'd be coming. Are you aware of the plan?",
            )
        } else {
            chatNpc(neutral, "You're back. Do you have everything needed yet?")
        }

        if (access.hasEverythingForRescue()) {
            chatPlayer(happy, "Yes. In fact, I already have everything we need.")

            quest.setQuestStage(access, DealWithGuard)

            chatNpc(
                neutral,
                "Good work. Now, before breaking the Prince out, you'll need to find a way to " +
                    "deal with his personal guard. He's talkative, so try to find a weakness. " +
                    "Remember, we don't want any unneeded violence.",
            )
            chatPlayer(happy, "Alright. I'll go have a chat to this guard.")
            return
        }

        leelaAdvice()
    }

    private suspend fun Dialogue.leelaAdvice() {
        while (true) {
            val option =
                choice3(
                    "Any ideas for the key?",
                    1,
                    "Any ideas for the disguise?",
                    2,
                    "Not yet. I'll go and prepare.",
                    3,
                )

            when (option) {
                1 -> {
                    chatPlayer(quiz, "Any ideas for the key?")
                    chatNpc(
                        neutral,
                        "Keli keeps it on her at all times, on a chain around her neck. If you " +
                            "can convince her to show it to you, you might be able to use some " +
                            "soft clay to take an imprint.",
                    )
                    if (access.carrying(KeyPrint)) {
                        chatPlayer(happy, "I already have the imprint!")
                    } else {
                        chatPlayer(neutral, "That doesn't sound easy.")
                        chatNpc(
                            neutral,
                            "My suggestion is that you pretend to be interested in joining her " +
                                "bandits. From there, you should be able to steer the " +
                                "conversation towards the key.",
                        )
                    }
                    chatNpc(
                        neutral,
                        "Once you have the imprint, take it to a furnace with a bronze bar. That " +
                            "will cast us a copy.",
                    )
                    chatNpc(quiz, "Do you have any other questions about the plan?")
                }
                2 -> {
                    chatPlayer(quiz, "Any ideas for the disguise?")
                    chatNpc(
                        neutral,
                        "To make the Prince look like Keli, you'll need a blonde wig and a pink " +
                            "skirt. You'll also want some skin paste to hide the black eye he got " +
                            "when they captured him.",
                    )
                    chatNpc(
                        neutral,
                        "There's an old sailor in the village who makes rope. Perhaps he could " +
                            "make you a wig. Don't forget to dye it once you have one.",
                    )
                    chatNpc(
                        neutral,
                        "For the skin paste, there's a local witch who's apparently an expert on " +
                            "all sorts of potions. I'm sure she could make you some. I hear she " +
                            "also sells dye, if you need some for the wig.",
                    )
                    chatNpc(
                        neutral,
                        "Finally there's the skirt. I imagine you could just buy one from any " +
                            "clothes shop. Thessalia's Fine Clothes in Varrock is probably the " +
                            "closest.",
                    )
                    chatNpc(quiz, "Do you have any other questions about the plan?")
                }
                else -> {
                    chatPlayer(neutral, "Not yet. I'll go and prepare.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.leelaGuardStill() {
        chatNpc(neutral, "You're back. How are things going with that guard?")
        chatPlayer(neutral, "He's talkative, but he won't leave his post.")
        chatNpc(
            neutral,
            "Then loosen his tongue. A guard that far from a tavern will not turn down a drink. " +
                "I'd imagine three beers would do it. Why don't you give it a try?",
        )
    }

    private suspend fun Dialogue.leelaGuardDrunk() {
        chatNpc(neutral, "You're back. How are things going with that guard?")
        chatPlayer(happy, "He's been dealt with.")
        chatNpc(
            happy,
            "Great! I think that means we're ready. Go in and use some rope to tie Keli up, then " +
                "get the Prince out.",
        )
    }

    private suspend fun ProtectedAccess.talkToKeli(npc: Npc) {
        startDialogue(npc) { keliDialogue() }
    }

    private suspend fun Dialogue.keliDialogue() {
        if (access.askedToJoinKeli()) {
            chatPlayer(happy, "Hello again!")
            chatNpc(neutral, "Oh, it's you. What do you want?")
            chatPlayer(neutral, "I was hoping we could talk more about your prisoner.")
            chatNpc(
                neutral,
                "Until you join us, I can say little. What I can tell you is that he is very " +
                    "valuable. If all goes well, he will make us very rich.",
            )
            keliPrisonerMenu()
            return
        }

        chatPlayer(
            happy,
            "Are you the famous Lady Keli? Leader of the toughest gang of bandits around?",
        )
        chatNpc(neutral, "Yes, I am Keli. You've heard of me then?")

        val option =
            choice3(
                "Heard of you? You're famous in Gielinor!",
                1,
                "I've heard rumours that you kill people.",
                2,
                "No, I've never really heard of you.",
                3,
            )

        when (option) {
            1 -> {
                chatPlayer(happy, "Heard of you? You're famous in Gielinor!")
                chatNpc(
                    happy,
                    "That's very kind of you to say. Reputations are not easily earned. I have " +
                        "managed to succeed where many fail.",
                )
            }
            2 -> {
                chatPlayer(neutral, "I've heard rumours that you kill people.")
                chatNpc(
                    neutral,
                    "There's always someone ready to spread rumours. I hear all sort of " +
                        "ridiculous things these days.",
                )
            }
            3 -> {
                chatPlayer(neutral, "No, I've never really heard of you.")
                chatNpc(
                    neutral,
                    "You must be new around here then. Everyone knows of Lady Keli and her " +
                        "prowess with a sword.",
                )
            }
        }

        keliPlansMenu()
    }

    private suspend fun Dialogue.keliPlansMenu() {
        while (true) {
            val option =
                choice3(
                    "What's your latest plan then?",
                    1,
                    "You must have trained a lot for this work.",
                    2,
                    "I shouldn't disturb someone as tough as you.",
                    3,
                )

            when (option) {
                1 -> {
                    chatPlayer(quiz, "What's your latest plan then?")
                    chatNpc(confused, "Why do you want to know?")
                    chatPlayer(happy, "Well I was actually hoping to join your group.")
                    chatNpc(confused, "Join us? Interesting... I suppose you do look the type.")

                    access.markAskedToJoinKeli()

                    chatNpc(
                        neutral,
                        "You'll of course need to properly prove yourself before we let you join " +
                            "us. However, what I can tell you is that we currently have a very " +
                            "valuable prisoner. If all goes well, he will make us very rich.",
                    )
                    keliPrisonerMenu()
                    return
                }
                2 -> {
                    chatPlayer(quiz, "You must have trained a lot for this work.")
                    chatNpc(
                        neutral,
                        "I have used a sword since I was a girl. My first kill was before I was " +
                            "even six years old.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "I shouldn't disturb someone as tough as you.")
                    chatNpc(neutral, "Yes, I am very busy. Goodbye.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.keliPrisonerMenu() {
        val option =
            choice3(
                "Ah, I see. You must have been very skillful.",
                1,
                "How do you know someone won't try to free him?",
                2,
                "Well good luck with it.",
                3,
            )

        when (option) {
            1 -> {
                chatPlayer(happy, "Ah, I see. You must have been very skillful.")
                chatNpc(
                    happy,
                    "To catch him? Oh yes. We had to grab him without his bodyguards noticing. It " +
                        "was a stroke of genius. Mostly my doing, of course.",
                )
                val next =
                    choice2(
                        "How do you know someone won't try to free him?",
                        true,
                        "Well good luck with it.",
                        false,
                    )
                if (next) {
                    keliKeyBranch()
                } else {
                    chatPlayer(neutral, "Well good luck with it.")
                }
            }
            2 -> keliKeyBranch()
            else -> chatPlayer(neutral, "Well good luck with it.")
        }
    }

    private suspend fun Dialogue.keliKeyBranch() {
        chatPlayer(quiz, "How do you know someone won't try to free him?")
        chatNpc(
            shifty,
            "There is no way to release him. The only key to his cell is on a chain around my " +
                "neck, and the locksmith who made it died very suddenly.",
        )
        chatNpc(neutral, "There isn't another key like this in the world.")

        while (true) {
            val option =
                choice3(
                    "Could I see the key please?",
                    1,
                    "That is a good way to keep secrets.",
                    2,
                    "Well I'll be off. Good luck.",
                    3,
                )

            when (option) {
                1 -> {
                    keliShowsKey()
                    return
                }
                2 -> {
                    chatPlayer(neutral, "That is a good way to keep secrets.")
                    chatNpc(neutral, "It is the best way I know. Dead men tell no tales.")
                }
                else -> {
                    chatPlayer(neutral, "Well I'll be off. Good luck.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.keliShowsKey() {
        chatPlayer(quiz, "Could I see the key please?")
        chatNpc(confused, "Why?")
        chatPlayer(
            happy,
            "It will be something I can tell my grandchildren, when you are even more famous than " +
                "you are now.",
        )
        chatNpc(
            confused,
            "Well I suppose there's no harm in letting you see it. After all, you have no hope of " +
                "stealing it.",
        )
        objbox(PrincesKey, "Keli shows you a small key on a strong looking chain.")

        if (!access.carrying(SoftClay) || access.carrying(KeyPrint)) {
            chatPlayer(neutral, "Thank you. I'd better go now.")
            return
        }

        val touch =
            choice2(
                "Could I touch the key for a moment please?",
                true,
                "Thank you. I'd better go now.",
                false,
            )

        if (!touch) {
            chatPlayer(neutral, "Thank you. I'd better go now.")
            return
        }

        chatPlayer(happy, "Could I touch the key for a moment please?")
        chatNpc(confused, "Well... only for a moment then.")

        if (access.invReplace(access.inv, SoftClay, 1, KeyPrint).failure) {
            return
        }

        objbox(KeyPrint, "As you touch the key, you take an imprint of it using your soft clay.")
        chatPlayer(happy, "Thank you so much! You are too kind.")
        chatNpc(
            neutral,
            "You are welcome, but run along now. I will need some time to consider your request " +
                "to join us.",
        )
    }

    private suspend fun ProtectedAccess.tieUpKeli() {
        arriveDelay()

        if (quest.getQuestStage(player) >= KeliTiedUp) {
            mes("Keli is already tied up in the cupboard.")
            return
        }

        if (quest.getQuestStage(player) < GuardDrunk) {
            mes("The guard would notice. I should deal with him first.")
            return
        }

        if (invDel(inv, Rope, 1).failure) {
            return
        }

        quest.setQuestStage(this, KeliTiedUp)
        mesbox("You overpower Keli, tie her up, and put her in a cupboard.")
    }

    private suspend fun ProtectedAccess.talkToJoe(npc: Npc) {
        startDialogue(npc) { joeDialogue() }
    }

    private suspend fun Dialogue.joeDialogue() {
        val stage = quest.getQuestStage(player)

        if (stage >= PrinceFreed) {
            chatNpc(drunk, "Did yoush say something about shome Prince?")
            chatPlayer(neutral, "No.")
            chatNpc(drunk, "Oh... okay.")
            return
        }

        if (stage < DealWithGuard) {
            chatPlayer(quiz, "Hi. Who are you guarding here?")
            chatNpc(
                shifty,
                "Can't say. It's all very secret. You should get out of here. I am not supposed " +
                    "to talk while I guard.",
            )
            return
        }

        if (stage >= GuardDrunk) {
            chatNpc(drunk, "No more beersh. I don't want to get drunk on duty.")
            return
        }

        chatPlayer(happy, "Hi there.")
        chatNpc(shifty, "What do you want?")

        joeMenu()
    }

    private suspend fun Dialogue.joeMenu() {
        while (true) {
            val option =
                choice4(
                    "I have some beer here. Fancy one?",
                    1,
                    "Tell me about the life of a guard.",
                    2,
                    "What did you want to be when you were a boy?",
                    3,
                    "I'd better go.",
                    4,
                )

            when (option) {
                1 -> {
                    offerBeer()
                    return
                }
                2 -> {
                    chatPlayer(quiz, "Tell me about the life of a guard.")
                    chatNpc(
                        bored,
                        "Long. Hot. Dry. Mostly dry. You wouldn't believe how dry a man can get " +
                            "standing in one place.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "What did you want to be when you were a boy?")
                    chatNpc(happy, "A sailor. Sailors get a rum ration, see.")
                }
                else -> {
                    chatPlayer(neutral, "I'd better go.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.offerBeer() {
        chatPlayer(happy, "I have some beer here. Fancy one?")

        if (!access.carrying(Beer)) {
            chatPlayer(sad, "...actually, I don't have any on me.")
            return
        }

        chatNpc(happy, "Ah, that would be lovely. Only one though, just to wet my throat.")
        chatPlayer(neutral, "Of course. It must be tough being here without a drink.")

        if (access.invDel(access.inv, Beer, 1).failure) {
            return
        }
        if (quest.getQuestStage(player) < GuardHadOneBeer) {
            quest.setQuestStage(access, GuardHadOneBeer)
        }

        access.soundSynth(DrinkSound)
        objbox(Beer, "You hand a beer to the guard. He drinks it in seconds.")
        chatNpc(happy, "That was perfect! I can't thank you enough.")

        val remaining = BeersToDrunk - 1
        chatPlayer(happy, "Would you care for another beer, my friend?")
        chatNpc(bored, "I'd better not. I don't want to be drunk on duty.")

        if (access.carriedCount(Beer) < remaining) {
            chatPlayer(sad, "I'll bring you a couple more.")
            return
        }

        chatPlayer(happy, "Here, just keep these for later. I hate to see a thirsty guard.")

        if (access.invDel(access.inv, Beer, remaining).failure) {
            return
        }
        quest.setQuestStage(access, GuardDrunk)

        access.soundSynth(DrinkSound)
        doubleobjbox(
            Beer,
            Beer,
            "You hand two more beers to the guard. He takes a sip of one, and then he quickly " +
                "drinks them both.",
        )
        chatNpc(
            drunk,
            "Franksh! That wash jusht what I need to shtay on guard. No more beersh, I don't want " +
                "to get drunk.",
        )
    }

    private suspend fun ProtectedAccess.openCellDoor() {
        arriveDelay()

        if (!carrying(PrincesKey)) {
            mes("The gate is locked.")
            soundSynth(LockedSound)
            return
        }

        soundSynth(UnlockSound)
        soundSynth(GateOpenSound)

        locRepo.add(CellDoorClosedTile, InvisWall, OpenTicks, LocAngle.North, LocShape.WallStraight)
        locRepo.add(CellDoorOpenTile, CellDoorOpen, OpenTicks, LocAngle.East, LocShape.WallStraight)

        val southbound = player.coords.z >= CellDoorOpenTile.z
        val route =
            if (southbound) {
                listOf(CellDoorOpenTile, CellDoorClosedTile)
            } else {
                listOf(CellDoorClosedTile, CellDoorOpenTile)
            }

        val trimmed = route.dropWhile { it == player.coords }
        if (trimmed.isEmpty()) {
            return
        }

        var tiles = 0
        var from = player.coords
        for (waypoint in trimmed) {
            tiles += from.chebyshevDistance(waypoint)
            from = waypoint
        }

        forcedWalk(trimmed, crossTiles = maxOf(1, tiles))
    }

    private suspend fun ProtectedAccess.talkToPrince(npc: Npc) {
        startDialogue(npc) { princeDialogue() }
    }

    private suspend fun Dialogue.princeDialogue() {
        if (quest.getQuestStage(player) >= PrinceFreed) {
            chatNpc(happy, "I owe you my freedom, my friend. Thank you.")
            return
        }

        chatPlayer(happy, "Prince Ali? I'm here to rescue you.")
        chatNpc(happy, "Oh thank goodness! What's your plan?")

        val disguise = DisguiseParts.filterNot { access.carrying(it) }
        if (disguise.isNotEmpty()) {
            chatPlayer(sad, "I don't have everything I need yet.")
            chatNpc(worried, "Then please hurry. I cannot walk out of here as myself.")
            return
        }

        chatPlayer(neutral, "Take this disguise. You can use it to get past the guards outside.")
        chatNpc(
            happy,
            "Thank you, my friend. I must leave you now, but my father will pay you well for this.",
        )

        for (part in DisguiseParts) {
            access.invDel(access.inv, part, 1)
        }
        quest.setQuestStage(access, PrinceFreed)

        mesbox("Prince Ali puts on the disguise and uses it to escape.")
    }

    private fun ProtectedAccess.hasEverythingForRescue(): Boolean =
        DisguiseParts.all { carrying(it) } && carrying(PrincesKey)

    private fun ProtectedAccess.metLeela(): Boolean = MET_LEELA.get(player)

    private fun ProtectedAccess.markMetLeela() {
        MET_LEELA.set(player, true)
    }

    private fun ProtectedAccess.askedToJoinKeli(): Boolean =
        ASKED_TO_JOIN_KELI.get(player)

    private fun ProtectedAccess.markAskedToJoinKeli() {
        ASKED_TO_JOIN_KELI.set(player, true)
    }

    private companion object {
        private const val CellDoor = "loc.alidoor"

        private const val CellDoorOpen = "loc.inactiveprisondoor"

        private const val InvisWall = "loc.inviswall"

        private val CellDoorClosedTile = CoordGrid(3123, 3243, 0)
        private val CellDoorOpenTile = CoordGrid(3123, 3244, 0)

        private const val OpenTicks = 4

        private val DisguiseParts = listOf(BlondWig, PinkSkirt, SkinPaste)

        private const val DrinkSound = "synth.LIQUID"

        private const val LockedSound = "synth.LOCKED"

        private const val UnlockSound = "synth.UNLOCK"
        private const val GateOpenSound = "synth.IRON_DOOR_OPEN"

        private val MET_LEELA = QuestVar.boolean("MET_LEELA", "varp.prince_ali_met_leela", legacyKey = "prince_ali_met_leela", migratedVarp = "varp.prince_ali_met_leela_migrated")

        private val ASKED_TO_JOIN_KELI = QuestVar.boolean("ASKED_TO_JOIN_KELI", "varp.prince_ali_asked_keli", legacyKey = "prince_ali_asked_keli", migratedVarp = "varp.prince_ali_asked_keli_migrated")
    }
}
