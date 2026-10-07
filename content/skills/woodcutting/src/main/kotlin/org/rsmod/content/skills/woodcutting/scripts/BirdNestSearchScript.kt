package org.rsmod.content.skills.woodcutting.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.skills.woodcutting.configs.BirdNests
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BirdNestSearchScript : PluginScript() {

    override fun ScriptContext.startup() {
        for (nest in BirdNests.SEARCHABLE) {
            onOpHeld1(nest) { searchNest(it) }
        }
    }

    private fun ProtectedAccess.searchNest(ev: HeldObjEvents.Op1) {
        if (inv.isFull()) {
            mes("You don't have enough inventory space to do that.")
            return
        }

        val contents = BirdNests.rollContents(ev.type.internalName, random) ?: return
        val emptyNest =
            checkNotNull(ServerCacheManager.getItem(BirdNests.EMPTY.asRSCM(RSCMType.OBJ))) {
                "Missing item type for `${BirdNests.EMPTY}`."
            }

        if (invReplaceSlot(inv, ev.slot, 1, emptyNest).failure) {
            return
        }
        if (invAdd(inv, contents, 1).failure) {
            invReplaceSlot(inv, ev.slot, 1, ev.type)
            return
        }

        val name = ServerCacheManager.getItem(contents.asRSCM(RSCMType.OBJ))?.name?.lowercase()
        val article = if (name != null && name.first() in "aeiou") "an" else "a"
        mes("You take $article ${name ?: "item"} out of the bird's nest.")
    }
}
