package org.rsmod.content.generic.npcs.person

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RowdyDwarf : PluginScript() {
    override fun ScriptContext.startup() {
        onProtectedEvent<NpcUnimplementedEvents.Op1>("npc.dwarf_city_rowdy_dwarf".asRSCM()) {
            startDialogue(it.npc) { chatNpc(angry, "Get OUT of my WAY!") }
        }
    }
}
