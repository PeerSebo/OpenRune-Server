package org.rsmod.api.repo.loc

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.game.map.collision.tilesInChebyshevSquare
import org.rsmod.map.CoordGrid

public fun LocRepository.locNearby(
    origin: CoordGrid,
    radius: Int,
    exclude: CoordGrid? = null,
    locTypeInternals: Iterable<String>,
): Boolean {

    val types =
        locTypeInternals.mapNotNull { ServerCacheManager.getObject(it.asRSCM(RSCMType.LOC)) }

    for (t in origin.tilesInChebyshevSquare(radius, exclude)) {
        for (type in types) {
            if (findExact(t, type) != null) {
                return true
            }
        }
    }
    return false
}
