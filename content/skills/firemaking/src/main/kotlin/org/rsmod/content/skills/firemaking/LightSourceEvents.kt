package org.rsmod.content.skills.firemaking

import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.table.LightSourcesRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LightSourceEvents : PluginScript() {

    override fun ScriptContext.startup() {
        LightSourcesRow.all().forEach { row ->
            onOpHeldU("obj.tinderbox", row.unlit) { light(row) }
            registerExtinguish(row)
        }
    }

    // Extinguish uses different inventory-op slots across light-source variants.
    private fun ScriptContext.registerExtinguish(source: LightSourcesRow) {
        val op = source.lit.interfaceOptions.indexOf("Extinguish") + 1
        when (op) {
            1 -> onOpHeld1(source.lit) { extinguish(source) }
            2 -> onOpHeld2(source.lit) { extinguish(source) }
            3 -> onOpHeld3(source.lit) { extinguish(source) }
            4 -> onOpHeld4(source.lit) { extinguish(source) }
            else ->
                error(
                    "obj.${source.lit.internalName} has no 'Extinguish' interfaceOption " +
                        "(${source.lit.interfaceOptions})",
                )
        }
    }

    private fun ProtectedAccess.light(source: LightSourcesRow) {
        if (player.firemakingLvl < source.level) {
            mes("You need a Firemaking level of ${source.level} to light this.")
            return
        }

        if (invReplace(inv, source.unlit.internalName, 1, source.lit.internalName).success) {
            mes("You light the ${source.unlit.name}.")
            publish(PlayerActionCompleteEvent(player, PlayerAction.LightSource, source.lit.internalName))
        }
    }

    private fun ProtectedAccess.extinguish(source: LightSourcesRow) {
        if (invReplace(inv, source.lit.internalName, 1, source.unlit.internalName).success) {
            mes("You extinguish the light source.")
        }
    }
}
