package org.rsmod.content.generic.npcs.person

import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Barbarian : PluginScript() {
    override fun ScriptContext.startup() {
        for (npc in VillageBarbarians) {
            onOpNpc1(npc) { startDialogue(it.npc) { chatNpc(angry, "Grrr!") } }
        }
    }

    companion object {
        internal val VillageBarbarians = listOf(
            "npc.fai_barbarian_1", "npc.fai_barbarian_2", "npc.fai_barbarian_3",
            "npc.fai_barbarian_4", "npc.fai_barbarian_5", "npc.fai_barbarian_6",
            "npc.fai_barbarian_7", "npc.fai_barbarian_8", "npc.fai_barbarian_10",
            "npc.fai_barbarian_11", "npc.fai_barbarian_12", "npc.fai_barbarian_13",
            "npc.fai_barbarian_14", "npc.fai_barbarian_female_1", "npc.fai_barbarian_female_2",
            "npc.fai_barbarian_female_3", "npc.fai_barbarian_female_4",
        )
    }
}
