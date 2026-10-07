package org.rsmod.api.repo.region

import jakarta.inject.Inject
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.region.RegionRegistryResult
import org.rsmod.api.registry.region.isSuccess
import org.rsmod.game.region.Region
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey

public class RegionRepository @Inject constructor(private val registry: RegionRegistry) {
    public fun add(template: RegionTemplate): Region? =
        if (template.isLargeRegion()) {
            addLarge(template)
        } else {
            addSmall(template)
        }

    private fun addSmall(template: RegionTemplate): Region? {
        val result = registerSmall(template)
        if (result !is Result.Add.Success) {
            return addLarge(template)
        }
        return result.region
    }

    private fun addLarge(template: RegionTemplate): Region? {
        val result = registerLarge(template)
        if (result !is Result.Add.Success) {
            return null
        }
        return result.region
    }

    public fun protect(region: Region) {
        registry.protect(region.uid)
    }

    public fun unprotect(region: Region) {
        registry.unprotect(region.uid)
    }

    public fun isValid(region: Region): Boolean {
        if (region.slot == Region.INVALID_SLOT) return false
        return registry.isValid(region.slot, region.uid)
    }

    public fun del(region: Region): Boolean {
        if (!isValid(region)) return true
        val result = registry.unregister(region)
        if (result !is RegionRegistryResult.Delete.Success) return false
        registry.unprotect(region.uid)
        return true
    }

    public fun addWorldEntity(template: RegionTemplate): Region? {
        val result = registry.registerWorldEntity()
        if (!result.isSuccess()) return null
        val region = result.region
        try {
            val copyZones = template.build(region.southWestZone)
            require(copyZones.backing.keys.all { packed ->
                val coords = ZoneKey(packed).toCoords()
                coords.x in region.southWest.x until region.northEast.x &&
                    coords.z in region.southWest.z until region.northEast.z
            }) { "World entity template exceeds its 64-tile deck allocation." }
            registry.build(region, copyZones)
        } catch (cause: Exception) {
            del(region)
            throw cause
        }
        return region
    }

    public fun registerSmall(template: RegionTemplate): Result.Add {
        registry.removeInactiveSmallRegions()

        val result = registry.registerSmall()
        if (!result.isSuccess()) {
            return Result.Add.RegionCapacityReached
        }

        val region = result.region
        val southWestZone = ZoneKey.from(region.southWest)
        val copyZones = template.build(southWestZone)

        registry.build(region, copyZones)

        return Result.Add.Registered(region)
    }

    public fun registerLarge(template: RegionTemplate): Result.Add {
        registry.removeInactiveLargeRegions()

        val result = registry.registerLarge()
        if (!result.isSuccess()) {
            return Result.Add.RegionCapacityReached
        }

        val region = result.region
        val southWestZone = ZoneKey.from(region.southWest)
        val copyZones = template.build(southWestZone)

        registry.build(region, copyZones)

        return Result.Add.Registered(region)
    }

    public class Result {
        public sealed class Add {
            public sealed class Success(public val region: Region) : Add()

            public sealed class Failure : Add()

            public class Registered(region: Region) : Success(region)

            public data object RegionCapacityReached : Failure()
        }
    }

    public companion object {
        private const val SMALL_REGION_SQ_LENGTH = RegionRegistry.SMALL_REGION_SQUARE_LENGTH
        public const val SMALL_REGION_ZONE_LENGTH: Int = SMALL_REGION_SQ_LENGTH / ZoneGrid.LENGTH

        private const val LARGE_REGION_SQ_LENGTH = RegionRegistry.LARGE_REGION_SQUARE_LENGTH
        public const val LARGE_REGION_ZONE_LENGTH: Int = LARGE_REGION_SQ_LENGTH / ZoneGrid.LENGTH
    }
}
