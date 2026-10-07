package org.rsmod.content.quest.area.varrock

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

internal object RomeoAndJulietRegions {
    val JulietHouse: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(HouseZoneX, HouseZoneZ) {
                regionZoneX = BlockOffsetZones
                regionZoneZ = BlockOffsetZones
                zoneWidth = BlockZones
                zoneLength = BlockZones
            }
        }

    val Crypt: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(CryptZoneX, CryptZoneZ) {
                regionZoneX = BlockOffsetZones
                regionZoneZ = BlockOffsetZones
                zoneWidth = BlockZones
                zoneLength = BlockZones
            }
        }

    const val HouseZoneX: Int = 393
    const val HouseZoneZ: Int = 427
    const val CryptZoneX: Int = 290
    const val CryptZoneZ: Int = 579

    private const val BlockZones = 3
    private const val BlockOffsetZones = 6
}

internal fun assertRomeoAndJulietSceneCoords() {
    check(RomeoAndJulietRegions.JulietHouse !== RomeoAndJulietRegions.Crypt) {
        "Romeo & Juliet region templates failed to build."
    }

    val houseX = RomeoAndJulietRegions.HouseZoneX * ZoneSize
    val houseZ = RomeoAndJulietRegions.HouseZoneZ * ZoneSize
    val cryptX = RomeoAndJulietRegions.CryptZoneX * ZoneSize
    val cryptZ = RomeoAndJulietRegions.CryptZoneZ * ZoneSize

    for ((name, coord) in JulietSceneCoords) {
        check(
            coord.x in houseX until houseX + BlockSize && coord.z in houseZ until houseZ + BlockSize
        ) {
            "Romeo & Juliet death-scene coord is outside the copied block, so " +
                "`region.normal[...]` would throw mid-scene: $name=$coord " +
                "(allowed: x ${houseX}..${houseX + BlockSize - 1}, " +
                "z ${houseZ}..${houseZ + BlockSize - 1})"
        }
    }

    for ((name, coord) in CryptSceneCoords) {
        check(
            coord.x in cryptX until cryptX + BlockSize && coord.z in cryptZ until cryptZ + BlockSize
        ) {
            "Romeo & Juliet crypt-scene coord is outside the copied block, so " +
                "`region.normal[...]` would throw mid-scene: $name=$coord " +
                "(allowed: x ${cryptX}..${cryptX + BlockSize - 1}, " +
                "z ${cryptZ}..${cryptZ + BlockSize - 1})"
        }
    }
}

private const val ZoneSize = 8
private const val BlockSize = 3 * ZoneSize

private val JulietSceneCoords: List<Pair<String, CoordGrid>>
    get() =
        listOf(
            "vantage" to DeathScene.Vantage,
            "juliet" to DeathScene.JulietTile,
            "phillipa" to DeathScene.PhillipaTile,
            "draulSpawn" to DeathScene.DraulSpawn,
            "draulStand" to DeathScene.DraulStand,
            "cameraEye" to DeathScene.OpeningCamera.eye,
            "cameraLookAt" to DeathScene.OpeningCamera.lookAt,
            "draulCameraEye" to DeathScene.DraulCamera.eye,
            "draulCameraLookAt" to DeathScene.DraulCamera.lookAt,
        )

private val CryptSceneCoords: List<Pair<String, CoordGrid>>
    get() =
        listOf(
            "vantage" to CryptScene.Vantage,
            "romeoSpawn" to CryptScene.RomeoSpawn,
            "romeoWalkStart" to CryptScene.RomeoWalkStart,
            "romeoWalkEnd" to CryptScene.RomeoWalkEnd,
            "coffin" to CryptScene.CoffinTile,
            "phillipaSpawn" to CryptScene.PhillipaSpawn,
            "phillipaWalkEnd" to CryptScene.PhillipaWalkEnd,
        ) +
            CryptScene.Cameras.flatMapIndexed { index, camera ->
                listOf("camera${index}Eye" to camera.eye, "camera${index}LookAt" to camera.lookAt)
            }
