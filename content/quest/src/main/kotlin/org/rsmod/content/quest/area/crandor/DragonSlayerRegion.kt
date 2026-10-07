package org.rsmod.content.quest.area.crandor

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

internal val DragonShipTemplate: RegionTemplate =
    RegionTemplate.create {
        for (level in 0..3) {
            copy(ShipZoneX, ShipZoneZ, level) {
                regionZoneX = BlockOffsetZones
                regionZoneZ = BlockOffsetZones
                zoneWidth = ShipZoneWidth
                zoneLength = ShipZoneLength
            }
        }

        for (level in 0..1) {
            for (zoneX in 0 until SmallRegionZoneLength) {
                for (zoneZ in 0 until SmallRegionZoneLength) {
                    val insideBlock =
                        zoneX in BlockOffsetZones until BlockOffsetZones + ShipZoneWidth &&
                            zoneZ in BlockOffsetZones until BlockOffsetZones + ShipZoneLength
                    if (insideBlock) {
                        continue
                    }
                    this[zoneX, zoneZ, level] =
                        org.rsmod.map.zone.ZoneKey(OceanZoneX, OceanZoneZ, level)
                }
            }
        }
    }

private const val ShipZoneX: Int = 257

private const val ShipZoneZ: Int = 689

private const val ShipZoneWidth: Int = 3

private const val ShipZoneLength: Int = 2

private const val OceanZoneX: Int = 259

private const val OceanZoneZ: Int = 690

private const val BlockOffsetZones: Int = 4

private const val ZoneSize: Int = 8

private const val SmallRegionZoneLength: Int = 16

internal data class ShipTile(val dx: Int, val dz: Int)

@JvmInline
internal value class ShipScene(private val region: Region?) {
    val instanced: Boolean
        get() = region != null

    operator fun get(tile: ShipTile, level: Int): CoordGrid {
        val base = region?.southWest ?: CoordGrid(ShipZoneX * ZoneSize, ShipZoneZ * ZoneSize, 0)
        val originX = if (region == null) 0 else BlockOffsetZones * ZoneSize
        val originZ = if (region == null) 0 else BlockOffsetZones * ZoneSize
        return CoordGrid(base.x + originX + tile.dx, base.z + originZ + tile.dz, level)
    }

    fun camera(camera: ShipCamera): SceneCamera =
        SceneCamera(
            eye = this[camera.eye, ShipCameraLevel],
            eyeHeight = camera.eyeHeight,
            lookAt = this[camera.lookAt, ShipCameraLevel],
            lookAtHeight = camera.lookAtHeight,
        )
}

private const val ShipCameraLevel: Int = 1

internal class ShipCamera(
    val eye: ShipTile,
    val eyeHeight: Int,
    val lookAt: ShipTile,
    val lookAtHeight: Int,
)

internal object VoyageTiles {

    val Player = ShipTile(10, 4)

    val Ned = ShipTile(10, 5)

    val NedFace = ShipTile(11, 5)

    val Jenkins = ShipTile(7, 6)

    val JenkinsStormDest = ShipTile(10, 6)

    val ForwardFireTrail = listOf(ShipTile(9, 4), ShipTile(8, 4), ShipTile(7, 4))

    val AftFireTrail =
        listOf(ShipTile(7, 6), ShipTile(8, 6), ShipTile(9, 6), ShipTile(10, 6), ShipTile(11, 6))

    val LargeFire = ShipTile(8, 4)

    val SinkLargeFire = ShipTile(6, 5)

    val OpeningEye = ShipTile(21, 6)

    val OpeningLookAt = ShipTile(13, 5)

    val CloseUpEye = ShipTile(17, 4)

    val NedWatch = ShipTile(10, 5)

    val WideEye = ShipTile(13, 1)

    val StormLookAt = ShipTile(8, 8)

    val StormEye = ShipTile(18, 13)

    val StormDrift = ShipTile(8, 13)

    val StrafeEye = ShipTile(16, 5)

    val StrafeLookAt = ShipTile(6, 3)

    val StrafeDrift = ShipTile(16, 16)

    val AlightEye = ShipTile(12, 6)

    val AlightLookAt = ShipTile(6, 6)

    val AlightDrift = ShipTile(26, 6)

    val SinkEye = ShipTile(26, 6)

    val SinkDrift = ShipTile(8, 6)

    val OnDeck: List<Pair<String, ShipTile>> =
        listOf(
            "player" to Player,
            "ned" to Ned,
            "nedFace" to NedFace,
            "jenkins" to Jenkins,
            "jenkinsStormDest" to JenkinsStormDest,
            "largeFire" to LargeFire,
            "sinkLargeFire" to SinkLargeFire,
        ) +
            ForwardFireTrail.mapIndexed { i, tile -> "forwardFire[$i]" to tile } +
            AftFireTrail.mapIndexed { i, tile -> "aftFire[$i]" to tile }

