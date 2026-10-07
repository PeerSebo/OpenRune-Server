package org.rsmod.content.skills.cooking

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StewEvents : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU("obj.bowl_water", "obj.potato") { addPotato() }
        onOpHeldU("obj.stew1", "obj.cooked_meat") { addMeat("obj.cooked_meat") }
        onOpHeldU("obj.stew1", "obj.cooked_chicken") { addMeat("obj.cooked_chicken") }
        onOpHeldU("obj.uncooked_stew", "obj.curry_leaf") { addCurryFlavour("obj.curry_leaf", 3) }
        onOpHeldU("obj.uncooked_stew", "obj.spicespot") { addCurryFlavour("obj.spicespot", 1) }
    }

    private fun ProtectedAccess.addPotato() {
        invDel(inv, "obj.bowl_water", 1)
        invDel(inv, "obj.potato", 1)
        invAdd(inv, "obj.stew1", 1)
        mes("You add the potato to the bowl of water.")
    }

    private fun ProtectedAccess.addMeat(meat: String) {
        invDel(inv, "obj.stew1", 1)
        invDel(inv, meat, 1)
        invAdd(inv, "obj.uncooked_stew", 1)
        mes("You add the meat to the stew.")
    }

    private fun ProtectedAccess.addCurryFlavour(flavour: String, count: Int) {
        if (player.cookingLvl < 60) {
            mes("You need a Cooking level of 60 to make a curry.")
            return
        }
        if (inv.count(flavour) < count) {
            mes("You need three curry leaves to flavour the stew.")
            return
        }
        // Replacing the stew always frees the curry's slot.
        if (invDel(inv, flavour, count).failure) {
            return
        }
        if (invDel(inv, "obj.uncooked_stew", 1).failure) {
            invAdd(inv, flavour, count)
            return
        }
        invAdd(inv, "obj.uncooked_curry", 1)
        mes(
            if (flavour == "obj.curry_leaf") {
                "You add the curry leaves to the stew."
            } else {
                "You add the spice to the stew."
            }
        )
    }
}
