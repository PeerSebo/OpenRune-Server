package org.rsmod.content.quest.area.goblinvillage

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

internal object GoblinDiplomacyRegions {
    val GoblinVillage: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(VillageZoneX, VillageZoneZ) {
                regionZoneX = BlockOffsetZones
                regionZoneZ = BlockOffsetZones
                zoneWidth = BlockZones
                zoneLength = BlockZones
            }
        }

    const val VillageZoneX: Int = 368
    const val VillageZoneZ: Int = 438

    private const val BlockZones = 3
    private const val BlockOffsetZones = 6
}

internal fun assertGoblinDiplomacySceneCoords() {
    val template: RegionTemplate = GoblinDiplomacyRegions.GoblinVillage
    check(template === GoblinDiplomacyRegions.GoblinVillage) {
        "Goblin Diplomacy cutscene region template failed to build."
    }

    val originX = GoblinDiplomacyRegions.VillageZoneX * ZoneSize
    val originZ = GoblinDiplomacyRegions.VillageZoneZ * ZoneSize

    for ((name, coord) in CatwalkSceneCoords) {
        check(
            coord.level == 0 &&
                coord.x in originX until originX + BlockSize &&
                coord.z in originZ until originZ + BlockSize
        ) {
            "Goblin Diplomacy catwalk-scene coord is outside the copied block, so " +
                "`region.normal[...]` would throw mid-scene: $name=$coord " +
                "(allowed: level 0, x ${originX}..${originX + BlockSize - 1}, " +
                "z ${originZ}..${originZ + BlockSize - 1})"
        }
    }
}

private const val ZoneSize = 8
private const val BlockSize = 3 * ZoneSize

private val CatwalkSceneCoords: List<Pair<String, CoordGrid>>
    get() =
        listOf(
            "vantage" to GrubfootScene.Vantage,
            "wartface" to GrubfootScene.WartfaceTile,
            "wartfaceFaces" to GrubfootScene.WartfaceFaces,
            "bentnoze" to GrubfootScene.BentnozeTile,
            "bentnozeFaces" to GrubfootScene.BentnozeFaces,
            "grubfootSpawn" to GrubfootScene.GrubfootSpawn,
            "grubfootAttends" to GrubfootScene.GrubfootAttends,
            "grubfootCurtain" to GrubfootScene.GrubfootCurtain,
            "grubfootCurtainFront" to GrubfootScene.GrubfootCurtainFront,
            "grubfootModels" to GrubfootScene.GrubfootModels,
            "cameraEye" to GrubfootScene.Camera.eye,
            "cameraLookAt" to GrubfootScene.Camera.lookAt,
            "doorWest" to GrubfootScene.HutDoorWest,
            "doorEast" to GrubfootScene.HutDoorEast,
            "caveEntrance" to GrubfootScene.CaveEntrance,
        )
