package org.rsmod.content.quest.area.varrock

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

internal val DemonSlayerCircleTemplate: RegionTemplate =
    RegionTemplate.create {
        for (level in 0..1) {
            copy(CircleZoneX, CircleZoneZ, level) {
                regionZoneX = BlockOffsetZones
                regionZoneZ = BlockOffsetZones
                zoneWidth = BlockZoneWidth
                zoneLength = BlockZoneLength
            }
        }
    }

internal const val CircleZoneX: Int = 402

internal const val CircleZoneZ: Int = 420

internal const val BlockZoneWidth: Int = 3

internal const val BlockZoneLength: Int = 4

private const val BlockOffsetZones: Int = 4

private const val ZoneSize: Int = 8

internal fun assertDemonSlayerSceneCoords() {
    val template: RegionTemplate = DemonSlayerCircleTemplate
    check(template === DemonSlayerCircleTemplate) {
        "Demon Slayer region template failed to build."
    }

    val minX = CircleZoneX * ZoneSize
    val minZ = CircleZoneZ * ZoneSize
    val maxX = minX + BlockZoneWidth * ZoneSize - 1
    val maxZ = minZ + BlockZoneLength * ZoneSize - 1

    for ((name, coord) in DemonSlayerSceneCoords) {
        check(coord.level in 0..1 && coord.x in minX..maxX && coord.z in minZ..maxZ) {
            "Demon Slayer scene coord is outside the copied block, so `region.normal[...]` would " +
                "throw mid-scene: $name=$coord (allowed: levels 0-1, x $minX..$maxX, z $minZ..$maxZ)"
        }
    }
}

private val DemonSlayerSceneCoords: List<Pair<String, CoordGrid>>
    get() =
        listOf(
            "stoneTable" to DemonSlayerCoords.StoneTable,
            "wizard1" to DemonSlayerCoords.Wizard1Tile,
            "wizard2" to DemonSlayerCoords.Wizard2Tile,
            "wizard3" to DemonSlayerCoords.Wizard3Tile,
            "denath" to DemonSlayerCoords.DenathTile,
            "denathExit" to DemonSlayerCoords.DenathExit,
        ) +
            DemonSlayerCoords.CircleCorners.mapIndexed { index, coord -> "corner$index" to coord } +
            (WallyScene.Cameras + SummoningScene.Cameras).flatMapIndexed { index, camera ->
                listOf("camera${index}Eye" to camera.eye, "camera${index}LookAt" to camera.lookAt)
            } +
            listOf("wallyVantage" to WallyScene.Vantage)
