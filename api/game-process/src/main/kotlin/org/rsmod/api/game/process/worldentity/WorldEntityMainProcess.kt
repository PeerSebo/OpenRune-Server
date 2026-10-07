package org.rsmod.api.game.process.worldentity

import jakarta.inject.Inject
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.seq.EntitySeq

/**
 * Update world-entity avatars before player info so deck changes and root-world movement share
 * the same cycle.
 */
public class WorldEntityMainProcess
@Inject
constructor(private val worldEntities: WorldEntityRegistry) {
    public fun process() {
        worldEntities.forEach { it.syncAvatar() }
    }

    private fun WorldEntity.syncAvatar() {
        if (!infoProtocol.isActive()) {
            return
        }
        infoProtocol.updateCoord(projectedLevel, fineX, fineZ, teleport = false)
        infoProtocol.updateAngle(angle)
        applyAnim()
    }

    private fun WorldEntity.applyAnim() {
        when (pendingSequence) {
            EntitySeq.NULL -> return
            EntitySeq.ZERO -> infoProtocol.setSequence(-1, 0)
            else -> infoProtocol.setSequence(pendingSequence.id, pendingSequence.delay)
        }
        pendingSequence = EntitySeq.NULL
    }
}
