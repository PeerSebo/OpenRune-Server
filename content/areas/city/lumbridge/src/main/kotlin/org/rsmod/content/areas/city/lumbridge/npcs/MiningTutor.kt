package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseMiningLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MiningTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_mining") { startDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc) {
        startDialogue(npc) { mainMenu() }
    }

    private suspend fun Dialogue.mainMenu() {
        val advice =
            when {
                player.baseMiningLvl >= AdvancedMiningLvl -> "Any advice for an advanced miner?"
                player.baseMiningLvl >= IntermediateMiningLvl ->
                    "I already know about the basics of mining, got any tips?"
                else -> "Can you teach me the basics of mining please?"
            }
        val choice =
            choice4(
                advice,
                1,
                "Are there any mining related quests?",
                2,
                "Tell me about different rocks and picks.",
                3,
                "Goodbye.",
                4,
            )
        when (choice) {
            1 ->
                when {
                    player.baseMiningLvl >= AdvancedMiningLvl -> advancedAdvice()
                    player.baseMiningLvl >= IntermediateMiningLvl -> intermediateAdvice()
                    else -> noviceBasics()
                }
            2 -> quests()
            3 -> rocksAndPicksInquiry()
            4 -> goodbye()
        }
    }

    private suspend fun Dialogue.noviceBasics() {
        chatPlayer(quiz, "Can you teach me the basics of mining please?")
        objbox(MiningSiteIcon, "Look for this icon on your minimap to find mining rocks.")
        givePickIfRequired()
        chatNpc(
            neutral,
            "You can tell the ore you'll get from a rock by looking at it. The colour of the " +
                "rock helps, too.",
        )
        chatNpc(
            neutral,
            "To mine, simply click on the rock to mine it, while you've got a pickaxe with you.",
        )
        chatNpc(
            happy,
            "When you have a full inventory, take it to the bank. There's a bank on the roof of " +
                "the castle in Lumbridge.",
        )
        objbox(
            BankIcon,
            "Look for this symbol on your minimap after climbing the stairs of the Lumbridge " +
                "Castle to the top.",
        )
        mainMenu()
    }

    private suspend fun Dialogue.intermediateAdvice() {
        chatPlayer(quiz, "I already know about the basics of mining, got any tips?")
        givePickIfRequired()
        chatNpc(
            neutral,
            "You can get different rocks in different places throughout the land, explore and " +
                "find them.",
        )
        chatNpc(
            neutral,
            "You could start by traveling to the Al Kharid mine. Follow the path north and pay " +
                "the 10 coins toll to go through the gate. Head north after passing through the " +
                "gate and you will find the mine.",
        )
        chatNpc(shifty, "Be careful though, the scorpions in the mine will attack you.")
        chatNpc(neutral, "I could show you where Al Kharid mine is on your map if you like?")
        offerMap()
    }

    private suspend fun Dialogue.advancedAdvice() {
        chatPlayer(quiz, "Any advice for an advanced miner?")
        givePickIfRequired()
        chatNpc(
            neutral,
            "Investigate the mining guild, the entrance to this is in the east of Falador down " +
                "some steps. It could be most useful to you!",
        )
        chatPlayer(happy, "Thank you, I'll remember that.")
        chatNpc(neutral, "I could show you where the mining guild is on your map if you like?")
        offerMap()
    }

    private suspend fun Dialogue.offerMap() {
        val choice = choice2("Yes please.", 1, "No thanks.", 2)
        if (choice == 1) {
            chatPlayer(happy, "Yes please.")
            // TODO: Open and pan the world map to the selected mine.
        } else {
            chatPlayer(neutral, "No thanks.")
            mainMenu()
        }
    }

    private suspend fun Dialogue.quests() {
        chatNpc(
            neutral,
            "Oh yes, if you haven't already, speak to Doric who can be found around the anvils " +
                "north of Falador. I'm sure he can help you out.",
        )
        mainMenu()
    }

    private suspend fun Dialogue.goodbye() {
        chatPlayer(neutral, "Goodbye.")
    }

    private suspend fun Dialogue.givePickIfRequired() {
        if (MiningPickaxe in player.inv || MiningPickaxe in player.worn) {
            chatNpc(happy, "As you already have a pick, you can mine the rocks around me.")
            return
        }
        val add = player.invAdd(player.inv, BronzePickaxe)
        if (add.success) {
            chatNpc(
                happy,
                "As you're already here, have a pick so that you can mine the rocks around me.",
            )
        } else {
            chatNpc(
                sad,
                "I'd give you a pick to mine the rocks around me, but you don't have room in " +
                    "your inventory.",
            )
        }
    }

    private suspend fun Dialogue.rocksAndPicksInquiry() {
        chatPlayer(quiz, "Tell me about different rocks and picks.")
        chatNpc(happy, "Ok then.. here goes....")
        rocksAndPicksMenu()
    }

    private suspend fun Dialogue.rocksAndPicksMenu() {
        val choice =
            choice3("Rocks and Ores.", 1, "Pickaxes.", 2, "Teach me about mining.", 3)
        when (choice) {
            1 -> rocksMenu()
            2 -> picksMenu()
            3 -> mainMenu()
        }
    }

    private suspend fun Dialogue.rocksMenu() {
        val choice =
            choice5(
                "Clay and Rune Essence.",
                1,
                "Copper and Tin.",
                2,
                "Iron and Coal.",
                3,
                "Teach me about mining.",
                4,
                "More...",
                5,
            )
        when (choice) {
            1 -> clayAndEssence()
            2 -> copperAndTin()
            3 -> ironAndCoal()
            4 -> mainMenu()
            5 -> moreRocksMenu()
        }
    }

    private suspend fun Dialogue.moreRocksMenu() {
        val choice =
            choice4(
                "Silver and Gold.",
                1,
                "Mithril, Adamantite and Runite.",
                2,
                "Teach me about mining.",
                3,
                "Back...",
                4,
            )
        when (choice) {
            1 -> silverAndGold()
            2 -> mithrilAdamantiteRunite()
            3 -> mainMenu()
            4 -> rocksMenu()
        }
    }

    private suspend fun Dialogue.clayAndEssence() {
        objbox(
            Clay,
            "Clay can be mined in many places. Try south west of Varrock or the crafting guild. " +
                "See the crafting tutor upstairs in Lumbridge Castle for more information on what " +
                "you can make with clay.",
        )
        objbox(
            RuneEssence,
            "Rune Essence can only be mined at secret places... I don't know where these are but " +
                "you can speak to the Duke of Lumbridge to find out more.",
        )
        chatNpc(
            neutral,
            "You can make all sorts of things from clay, if you have the crafting skill. Rune " +
                "Essence is used for magic to cast spells.",
        )
        backToMiningOrRocks()
    }

    private suspend fun Dialogue.copperAndTin() {
        objbox(
            CopperOre,
            "Rocks which yield copper ore can be found around here, and other places such as Al " +
                "Kharid, Rimmington or Varrock mines. They're quite common.",
        )
        objbox(
            TinOre,
            "Tin ore is much the same as copper. It can be found around this area as well as " +
                "Varrock, Al Kharid and the Barbarian Village.",
        )
        backToMiningOrRocks()
    }

    private suspend fun Dialogue.ironAndCoal() {
        objbox(
            IronOre,
            "Iron can be mined around Rimmington, Al Kharid and Varrock. It's tricky to smelt " +
                "though - see the boy at the furnace in Lumbridge for tips.",
        )
        objbox(FurnaceIcon, "Look for this icon on your minimap to find the furnace.")
        objbox(
            Coal,
            "You'll need to be a fairly good miner before you can start mining coal. You can " +
                "find it in the Dwarf mine, Al Kharid and quite a few other places.",
        )
        backToMiningOrRocks()
    }

    private suspend fun Dialogue.silverAndGold() {
        objbox(
            SilverOre,
            "Silver can be mined to help you with your crafting. You can find it in the Crafting " +
                "guild, Al Kharid and many other mines.",
        )
        objbox(
            GoldOre,
            "Gold requires quite a lot of experience in mining before you can successfully " +
                "extract it from the rocks. It can be found in Al Kharid, the Crafting guild, " +
                "Rimmington and other places.",
        )
        backToMiningOrMoreRocks()
    }

    private suspend fun Dialogue.mithrilAdamantiteRunite() {
        doubleobjbox(
            MithrilOre,
            AdamantiteOre,
            "Mithril and Adamantite rocks are fairly rare and require great skill to mine. You " +
                "can find them in Al Kharid, the Dwarven mines, Lumbridge Swamps and other places.",
        )
        objbox(
            RuniteOre,
            "Runite is the most prized of ores and is very rare. I'll leave you to find where to " +
                "mine it.",
        )
        backToMiningOrMoreRocks()
    }

    private suspend fun Dialogue.backToMiningOrRocks() {
        val choice =
            choice2("Teach me about mining.", 1, "Teach me about different rocks.", 2)
        if (choice == 1) {
            mainMenu()
        } else {
            rocksMenu()
        }
    }

    private suspend fun Dialogue.backToMiningOrMoreRocks() {
        val choice =
            choice2("Teach me about mining.", 1, "Teach me about different rocks.", 2)
        if (choice == 1) {
            mainMenu()
        } else {
            moreRocksMenu()
        }
    }

    private suspend fun Dialogue.picksMenu() {
        val choice =
            choice3(
                "Bronze, Iron and Steel.",
                1,
                "Mithril, Adamantite and Runite.",
                2,
                "Teach me about mining.",
                3,
            )
        when (choice) {
            1 -> lowTierPicks()
            2 -> highTierPicks()
            3 -> mainMenu()
        }
    }

    private suspend fun Dialogue.lowTierPicks() {
        objbox(
            BronzePickaxe,
            "The Bronze Pick is your basic tool of the trade. You can buy one from Bob's Axe shop " +
                "very cheaply, that's north of here in Lumbridge, I also heard that exploring the " +
                "castle walls may yield a surprise or two.",
        )
        doubleobjbox(
            IronPickaxe,
            SteelPickaxe,
            "The Iron and Steel Picks are faster than the bronze pickaxe, but you will need to " +
                "progress with your mining a little before you can use them. You can buy them " +
                "from Nurmof in the Dwarven mines.",
        )
        picksMenu()
    }

    private suspend fun Dialogue.highTierPicks() {
        doubleobjbox(
            MithrilPickaxe,
            RunePickaxe,
            "Mithril, Adamantite, and Rune Picks may be used as you progress with your skill of " +
                "mining. They can be obtained from Nurmof in the Dwarven mines.",
        )
        picksMenu()
    }

    private companion object {
        const val IntermediateMiningLvl: Int = 25
        const val AdvancedMiningLvl: Int = 39

        const val MiningSiteIcon = "obj.mining_site_icon_dummy"

        const val BankIcon = "obj.bank_icon_dummy"

        const val FurnaceIcon = "obj.furnace_icon_dummy"

        const val MiningPickaxe = "content.mining_pickaxe"

        const val BronzePickaxe = "obj.bronze_pickaxe"
        const val IronPickaxe = "obj.iron_pickaxe"
        const val SteelPickaxe = "obj.steel_pickaxe"
        const val MithrilPickaxe = "obj.mithril_pickaxe"
        const val RunePickaxe = "obj.rune_pickaxe"

        const val Clay = "obj.clay"

        const val RuneEssence = "obj.blankrune"

        const val CopperOre = "obj.copper_ore"
        const val TinOre = "obj.tin_ore"
        const val IronOre = "obj.iron_ore"
        const val Coal = "obj.coal"
        const val SilverOre = "obj.silver_ore"
        const val GoldOre = "obj.gold_ore"
        const val MithrilOre = "obj.mithril_ore"
        const val AdamantiteOre = "obj.adamantite_ore"
        const val RuniteOre = "obj.runite_ore"
    }
}
