package org.rsmod.content.areas.city.lumbridge.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseFishingLvl
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FishingTutor @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_fishing") { startDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc): Unit =
        startDialogue(npc) { mainMenu() }

    private suspend fun Dialogue.mainMenu() {
        val level = player.baseFishingLvl
        val advice =
            when {
                level >= 39 -> "Any advice for an advanced fisher?"
                level >= 29 -> "I already know about the basics of fishing, got any tips?"
                else -> "Can you teach me the basics of fishing please?"
            }
        val choice =
            choice4(
                advice,
                1,
                "Tell me about different fish.",
                2,
                "Where and what should I fish?",
                3,
                "Goodbye.",
                4,
            )
        when (choice) {
            1 ->
                when {
                    level >= 39 -> advancedAdvice()
                    level >= 29 -> intermediateAdvice()
                    else -> noviceBasics()
                }
            2 -> differentFish()
            3 -> whereAndWhat()
            4 -> goodbye()
        }
    }

    private suspend fun Dialogue.noviceBasics() {
        chatPlayer(quiz, "Can you teach me the basics of fishing please?")
        giveNetIfRequired()
        objbox(
            "obj.fishing_spot_icon_dummy",
            "Look for this icon on the minimap to find fishing spots, " +
                "they will move as the fish swim around so you can't be " +
                "lazy.",
        )
        chatNpcNoTurn(
            happy,
            "Ahoy, to fish, you click on the shrimp fishin' spot while ye'r carrying a net.",
        )
        chatPlayer(quiz, "I see... is that it?")
        chatNpcNoTurn(
            happy,
            "There's far more as you progress - not just shrimps. " +
                "You get more equipment, bigger fish and other things " +
                "too...",
        )
        chatNpcNoTurn(
            happy,
            "When you have a full inventory, you can cook it or " +
                "take it to the bank. You can find a bank on the roof of " +
                "the castle in Lumbridge and a cookin' range in the " +
                "castle kitchen.",
        )
        if (player.members && questNotStarted(QUEST_FISHING_CONTEST)) {
            chatNpcNoTurn(
                happy,
                "Arrr.. if yer lookin' fer quests you should head over " +
                    "to the Mountain Dwarf who lies north-west of " +
                    "Taverley and have a chat with him, I'm sure he can " +
                    "point you in the right direction.",
            )
            offerMap(TAVERLEY_MOUNTAIN_DWARF)
        }
        mainMenu()
    }

    private suspend fun Dialogue.intermediateAdvice() {
        chatPlayer(quiz, "I already know about the basics of fishing, got any tips?")
        giveNetIfRequired()
        chatNpcNoTurn(
            happy,
            "Arrr, chose carefully where and what you fish, you can " +
                "get different fish in different places throughout the " +
                "land.",
        )
        chatNpcNoTurn(
            happy,
            "Make sure you hang on to your fish, don't throw them " +
                "away as you can get valuable cooking experience from " +
                "them, gar! Look out for other things too...",
        )
        if (player.members) {
            chatNpcNoTurn(happy, "Then of course there is always the trawler...")
            chatNpcNoTurn(
                happy,
                "If you venture to Port Khazard north of Yanille and " +
                    "speaks to me cabin boy Murphy, he can let you and " +
                    "a bunch of friends use his trawler to fish with... Just " +
                    "make sure you take a bailing bucket or two.",
            )
            offerMap(PORT_KHAZARD_MURPHY)
        }
        mainMenu()
    }

    private suspend fun Dialogue.advancedAdvice() {
        chatPlayer(quiz, "Any advice for an advanced fisher?")
        giveNetIfRequired()
        chatNpcNoTurn(
            happy,
            "Aye, as you get better and better you'll find that you can " +
                "fish for things like Tuna and Swordfish. These be very " +
                "good for combat.",
        )
        if (player.members) {
            chatNpcNoTurn(
                happy,
                "Of course, after that you will get Shark, and if you " +
                    "venture out onto the Trawler from Port Khazard, you " +
                    "can then get Manta Ray or Sea Turtle, very good " +
                    "eatin' on one of them.",
            )
            chatNpcNoTurn(
                happy,
                "You can also use a fishing potion if you can get your " +
                    "hands on one and just need that little edge.. ask " +
                    "someone who is familiar with herblore.",
            )
            chatNpcNoTurn(happy, "Now.. quests...")
            when {
                questNotStarted(QUEST_SEA_SLUG) -> {
                    chatNpcNoTurn(
                        happy,
                        "Head over to south of the Legends Guild and East " +
                            "of Ardougne and find Caroline. This will have you " +
                            "investigating some strange happenings on the " +
                            "fishing platform.",
                    )
                    offerMap(WITCHAVEN_CAROLINE)
                }
                questNotStarted(QUEST_TAI_BWO_WANNAI_TRIO) -> {
                    chatNpcNoTurn(
                        happy,
                        "Head over to Tai Bwo Wannai Village on the Isle " +
                            "of Karamja and speak to Timfraku to solve a " +
                            "strange mystery.",
                    )
                    offerMap(TAI_BWO_WANNAI_TIMFRAKU)
                }
                else ->
                    chatNpcNoTurn(
                        happy,
                        "Can't rightly say's there's any I know of that you " +
                            "could handle at the moment, but come back when " +
                            "you're a bit more experienced and I'm sure I can " +
                            "give you a clue.",
                    )
            }
        }
        mainMenu()
    }

    private suspend fun Dialogue.differentFish() {
        chatPlayer(quiz, "Tell me about different fish.")
        fishMenu()
    }

    private suspend fun Dialogue.fishMenu() {
        val level = player.baseFishingLvl
        val topics = buildList {
            if (level <= 38) add(FishTopic.SmallNet)
            if (player.members) add(FishTopic.BigNet)
            add(FishTopic.RodAndFly)
            if (level >= 25) add(FishTopic.LobsterPot)
            if (level >= 35) add(FishTopic.Harpoon)
            add(FishTopic.Back)
        }
        when (topics[selectTopic(topics.map(FishTopic::label)) - 1]) {
            FishTopic.SmallNet -> {
                smallNetFish()
                fishMenu()
            }
            FishTopic.BigNet -> {
                bigNetFish()
                fishMenu()
            }
            FishTopic.RodAndFly -> {
                rodAndFlyFishing()
                fishMenu()
            }
            FishTopic.LobsterPot -> {
                lobsterPot()
                fishMenu()
            }
            FishTopic.Harpoon -> {
                harpoonFish()
                fishMenu()
            }
            FishTopic.Back -> mainMenu()
        }
    }

    private suspend fun Dialogue.smallNetFish() {
        objbox(
            "obj.net",
            "Ahoy, small net fishin' you can do just south of " +
                "Draynor Village and in these very spots here. Aye.",
        )
        objbox("obj.shrimp", "Shrimp and anchovies can be caught with your small fishin' net.")
    }

    private suspend fun Dialogue.bigNetFish() {
        objbox(
            "obj.big_net",
            "Aye, you can net yourself some big fish in Catherby, " +
                "which is a good place to fish for most things, Gar!",
        )
        objbox(
            "obj.cod",
            "Mackrel and Cod will form the backbone of your catch " +
                "when big net fishin'.. except for the added extras...",
        )
        chatNpcNoTurn(
            happy,
            "Some rich rewards for big net fishin', make sure you be " +
                "using a big net fishing spot though...",
        )
    }

    private suspend fun Dialogue.rodAndFlyFishing() {
        objbox(
            "obj.fishing_rod",
            "Aye, rod fishin' can be practiced here at these spots, as " +
                "well as south of Draynor Village and in the Lumbridge " +
                "river, depending upon your experience. You can get " +
                "bait at any fishin' shop, there be one in Port Sarim.",
        )
        objbox(
            "obj.pike",
            "With a rod you can catch pike, sardines and herring. Good eating on them.",
        )
        objbox(
            "obj.fly_fishing_rod",
            "The art of fly fishin' can be done in rivers, so the " +
                "Lumbridge river here would suffice.",
        )
        objbox("obj.salmon", "Aye, you can catch yourself a delicious trout or salmon.")
    }

    private suspend fun Dialogue.lobsterPot() {
        objbox(
            "obj.lobster_pot",
            "Arrr, Lobster pots can be used from the pier on the island of Karamja.",
        )
        objbox(
            "obj.lobster",
            "In Lobster pots you can catch lobsters. They're tasty after thems cooked.",
        )
        chatPlayer(quiz, "Is that all?")
    }

    private suspend fun Dialogue.harpoonFish() {
        objbox(
            "obj.harpoon",
            "Arrr, you can also use your harpoon from the pier on the island of Karamja.",
        )
        objbox("obj.swordfish", "With it you can catch Tuna and Swordfish.")
        if (player.members) {
            objbox("obj.shark", "... and shark.")
            chatNpcNoTurn(
                happy,
                "Ahoy, there's also the fishin' guild if ya skilled " +
                    "enough, you can find it south west of Seers' Village.",
            )
        }
    }

    private suspend fun Dialogue.whereAndWhat() {
        chatPlayer(quiz, "Where and what should I fish?")
        val level = player.baseFishingLvl
        when {
            level <= 9 -> parrotExchange()
            player.members -> membersSuggestion(level)
            else -> freeSuggestion(level)
        }
        mainMenu()
    }

    private suspend fun Dialogue.parrotExchange() {
        chatNpcNoTurn(
            happy,
            "A young and enthusiastic fisher. Try south of Draynor " +
                "Village or the pier on the island of Karamja to fish for " +
                "Shrimp at level 1 or Sardines at level 5. Aye, me parrot " +
                "concurs.",
        )
        chatPlayer(confused, "What parrot?")
        chatNpcNoTurn(happy, "Me parrot Percy on me shoulder thar! Arr!")
        chatPlayer(neutral, "Oookay...sure, a parrot, on your shoulder.")
        chatNpcNoTurn(happy, "Arrr!")
    }

    private suspend fun Dialogue.freeSuggestion(level: Int) {
        val line =
            when {
                level >= 50 ->
                    "Congratulations, you can now harpoon Swordfish " +
                        "after level 50. Try the pier on the island of Karamja."
                level >= 40 ->
                    "You can use that Lobster pot from the pier on the " +
                        "island of Karamja to get yourself a whole bunch of " +
                        "the little critters once you reach level 40."
                level >= 35 ->
                    "Use your harpoon and see if you can catch yourself " +
                        "a few Tuna from the pier on the island of Karamja " +
                        "at level 35."
                level >= 25 ->
                    "Pike can be fished from Lumbridge river using your " +
                        "fishing rod and some bait with at least level 25 " +
                        "fishing."
                level >= 20 ->
                    "You can practice your fly fishin' in the Lumbridge " +
                        "river to catch Trout at level 20."
                level >= 15 ->
                    "You can use your small net to catch Anchovies " +
                        "from south of Draynor when you reach level 15."
                else ->
                    "Herrin' can be fished from south of Draynor Village " +
                        "or the pier on the island of Karamja when you " +
                        "reach level 10."
            }
        chatNpcNoTurn(happy, line)
    }

    private suspend fun Dialogue.membersSuggestion(level: Int) {
        if (level >= 50) {
            chatNpcNoTurn(
                happy,
                "Tuna and Swordfish can be harpooned - if you're " +
                    "good enough - from the thrivin' fishing village of " +
                    "Catherby, or if you can get in try the Fishin' Guild. " +
                    "Level 35 for Tuna and 50 for Swordfish.",
            )
            chatNpcNoTurn(
                happy,
                "You can also go talk to Murphy and see if you can " +
                    "use his trawler to catch Manta Ray and Sea Turtle if " +
                    "you're really good... Gar!",
            )
            return
        }
        val line =
            when {
                level >= 46 ->
                    "Bass can be caught at level 46 in your big net if " +
                        "you wander along to Catherby."
                level >= 38 ->
                    "You can use your fishin' rod and some bait to catch " +
                        "Cave Eel in the caves below Lumbridge Swamp at " +
                        "level 38."
                level >= 28 ->
                    "You can use your fishin' rod and some bait to catch " +
                        "Slimy Eel in the swamps at level 28."
                level >= 23 ->
                    "Cod can be fished from Catherby and some other " +
                        "places once you reach level 23."
                level >= 16 ->
                    "You can use a big net to catch Mackerel from " +
                        "Catherby when you reach level 16."
                else ->
                    "Herrin' can be fished from Catherby and some other " +
                        "places when you reach level 10."
            }
        chatNpcNoTurn(happy, line)
    }

    private suspend fun Dialogue.goodbye() {
        chatPlayer(neutral, "Goodbye.")
    }

    private suspend fun Dialogue.giveNetIfRequired() {
        if (access.invTotal(player.inv, "obj.net") > 0) {
            chatNpcNoTurn(happy, "I see you already have a net.")
            return
        }
        val add = player.invAdd(player.inv, "obj.net")
        if (add.success) {
            chatNpcNoTurn(happy, "Ah, you've lost your net have you? Have another!")
        } else {
            chatNpcNoTurn(
                happy,
                "You don't seem to have any space so I can't give you another net.",
            )
        }
    }

    private suspend fun Dialogue.offerMap(coord: CoordGrid) {
        chatNpcNoTurn(happy, "I can show ye on that map of yours if ya like?")
        if (choice2("Yes please.", true, "No thanks.", false)) {
            player.showOnMap(coord)
        }
    }

    private fun Player.showOnMap(coord: CoordGrid) {
        runClientScript(WORLDMAP_SET_POSITION, coord.packed)
        ifOpenOverlay("interface.worldmap", eventBus)
    }

    private suspend fun Dialogue.selectTopic(labels: List<String>): Int =
        when (labels.size) {
            3 -> choice3(labels[0], 1, labels[1], 2, labels[2], 3)
            4 -> choice4(labels[0], 1, labels[1], 2, labels[2], 3, labels[3], 4)
            5 -> choice5(labels[0], 1, labels[1], 2, labels[2], 3, labels[3], 4, labels[4], 5)
            6 ->
                choice6(
                    labels[0],
                    1,
                    labels[1],
                    2,
                    labels[2],
                    3,
                    labels[3],
                    4,
                    labels[4],
                    5,
                    labels[5],
                    6,
                )
            else -> error("Unexpected fish topic count: ${labels.size} ($labels)")
        }

    private fun Dialogue.questNotStarted(key: String): Boolean {
        val quest = Quest.get(key) ?: return true
        return quest.isQuestNotStarted(player)
    }

    private enum class FishTopic(val label: String) {
        SmallNet("Small Net Fish"),
        BigNet("Big Net Fish"),
        RodAndFly("Rod and Fly Fishing"),
        LobsterPot("Lobster pot"),
        Harpoon("Harpoon Fish"),
        Back("Tell me about..."),
    }

    private companion object {
        const val WORLDMAP_SET_POSITION = 1749

        const val QUEST_FISHING_CONTEST = "quest_fishingcontest"
        const val QUEST_SEA_SLUG = "quest_seaslug"
        const val QUEST_TAI_BWO_WANNAI_TRIO = "quest_taibwowannaitrio"

        val TAVERLEY_MOUNTAIN_DWARF = CoordGrid(2843, 3540, 0)
        val PORT_KHAZARD_MURPHY = CoordGrid(2675, 3170, 0)
        val WITCHAVEN_CAROLINE = CoordGrid(2715, 3302, 0)
        val TAI_BWO_WANNAI_TIMFRAKU = CoordGrid(2803, 3085, 0)
    }
}
