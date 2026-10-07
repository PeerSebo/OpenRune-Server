package org.rsmod.content.areas.city.draynor

import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.obj
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpWorn2
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiangoScarf : PluginScript() {
    override fun ScriptContext.startup() {
        for ((type, _) in styles) {
            onOpHeld3(type) { changeStyle(it.inventory, it.slot) }
            onOpWorn2(type.internalName) { changeStyle(worn, it.slot) }
        }
    }

    private suspend fun ProtectedAccess.changeStyle(inventory: Inventory, slot: Int) {
        val original = inventory[slot] ?: return
        val selected = menu("Choose a scarf style", hotkeys = true,
            choices = styles.map { it.second } + "Cancel")
        val replacement = styles.getOrNull(selected)?.first ?: return
        if (inventory[slot] !== original) return
        if (invReplaceSlot(inventory, slot, count = 1, replacement = replacement).failure) return
        if (inventory === worn) rebuildAppearance()
    }

    internal companion object {
        val styles by lazy {
            DbHelper.table("dbtable.diango_scarf_styles")
                .sortedBy { it.int("dbcol.diango_scarf_styles:order") }
                .map { it.obj("dbcol.diango_scarf_styles:item") to
                    it.column("dbcol.diango_scarf_styles:name", StringCodec) }
        }
    }
}
