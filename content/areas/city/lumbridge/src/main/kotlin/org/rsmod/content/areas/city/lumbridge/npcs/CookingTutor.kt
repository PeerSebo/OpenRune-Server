package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CookingTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_cooking") { startDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc): Unit =
        startDialogue(npc) { greeting() }

    private suspend fun Dialogue.greeting() {
        chatPlayer(neutral, "Hello.")
        chatNpc(happy, "Hello there! Are you interested in hearing all about cooking?")
        mainMenu()
    }

    private suspend fun Dialogue.mainMenu() {
        // Loop the menu; recursive suspending dialogue would retain a frame on every round trip.
        var lastAnswered: MainTopic? = null
        while (true) {
            val shown = MainTopic.entries.filter { it != lastAnswered }
            val picked = selectTopic(shown.map(MainTopic::label) + DECLINE)
            val topic = shown.getOrNull(picked - 1)
            if (topic == null) {
                farewell()
                return
            }
            chatPlayer(quiz, topic.label)
            when (topic) {
                MainTopic.Training -> trainingAdvice()
                MainTopic.Dishes -> dishes()
            }
            chatNpc(quiz, "Is there anything else you want to know?")
            lastAnswered = topic
        }
    }

    private suspend fun Dialogue.farewell() {
        chatPlayer(neutral, DECLINE)
        chatNpc(happy, "Well, just come back any time you want to know how to cook up a storm!")
    }

    private suspend fun Dialogue.trainingAdvice() {
        chatNpc(
            neutral,
            "The simplest thing to cook is raw meat or fish. Shrimp " +
                "can be caught south of here, to the east of Lumbridge " +
                "Swamp. You could ask the fishing tutor for a net if you " +
                "have not got one.",
        )
        chatNpc(
            happy,
            "Alternatively, you could acquire some raw beef or " +
                "chicken. You can find cows and chickens north of here. " +
                "Go over the bridge then follow the path north, you can't " +
                "miss them!",
        )
        objbox(
            "obj.range_icon_dummy",
            "When you have a full inventory of meat or fish, find a " +
                "range. Look for this icon on your minimap.",
        )
        chatNpc(neutral, "You could use my range here if you like.")
        chatNpc(
            happy,
            "If you wanted to, my friend the cook in Lumbridge Castle " +
                "has a range you could use. You might find you burn even " +
                "less on that range as its top notch.",
        )
        chatNpc(
            happy,
            "Alternatively, you can use your own fire, but it's not as " +
                "effective and the food will burn more frequently. To make " +
                "a fire, use a tinderbox on some logs.",
        )
        chatNpc(
            neutral,
            "Once you've found your range or fire, use your raw food " +
                "on it. This will bring up a menu of the food you can cook. " +
                "Then select the food you want.",
        )
        chatNpc(
            neutral,
            "When you have a full inventory of cooked food, drop the " +
                "useless burnt food and find a bank.",
        )
        objbox(
            "obj.bank_icon_dummy",
            "Look for this symbol on your minimap after climbing the " +
                "stairs of the Lumbridge Castle to the top. There are " +
                "numerous banks around the world, all marked with that " +
                "symbol.",
        )
    }

    private suspend fun Dialogue.dishes() {
        chatNpc(
            happy,
            "Many things! You can cook anything from fish to poultry. " +
                "You could try your hand at making baked potatoes or " +
                "even brew some of the good stuff.",
        )
        chatNpc(happy, "What would you like to hear about?")
        dishMenu()
    }

    private suspend fun Dialogue.dishMenu() {
        var lastAnswered: DishTopic? = null
        while (true) {
            val shown = DishTopic.entries.filter { it != lastAnswered }
            val labels = shown.map(DishTopic::label)
            val picked = selectTopic(if (lastAnswered == null) labels else labels + NOT_RIGHT_NOW)
            val topic = shown.getOrNull(picked - 1)
            if (topic == null) {
                chatPlayer(neutral, NOT_RIGHT_NOW)
                return
            }
            chatPlayer(neutral, topic.label)
            when (topic) {
                DishTopic.FishAndMeat -> fishAndMeat()
                DishTopic.Brewing -> brewing()
                DishTopic.Vegetables -> vegetables()
                DishTopic.PiesAndPizzas -> piesAndPizzas()
            }
            chatNpc(neutral, "Would you like to hear about anything else I mentioned?")
            lastAnswered = topic
        }
    }

    private suspend fun Dialogue.fishAndMeat() {
        doubleobjbox(
            "obj.raw_beef",
            "obj.cooked_meat",
            "Fish and meat of most varieties can be cooked very " +
                "simply on either a fire or range, experiment which one " +
                "works for you.",
        )
        chatNpc(
            neutral,
            "You might find that when killing monsters, they drop some raw meat for you to cook.",
        )
        chatNpc(
            neutral,
            "Some of them might have hidden properties. Such as " +
                "anglerfish, but you will find it hard getting hold of any of " +
                "them at this time.",
        )
        chatNpc(
            neutral,
            "Most of the time, cooking meat and fish requires nothing " +
                "else but a range or fire. More complicated dishes might " +
                "require specialized equipment.",
        )
    }

    private suspend fun Dialogue.brewing() {
        doubleobjbox(
            "obj.dragon_bitter",
            "obj.beer",
            "You can brew your own beers using the fermenting " +
                "vats in either Keldagrim or Port Phasmatys.",
        )
        chatNpc(
            neutral,
            "You might find it hard getting to these places but you can " +
                "always buy beer at any pub. Some pubs sell unique " +
                "beers too.",
        )
    }

    private suspend fun Dialogue.vegetables() {
        // The `+` is part of the gameval key itself (obj 7060), not a typo or a concatenation.
        objbox(
            "obj.potato_tuna+sweetcorn",
            "Baked potatoes are a more advanced food and are healthy too!",
        )
        chatNpc(
            neutral,
            "They require many different ingredients to make, such as " +
                "butter and vegetables, but you will find it hard to acquire " +
                "the ingredients needed.",
        )
    }

    private suspend fun Dialogue.piesAndPizzas() {
        doubleobjbox(
            "obj.bucket_water",
            "obj.pot_flour",
            "Use a pot of flour with a bucket of water. You will then " +
                "need to make some pastry dough.",
        )
        doubleobjbox(
            "obj.piedish",
            "obj.pastry_dough",
            "Use the pastry dough with a pie dish to get a pie shell, " +
                "then add your choice of filling such as apples or red " +
                "berries.",
        )
        chatNpc(
            happy,
            "Finally cook your pie by using the unbaked pie on a cooking range. Mmmm...pie.",
        )
        chatNpc(
            happy,
            "Next, we have pizza. You will first need to make yourself " +
                "a pizza base, use a pot of flour with a bucket of water " +
                "and make a pizza base.",
        )
        doubleobjbox(
            "obj.tomato",
            "obj.cheese",
            "Now you will need to get yourself a tomato and some " +
                "cheese. Use the tomato on the pizza base and then use " +
                "the cheese. Cook the uncooked pizza base and there you " +
                "have yourself a plain pizza.",
        )
        chatNpc(
            happy,
            "If you want to, you could add yourself a topping to the " +
                "pizza too. Why not try some anchovies?",
        )
    }

    private suspend fun Dialogue.selectTopic(labels: List<String>): Int =
        when (labels.size) {
            2 -> choice2(labels[0], 1, labels[1], 2)
            3 -> choice3(labels[0], 1, labels[1], 2, labels[2], 3)
            4 -> choice4(labels[0], 1, labels[1], 2, labels[2], 3, labels[3], 4)
            else -> error("Unexpected cooking tutor topic count: ${labels.size} ($labels)")
        }

    private enum class MainTopic(val label: String) {
        Training("How can I train my cooking?"),
        Dishes("What kinds of things can I cook?"),
    }

    private enum class DishTopic(val label: String) {
        FishAndMeat("Tell me about Fish and Meat."),
        Brewing("Tell me about Brewing."),
        Vegetables("Tell me about Vegetables."),
        PiesAndPizzas("Tell me about Pies and Pizzas."),
    }

    private companion object {
        const val DECLINE = "No, thank you."

        const val NOT_RIGHT_NOW = "Not right now."
    }
}
