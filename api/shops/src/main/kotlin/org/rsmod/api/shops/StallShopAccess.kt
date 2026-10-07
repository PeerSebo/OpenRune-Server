package org.rsmod.api.shops

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public object StallShopAccess {
    private val thefts = AttributeKey<MutableMap<String, Int>>(persistenceKey = "shop_stall_thefts")
    private val Player.playtime by intVarp("varp.playtime")

    public fun record(player: Player, stall: String, coords: CoordGrid) {
        val recent = player.attr.getOrPut(thefts) { mutableMapOf() }
        recent.entries.removeIf { player.playtime - it.value >= 3_000 }
        recent["$stall:${coords.packed}"] = player.playtime
        player.attr[thefts] = recent
    }

    public fun canTrade(
        player: Player,
        merchant: CoordGrid,
        stalls: Set<String>,
        refusalCycles: Int = 1_000,
    ): Boolean {
        val recent = player.attr[thefts] ?: return true
        return recent.none { (key, stolenAt) ->
            val separator = key.lastIndexOf(':')
            val stall = key.substring(0, separator)
            val packed = key.substring(separator + 1).toIntOrNull()
            val coords = packed?.let(::CoordGrid)
            stall in stalls && coords != null && coords.level == merchant.level &&
                coords.chebyshevDistance(merchant) <= 16 &&
                player.playtime - stolenAt < refusalCycles
        }
    }
}
