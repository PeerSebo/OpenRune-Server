package org.rsmod.api.death

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public data class NpcDeathKillContext(
    public val hero: Player,
    public val npc: Npc,
    public val lootTrackerEventId: Int,
    public val dropCoords: CoordGrid = npc.coords,
)

public fun interface NpcDeathKillHook {
    public fun onKill(context: NpcDeathKillContext)
}
