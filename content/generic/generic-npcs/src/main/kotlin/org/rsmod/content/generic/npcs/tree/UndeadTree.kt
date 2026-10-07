package org.rsmod.content.generic.npcs.tree

import org.rsmod.api.script.onApNpc2
import org.rsmod.api.script.onOpNpc2
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class UndeadTree : PluginScript() {
    override fun ScriptContext.startup() {
        for (tree in Trees) {
            onOpNpc2(tree) {}
            onApNpc2(tree) {}
        }
    }

    private companion object {
        private val Trees =
            listOf(
                "npc.nasty_tree",
                "npc.nasty_tree_unchoppable",
                "npc.nasty_tree_choppable",
                "npc.nasty_tree_swamp",
            )
    }
}
