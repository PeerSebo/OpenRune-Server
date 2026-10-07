package org.rsmod.api.repo.region

import org.rsmod.game.region.util.RegionRotations
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.region.zone.RegionZoneCopyMap
import org.rsmod.game.region.zone.RegionZoneFlag
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Translation
import org.rsmod.map.zone.ZoneKey

@DslMarker private annotation class RegionTemplateBuilderDsl

@RegionTemplateBuilderDsl
public class RegionStaticTemplate internal constructor(private val regionZoneLength: Int) :
    RegionTemplate() {
    internal val backing: RegionZoneCopyMap = RegionZoneCopyMap()
    internal var largeRegion: Boolean = false

    override fun build(southWest: ZoneKey): RegionZoneCopyMap = backing.translate(southWest)

    override fun isLargeRegion(): Boolean = largeRegion

    public operator fun set(
        regionZoneX: Int,
        regionZoneZ: Int,
        regionLevel: Int,
        normalZone: ZoneKey,
    ) {
        val copy = RegionZoneCopy(normalZone, rotation = 0, flag = null)
        this[regionZoneX, regionZoneZ, regionLevel] = copy
    }

    public operator fun set(
        regionZoneX: Int,
        regionZoneZ: Int,
        regionLevel: Int,
        normalZoneCopy: RegionZoneCopy,
    ) {
        assertZone(regionZoneX, regionZoneZ, regionLevel)

        val regionZone = ZoneKey(regionZoneX, regionZoneZ, regionLevel)
        assertZoneNotInUse(regionZone)

        backing[regionZone] = normalZoneCopy
    }

    public fun ZoneKey.rotate90(): RegionZoneCopy = rotate(rotation = 1)

    public fun ZoneKey.rotate180(): RegionZoneCopy = rotate(rotation = 2)

    public fun ZoneKey.rotate270(): RegionZoneCopy = rotate(rotation = 3)

    private fun ZoneKey.rotate(rotation: Int): RegionZoneCopy {
        require(rotation in 0..3) { "Rotation must be within range [0..3]. (rotation=$rotation)" }
        return RegionZoneCopy(this, rotation, flag = null)
    }

    /**
     * Unique flags distinguish repeated copies of the same source zone during coordinate
     * resolution.
     */
    public fun ZoneKey.uniqueFlag(flag: RegionZoneFlag): RegionZoneCopy {
        return RegionZoneCopy(this, rotation = 0, flag)
    }

    public fun RegionZoneCopy.uniqueFlag(flag: RegionZoneFlag): RegionZoneCopy {
        return RegionZoneCopy(normalZone(), rotation, flag)
    }

    public fun copy(copyZoneX: Int, copyZoneZ: Int, copyLevel: Int, init: BlockBuilder.() -> Unit) {
        copy(copyZoneX, copyZoneZ, copyLevel..copyLevel, init)
    }

    public fun copyAllLevels(copyZoneX: Int, copyZoneZ: Int, init: BlockBuilder.() -> Unit) {
        copy(copyZoneX, copyZoneZ, 0..<CoordGrid.LEVEL_COUNT, init)
    }

    private fun copy(
        copyZoneX: Int,
        copyZoneZ: Int,
        levels: IntRange,
        init: BlockBuilder.() -> Unit,
    ) {
        val block = BlockBuilder().apply(init).validate()
        for (level in levels) {
            for (x in 0 until block.zoneWidth) {
                for (z in 0 until block.zoneLength) {
                    val zone = ZoneKey(copyZoneX + x, copyZoneZ + z, level)
                    val copy = zone.rotate(block.rotation)

                    // When rotating a block of zones, we must ensure that all zones within the
                    // block rotate as a cohesive unit. Each zone's relative position must be
                    // transformed consistently so the block retains its original shape after
                    // rotation.
                    val (rx, rz) = block.rotate(x, z)

                    this[block.regionZoneX + rx, block.regionZoneZ + rz, level] = copy
                }
            }
        }
    }

    @RegionTemplateBuilderDsl
    public class BlockBuilder {
        public var regionZoneX: Int = 0
        public var regionZoneZ: Int = 0
        public var zoneWidth: Int = 0
        public var zoneLength: Int = 0
        public var rotation: Int = 0

        internal fun validate(): BlockBuilder {
            check(zoneWidth > 0) {
                "`zoneWidth` must be set to amount of horizontal zones to copy."
            }
            check(zoneLength > 0) {
                "`zoneLength` must be set to amount of vertical zones to copy."
            }
            return this
        }

        internal fun rotate(localZoneX: Int, localZoneZ: Int): Translation {
            val translation =
                RegionRotations.translateZone(
                    regionRot = rotation,
                    zoneX = localZoneX,
                    zoneZ = localZoneZ,
                    zoneWidth = zoneWidth,
                    zoneLength = zoneLength,
                )
            return translation
        }
    }

    private fun assertZone(zoneX: Int, zoneZ: Int, level: Int) {
        require(zoneX in 0..<regionZoneLength) {
            "`zoneX` should be within range [0..${regionZoneLength - 1}]. (zoneX=$zoneX)"
        }

        require(zoneZ in 0..<regionZoneLength) {
            "`zoneZ` should be within range [0..${regionZoneLength - 1}]. (zoneZ=$zoneZ)"
        }

        require(level in 0..CoordGrid.LEVEL_BIT_MASK) {
            "`level` should be within range [0..${CoordGrid.LEVEL_BIT_MASK}]. (level=$level)"
        }
    }

    private fun assertZoneNotInUse(zone: ZoneKey) {
        check(zone !in backing) { "Zone has already been set: $zone" }
    }
}

public sealed class RegionTemplate {
    internal abstract fun build(southWest: ZoneKey): RegionZoneCopyMap

    internal abstract fun isLargeRegion(): Boolean

    public companion object {
        public fun create(init: RegionStaticTemplate.() -> Unit): RegionStaticTemplate {
            val template = RegionStaticTemplate(RegionRepository.SMALL_REGION_ZONE_LENGTH)
            return template.apply(init)
        }

        public fun createLarge(init: RegionStaticTemplate.() -> Unit): RegionStaticTemplate {
            val template = RegionStaticTemplate(RegionRepository.LARGE_REGION_ZONE_LENGTH)
            template.largeRegion = true
            return template.apply(init)
        }
    }
}
