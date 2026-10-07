package org.rsmod.content.quest.area.crandor

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

internal class DragonSlayerDialogue(
    private val quest: Quest,
    private val cutscene: DragonSlayerCutscene,
    private val shops: Shops,
) {
    private var Player.instructionsMelzar by intVarBit("varbit.dragonslayer_instructions_melzar")
    private var Player.instructionsOracle by intVarBit("varbit.dragonslayer_instructions_oracle")
    private var Player.instructionsWormbrain by
        intVarBit("varbit.dragonslayer_instructions_wormbrain")
    private var Player.instructionsShip by intVarBit("varbit.dragonslayer_instructions_ship")
    private var Player.instructionsShield by intVarBit("varbit.dragonslayer_instructions_shield")

    private var Player.briefed by intVarBit("varbit.dragonslayer_champion_explained_elvarg")

    private var Player.oracleRiddleTold by intVarBit("varbit.dragonslayer_secret_told")

    private var Player.nedAgreed by intVarBit("varbit.dragonslayer_asked_ned")

    fun ScriptContext.register() {
        onOpNpc1(DragonSlayerRefs.Guildmaster) { startDialogue(it.npc) { guildmaster() } }
        onOpNpc1(DragonSlayerRefs.Oziach) { startDialogue(it.npc) { oziach(it.npc) } }
        onOpNpc3(DragonSlayerRefs.Oziach) { oziachTrade(it.npc) }
        onOpNpc1(DragonSlayerRefs.Oracle) { startDialogue(it.npc) { oracle() } }
        onApNpc1(DragonSlayerRefs.Wormbrain) { startDialogue(it.npc) { wormbrain() } }
        onOpNpc1(DragonSlayerRefs.Wormbrain) { startDialogue(it.npc) { wormbrain() } }
        onOpNpc1(DragonSlayerRefs.Klarense) { startDialogue(it.npc) { klarense() } }

        // Ned's parent renders only from stage seven; bind its visible leaf as well.
        onOpNpc1(DragonSlayerRefs.NedOnShip) { startDialogue(it.npc) { nedOnShip() } }
        onOpNpc1(NedOnShipLeaf) { startDialogue(it.npc) { nedOnShip() } }
    }

    private suspend fun Dialogue.guildmaster() {
        chatNpc(neutral, "Greetings!")

        if (quest.isQuestCompleted(access.player)) {
            guildmasterAfterQuest()
            return
        }

        val stage = quest.getQuestStage(access.player)
        val questOption =
            when {
                stage >= DragonSlayerStage.OziachSpoken && access.player.briefed == 1 ->
                    "About my quest to kill the dragon..."
                stage >= DragonSlayerStage.OziachSpoken -> "I talked to Oziach..."
                else -> "Can I have a quest?"
            }

        val choice = choice2("What is this place?", 1, questOption, 2)
        if (choice == 1) {
            whatIsThisPlace()
            return
        }

        when {
            stage >= DragonSlayerStage.OziachSpoken -> guildmasterBriefing()
            stage == DragonSlayerStage.Started -> guildmasterAlreadyQuesting()
            else -> guildmasterOffersQuest()
        }
    }

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            neutral,
            "This is the Champions' Guild. Only adventurers who have proved themselves " +
                "worthy by gaining influence from quests are allowed in here.",
        )
    }

    private suspend fun Dialogue.guildmasterAfterQuest() {
        val choice = choice2("What is this place?", 1, MazeKeyOption, 2)
        if (choice == 1) {
            whatIsThisPlace()
            return
        }

        chatPlayer(quiz, MazeKeyOption)
        if (
            access.inv.contains(DragonSlayerRefs.MelzarKey) ||
                access.bank.contains(DragonSlayerRefs.MelzarKey)
        ) {
            chatNpc(neutral, "I think you've already got a key somewhere.")
            return
        }

        chatNpc(happy, "Of course! Here you go.")
        if (access.invAdd(access.inv, DragonSlayerRefs.MelzarKey, 1).failure) {
            chatNpc(neutral, "You don't have enough inventory space for the key.")
            return
        }
        access.mes("You receive a maze key.")
    }

    private suspend fun Dialogue.guildmasterOffersQuest() {
        chatPlayer(quiz, "Can I have a quest?")
        chatNpc(happy, "Aha!")
        chatNpc(
            happy,
            "Yes! A mighty and perilous quest fit only for the most powerful champions! And, at " +
                "the end of it, you will earn the right to wear the legendary rune platebody!",
        )

        if (access.player.combatLevel < RecommendedCombatLevel) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RecommendedCombatLevel."
            )
        }

        val start =
            choice2("Yes.", true, "No.", false, title = "Start the Dragon Slayer I quest?")
        if (!start) {
            // TODO: Verify the Guildmaster's declined-challenge response.
            return
        }

        chatPlayer(quiz, "So, what is this quest?")
        quest.setQuestStage(access, DragonSlayerStage.Started)
        chatNpc(
            neutral,
            "You'll have to speak to Oziach, the maker of rune armour. He sets the quests that " +
                "champions must complete in order to wear it.",
        )
        chatNpc(
            neutral,
            "Oziach lives in a hut, by the cliffs to the west of Edgeville. He can be a " +
                "little...odd...sometimes, though.",
        )
    }

    private suspend fun Dialogue.guildmasterAlreadyQuesting() {
        chatPlayer(quiz, "Can I have a quest?")
        chatNpc(
            neutral,
            "You're already on a quest for me, if I recall correctly. Have you talked to Oziach " +
                "yet?",
        )
        chatPlayer(neutral, "No, not yet.")
        chatNpc(
            neutral,
            "Well, he's the only one who can grant you the right to wear rune platemail. He " +
                "lives in a hut, by the cliffs west of Edgeville.",
        )
        chatPlayer(neutral, "Okay, I'll go and talk to him.")
    }

    private suspend fun Dialogue.guildmasterBriefing() {
        if (access.player.briefed == 0) {
            chatPlayer(neutral, "I talked to Oziach and he gave me a quest.")
            chatNpc(quiz, "Oh? What did he tell you to do?")
            chatPlayer(neutral, "Defeat the dragon of Crandor.")
            chatNpc(worried, "The dragon of Crandor?")
            chatPlayer(neutral, "Um, yes...")
            chatNpc(worried, "Goodness, he hasn't given you an easy job, has he?")
            chatPlayer(quiz, "What's so special about this dragon?")
            elvargBackstory()
            access.player.briefed = 1
            access.player.instructionsMelzar = 1
            access.player.instructionsOracle = 1
            access.player.instructionsWormbrain = 1
            access.player.instructionsShip = 1
            access.player.instructionsShield = 1
        } else {
            chatPlayer(quiz, "About my quest to kill the dragon...")
        }

        seaAdviceAndMenu()
    }

    private suspend fun Dialogue.elvargBackstory() {
        chatNpc(
            happy,
            "Thirty years ago, Crandor was a thriving community with a great tradition of mages " +
                "and adventurers. Many Crandorians even earned the right to be part of the " +
                "Champions' Guild!",
        )
        chatNpc(
            sad,
            "One of their adventurers went too far, however. He descended into the volcano in " +
                "the centre of Crandor and woke the dragon Elvarg.",
        )
        chatNpc(
            happy,
            "He must have fought valiantly against the dragon because they say that, to this " +
                "day, she has a scar down her side,",
        )
        chatNpc(
            worried,
            "but the dragon still won the fight. She emerged and laid waste to the whole of " +
                "Crandor with her fire breath!",
        )
        chatNpc(
            worried,
            "Some refugees managed to escape in fishing boats. They landed on the coast, north " +
                "of Rimmington, and set up camp but the dragon followed them and burned the camp " +
                "to the ground.",
        )
        chatNpc(
            neutral,
            "Out of all the people of Crandor there were only three survivors: a trio of wizards " +
                "who used magic to escape. Their names were Thalzar, Lozar and Melzar.",
        )
    }

    private suspend fun Dialogue.seaAdviceAndMenu() {
        seaAdvice()

        var justAsked: String? = null
        var enteredMapPath = false
        val piecesLeft = mutableListOf<String>()

        while (true) {
            val options = mutableListOf<Pair<String, suspend () -> Unit>>()

            if (enteredMapPath) {
                // TODO: Verify the menu after discussing all three map pieces.
                for (piece in piecesLeft.toList()) {
                    options +=
                        piece to
                            {
                                askPiece(piece)
                                piecesLeft.remove(piece)
                                justAsked = null
                            }
                }
            } else {
                options +=
                    MapOption to
                        {
                            val picked = mapBranch()
                            enteredMapPath = true
                            piecesLeft +=
                                listOf(MelzarOption, ThalzarOption, LozarOption).filter {
                                    it != picked
                                }
                            justAsked = null
                        }
            }
            if (justAsked != ShipOption) {
                options +=
                    ShipOption to
                        {
                            shipBranch()
                            justAsked = ShipOption
                        }
            }
            if (justAsked != ShieldOption) {
                options +=
                    ShieldOption to
                        {
                            shieldBranch()
                            justAsked = ShieldOption
                        }
            }
            if (justAsked == null && !enteredMapPath) {
                options +=
                    BackstoryOption to
                        {
                            chatPlayer(quiz, BackstoryOption)
                            elvargBackstory()
                            seaAdvice()
                        }
            }
            options += LeaveOption to { chatPlayer(happy, LeaveOption) }

            val picked = pickOption(options)
            if (picked == LeaveOption) {
                return
            }
        }
    }

    private suspend fun Dialogue.seaAdvice() {
        chatNpc(
            neutral,
            "If you're serious about taking on Elvarg, first you'll need to get to Crandor, and " +
                "to my knowledge, the only way to do that is by sea.",
        )
        chatPlayer(quiz, "So I just sail over there? Seems easy enough.")
        chatNpc(
            confused,
            "If only that were true. The island is surrounded by dangerous underwater reefs. " +
                "You'll need a map to guide you through them and a ship capable of doing so.",
        )
        chatNpc(
            confused,
            "And then of course there's the dragon herself. Once you reach Crandor, you'll need " +
                "some kind of protection against her breath.",
        )
    }

    private suspend fun Dialogue.askPiece(option: String) {
        when (option) {
            MelzarOption -> melzarPiece()
            ThalzarOption -> thalzarPiece()
            else -> lozarPiece()
        }
    }

    private suspend fun Dialogue.pickOption(
        options: List<Pair<String, suspend () -> Unit>>
    ): String {
        val chosen =
            when (options.size) {
                1 -> 0
                2 -> choice2(options[0].first, 0, options[1].first, 1)
                3 -> choice3(options[0].first, 0, options[1].first, 1, options[2].first, 2)
                4 ->
                    choice4(
                        options[0].first,
                        0,
                        options[1].first,
                        1,
                        options[2].first,
                        2,
                        options[3].first,
                        3,
                    )
                else ->
                    choice5(
                        options[0].first,
                        0,
                        options[1].first,
                        1,
                        options[2].first,
                        2,
                        options[3].first,
                        3,
                        options[4].first,
                        4,
                    )
            }
        options[chosen].second.invoke()
        return options[chosen].first
    }

    private suspend fun Dialogue.mapBranch(): String {
        chatPlayer(quiz, MapOption)
        chatNpc(
            neutral,
            "After what happened, people were afraid of enraging the dragon. Most maps to the " +
                "island were destroyed, but I have heard that one survived. It was held by " +
                "Melzar, Thalzar and Lozar.",
        )
        chatNpc(
            neutral,
            "However, the three apparently split the map into three pieces, with each of them " +
                "taking one piece.",
        )

        return pickOption(
            listOf(
                MelzarOption to suspend { melzarPiece() },
                ThalzarOption to suspend { thalzarPiece() },
                LozarOption to suspend { lozarPiece() },
            )
        )
    }

    private suspend fun Dialogue.melzarPiece() {
        chatPlayer(quiz, MelzarOption)
        chatNpc(
            neutral,
            "Melzar built a castle on the site of the Crandorian refugee camp, north of " +
                "Rimmington. He's locked himself in there and no one's seen him for years.",
        )
        chatNpc(
            neutral,
            "The inside of his castle is like a maze, and is populated by undead monsters. " +
                "Maybe, if you could get all the way through the maze, you could find his piece " +
                "of the map.",
        )
        chatNpc(
            neutral,
            "Adventurers sometimes go in there to prove themselves, so I can give you this key " +
                "to Melzar's Maze.",
        )
        access.player.instructionsMelzar = 0

        if (!access.inv.contains(DragonSlayerRefs.MelzarKey)) {
            if (access.invAdd(access.inv, DragonSlayerRefs.MelzarKey, 1).failure) {
                chatNpc(neutral, "You don't have enough inventory space for the key.")
                return
            }
        }
        objbox(DragonSlayerRefs.MelzarKey, "The Guildmaster hands you a key.")
    }

    private suspend fun Dialogue.thalzarPiece() {
        chatPlayer(quiz, ThalzarOption)
        chatNpc(
            neutral,
            "Thalzar was the most paranoid of the three wizards. He hid his map piece and took " +
                "the secret of its location to his grave.",
        )
        chatNpc(
            neutral,
            "I don't think you'd be able to find out where it is by ordinary means. You'll need " +
                "to talk to the Oracle on Ice Mountain.",
        )
        access.player.instructionsOracle = 0
    }

    private suspend fun Dialogue.lozarPiece() {
        chatPlayer(quiz, LozarOption)
        chatNpc(
            sad,
            "A few weeks ago, I'd have told you to speak to Lozar herself, in her house across " +
                "the river from Lumbridge.",
        )
        chatNpc(
            sad,
            "Unfortunately, goblin raiders killed her and stole everything. One of the goblins " +
                "from the Goblin Village probably has the map piece now.",
        )
        access.player.instructionsWormbrain = 0
    }

    private suspend fun Dialogue.shipBranch() {
        chatPlayer(quiz, ShipOption)
        chatNpc(
            neutral,
            "Quite a specific one I'm afraid. Even with a map, very few ships will be able to " +
                "navigate the reefs around the island. You're going to need one of Crandorian " +
                "design.",
        )
        chatNpc(
            neutral,
            "If there's still one in existence, it's probably in Port Sarim. However, a ship " +
                "will also need a captain, and I'm not sure where you'd find one willing to sail " +
                "to Crandor!",
        )
        access.player.instructionsShip = 0
    }

    private suspend fun Dialogue.shieldBranch() {
        chatPlayer(quiz, ShieldOption)
        chatNpc(
            neutral,
            "That part shouldn't be too difficult, actually. I believe the Duke of Lumbridge has " +
                "a special shield in his armoury that is enchanted against dragon's breath.",
        )
        access.player.instructionsShield = 0
    }

    private suspend fun Dialogue.oziach(npc: Npc) {
        when {
            quest.isQuestCompleted(access.player) -> oziachAfterQuest(npc)
            quest.getQuestStage(access.player) >= DragonSlayerStage.OziachSpoken -> oziachProgress(npc)
            else -> oziachQuestOffer()
        }
    }

    private suspend fun ProtectedAccess.oziachTrade(npc: Npc) {
        if (quest.isQuestCompleted(player)) {
            openOziachShop(npc)
            return
        }
        startDialogue(npc) {
            chatNpc(angry, "I ain't got nothing to sell ye, adventurer. Leave me be!")
        }
    }

    private suspend fun Dialogue.oziachAfterQuest(npc: Npc) {
        chatNpc(happy, "Aye, 'tis a fair day, my mighty dragon-slaying friend.")
        val choice =
            choice4(
                BuyPlatebodyOption,
                1,
                "I'm not your friend.",
                2,
                "Yes, it's a very nice day.",
                3,
                MazeKeyOption,
                4,
            )
        when (choice) {
            1 -> {
                chatPlayer(neutral, "Can I buy a rune platebody now please?")
                access.openOziachShop(npc)
            }
            2 -> {
                chatPlayer(neutral, "I'm not your friend.")
                chatNpc(
                    neutral,
                    "I'm surprised if you're anyone's friend with those kind of manners.",
                )
            }
            3 -> {
                chatPlayer(happy, "Yes, it's a very nice day.")
                chatNpc(neutral, "Aye, may the gods walk by yer side. Now leave me alone.")
            }
            4 -> {
                chatPlayer(quiz, MazeKeyOption)
                chatNpc(
                    neutral,
                    "It's the Guildmaster in the Champions' Guild who hands those keys out now. " +
                        "Go talk to him. No need to bother me if you don't need armour.",
                )
            }
        }
    }

    private suspend fun Dialogue.oziachQuestOffer() {
        chatPlayer(quiz, "Can you sell me a rune platebody?")
        chatNpc(shocked, "So, how does thee know I 'ave some?")

        val told =
            choice2(
                "The Guildmaster of the Champions' Guild told me.",
                true,
                "I am a master detective.",
                false,
            )

        if (!told) {
            chatPlayer(neutral, "I am a master detective.")
            chatNpc(
                angry,
                "Well, however you found out about it, I'm not going to let just anyone wear my " +
                    "rune platemail. It's only for heroes. So leave me alone.",
            )
        } else {
            chatPlayer(neutral, "The Guildmaster of the Champions' Guild told me.")
            chatNpc(
                sad,
                "Yes, I suppose he would, wouldn't he? He's always sending you fancy-pants " +
                    "'heroes' up to bother me. Telling me I'll give them a quest or sommat like " +
                    "that.",
            )
            chatNpc(
                angry,
                "Well, I'm not going to let just anyone wear my rune platemail. It's only for " +
                    "heroes. So, leave me alone.",
            )
        }

        val pressOn =
            choice2(
                "I thought you were going to give me a quest.",
                true,
                "That's a pity, I'm not a hero.",
                false,
            )

        if (!pressOn) {
            chatPlayer(neutral, "That's a pity, I'm not a hero.")
            chatNpc(laugh, "Aye, I ken tell!")
            return
        }

        chatPlayer(shocked, "I thought you were going to give me a quest.")
        chatNpc(sad, "*Sigh*")
        chatNpc(angry, "All right, I'll give ye a quest. I'll let ye wear my rune platemail if ye...")
        chatNpc(laugh, "Slay the dragon of Crandor!")

        val keen =
            choice2(
                "A dragon, that sounds like fun.",
                true,
                "I may be a champion, but I don't think I'm up to dragon-killing yet.",
                false,
            )

        if (!keen) {
            chatPlayer(
                sad,
                "I may be a champion, but I don't think I'm up to dragon-killing yet.",
            )
            return
        }

        chatPlayer(happy, "A dragon, that sounds like fun!")
        chatNpc(
            neutral,
            "Hah, yes, you are a typical reckless adventurer, aren't you? Now go kill the dragon " +
                "and get out of my face.",
        )
        chatPlayer(quiz, "But how can I defeat the dragon?")
        quest.setQuestStage(access, DragonSlayerStage.OziachSpoken)
        chatNpc(
            neutral,
            "Go talk to the Guildmaster in the Champions' Guild. He'll help ye out if yer so " +
                "keen on doing a quest. I'm not going to be handholding any adventurers.",
        )
    }

    private suspend fun Dialogue.oziachProgress(npc: Npc) {
        chatNpc(quiz, "Have ye slayed that dragon yet?")

        val slain = quest.getQuestStage(access.player) >= DragonSlayerStage.ElvargSlain
        val hasHead = access.inv.contains(DragonSlayerRefs.ElvargHead)

        if (!slain || !hasHead) {
            // TODO: Verify Oziach's refusal when Elvarg's head is missing.
            chatPlayer(sad, "No, not yet.")
            return
        }

        chatPlayer(happy, "Yes, I have!")
        chatPlayer(happy, "I have its head here.")
        chatNpc(shocked, "You actually did it?")
        chatNpc(neutral, "I underestimated ye, adventurer. I apologise.")
        chatNpc(neutral, "Yer a true hero, and I'd be happy to sell ye rune platebodies.")

        if (access.invDel(access.inv, DragonSlayerRefs.ElvargHead, 1).failure) {
            return
        }
        quest.completeQuest(access)
        quest.awaitRewardScroll(access)

        oziachShopMenu(npc)
    }

    private suspend fun Dialogue.oziachShopMenu(npc: Npc) {
        val buy =
            choice2("Can I buy a rune platebody now please?", true, "Thank you.", false)
        if (!buy) {
            chatPlayer(happy, "Thank you.")
            return
        }
        chatPlayer(neutral, "Can I buy a rune platebody now please?")
        access.openOziachShop(npc)
    }

    private suspend fun Dialogue.oracle() {
        val seek =
            choice2(
                "I seek a piece of the map to the island of Crandor.",
                true,
                "Can you impart your wise knowledge to me, O Oracle?",
                false,
            )

        if (!seek) {
            chatPlayer(quiz, "Can you impart your wise knowledge to me, O Oracle?")
            chatNpc(neutral, "Of course. What would you like to know?")
            return
        }

        chatPlayer(neutral, "I seek a piece of the map to the island of Crandor.")
        chatNpc(
            neutral,
            "The map's behind a door below, but entering is rather tough. This is what you need " +
                "to know: You must use the following stuff.",
        )
        chatNpc(
            neutral,
            "First, a drink used by a mage. Next, some worm string, changed to sheet. Then, a " +
                "small crustacean cage. Last, a bowl that's not seen heat.",
        )
        access.player.oracleRiddleTold = 1
    }

    private suspend fun Dialogue.wormbrain() {
        chatNpc(angry, "Whut you want?")

        val opening =
            if (canDiscussWormbrainMap(access.player)) {
                choice3(
                    "I believe you've got a piece of map that I need.",
                    1,
                    "What are you in for?",
                    2,
                    "Sorry, thought this was a zoo.",
                    3,
                )
            } else {
                choice2("What are you in for?", 2, "Sorry, thought this was a zoo.", 3)
            }

        when (opening) {
            2 -> {
                chatPlayer(quiz, "What are you in for?")
                chatNpc(angry, "Me not do nuffink!")
                return
            }
            3 -> {
                chatPlayer(happy, "Sorry, thought this was a zoo.")
                chatNpc(angry, "You not funny!")
                return
            }
        }

        if (!canDiscussWormbrainMap(access.player)) return

        chatPlayer(neutral, "I believe you've got piece of a map that I need.")
        chatNpc(confused, "So? Why should I be giving it to you? What you do for Wormbrain?")

        val approach =
            choice4(
                "I'm not going to do anything for you. Forget it.",
                1,
                "I'll let you live. I could just kill you.",
                2,
                "I suppose I could pay you for the map piece...",
                3,
                "Where did you get the map piece from?",
                4,
            )

        when (approach) {
            1 -> {
                chatPlayer(angry, "I'm not going to do anything for you. Forget it.")
                return
            }
            2 -> {
                chatPlayer(angry, "I'll let you live. I could just kill you.")
                return
            }
            4 -> {
                chatPlayer(quiz, "Where did you get the map piece from?")
                return
            }
        }

        chatPlayer(shifty, "I suppose I could pay you for the map piece. Say, 500 coins?")
        chatNpc(laugh, "Me not stoopid, it worth at least 10,000 coins!")

        val pay = choice2("You must be joking! Forget it.", false, "Alright then, 10,000 it is.", true)
        if (!pay) {
            chatPlayer(angry, "You must be joking! Forget it.")
            return
        }

        chatPlayer(neutral, "Alright then, 10,000 coins it is.")

        // Dialogue can remain open while an administrative quest reset changes these flags.
        // Recheck at the exchange, before charging anything for a now-ineligible map piece.
        if (!canDiscussWormbrainMap(access.player)) return

        if (access.invCoinTotal(access.inv) < WormbrainPrice) {
            chatPlayer(sad, "Oops, I don't have enough on me.")
            chatNpc(angry, "Comes back when you has enough.")
            return
        }

        // TODO: Add Wormbrain's paid-exchange dialogue.
        access.ifClose()
        if (access.invDel(access.inv, DragonSlayerRefs.Coins, WormbrainPrice).failure) {
            chatPlayer(sad, "Oops, I don't have enough on me.")
            chatNpc(angry, "Comes back when you has enough.")
            return
        }
        if (access.invAdd(access.inv, DragonSlayerRefs.MapPartLozar, 1).failure) {
            access.invAdd(access.inv, DragonSlayerRefs.Coins, WormbrainPrice)
            mesbox("You don't have enough inventory space for the map piece.")
            return
        }
        objbox(DragonSlayerRefs.MapPartLozar, "Wormbrain hands you a piece of map.")
    }

    private fun canDiscussWormbrainMap(player: Player): Boolean =
        quest.getQuestStage(player) in DragonSlayerStage.OziachSpoken until DragonSlayerStage.Complete &&
            player.briefed != 0 && player.instructionsWormbrain == 0

    private suspend fun Dialogue.klarense() {
        if (quest.getQuestStage(access.player) >= DragonSlayerStage.ShipBought) {
            chatNpc(happy, "She's a fine ship. Look after her!")
            return
        }

        chatNpc(
            neutral,
            "Hello there! Interested in buying this fine ship? I'm sure I can offer her to you " +
                "for a much better price than any of those shipwrights ask for!",
        )
        chatPlayer(quiz, "A much better price? What's the catch?")
        chatNpc(neutral, "Well, she's not quite seaworthy right now...")
        chatPlayer(quiz, "Not seaworthy? Why would I want to buy her then?")
        chatNpc(
            happy,
            "Because this isn't just any ship! This is the Lady Lumbridge, one of the last " +
                "Crandorian ships! Despite her size, she's one of the most agile ship around!",
        )
        chatPlayer(quiz, "Except she's not seaworthy...")
        chatNpc(happy, "No, but a bit of work will quickly sort that out!")

        val option =
            choice3("I'd like to buy her.", 1, "Why is she damaged?", 2, "I think I'll pass.", 3)

        when (option) {
            2 -> {
                chatPlayer(quiz, "Why is she damaged?")
                chatNpc(
                    sad,
                    "There's a hole in her hull. I'd fix her myself, but I've not the coin for it.",
                )
                return
            }
            3 -> {
                chatPlayer(neutral, "I think I'll pass.")
                return
            }
        }

        chatPlayer(quiz, "I'd like to buy her.")
        chatNpc(
            happy,
            "Of course! How does 2,000 coins sound? I'll even throw in my cabin boy, Jenkins, " +
                "for free! He'll swab the decks and splice the mainsails for you!",
        )

        val buy =
            choice2(
                "Yep, sounds good.",
                true,
                "I'm not paying that much for a broken boat!",
                false,
            )
        if (!buy) {
            chatPlayer(angry, "I'm not paying that much for a broken boat!")
            return
        }

        chatPlayer(happy, "Yep, sounds good.")

        if (access.invDel(access.inv, DragonSlayerRefs.Coins, ShipPrice).failure) {
            // TODO: Verify Klarense's insufficient-coins response.
            chatPlayer(sad, "Oops, I don't have enough on me.")
            return
        }

        quest.setQuestStage(access, DragonSlayerStage.ShipBought)
        chatNpc(happy, "Okey-dokey! She's all yours!")
    }

    private suspend fun Dialogue.nedOnShip() {
        chatNpcSpecific(
            CaptainNed,
            DragonSlayerRefs.NedOnShip,
            happy,
            "Ah, it's good to be on board a ship again! No matter how long I live on land, a " +
                "ship will always seem better. Are you ready to depart?",
        )

        val go =
            choice2("Yes, let's go!", true, "No, I'm not quite ready yet.", false)
        if (!go) {
            chatPlayer(neutral, "No, I'm not quite ready yet.")
            return
        }

        chatPlayer(happy, "Yes, let's go!")
        cutscene.voyage(access)
    }

    fun nedCrandorOption(player: Player): String? {
        val stage = quest.getQuestStage(player)
        return when {
            stage < DragonSlayerStage.OziachSpoken -> null
            stage >= DragonSlayerStage.MapGivenToNed -> null
            player.nedAgreed == 1 -> "Will you take me to Crandor now, then?"
            else -> "You're a sailor? Could you take me to Crandor?"
        }
    }

    suspend fun nedCrandorBranch(dialogue: Dialogue) {
        with(dialogue) {
            if (access.player.nedAgreed == 0) {
                nedAgreesToSail()
                return
            }
            nedTakesTheMap()
        }
    }

    private suspend fun Dialogue.nedAgreesToSail() {
        chatPlayer(confused, "You're a sailor? Could you take me to the island of Crandor?")
        chatNpc(
            bored,
            "Well, I was a sailor. I've not been able to get work at sea these days, though. " +
                "They say I'm too old.",
        )
        chatNpc(quiz, "Sorry, where was it you said you wanted to go?")
        chatPlayer(neutral, "To the island of Crandor.")
        chatNpc(shocked, "Crandor?")
        access.player.nedAgreed = 1
        chatNpc(
            shifty,
            "But... It would be a chance to sail a ship once more. I'd sail anywhere if it was a " +
                "chance to sail again.",
        )
        chatNpc(confused, "Then again, no captain in his right mind would sail to that island...")
        chatNpc(happy, "Ah, you only live once! I'll do it!")
        chatNpc(quiz, "So, where's your ship?")
        nedWheresYourShip()
    }

    private suspend fun Dialogue.nedTakesTheMap() {
        chatPlayer(quiz, "Will you take me to Crandor now, then?")
        chatNpc(neutral, "I said I would and old Ned is a man of his word!")
        chatNpc(quiz, "So, where's your ship?")
        nedWheresYourShip()
    }

    private suspend fun Dialogue.nedWheresYourShip() {
        if (quest.getQuestStage(access.player) < DragonSlayerStage.ShipRepaired) {
            chatPlayer(neutral, "Well, I don't actually have a ship yet.")
            chatNpc(sad, "Ah, well. Let me know when you do. The place to look would be Port Sarim.")
            return
        }

        chatPlayer(neutral, "It's the Lady Lumbridge, in Port Sarim.")
        chatNpc(bored, "That old pile of junk? Last I heard, she wasn't seaworthy.")
        chatPlayer(happy, "I fixed her up!")
        chatNpc(happy, "You did? Excellent!")
        chatNpc(neutral, "Just show me the map and we can get ready to go!")

        if (!access.inv.contains(DragonSlayerRefs.DragonMap)) {
            // TODO: Verify Ned's missing-map response.
            chatPlayer(sad, "I don't have the map with me.")
            return
        }

        chatPlayer(neutral, "Here you go.")
        if (access.invDel(access.inv, DragonSlayerRefs.DragonMap, 1).failure) {
            return
        }
        quest.setQuestStage(access, DragonSlayerStage.MapGivenToNed)
        objbox(DragonSlayerRefs.DragonMap, "You hand the map to Ned.")
        chatNpc(happy, "Excellent! I'll meet you at the ship, then.")
    }

    private fun ProtectedAccess.openOziachShop(npc: Npc) {
        shops.open(player, npc, OziachShopTitle, OziachShopInv)
    }

    private companion object {
        const val NedOnShipLeaf = "npc.ned_1op"

        const val CaptainNed = "Captain Ned"

        const val RecommendedCombatLevel = 45

        const val ShipPrice = 2_000

        const val WormbrainPrice = 10_000

        const val OziachShopInv = "inv.runeplateshop"

        const val OziachShopTitle = "Oziach"

        const val MazeKeyOption = "Can I have another key to Melzar's Maze?"

        const val BuyPlatebodyOption = "Can I buy a rune platebody now, please?"

        const val MapOption = "Where can I find a map to Crandor?"

        const val ShipOption = "What kind of ship do I need?"

        const val ShieldOption = "How can I protect myself from the dragon's breath?"

        const val BackstoryOption = "What's so special about this dragon?"

        const val LeaveOption = "Okay, I'll get going!"

        const val MelzarOption = "Where is Melzar's map piece?"

        const val ThalzarOption = "Where is Thalzar's map piece?"

        const val LozarOption = "Where is Lozar's map piece?"
    }
}
