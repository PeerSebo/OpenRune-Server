package org.rsmod.api.route

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.game.entity.PathingEntityAvatar
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocShapeConstants
import org.rsmod.routefinder.reach.ReachStrategy
import org.rsmod.routefinder.reach.RectangularBounds

/**
 * Reject cross-plane reach checks before planar distance/raycast helpers. Bounds calculations
 * must include the full entity footprint.
 */
public class BoundValidator @Inject constructor(private val flags: CollisionFlagMap) {
    public fun touches(source: PathingEntityAvatar, target: PathingEntityAvatar): Boolean {
        if (!sameLevel(source.coords, target.coords)) {
            return false
        }
        return ReachStrategy.reachExclusiveRectangle(
            flags = flags,
            level = source.coords.level,
            srcX = source.coords.x,
            srcZ = source.coords.z,
            destX = target.coords.x,
            destZ = target.coords.z,
            destWidth = target.size,
            destLength = target.size,
            srcSize = source.size,
        )
    }

    public fun touches(source: PathingEntityAvatar, target: BoundLocInfo): Boolean {
        if (!sameLevel(source.coords, target.coords)) {
            return false
        }
        return ReachStrategy.reached(
            flags = flags,
            level = source.coords.level,
            srcX = source.coords.x,
            srcZ = source.coords.z,
            destX = target.coords.x,
            destZ = target.coords.z,
            destWidth = target.width,
            destLength = target.length,
            srcSize = source.size,
            locAngle = target.angleId,
            locShape = target.shapeId,
            blockAccessFlags = target.forceApproachFlags,
        )
    }

    public fun collides(source: PathingEntityAvatar, target: PathingEntityAvatar): Boolean {
        if (!sameLevel(source.coords, target.coords)) {
            return false
        }
        return RectangularBounds.collides(
            srcX = source.coords.x,
            srcZ = source.coords.z,
            destX = target.coords.x,
            destZ = target.coords.z,
            srcWidth = source.size,
            srcLength = source.size,
            destWidth = target.size,
            destLength = target.size,
        )
    }

    public fun collides(source: PathingEntityAvatar, target: BoundLocInfo): Boolean {
        if (!sameLevel(source.coords, target.coords)) {
            return false
        }
        return RectangularBounds.collides(
            srcX = source.coords.x,
            srcZ = source.coords.z,
            destX = target.coords.x,
            destZ = target.coords.z,
            srcWidth = source.size,
            srcLength = source.size,
            destWidth = target.adjustedWidth,
            destLength = target.adjustedLength,
        )
    }

    public fun collides(
        source: PathingEntityAvatar,
        target: LocInfo,
        type: ObjectServerType,
    ): Boolean = collides(source, BoundLocInfo(target, type))

    public fun touches(
        source: PathingEntityAvatar,
        target: LocInfo,
        type: ObjectServerType,
    ): Boolean = touches(source, BoundLocInfo(target, type))

    public fun touches(source: PathingEntityAvatar, target: Obj): Boolean {
        if (!sameLevel(source.coords, target.coords)) {
            return false
        }
        return ReachStrategy.reached(
            flags = flags,
            level = source.coords.level,
            srcX = source.coords.x,
            srcZ = source.coords.z,
            destX = target.coords.x,
            destZ = target.coords.z,
            destWidth = 1,
            destLength = 1,
            srcSize = source.size,
            locShape = LocShapeConstants.CENTREPIECE_STRAIGHT,
        )
    }

    private companion object {
        @Suppress("NOTHING_TO_INLINE")
        private inline fun sameLevel(source: CoordGrid, target: CoordGrid): Boolean =
            source.level == target.level
    }
}
