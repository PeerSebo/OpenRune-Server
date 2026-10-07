package org.rsmod.game.entity.util

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Translation
import org.rsmod.routefinder.collision.CollisionFlagMap

public object PathingEntityCommon {
    // Internal encoding limit; changing NpcList capacity must not change this value.
    internal const val INTERNAL_NPC_LIMIT = 65535

    public fun telejump(entity: PathingEntity, collision: CollisionFlagMap, dest: CoordGrid) {
        telemove(entity, collision, dest)
        entity.pendingTelejump = true
        entity.moveSpeed = MoveSpeed.Stationary
    }

    public fun teleport(entity: PathingEntity, collision: CollisionFlagMap, dest: CoordGrid) {
        telemove(entity, collision, dest)
        entity.pendingTeleport = true
        entity.moveSpeed = MoveSpeed.Walk
        entity.lastMovement = entity.currentMapClock
    }

    private fun telemove(entity: PathingEntity, collision: CollisionFlagMap, dest: CoordGrid) {
        check(collision.isZoneValid(dest)) {
            "Entity cannot be moved to an invalid zone: entity=$entity, dest=$dest"
        }

        val start = entity.coords
        entity.coords = dest
        entity.lastProcessedCoord = CoordGrid.NULL

        entity.abortRoute()
        entity.clearInteraction()
        // Move collision immediately so later entities in this cycle see the new position.
        collision.move(entity, start, dest)
    }

    private fun CollisionFlagMap.move(entity: PathingEntity, from: CoordGrid, to: CoordGrid) {
        if (isZoneValid(from)) {
            entity.removeBlockWalkCollision(this, from)
        }
        entity.addBlockWalkCollision(this, to)
    }

    public fun exactMove(
        entity: PathingEntity,
        start: CoordGrid,
        end: CoordGrid,
        delay1: Int,
        delay2: Int,
        dir: Int,
        collision: CollisionFlagMap,
    ) {
        telejump(entity, collision, end)
        val delta1 = Translation.between(start, end)
        val delta2 = Translation.between(end, entity.coords)
        val exactMove =
            EntityExactMove(
                deltaX1 = delta1.x,
                deltaZ1 = delta1.z,
                clientDelay1 = delay1,
                deltaX2 = delta2.x,
                deltaZ2 = delta2.z,
                clientDelay2 = delay2,
                direction = dir,
            )
        entity.pendingExactMove = exactMove
        entity.faceAngle = dir
    }

    public fun facePlayer(entity: PathingEntity, target: Player) {
        if (target.staffInvisible) {
            resetFaceEntity(entity)
            return
        }
        entity.faceEntity = EntityFaceTarget(target)
        entity.faceEntityFallbackAngle = entity.fallbackAngleTo(target)
        entity.lastFaceEntity = entity.currentMapClock
    }

    public fun faceNpc(entity: PathingEntity, target: Npc) {
        entity.faceEntity = EntityFaceTarget(target)
        entity.faceEntityFallbackAngle = entity.fallbackAngleTo(target)
        entity.lastFaceEntity = entity.currentMapClock
    }

    public fun resetFaceEntity(entity: PathingEntity) {
        entity.faceEntity = EntityFaceTarget.NULL
        entity.faceEntityFallbackAngle = 0
    }

    /**
     * Bearing from [this] to [target], for the client to fall back on while the target avatar is
     * outside its high-resolution set. Falls back to the entity's current angle when the two share
     * a tile, since there is no meaningful bearing to compute in that case.
     */
    private fun PathingEntity.fallbackAngleTo(target: PathingEntity): Int =
        calculateAngle(target.coords, target.size, target.size) ?: faceAngle

    public fun anim(
        entity: PathingEntity,
        seq: String,
        delay: Int,
        priority: Int,
    ): Boolean {
        require(delay in 0..254) { "`delay` must be within range [0..254]." }
        require(priority in 0..254) { "`priority` must be within range [0..254]." }

        if (entity.animProtect) {
            return false
        }

        val hasPriority = entity.pendingSequence.isLowerPriorityThan(priority)
        if (!hasPriority) {
            return false
        }

        entity.pendingSequence = EntitySeq(seq.asRSCM(RSCMType.SEQ), delay, priority)
        return true
    }

    private fun EntitySeq.isLowerPriorityThan(otherPriority: Int): Boolean =
        when (this) {
            EntitySeq.NULL -> true
            EntitySeq.ZERO -> true
            else -> otherPriority > priority
        }

    public fun setAnimProtect(entity: PathingEntity, animProtect: Boolean) {
        entity.animProtect = animProtect
    }

    public fun spotanim(entity: PathingEntity, spot: Int, delay: Int, height: Int, slot: Int) {
        val spotanim = EntitySpotanim(spot, delay, height, slot)
        entity.pendingSpotanims.add(spotanim.packed)
    }
}
