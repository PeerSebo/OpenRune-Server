package org.rsmod.content.quest.area.alkharid

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Ashes
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.BallOfWool
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.BlondWig
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.BronzeBar
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Coins
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.EmptiedWaterContainer
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.KeyCraftingXp
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.KeyPrint
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.PlainWig
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.PotOfFlour
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.PrincesKey
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Redberries
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.SkinPaste
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.WaterContainers
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.WigWoolCost
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.YellowDye
import org.rsmod.content.quest.area.crandor.DragonSlayer
import org.rsmod.content.quest.area.goblinvillage.gobdipMetAggie
import org.rsmod.content.quest.manager.QuestVar
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PrinceAliRescueDisguise
@Inject
constructor(private val shops: Shops, private val dragonSlayer: DragonSlayer) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.ned") { talkToNed(it.npc) }
        onOpNpc3("npc.ned") { player.openRopeShop(it.npc) }

        for (aggie in listOf("npc.aggie", "npc.aggie_1op", "npc.aggie_2ops")) {
            onOpNpc1(aggie) { talkToAggie(it.npc) }
        }
        onOpNpc3("npc.aggie_2ops") { startDialogue(it.npc) { dyeMenu() } }

        onOpHeldU(YellowDye, PlainWig) { dyeWig() }

        onOpHeldU(RedDye, YellowDye) { mixOrangeDye() }

        onOpLocCategoryU("category.furnace", KeyPrint) { castKey() }
    }

    private fun Player.openRopeShop(npc: Npc) {
        shops.open(this, npc, "Ned's Ropes", "inv.neds_rope_shop")
    }

    private suspend fun ProtectedAccess.talkToNed(npc: Npc) {
        startDialogue(npc) { nedDialogue() }
    }

    private suspend fun Dialogue.nedDialogue() {
        if (MET_NED.get(access.player)) {
            chatNpc(
                neutral,
                "Hello again, ${access.nedLadOrLass}. Something I can help you with? Want to buy " +
                    "some rope perhaps?",
            )
        } else {
            MET_NED.set(access.player, true)
            chatNpc(
                happy,
                "Why, hello there, ${access.nedLadOrLass}. Me friends call me Ned. I was a man of " +
                    "the sea, but it's " +
                    "past me now. Could I be making or selling you some rope?",
            )
        }

        val crandorOption = dragonSlayer.nedCrandorOption(access.player)

        val option =
            if (crandorOption != null) {
                choice4(
                    crandorOption,
                    0,
                    "Could you make other things apart from rope?",
                    1,
                    "Yes, I would like some rope.",
                    2,
                    "No thanks, Ned. I don't need any.",
                    3,
                )
            } else {
                choice3(
                    "Could you make other things apart from rope?",
                    1,
                    "Yes, I would like some rope.",
                    2,
                    "No thanks, Ned. I don't need any.",
                    3,
                )
            }

        when (option) {
            0 -> dragonSlayer.nedCrandorBranch(this)
            1 -> nedOtherThings()
            2 -> nedRope()
            3 -> {
                chatPlayer(neutral, "No thanks, Ned. I don't need any.")
                chatNpc(neutral, "Right you are.")
            }
        }
    }

    private suspend fun Dialogue.nedOtherThings() {
        chatPlayer(quiz, "Could you make other things apart from rope?")
        chatNpc(happy, "I'm sure I can. What are you thinking of?")

        val option =
            choice4(
                "Could you knit me a sweater?",
                1,
                "How about some sort of wig?",
                2,
                "Could you repair the arrow holes in the back of my shirt?",
                3,
                "Actually, I don't need anything.",
                4,
            )

        when (option) {
            1 -> {
                chatPlayer(quiz, "Could you knit me a sweater?")
                chatNpc(
                    angry,
                    "Do I look like a member of a sewing circle? Be off wi' you. I have fought " +
                        "monsters that would turn your hair blue.",
                )
                chatNpc(angry, "I don't need to be laughed at just 'cos I'm getting a bit old.")
            }
            2 -> nedWig()
            3 -> {
                chatPlayer(quiz, "Could you repair the arrow holes in the back of my shirt?")
                chatNpc(
                    happy,
                    "Ah yes, it's a tough world these days. There's a few brave enough to attack " +
                        "from ten metres away.",
                )
                mesbox("Ned pulls out a needle and attacks your shirt.")
                chatNpc(happy, "There you go, good as new.")
                chatPlayer(happy, "Thanks Ned. Maybe next time they will attack me face to face.")
            }
            4 -> {
                chatPlayer(neutral, "Actually, I don't need anything.")
            }
        }
    }

    private suspend fun Dialogue.nedWig() {
        chatPlayer(quiz, "How about some sort of wig?")
        chatNpc(
            neutral,
            "Well... that's an interesting thought. Yes, I think I could do something. Give me " +
                "three balls of wool and I might be able to do it.",
        )

        if (access.carriedCount(BallOfWool) < WigWoolCost) {
            chatPlayer(neutral, "Great, I will get some. I think a wig would be useful.")
            return
        }

        val make =
            choice2(
                "I have them here. Please make me a wig.",
                true,
                "Actually, I don't need one right now.",
                false,
            )

        if (!make) {
            chatPlayer(neutral, "Actually, I don't need one right now.")
            chatNpc(neutral, "Fair enough.")
            return
        }

        chatPlayer(neutral, "I have them here. Please make me a wig.")
        chatNpc(neutral, "Okay, I'll have a go.")

        if (access.invDel(access.inv, BallOfWool, WigWoolCost).failure) {
            return
        }
        if (access.invAdd(access.inv, PlainWig, 1).failure) {
            access.invAdd(access.inv, BallOfWool, WigWoolCost)
            return
        }

        objbox(PlainWig, "Ned gives you a pretty good wig.")

        chatNpc(happy, "Here you go. How's that for a quick effort? Not bad I think!")
        chatPlayer(happy, "Thanks Ned. There's more to you than meets the eye.")
    }

    private suspend fun Dialogue.nedRope() {
        chatPlayer(neutral, "Yes, I would like some rope.")
        chatNpc(
            happy,
            "Well, I can sell you some rope for 15 coins. Or I can be making you some if you gets " +
                "me four balls of wool. I strands them together I does, makes em strong.",
        )
        chatPlayer(quiz, "You make rope from wool?")
        chatNpc(shifty, "Of course you can!")
        chatPlayer(quiz, "I thought you needed hemp or jute.")
        chatNpc(angry, "Do you want some rope or not?")

        val option =
            choice3(
                "Okay, please sell me some rope.",
                1,
                "That's a little more than I want to pay.",
                2,
                "I will go and get some wool.",
                3,
            )

        when (option) {
            1 -> {
                chatPlayer(neutral, "Okay, please sell me some rope.")

                if (access.carriedCount(Coins) < RopePrice) {
                    chatPlayer(sad, "Actually, I don't have enough coins.")
                    return
                }

                chatNpc(happy, "There you go, finest rope in Gielinor.")

                if (access.invDel(access.inv, Coins, RopePrice).failure) {
                    return
                }
                access.invAdd(access.inv, PrinceAliRescueState.Rope, 1)

                mesbox("You hand Ned $RopePrice coins. Ned gives you a coil of rope.")
            }
            2 -> {
                chatPlayer(neutral, "That's a little more than I want to pay.")
                chatNpc(
                    bored,
                    "Well, if you ever need rope that's the price. Sorry. An old sailor needs " +
                        "money for a little drop o' rum.",
                )
            }
            3 -> {
                chatPlayer(neutral, "I will go and get some wool.")
                chatNpc(happy, "Right you are. Four balls, mind.")
            }
        }
    }

    private suspend fun ProtectedAccess.dyeWig() {
        if (invDel(inv, PlainWig, 1).failure) {
            return
        }
        invDel(inv, YellowDye, 1)
        invAdd(inv, BlondWig, 1)
        mes("You dye the wig blonde.")
    }

    private suspend fun ProtectedAccess.talkToAggie(npc: Npc) {
        startDialogue(npc) { aggieDialogue() }
    }

    private suspend fun Dialogue.aggieDialogue() {
        chatNpc(happy, "What can I help you with?")

        val option =
            choice5(
                "Cool, do you turn people into frogs?",
                1,
                "What could you make for me?",
                2,
                "Can you make skin paste?",
                3,
                "You mad old witch, you can't help me.",
                4,
                "I'm okay, thanks.",
                5,
            )

        when (option) {
            1 -> {
                chatPlayer(happy, "Cool, do you turn people into frogs?")
                chatNpc(
                    neutral,
                    "Oh, not for years, but if you meet a talking chicken, you have probably met " +
                        "the professor in the manor north of here. A few years ago it was flying " +
                        "fish. That machine is a menace.",
                )
            }
            2 -> aggieDyes()
            3 -> aggieSkinPaste()
            4 -> aggieInsult()
            5 -> {
                chatPlayer(neutral, "I'm okay, thanks.")
            }
        }
    }

    private suspend fun Dialogue.aggieDyes() {
        chatPlayer(quiz, "What could you make for me?")
        chatNpc(
            neutral,
            "Dyes, dearie, and skin paste. Ask me about the paste if you've a face to pretty up.",
        )

        access.gobdipMetAggie = 1

        dyeMenu()
    }

    private suspend fun Dialogue.dyeMenu() {
        while (true) {
            val recipe =
                choice4(
                    "Can you make me some red dye?",
                    AggieDye.Red,
                    "Can you make me some yellow dye?",
                    AggieDye.Yellow,
                    "Can you make me some blue dye?",
                    AggieDye.Blue,
                    "Nothing right now, thanks.",
                    null,
                )

            if (recipe == null) {
                chatPlayer(neutral, "Nothing right now, thanks.")
                return
            }

            mixDye(recipe)
        }
    }

    private suspend fun Dialogue.mixDye(recipe: AggieDye) {
        chatPlayer(quiz, "Can you make me some ${recipe.colour} dye?")
        chatNpc(
            neutral,
            "That I can, dearie. Bring me ${recipe.ingredientCount} ${recipe.ingredientName} and " +
                "$DyePrice coins, and a pot of ${recipe.colour} dye is yours.",
        )

        val hasIngredients = access.carriedCount(recipe.ingredient) >= recipe.ingredientCount
        val hasCoins = access.carriedCount(Coins) >= DyePrice
        if (!hasIngredients || !hasCoins) {
            chatPlayer(neutral, "I'll fetch them and come back.")
            return
        }

        val mix =
            choice2(
                "I have them right here. Please make me some.",
                true,
                "Actually, not just now.",
                false,
            )

        if (!mix) {
            chatPlayer(neutral, "Actually, not just now.")
            return
        }

        chatPlayer(neutral, "I have them right here. Please make me some.")
        chatNpc(happy, "Hand them over then, dearie.")

        if (access.invDel(access.inv, recipe.ingredient, recipe.ingredientCount).failure) {
            return
        }
        if (access.invDel(access.inv, Coins, DyePrice).failure) {
            access.invAdd(access.inv, recipe.ingredient, recipe.ingredientCount)
            return
        }
        if (access.invAdd(access.inv, recipe.dye, 1).failure) {
            access.invAdd(access.inv, recipe.ingredient, recipe.ingredientCount)
            access.invAdd(access.inv, Coins, DyePrice)
            return
        }

        objbox(recipe.dye, "Aggie hands you a pot of ${recipe.colour} dye.")
    }

    private suspend fun ProtectedAccess.mixOrangeDye() {
        if (invDel(inv, RedDye, 1).failure) {
            return
        }
        if (invDel(inv, YellowDye, 1).failure) {
            invAdd(inv, RedDye, 1)
            return
        }
        if (invAdd(inv, OrangeDye, 1).failure) {
            invAdd(inv, RedDye, 1)
            invAdd(inv, YellowDye, 1)
            return
        }
        mes("You mix the red and yellow dye. You now have orange dye.")
    }

    private suspend fun Dialogue.aggieSkinPaste() {
        chatPlayer(quiz, "Can you make skin paste?")

        val water = WaterContainers.firstOrNull { access.carrying(it) }
        val hasAll =
            water != null &&
                access.carrying(Ashes) &&
                access.carrying(PotOfFlour) &&
                access.carrying(Redberries)

        if (!hasAll) {
            chatNpc(
                neutral,
                "Why, it's one of my most popular potions! Lots of people around here like to " +
                    "pretty their faces up a bit. I can make it for you if you get me what's " +
                    "needed.",
            )
            chatPlayer(quiz, "What do you need?")
            chatNpc(
                neutral,
                "Well dearie, you need a base for the paste. That's a mix of ash, flour and " +
                    "water. Then you need redberries to colour it as you want. Bring me those " +
                    "four items and I will make you some.",
            )
            return
        }

        chatNpc(
            neutral,
            "Yes, I can. I see you already have the ingredients - the water, flour, ashes and " +
                "redberries. Would you like me to mix some for you now?",
        )

        val mix =
            choice2(
                "Yes please. Mix me some skin paste.",
                true,
                "No thank you. I don't need any skin paste right now.",
                false,
            )

        if (!mix) {
            chatPlayer(neutral, "No thank you. I don't need any skin paste right now.")
            chatNpc(neutral, "Okay dearie, that's always your choice.")
            return
        }

        chatPlayer(happy, "Yes please. Mix me some skin paste.")
        chatNpc(happy, "That should be simple. Hand the things to Aggie then.")

        doubleobjbox(
            Redberries,
            PotOfFlour,
            "You hand the ash, flour, water and redberries to Aggie. She tips the ingredients " +
                "into a cauldron and mutters some words.",
        )
        chatNpc(confused, "Tourniquet, Fenderbaum, Tottenham, Marshmallow, Marblearch.")

        if (access.invDel(access.inv, Ashes, 1).failure) {
            return
        }
        access.invDel(access.inv, PotOfFlour, 1)
        access.invDel(access.inv, Redberries, 1)
        access.invDel(access.inv, water, 1)
        EmptiedWaterContainer[water]?.let { access.invAdd(access.inv, it, 1) }
        access.invAdd(access.inv, SkinPaste, 1)

        objbox(SkinPaste, "Aggie hands you the skin paste.")
        chatNpc(happy, "There you go dearie. That will make you look good at the Varrock dances.")
    }

    private suspend fun Dialogue.aggieInsult() {
        chatPlayer(angry, "You mad old witch, you can't help me.")
        chatNpc(angry, "Oh, you like to call a witch names do you?")

        access.soundSynth(WitchFineSound)

        if (access.carriedCount(Coins) >= WitchFine) {
            access.invDel(access.inv, Coins, WitchFine)
            objbox(
                Coins,
                "Aggie waves her hands about, and you seem to be $WitchFine coins poorer.",
            )
            chatNpc(neutral, "That's a fine for insulting a witch. You should learn some respect.")
            return
        }

        val slot = access.inv.indices.firstOrNull { access.inv[it] != null }
        val victim = slot?.let { access.inv[it] }
        if (slot == null || victim == null) {
            chatNpc(neutral, "Hmph. You've nothing worth taking anyway.")
            return
        }

        val symbol = RSCM.getReverseMapping(RSCMType.OBJ, victim.id)
        val name = ServerCacheManager.getItem(victim.id)?.name?.lowercase() ?: "trinket"

        if (access.invDel(access.inv, symbol, 1, slot = slot).failure) {
            return
        }

        objbox(symbol, "Aggie waves her hands about, and you seem to have a $name less.")
        chatNpc(
            neutral,
            "Thank you for your kind present of some $name. I am sure you never meant to insult me.",
        )
    }

    private suspend fun ProtectedAccess.castKey() {
        arriveDelay()

        if (!carrying(BronzeBar)) {
            mes("You need a bronze bar to cast a copy of the key.")
            return
        }

        var confirm = false
        startDialogue { confirm = choice2("Yes", true, "No", false, title = KeyConfirmTitle) }
        if (!confirm) {
            return
        }

        if (invDel(inv, KeyPrint, 1).failure) {
            return
        }
        if (invDel(inv, BronzeBar, 1).failure) {
            invAdd(inv, KeyPrint, 1)
            return
        }

        anim(SmeltAnim)
        soundSynth(CastKeySound)
        delay(SmeltTicks)

        invAdd(inv, PrincesKey, 1)
        statAdvance("stat.crafting", KeyCraftingXp)

        mes("You cast the imprint and a bronze bar into a copy of the key.")
    }

    private companion object {
        private const val RopePrice = 15

        private const val WitchFine = 20

        private const val DyePrice = 5

        private const val RedDye = "obj.reddye"
        private const val OrangeDye = "obj.orangedye"

        private const val WitchFineSound = "synth.PICK"

        private const val CastKeySound = "synth.FURNACE"

        private const val SmeltAnim = "seq.human_furnace"

        private const val SmeltTicks = 2

        private const val KeyConfirmTitle = "Create a key using the key print?"

        private val MET_NED = QuestVar.boolean("MET_NED", "varp.prince_ali_met_ned", legacyKey = "prince_ali_met_ned", migratedVarp = "varp.prince_ali_met_ned_migrated")
    }
}

private enum class AggieDye(
    val dye: String,
    val ingredient: String,
    val ingredientCount: Int,
    val ingredientName: String,
    val colour: String,
) {
    Red("obj.reddye", "obj.redberries", 3, "redberries", "red"),
    Yellow("obj.yellowdye", "obj.onion", 2, "onions", "yellow"),
    Blue("obj.bluedye", "obj.woadleaf", 2, "woad leaves", "blue"),
}

private val ProtectedAccess.nedLadOrLass: String
    get() = if (isBodyTypeA()) "lad" else "lass"