    val Cameras: List<Pair<String, ShipTile>> =
        listOf(
            "openingEye" to OpeningEye,
            "openingLookAt" to OpeningLookAt,
            "closeUpEye" to CloseUpEye,
            "nedWatch" to NedWatch,
            "wideEye" to WideEye,
            "stormLookAt" to StormLookAt,
            "stormEye" to StormEye,
            "stormDrift" to StormDrift,
            "strafeEye" to StrafeEye,
            "strafeLookAt" to StrafeLookAt,
            "strafeDrift" to StrafeDrift,
            "alightEye" to AlightEye,
            "alightLookAt" to AlightLookAt,
            "alightDrift" to AlightDrift,
            "sinkEye" to SinkEye,
            "sinkDrift" to SinkDrift,
        )
}

internal val CrandorLairTemplate: RegionTemplate =
    RegionTemplate.create {
        copy(LairZoneX, LairZoneZ, 0) {
            regionZoneX = BlockOffsetZones
            regionZoneZ = BlockOffsetZones
            zoneWidth = LairZoneWidth
            zoneLength = LairZoneLength
        }

        for (x in 0 until LairZoneWidth) {
            for (z in 0 until LairZoneLength) {
                this[BlockOffsetZones + x, BlockOffsetZones + z, 1] =
                    org.rsmod.map.zone.ZoneKey(LairZoneX + x, LairZoneZ + z, 0)
            }
        }
    }

private const val LairZoneX: Int = 355

private const val LairZoneZ: Int = 1203

private const val LairZoneWidth: Int = 4

private const val LairZoneLength: Int = 4

internal fun assertDragonSlayerSceneCoords() {
    check(DragonShipTemplate === DragonShipTemplate) { "Dragon ship region template failed." }
    check(CrandorLairTemplate === CrandorLairTemplate) { "Crandor lair region template failed." }

    val minX = LairZoneX * ZoneSize
    val minZ = LairZoneZ * ZoneSize
    val maxX = minX + LairZoneWidth * ZoneSize - 1
    val maxZ = minZ + LairZoneLength * ZoneSize - 1

    for ((name, coord) in DescentSceneCoords) {
        check(coord.level in 0..1 && coord.x in minX..maxX && coord.z in minZ..maxZ) {
            "Dragon Slayer descent coord is outside the copied block, so `region.normal[...]` " +
                "would throw mid-scene: $name=$coord (allowed: levels 0-1, x $minX..$maxX, " +
                "z $minZ..$maxZ)"
        }
    }

    val blockMaxX = ShipZoneWidth * ZoneSize - 1
    val blockMaxZ = ShipZoneLength * ZoneSize - 1
    for ((name, tile) in VoyageTiles.OnDeck) {
        check(tile.dx in 0..blockMaxX && tile.dz in 0..blockMaxZ) {
            "Dragon Slayer voyage tile is off the ship block: $name=$tile " +
                "(allowed: dx 0..$blockMaxX, dz 0..$blockMaxZ)"
        }
    }
    for ((name, tile) in VoyageTiles.Cameras) {
        check(tile.dx in CameraEnvelopeX && tile.dz in CameraEnvelopeZ) {
            "Dragon Slayer voyage camera is outside the ship-block envelope - almost certainly " +
                "derived against the wrong south-west (see ShipTile): $name=$tile " +
                "(allowed: dx $CameraEnvelopeX, dz $CameraEnvelopeZ)"
        }
    }
}

private val CameraEnvelopeX = -8..32

private val CameraEnvelopeZ = -8..24

private val DescentSceneCoords: List<Pair<String, CoordGrid>>
    get() =
        listOf(
            "vantage" to DescentScene.Vantage,
            "elvarg" to DescentScene.ElvargMark,
            "elvargFaceTarget" to DescentScene.ElvargFaceTarget,
            "elvargWalkDest" to DescentScene.ElvargWalkDest,
            "openingEye" to DescentScene.Opening.eye,
            "openingLookAt" to DescentScene.Opening.lookAt,
            "pushInEye" to DescentScene.PushIn.eye,
            "pushInLookAt" to DescentScene.PushIn.lookAt,
        )

internal object DescentScene {
    val Vantage: CoordGrid = CoordGrid(2840, 9624, 0)

    val ElvargMark: CoordGrid = CoordGrid(2854, 9638, 0)

    val ElvargFaceTarget: CoordGrid = CoordGrid(2840, 9639, 0)

    val ElvargWalkDest: CoordGrid = CoordGrid(2849, 9638, 0)

    val Opening: SceneCamera =
        SceneCamera(
            eye = CoordGrid(2848, 9639, 0),
            eyeHeight = 475,
            lookAt = CoordGrid(2856, 9650, 0),
            lookAtHeight = 150,
        )

    val PushIn: SceneCamera =
        SceneCamera(
            eye = CoordGrid(2848, 9639, 0),
            eyeHeight = 250,
            lookAt = CoordGrid(2866, 9640, 0),
            lookAtHeight = 150,
        )
}
