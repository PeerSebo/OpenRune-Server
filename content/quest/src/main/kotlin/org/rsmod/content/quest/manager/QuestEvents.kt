package org.rsmod.content.quest.manager

import dev.openrune.definition.type.widget.IfEvent
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.table.QuestRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class QuestEvents : PluginScript() {

    private var questCount: Int = 0

    override fun ScriptContext.startup() {
        val rows = QuestRow.all()
        questCount = rows.size

        // Arm quest-list events after the matching interface opens; out-of-range ends invalidate the whole range.
        onIfOpen("interface.questlist") {
            player.ifSetEvents(
                "component.questlist:list",
                0..questCount,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op4,
                IfEvent.Op5,
            )
        }
    }
}
