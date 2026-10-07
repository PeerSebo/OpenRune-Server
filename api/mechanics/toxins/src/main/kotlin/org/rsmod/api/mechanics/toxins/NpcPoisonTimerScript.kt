package org.rsmod.api.mechanics.toxins

import org.rsmod.api.mechanics.toxins.impl.NpcPoison
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcTimer
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class NpcPoisonTimerScript : PluginScript() {
    override fun ScriptContext.startup() {
        onNpcTimer(NpcPoison.TIMER) { NpcPoison.onTimerTick(npc) }
        onEvent<NpcStateEvents.Respawn> { NpcPoison.clear(npc) }
    }
}
