package org.rsmod.content.bosses.vardorvis

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirementResolver
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VardorvisAccess @Inject constructor(
    private val locs: LocRepository,
    private val requirements: QuestRequirementResolver,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup {
            if (!opensEntry(requirements.policy)) return@onGameStartup
            val entryIds = setOf(BASE_ENTRY.asRSCM(), NO_OPTION_ENTRY.asRSCM())
            for (x in 0 until ZONES_PER_REGION) {
                for (z in 0 until ZONES_PER_REGION) {
                    val zone = ZoneKey(REGION_ZONE_X + x, REGION_ZONE_Z + z, 0)
                    for (loc in locs.findAll(zone).filter { it.id in entryIds }.toList()) {
                        locs.add(openEntry(loc), Int.MAX_VALUE)
                    }
                }
            }
        }
    }

    companion object {
        internal fun openEntry(loc: LocInfo): LocInfo =
            LocInfo(loc.layer, loc.coords, LocEntity(OPEN_ENTRY.asRSCM(), loc.shape.id, loc.angle.id))

        internal fun opensEntry(policy: QuestRequirementPolicy): Boolean = when (policy.mode) {
            QuestRequirementMode.Optional, QuestRequirementMode.AssumeCompleted -> true
            QuestRequirementMode.VirtualCompletions -> policy.isVirtuallyCompleted(QUEST)
            QuestRequirementMode.RespectProgress -> false
        }

        private const val QUEST = "quest_deserttreasure2"
        private const val BASE_ENTRY = "loc.dt2_stranglewood_boss_entry"
        private const val NO_OPTION_ENTRY = "loc.dt2_stranglewood_boss_entry_noop"
        private const val OPEN_ENTRY = "loc.dt2_stranglewood_boss_entry_op"
        private const val REGION_ZONE_X = 17 * 8
        private const val REGION_ZONE_Z = 53 * 8
        private const val ZONES_PER_REGION = 8
    }
}
