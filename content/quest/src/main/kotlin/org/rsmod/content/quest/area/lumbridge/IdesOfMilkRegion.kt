package org.rsmod.content.quest.area.lumbridge

import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

// The copied block needs the client scene's six-zone margin within the allocated region.
internal val IdesOfMilkArenaTemplate: RegionStaticTemplate =
    RegionTemplate.createLarge {
        copy(ArenaZoneX, ArenaZoneZ, 0) {
            regionZoneX = ArenaOffsetZones
            regionZoneZ = ArenaOffsetZones
            zoneWidth = ArenaZoneWidth
            zoneLength = ArenaZoneLengthGround
        }
        for (level in 1..3) {
            copy(ArenaZoneX, ArenaZoneZ, level) {
                regionZoneX = ArenaOffsetZones
                regionZoneZ = ArenaOffsetZones
                zoneWidth = ArenaZoneWidth
                zoneLength = ArenaZoneLengthUpper
            }
        }
    }

internal const val ArenaZoneX: Int = 401

internal const val ArenaZoneZ: Int = 405

internal const val ArenaZoneWidth: Int = 11

internal const val ArenaZoneLengthGround: Int = 13

internal const val ArenaZoneLengthUpper: Int = 12

private const val ArenaOffsetZones: Int = 6

private const val ZoneSize: Int = 8

internal fun assertIdesOfMilkArenaCoords() {
    val template: RegionTemplate = IdesOfMilkArenaTemplate
    check(template === IdesOfMilkArenaTemplate) { "Ides of Milk region template failed to build." }

    val minX = ArenaZoneX * ZoneSize
    val minZ = ArenaZoneZ * ZoneSize
    val maxX = minX + ArenaZoneWidth * ZoneSize - 1
    val maxZ = minZ + ArenaZoneLengthGround * ZoneSize - 1

    val named =
        listOf(
            "bullSpawn" to IdesOfMilkCoords.BullSpawn,
            "fightEntry" to IdesOfMilkCoords.FightEntry,
            "fightEntryFace" to IdesOfMilkCoords.FightEntryFace,
            "fightExit" to IdesOfMilkCoords.FightExit,
            "entryGateLeft" to IdesOfMilkCoords.EntryGateLeft,
            "entryGateRight" to IdesOfMilkCoords.EntryGateRight,
            "exitGateLeft" to IdesOfMilkCoords.ExitGateLeft,
            "exitGateRight" to IdesOfMilkCoords.ExitGateRight,
        )
    for ((name, coord) in named) {
        check(coord.level == 0 && coord.x in minX..maxX && coord.z in minZ..maxZ) {
            "Ides of Milk fight coord is outside the copied block, so `region.normal[...]` would " +
                "throw mid-fight: $name=$coord (allowed: level 0, x $minX..$maxX, z $minZ..$maxZ)"
        }
    }
}

internal fun org.rsmod.game.region.Region.containsCoord(coords: CoordGrid): Boolean =
    coords.x in southWest.x..northEast.x && coords.z in southWest.z..northEast.z
