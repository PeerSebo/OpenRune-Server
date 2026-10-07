package org.rsmod.content.areas.city.draynor.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AggieDyes : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc3("npc.aggie") { startDialogue(it.npc) { aggieDyes() } }
    }

    private suspend fun Dialogue.aggieDyes() {
        chatNpc(quiz, "What sort of dye would you like? Red, yellow or blue?")
        dyeMenu(includeDecline = false)
    }

    private suspend fun Dialogue.dyeMenu(includeDecline: Boolean) {
        val options = buildList {
            for (dye in Dye.entries) add("What do you need to make ${dye.colour} dye?" to dye)
            if (includeDecline) add("No thanks, I am happy the colour I am." to null)
        }
        val dye = menu(options)
        if (dye == null) {
            chatPlayer(neutral, "No thanks, I am happy the colour I am.")
            chatNpc(
                shifty,
                "You are easily pleased with yourself then. When you need dyes, come to me.",
            )
            return
        }
        dyeRecipe(dye)
    }

    private suspend fun Dialogue.dyeRecipe(dye: Dye) {
        chatPlayer(quiz, "What do you need to make ${dye.colour} dye?")
        chatNpc(neutral, dye.recipe)
        val choice =
            menu(
                "Okay, make me some ${dye.colour} dye please." to DyeChoice.Make,
                "I don't think I have all the ingredients yet." to DyeChoice.NotYet,
                "I can do without dye at that price." to DyeChoice.TooDear,
                "Where do I get ${dye.ingredientName}?" to DyeChoice.Where,
                "What other colours can you make?" to DyeChoice.Others,
            )
        when (choice) {
            DyeChoice.Make -> makeDye(dye)
            DyeChoice.NotYet -> {
                chatPlayer(neutral, "I don't think I have all the ingredients yet.")
                chatNpc(
                    neutral,
                    "You know what you need to get, now come back when you have them. Goodbye " +
                        "for now.",
                )
            }
            DyeChoice.TooDear -> {
                chatPlayer(neutral, "I can do without dye at that price.")
                chatNpc(
                    neutral,
                    "That's your choice, but I would think you have killed for less. I can see " +
                        "it in your eyes.",
                )
            }
            DyeChoice.Where -> {
                chatPlayer(quiz, "Where do I get ${dye.ingredientName}?")
                chatNpc(neutral, dye.whereToFind)
                if (menu("What other colours can you make?" to true, "Thanks" to false)) {
                    otherColours()
                } else {
                    chatPlayer(happy, "Thanks.")
                    chatNpc(happy, "You're welcome!")
                }
            }
            DyeChoice.Others -> otherColours()
        }
    }

    private suspend fun Dialogue.otherColours() {
        chatPlayer(quiz, "What other colours can you make?")
        chatNpc(neutral, "Red, yellow and blue. Which one would you like?")
        dyeMenu(includeDecline = false)
    }

    private suspend fun Dialogue.makeDye(dye: Dye) {
        chatPlayer(happy, "Okay, make me some ${dye.colour} dye please.")
        val inv = access.inv
        if (inv.count(dye.ingredient) < dye.ingredientCount) {
            mesbox("You don't have enough ${dye.shortName} to make the ${dye.colour} dye.")
            return
        }
        if (inv.count(COINS) < DYE_PRICE) {
            mesbox("You don't have enough coins to pay for the dye.")
            return
        }
        access.invDel(inv, dye.ingredient, dye.ingredientCount)
        access.invDel(inv, COINS, DYE_PRICE)
        access.invAdd(inv, dye.obj)
        objbox(
            dye.obj,
            "You hand the ${dye.shortName} and payment to Aggie. Aggie takes a ${dye.colour} " +
                "bottle from nowhere and hands it to you.",
        )
    }

    private enum class Dye(
        val colour: String,
        val obj: String,
        val ingredient: String,
        val ingredientCount: Int,
        val ingredientName: String,
        val shortName: String,
        val recipe: String,
        val whereToFind: String,
    ) {
        Red(
            colour = "red",
            obj = "obj.reddye",
            ingredient = "obj.redberries",
            ingredientCount = 3,
            ingredientName = "redberries",
            shortName = "berries",
            recipe = "3 lots of redberries and 5 coins to you.",
            whereToFind =
                "I pick mine from the woods south of Varrock. The food shop in Port Sarim " +
                    "sometimes has some as well.",
        ),
        Yellow(
            colour = "yellow",
            obj = "obj.yellowdye",
            ingredient = "obj.onion",
            ingredientCount = 2,
            ingredientName = "onions",
            shortName = "onions",
            recipe =
                "Yellow is a strange colour to get, comes from onion skins. I need 2 onions and " +
                    "5 coins to make yellow dye.",
            whereToFind =
                "There are some onions growing on a farm to the East of here, next to the sheep " +
                    "field.",
        ),
        Blue(
            colour = "blue",
            obj = "obj.bluedye",
            ingredient = "obj.woadleaf",
            ingredientCount = 2,
            ingredientName = "woad leaves",
            shortName = "woad leaves",
            recipe = "2 woad leaves and 5 coins to you.",
            whereToFind =
                "Woad leaves are fairly hard to find. My other customers tell me the chief " +
                    "gardener in Falador grows them.",
        ),
    }

    private enum class DyeChoice {
        Make,
        NotYet,
        TooDear,
        Where,
        Others,
    }

    private companion object {
        const val COINS = "obj.coins"
        const val DYE_PRICE = 5
    }
}
