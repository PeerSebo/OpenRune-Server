package org.rsmod.api.hunt

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.aconverted.CategoryType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

public class NpcSearch @Inject constructor(private val hunt: Hunt) {
    public fun hunt(center: CoordGrid, distance: Int, vis: HuntVis): Npc? {
        var minDistanceNpc: Npc? = null
        var minDistance = Int.MAX_VALUE

        val npcs = hunt.findNpcs(center, distance, vis)
        for (npc in npcs) {
            if (!npc.type.hasOp(InteractionOp.Op2.slot)) {
                continue
            }
            val distance = center.euclideanSquaredDistance(npc.coords)
            if (distance <= minDistance) {
                minDistanceNpc = npc
                minDistance = distance
            }
        }

        return minDistanceNpc
    }

    public fun huntAll(
        center: CoordGrid,
        type: NpcServerType,
        distance: Int,
        vis: HuntVis,
    ): Sequence<Npc> {
        return sequence {
            val npcs = hunt.findNpcs(center, distance, vis)
            for (npc in npcs) {
                if (npc.id != type.id) {
                    continue
                }
                if (!npc.type.hasOp(InteractionOp.Op2.slot)) {
                    continue
                }
                yield(npc)
            }
        }
    }

    public fun find(center: CoordGrid, type: String, distance: Int, vis: HuntVis): Npc? {
        var minDistanceNpc: Npc? = null
        var minDistance = Int.MAX_VALUE

        val npcs = hunt.findNpcs(center, distance, vis)
        for (npc in npcs) {
            if (npc.id != type.asRSCM(RSCMType.NPC)) {
                continue
            }
            val distance = center.euclideanSquaredDistance(npc.coords)
            if (distance <= minDistance) {
                minDistanceNpc = npc
                minDistance = distance
            }
        }

        return minDistanceNpc
    }

    public fun findCat(center: CoordGrid, type: CategoryType, distance: Int, vis: HuntVis): Npc? {
        var minDistanceNpc: Npc? = null
        var minDistance = Int.MAX_VALUE

        val npcs = hunt.findNpcs(center, distance, vis)
        for (npc in npcs) {
            if (npc.type.category != type.id) {
                continue
            }
            val distance = center.euclideanSquaredDistance(npc.coords)
            if (distance <= minDistance) {
                minDistanceNpc = npc
                minDistance = distance
            }
        }

        return minDistanceNpc
    }

    public fun findAll(
        center: CoordGrid,
        type: String,
        distance: Int,
        vis: HuntVis,
    ): Sequence<Npc> {
        return hunt.findNpcs(center, distance, vis).filter { it.id == type.asRSCM(RSCMType.NPC) }
    }

    public fun findAllZone(center: CoordGrid, distance: Int, vis: HuntVis): Sequence<Npc> {
        val zone = ZoneKey.from(center)
        return hunt.findNpcs(center, distance, vis).filter { ZoneKey.from(it.coords) == zone }
    }

    public fun findAllAny(center: CoordGrid, distance: Int, vis: HuntVis): Sequence<Npc> {
        return hunt.findNpcs(center, distance, vis)
    }

    private fun CoordGrid.euclideanSquaredDistance(other: CoordGrid): Int {
        val dx = abs(x - other.x)
        val dz = abs(z - other.z)
        return dx * dx + dz * dz
    }
}
