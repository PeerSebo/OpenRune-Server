package org.rsmod.game.loc

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds
import org.rsmod.routefinder.util.Rotations

public data class BoundLocInfo(
    public val coords: CoordGrid,
    public val entity: LocEntity,
    public val layer: Int,
    public val width: Int,
    public val length: Int,
    public val forceApproachFlags: Int,
) {
    public constructor(
        info: LocInfo,
        width: Int,
        length: Int,
        forceApproachFlags: Int,
    ) : this(info.coords, info.entity, info.layer, width, length, forceApproachFlags)

    public constructor(
        info: LocInfo,
        type: ObjectServerType,
    ) : this(info.coords, info.entity, info.layer, type.width, type.length, type.forceApproachFlags)

    public val id: Int
        get() = entity.id

    public val shape: LocShape
        get() = LocShape[shapeId]

    public val shapeId: Int
        get() = entity.shape

    public val angle: LocAngle
        get() = LocAngle[angleId]

    public val angleId: Int
        get() = entity.angle

    public val x: Int
        get() = coords.x

    public val z: Int
        get() = coords.z

    public val level: Int
        get() = coords.level

    val internalName: String
        get() = ServerCacheManager.getObject(id)?.internalName ?: error("Missing loc type id=$id")

    public val adjustedCentre: CoordGrid
        get() = coords.translate(adjustedWidth / 2, adjustedLength / 2)

    public val adjustedWidth: Int
        get() = Rotations.rotate(angleId, width, length)

    public val adjustedLength: Int
        get() = Rotations.rotate(angleId, length, width)

    public fun bounds(): Bounds = Bounds(coords, adjustedWidth, adjustedLength)

    public fun turnAngle(rotations: Int = 1): LocAngle = angle.turn(rotations)

    public fun isType(type: ObjectServerType): Boolean = type.id == id

    override fun toString(): String =
        "BoundLocInfo(" +
            "coords=$coords, " +
            "id=$id, " +
            "shape=$shape, " +
            "angle=$angle, " +
            "adjustedWidth=$adjustedWidth, " +
            "adjustedLength=$adjustedLength, " +
            "width=$width, " +
            "length=$length, " +
            "forceApproachFlags=$forceApproachFlags" +
            ")"
}
