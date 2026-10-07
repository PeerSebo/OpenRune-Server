package org.rsmod.content.bosses.kalphitequeen

import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

internal fun CollisionFlagMap.queenTileBlocked(tile: CoordGrid): Boolean =
    this[tile.x, tile.z, tile.level] and
        (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) != 0
