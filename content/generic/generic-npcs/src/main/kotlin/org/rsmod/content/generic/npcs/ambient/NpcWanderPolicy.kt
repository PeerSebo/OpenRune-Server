package org.rsmod.content.generic.npcs.ambient

import dev.openrune.ServerCacheManager
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import org.rsmod.api.script.onGameStartup
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NpcWanderPolicy : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { apply(ServerCacheManager.getNpcs().values) }
    }

    internal fun apply(types: Collection<NpcServerType>) {
        val byId = types.associateBy { it.id }
        val shops = types.filter(::hasShopOption).mapTo(mutableSetOf()) { it.id }
        var expanded: Boolean
        do {
            expanded = false
            for (type in types) {
                if (type.id in shops) continue
                if (type.multiDefault in shops || type.transforms.orEmpty().any { it in shops }) {
                    expanded = shops.add(type.id) || expanded
                }
            }
        } while (expanded)

        for (type in byId.values) {
            if (type.defaultMode != NpcMode.Wander || type.moveRestrict == MoveRestrict.NoMove) continue
            if (type.waypoints.isNotEmpty() || type.patrol != null || type.wanderRange <= 0) continue
            type.wanderRange = minOf(type.wanderRange, if (type.id in shops) 3 else 4)
        }
    }

    private fun hasShopOption(type: NpcServerType): Boolean =
        (0 until 5).any { slot ->
            val op = type.actions.getOpOrNull(slot) ?: return@any false
            op.equals("Shop", ignoreCase = true) ||
                op.equals("Trade", ignoreCase = true) ||
                op.startsWith("Trade-", ignoreCase = true)
        }
}
