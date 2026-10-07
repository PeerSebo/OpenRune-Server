package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.configs.BirdhouseParams
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BirdhouseClueNestScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in BirdhouseClues.Tier.entries) onOpHeld1(tier.nest) { search(tier, it.slot) }
    }

    internal fun ProtectedAccess.search(tier: BirdhouseClues.Tier, slot: Int) {
        if (!inv[slot].isType(tier.nest)) return
        val boxed = QuestRequirements.hasCompleted(player, "quest_xmarksthespot")
        if (!boxed && BirdhouseClues.hasClue(player, tier, includeContainers = false)) {
            mes("You already have a clue of this level.")
            return
        }
        val empty = checkNotNull(ServerCacheManager.getItem(BirdhouseParams.EMPTY_NEST.asRSCM()))
        if (!invReplaceSlot(inv, slot, 1, empty).success) return
        invAddOrDrop(objRepo, if (boxed) tier.box else tier.scroll, 1)
        mes("You find a clue in the bird's nest.")
    }
}
