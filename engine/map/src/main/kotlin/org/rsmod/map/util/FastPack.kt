package org.rsmod.map.util

import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey

private typealias MsKey = MapSquareKey

public object FastPack {
    /**
     * Unchecked packing: callers must guarantee valid coordinates; prefer ZoneKey.from
     * otherwise.
     */
    public fun zoneKey(coords: CoordGrid): Int =
        (((coords.x / ZoneGrid.LENGTH) and ZoneKey.X_BIT_MASK) shl ZoneKey.X_BIT_OFFSET) or
            (((coords.z / ZoneGrid.LENGTH) and ZoneKey.Z_BIT_MASK) shl ZoneKey.Z_BIT_OFFSET) or
            ((coords.level and ZoneKey.LEVEL_BIT_MASK) shl ZoneKey.LEVEL_BIT_OFFSET)

    /**
     * Unchecked packing: callers must guarantee valid coordinates; prefer MapSquareKey.from
     * otherwise.
     */
    public fun mapSquareKey(coords: CoordGrid): Int =
        ((coords.x / MapSquareGrid.LENGTH) and MsKey.X_BIT_MASK shl MsKey.X_BIT_OFFSET) or
            ((coords.z / MapSquareGrid.LENGTH) and MsKey.Z_BIT_MASK shl MsKey.Z_BIT_OFFSET)
}
