package org.rsmod.content.quest.area.blackknights

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

internal object BlackKnightsFortressRegions {
    val WitchRoom: RegionTemplate =
        RegionTemplate.create {
            copy(RoomZoneX, RoomZoneZ, 0) {
                regionZoneX = BlockOffsetZones
                regionZoneZ = BlockOffsetZones
                zoneWidth = BlockZones
                zoneLength = BlockZones
            }
        }

    const val RoomZoneX: Int = 376
    const val RoomZoneZ: Int = 436

    const val BlockZones = 4

    private const val BlockOffsetZones = 6
}

internal fun assertBlackKnightsFortressSceneCoords() {
    val template: RegionTemplate = BlackKnightsFortressRegions.WitchRoom
    check(template === BlackKnightsFortressRegions.WitchRoom) {
        "Black Knights' Fortress cutscene region template failed to build."
    }

    val originX = BlackKnightsFortressRegions.RoomZoneX * ZoneSize
    val originZ = BlackKnightsFortressRegions.RoomZoneZ * ZoneSize
    val blockSize = BlackKnightsFortressRegions.BlockZones * ZoneSize

    for ((name, coord) in SabotageSceneCoords) {
        check(
            coord.level == 0 &&
                coord.x in originX until originX + blockSize &&
                coord.z in originZ until originZ + blockSize
        ) {
            "Black Knights' Fortress sabotage-scene coord is outside the copied block, so " +
                "`region.normal[...]` would throw mid-scene: $name=$coord " +
                "(allowed: level 0, x ${originX}..${originX + blockSize - 1}, " +
                "z ${originZ}..${originZ + blockSize - 1})"
        }
    }
}

private const val ZoneSize = 8

private val SabotageSceneCoords: List<Pair<String, CoordGrid>>
    get() =
        listOf(
            "vantage" to SabotageScene.Vantage,
            "cauldron" to SabotageScene.Cauldron,
            "projectileStart" to SabotageScene.ProjectileStart,
            "cat" to SabotageScene.CatTile,
            "captain" to SabotageScene.CaptainTile,
            "witchSpawn" to SabotageScene.WitchSpawn,
            "witchForward" to SabotageScene.WitchForward,
            "witchCauldron" to SabotageScene.WitchAtCauldron,
            "cameraEye" to SabotageScene.Camera.eye,
            "cameraLookAt" to SabotageScene.Camera.lookAt,
        )
