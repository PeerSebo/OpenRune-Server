package org.rsmod.content.areas.city.draynor

import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpWorn2
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiangoTreeHuggingRank : PluginScript() {
    override fun ScriptContext.startup() {
        for (item in listOf("obj.wreath", "obj.summer2015_mask")) {
            onOpHeld3(item) { mes("Your tree-hugging rank is: What's a tree?") }
            onOpWorn2(item) { mes("Your tree-hugging rank is: What's a tree?") }
        }
    }
}
