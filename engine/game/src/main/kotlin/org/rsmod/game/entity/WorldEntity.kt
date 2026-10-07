package org.rsmod.game.entity

import org.rsmod.game.entity.worldentity.NoopWorldEntityInfo
import org.rsmod.game.entity.worldentity.WorldEntityInfoProtocol
import org.rsmod.game.region.Region
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey

/**
 * The hull moves in root-world coordinates; occupants stay in a separate deck instance. Keep
 * both coordinate spaces distinct.
 */
public class WorldEntity(
    public val configId: Int,
    public val region: Region,
    public val instanceSouthWest: CoordGrid,
    public val zoneSizeX: Int,
    public val zoneSizeZ: Int,
    public val minLevel: Int,
    public val maxLevel: Int,
    public val activeLevel: Int,
) {
    public var slotId: Int = INVALID_SLOT
    public var ownerSlot: Int = NO_OWNER

    private var rootPosition: CoordGrid = CoordGrid.NULL

    public var rootCoords: CoordGrid
        get() = rootPosition
        set(value) {
            rootPosition = value
            fineX = value.x * FINE_UNITS_PER_TILE
            fineZ = value.z * FINE_UNITS_PER_TILE
        }

    public var angle: Int = 0

    public var pendingSequence: EntitySeq = EntitySeq.NULL

    public var infoProtocol: WorldEntityInfoProtocol = NoopWorldEntityInfo

    public val projectedLevel: Int
        get() = rootCoords.level

    public var fineX: Int = CoordGrid.NULL.x * FINE_UNITS_PER_TILE
        private set

    public var fineZ: Int = CoordGrid.NULL.z * FINE_UNITS_PER_TILE
        private set

    public val southWestZone: ZoneKey
        get() = ZoneKey.from(instanceSouthWest)

    /**
     * Moves only the root-world projection. Content must validate navigation collision before
     * calling.
     */
    public fun moveRoot(fineX: Int, fineZ: Int, level: Int = projectedLevel) {
        require(fineX in 0 until CoordGrid.MAP_WIDTH * FINE_UNITS_PER_TILE) {
            "Fine x coordinate is outside the root world: $fineX"
        }
        require(fineZ in 0 until CoordGrid.MAP_LENGTH * FINE_UNITS_PER_TILE) {
            "Fine z coordinate is outside the root world: $fineZ"
        }
        require(level in 0 until CoordGrid.LEVEL_COUNT) { "Invalid root level: $level" }
        rootPosition = CoordGrid(fineX / FINE_UNITS_PER_TILE, fineZ / FINE_UNITS_PER_TILE, level)
        this.fineX = fineX
        this.fineZ = fineZ
    }

    public fun anim(seq: Int, delay: Int = 0) {
        pendingSequence = EntitySeq(seq, delay, priority = 0)
    }

    public fun resetAnim() {
        pendingSequence = EntitySeq.ZERO
    }

    public operator fun contains(coords: CoordGrid): Boolean {
        if (coords.level < minLevel || coords.level > maxLevel) {
            return false
        }
        val minX = instanceSouthWest.x
        val minZ = instanceSouthWest.z
        val maxX = minX + (zoneSizeX * ZoneGrid.LENGTH)
        val maxZ = minZ + (zoneSizeZ * ZoneGrid.LENGTH)
        return coords.x >= minX && coords.x < maxX && coords.z >= minZ && coords.z < maxZ
    }

    override fun toString(): String =
        "WorldEntity(" +
            "slotId=$slotId, " +
            "configId=$configId, " +
            "rootCoords=$rootCoords, " +
            "angle=$angle, " +
            "instanceSouthWest=$instanceSouthWest, " +
            "zoneSize=(${zoneSizeX}x$zoneSizeZ), " +
            "levels=$minLevel..$maxLevel, " +
            "activeLevel=$activeLevel, " +
            "ownerSlot=$ownerSlot" +
            ")"

    public companion object {
        public const val INVALID_SLOT: Int = -1

        /** Sentinel for "no owning player slot"; rsprot reads a negative owner as npc-owned. */
        public const val NO_OWNER: Int = -1

        /**
         * Fine coords are `tile * 128`, per `WorldEntityAvatarFactory.alloc`'s KDoc ("This can be
         * calculated by doing x * 128 with absolute coord grid values").
         */
        public const val FINE_UNITS_PER_TILE: Int = 128

        public const val ANGLE_UNITS_PER_TURN: Int = 2048

        /**
         * Hard protocol floor on turn rate. `WorldEntityAvatar.updateAngle`'s KDoc: "the client is
         * only made to rotate by a maximum of 22.5 degrees (128/2048 units) per game cycle". No
         * server-side turn rate faster than this can ever be observed.
         *
         * The real OSRS turn rate is still unverified.
         */
        public const val ANGLE_UNITS_PER_TICK_MAX: Int = 128

        /**
         * `WorldEntityInfo.processLowResolution` writes `p1Alt3((sizeX shl 4) or sizeZ)` - four
         * bits each - so any zone size above 15 silently corrupts the packet even though `alloc`
         * only requires `0..255`.
         *
         * [MAX_ZONE_SIZE] is the tighter of the two real ceilings: `RebuildWorldEntityV4` requires
         * `sizeX in 0..<13`, so 12 is the largest value that works through both paths.
         */
        public const val MAX_ZONE_SIZE: Int = 12

        /**
         * Capped at 2047 rather than rsprot's 4095 so every index stays usable by
         * `CamTargetV4.WorldEntityTarget`, which requires `index in 0..<2048`.
         */
        public const val MAX_INDEX: Int = 2047
    }
}
