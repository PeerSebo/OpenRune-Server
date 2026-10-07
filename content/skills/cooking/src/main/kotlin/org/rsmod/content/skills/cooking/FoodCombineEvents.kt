package org.rsmod.content.skills.cooking

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FoodCombineEvents @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {

    private data class Combine(
        val first: String,
        val second: String,
        val level: Int,
        val xp: Double,
        val output: String,
        val knife: Boolean = false,
        val extraOutput: String? = null,
        val message: String,
    )

    private val combines =
        listOf(
            Combine(
                first = "obj.bowl_empty", second = "obj.egg",
                level = 13, xp = 0.0, output = "obj.bowl_egg_raw",
                message = "You crack the egg into the bowl.",
            ),
            Combine(
                first = "obj.bowl_egg_scrambled", second = "obj.tomato",
                level = 23, xp = 0.0, output = "obj.bowl_egg+tomato",
                message = "You add the tomato to the scrambled egg.",
            ),
            Combine(
                first = "obj.bowl_empty", second = "obj.garlic",
                level = 1, xp = 0.0, output = "obj.bowl_garlic", knife = true,
                message = "You chop the garlic into the bowl.",
            ),
            Combine(
                first = "obj.bowl_garlic", second = "obj.gnome_spice",
                level = 9, xp = 25.0, output = "obj.bowl_chilli",
                message = "You mix the spice with the chopped garlic.",
            ),
            Combine(
                first = "obj.bowl_empty", second = "obj.cooked_meat",
                level = 1, xp = 0.0, output = "obj.bowl_carne", knife = true,
                message = "You chop the meat into the bowl.",
            ),
            Combine(
                first = "obj.bowl_chilli", second = "obj.cooked_meat",
                level = 11, xp = 0.0, output = "obj.bowl_chilli+carne", knife = true,
                message = "You mix the meat with the spicy sauce.",
            ),
            Combine(
                first = "obj.bowl_chilli", second = "obj.bowl_carne",
                level = 11, xp = 0.0, output = "obj.bowl_chilli+carne",
                extraOutput = "obj.bowl_empty",
                message = "You mix the meat with the spicy sauce.",
            ),
            Combine(
                first = "obj.bowl_empty", second = "obj.bittercap_mushroom",
                level = 1, xp = 0.0, output = "obj.bowl_mushroom_raw", knife = true,
                message = "You slice the mushrooms into the bowl.",
            ),
            Combine(
                first = "obj.bowl_empty", second = "obj.onion",
                level = 1, xp = 0.0, output = "obj.bowl_onion", knife = true,
                message = "You chop the onion into the bowl.",
            ),
            Combine(
                first = "obj.bowl_mushroom_fried", second = "obj.bowl_onion_fried",
                level = 57, xp = 0.0, output = "obj.bowl_mushroom+onion",
                message = "You combine the fried mushrooms and onions.",
            ),
            Combine(
                first = "obj.bowl_empty", second = "obj.tuna",
                level = 1, xp = 0.0, output = "obj.bowl_tuna", knife = true,
                message = "You chop the tuna into the bowl.",
            ),
            Combine(
                first = "obj.bowl_empty", second = "obj.sweetcorn_cooked",
                level = 1, xp = 0.0, output = "obj.bowl_sweetcorn",
                message = "You put the sweetcorn into the bowl.",
            ),
            Combine(
                first = "obj.bowl_sweetcorn", second = "obj.tuna",
                level = 67, xp = 0.0, output = "obj.bowl_tuna+sweetcorn", knife = true,
                message = "You mix the tuna with the sweetcorn.",
            ),
            Combine(
                first = "obj.bowl_sweetcorn", second = "obj.bowl_tuna",
                level = 67, xp = 0.0, output = "obj.bowl_tuna+sweetcorn",
                extraOutput = "obj.bowl_empty",
                message = "You mix the tuna with the sweetcorn.",
            ),
            Combine(
                first = "obj.sweetcorn_cooked", second = "obj.bowl_tuna",
                level = 67, xp = 0.0, output = "obj.bowl_tuna+sweetcorn",
                message = "You mix the tuna with the sweetcorn.",
            ),
            Combine(
                first = "obj.potato_baked", second = "obj.pot_of_butter",
                level = 39, xp = 40.0, output = "obj.potato_butter",
                message = "You add the butter to the baked potato.",
            ),
            Combine(
                first = "obj.potato_butter", second = "obj.cheese",
                level = 47, xp = 40.0, output = "obj.potato_cheese",
                message = "You add the cheese to the potato.",
            ),
            Combine(
                first = "obj.potato_butter", second = "obj.bowl_chilli+carne",
                level = 41, xp = 15.0, output = "obj.potato_chilli+carne",
                extraOutput = "obj.bowl_empty",
                message = "You add the chilli con carne to the potato.",
            ),
            Combine(
                first = "obj.potato_butter", second = "obj.bowl_mushroom+onion",
                level = 64, xp = 55.0, output = "obj.potato_mushroom+onion",
                message = "You add the mushroom and onion to the potato.",
            ),
            Combine(
                first = "obj.potato_butter", second = "obj.bowl_tuna+sweetcorn",
                level = 68, xp = 10.0, output = "obj.potato_tuna+sweetcorn",
                extraOutput = "obj.bowl_empty",
                message = "You add the tuna and sweetcorn to the potato.",
            ),
            Combine(
                first = "obj.potato_butter", second = "obj.bowl_egg+tomato",
                level = 51, xp = 45.0, output = "obj.potato_egg+tomato",
                message = "You add the egg and tomato to the potato.",
            ),
        )

    override fun ScriptContext.startup() {
        combines.forEach { combine ->
            onOpHeldU(combine.first, combine.second) { runCombine(combine) }
        }
    }

    private fun ProtectedAccess.runCombine(combine: Combine) {
        if (combine.knife && !inv.contains("obj.knife")) {
            mes("You need a knife to do that.")
            return
        }
        if (player.cookingLvl < combine.level) {
            mes("You need a Cooking level of ${combine.level} to make this.")
            return
        }
        if (invDel(inv, combine.first, 1).failure) {
            return
        }
        if (invDel(inv, combine.second, 1).failure) {
            invAdd(inv, combine.first, 1)
            return
        }
        // Two consumed inputs free enough slots for both outputs.
        invAdd(inv, combine.output, 1)
        combine.extraOutput?.let { invAdd(inv, it, 1) }
        if (combine.xp > 0) {
            val xpModifier = xpMods.get(player, "stat.cooking")
            statAdvance("stat.cooking", combine.xp * xpModifier)
        }
        mes(combine.message)
    }
}
