package org.rsmod.content.skills.fishing.scripts

import org.rsmod.api.script.*
import org.rsmod.content.skills.fishing.FishingHarpoonCharges
import org.rsmod.content.skills.fishing.FishingHarpoonCharges.Kind
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HarpoonChargesScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (kind in Kind.entries) {
            if (kind == Kind.INFERNAL || kind == Kind.CRYSTAL) {
                onOpHeld3(kind.active) { mes("Your harpoon has ${it.inventory[it.slot]?.let(FishingHarpoonCharges::charges) ?: 0} charges remaining.") }
            } else {
                onOpHeld2(kind.active) { mes("Your harpoon has ${it.inventory[it.slot]?.let(FishingHarpoonCharges::charges) ?: 0} charges remaining.") }
            }
            onOpWorn2(kind.active) { mes("Your harpoon has ${FishingHarpoonCharges.charges(it.obj)} charges remaining.") }
            for (item in listOf(kind.active, kind.inactive)) {
                val materials = if (kind.crystal) listOf("obj.prif_crystal_shard") else listOf("obj.smouldering_stone", "obj.dragon_harpoon")
                for (material in materials) onOpHeldU(item, material) {
                    val selected = FishingHarpoonCharges.select(player, inv[it.firstSlot]) ?: return@onOpHeldU
                    if (kind.crystal) {
                        val maximum = minOf(inv.count(material), (kind.maximum - FishingHarpoonCharges.charges(selected.item) + 99) / 100)
                        if (maximum <= 0) { mes("Your crystal harpoon is already fully charged."); return@onOpHeldU }
                        val amount = if (maximum == 1) 1 else countDialog("How many crystal shards would you like to use? (Up to $maximum)")
                        FishingHarpoonCharges.recharge(player, selected, material, amount)
                    } else FishingHarpoonCharges.recharge(player, selected, material)
                }
            }
        }
    }
}
