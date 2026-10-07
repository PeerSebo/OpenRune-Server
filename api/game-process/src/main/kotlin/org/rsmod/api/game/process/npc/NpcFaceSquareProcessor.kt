package org.rsmod.api.game.process.npc

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.map.CoordGrid

public class NpcFaceSquareProcessor {
    public fun process(npc: Npc) {
        npc.processFaceSquare()
    }

    private fun Npc.processFaceSquare() {
        if (isFacingLocked) {
            val angle = calculateAngle(faceLockSquare, faceLockWidth, faceLockLength)
            // Recompute moving-target bearings each cycle, but transmit only changed angles.
            val resolved = EntityFaceAngle.fromOrNull(angle)
            if (resolved != EntityFaceAngle.NULL && resolved.intValue != lastTransmittedFaceAngle) {
                pendingFaceAngle = resolved
                lastTransmittedFaceAngle = resolved.intValue
            }
            resetPendingFaceSquare()
            return
        }
        lastTransmittedFaceAngle = Int.MIN_VALUE
        if (!hasMovedThisCycle && pendingFaceSquare != CoordGrid.NULL) {
            val angle = calculateAngle(pendingFaceSquare, pendingFaceWidth, pendingFaceLength)
            pendingFaceAngle = EntityFaceAngle.fromOrNull(angle)
            resetPendingFaceSquare()
        }
    }
}
