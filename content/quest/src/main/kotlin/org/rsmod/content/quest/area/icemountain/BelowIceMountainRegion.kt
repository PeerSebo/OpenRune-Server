package org.rsmod.content.quest.area.icemountain

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.quest.area.corsaircove.SceneFrame
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid

internal object BelowIceMountainRegions {
    val Montage: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(BarbZoneX, BarbZoneZ) {
                regionZoneX = 0
                regionZoneZ = 0
                zoneWidth = BarbZoneWidth
                zoneLength = BarbZoneLength
            }
            copyAllLevels(VarrockZoneX, VarrockZoneZ) {
                regionZoneX = 0
                regionZoneZ = BarbZoneLength
                zoneWidth = VarrockZoneWidth
                zoneLength = VarrockZoneLength
                rotation = 1
            }
        }

    val RisingSun: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(RisingSunZoneX, RisingSunZoneZ) {
                regionZoneX = 6
                regionZoneZ = 6
                zoneWidth = RisingSunZoneWidth
                zoneLength = RisingSunZoneLength
            }
        }

    val RuinsEntrance: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(EntranceZoneX, EntranceZoneZ) {
                regionZoneX = 6
                regionZoneZ = 6
                zoneWidth = EntranceZoneWidth
                zoneLength = EntranceZoneLength
            }
        }

    val RuinsInterior: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(InteriorZoneX, InteriorZoneZ) {
                regionZoneX = 5
                regionZoneZ = 4
                zoneWidth = InteriorZoneWidth
                zoneLength = InteriorZoneLength
            }
        }

    const val BarbZoneX: Int = 372
    const val BarbZoneZ: Int = 426
    const val BarbZoneWidth: Int = 16
    const val BarbZoneLength: Int = 8

    const val VarrockZoneX: Int = 400
    const val VarrockZoneZ: Int = 424
    const val VarrockZoneWidth: Int = 8
    const val VarrockZoneLength: Int = 12

    const val RisingSunZoneX: Int = 368
    const val RisingSunZoneZ: Int = 420
    const val RisingSunZoneWidth: Int = 4
    const val RisingSunZoneLength: Int = 3

    const val EntranceZoneX: Int = 372
    const val EntranceZoneZ: Int = 435
    const val EntranceZoneWidth: Int = 4
    const val EntranceZoneLength: Int = 4

    const val InteriorZoneX: Int = 366
    const val InteriorZoneZ: Int = 719
    const val InteriorZoneWidth: Int = 6
    const val InteriorZoneLength: Int = 6
}

internal class SceneOrigin(
    private val liveX: Int,
    private val liveZ: Int,
    private val real: CoordGrid,
) {
    fun at(x: Int, z: Int): CoordGrid =
        CoordGrid(real.x + (x - liveX), real.z + (z - liveZ), real.level)

    fun frame(
        eyeX: Int,
        eyeZ: Int,
        eyeHeight: Int,
        lookX: Int,
        lookZ: Int,
        lookHeight: Int,
    ): SceneFrame = SceneFrame(at(eyeX, eyeZ), eyeHeight, at(lookX, lookZ), lookHeight)
}

internal object MontageCity {
    val Vantage: CoordGrid = CoordGrid(3250, 3398, 0)

    val VantageFacing: CoordGrid = CoordGrid(3250, 3395, 0)

    val WarmUpWalk: CoordGrid = CoordGrid(3243, 3402, 0)

    val WarmUpRun: CoordGrid = CoordGrid(3243, 3428, 0)

    val WarmUpRun2: CoordGrid = CoordGrid(3222, 3429, 0)

    val Opening: SceneFrame =
        SceneFrame(CoordGrid(3250, 3392, 0), 500, CoordGrid(3250, 3398, 0), 350)

    val OpeningCreep: SceneFrame =
        SceneFrame(CoordGrid(3250, 3392, 0), 500, CoordGrid(3248, 3400, 0), 280)

    val Street: SceneFrame =
        SceneFrame(CoordGrid(3243, 3413, 0), 670, CoordGrid(3243, 3402, 0), 250)

    val StreetDolly: SceneFrame =
        SceneFrame(CoordGrid(3243, 3435, 0), 430, CoordGrid(3243, 3402, 0), 250)

    val Square: SceneFrame =
        SceneFrame(CoordGrid(3250, 3429, 0), 430, CoordGrid(3243, 3429, 0), 400)

    val SquareDolly: SceneFrame =
        SceneFrame(CoordGrid(3229, 3429, 0), 430, CoordGrid(3222, 3429, 0), 400)

    val ClosingVantage: CoordGrid = CoordGrid(3211, 3399, 0)

    val ClosingFacing: CoordGrid = CoordGrid(3211, 3422, 0)

    val ClosingRun: CoordGrid = CoordGrid(3211, 3421, 0)

    val ClosingRun2: CoordGrid = CoordGrid(3212, 3467, 0)

    val ClosingStopFacing: CoordGrid = CoordGrid(3212, 3466, 0)

    val Closing: SceneFrame =
        SceneFrame(CoordGrid(3211, 3394, 0), 300, CoordGrid(3211, 3399, 0), 280)

    val ClosingDolly: SceneFrame =
        SceneFrame(CoordGrid(3211, 3422, 0), 300, CoordGrid(3211, 3432, 0), 280)

    val ClosingRise: SceneFrame =
        SceneFrame(CoordGrid(3211, 3422, 0), 600, CoordGrid(3212, 3447, 0), 280)

    val Guards: SceneFrame =
        SceneFrame(CoordGrid(3212, 3442, 0), 500, CoordGrid(3212, 3432, 0), 280)

    val GuardsCreep: SceneFrame =
        SceneFrame(CoordGrid(3212, 3447, 0), 500, CoordGrid(3212, 3462, 0), 400)

    val GuardsRise: SceneFrame =
        SceneFrame(CoordGrid(3217, 3461, 0), 700, CoordGrid(3212, 3464, 0), 650)

    val Hobo: CoordGrid = CoordGrid(3248, 3397, 0)

    val Rat: CoordGrid = CoordGrid(3252, 3396, 0)

    val Aubury: CoordGrid = CoordGrid(3253, 3400, 0)

    val Man: CoordGrid = CoordGrid(3241, 3406, 0)

    val Man2: CoordGrid = CoordGrid(3245, 3417, 0)

    val Man2Dest: CoordGrid = CoordGrid(3245, 3406, 0)

    val Woman: CoordGrid = CoordGrid(3228, 3431, 0)

    val WomanFacing: CoordGrid = CoordGrid(3242, 3435, 0)

    val TomatoSplat: CoordGrid = CoordGrid(3237, 3429, 0)

    val SquareMan: CoordGrid = CoordGrid(3219, 3432, 0)

    val Baraek: CoordGrid = CoordGrid(3215, 3437, 0)

    val Dog: CoordGrid = CoordGrid(3212, 3407, 0)

    val ClosingMan: CoordGrid = CoordGrid(3219, 3432, 0)

    val ClosingBaraek: CoordGrid = CoordGrid(3215, 3435, 0)

    val GuardF: CoordGrid = CoordGrid(3216, 3459, 0)

    val GuardVariant: CoordGrid = CoordGrid(3208, 3464, 0)

    val Guard: CoordGrid = CoordGrid(3210, 3464, 0)

    fun coords(): List<Pair<String, CoordGrid>> =
        listOf(
            "city.vantage" to Vantage,
            "city.vantageFacing" to VantageFacing,
            "city.warmUpWalk" to WarmUpWalk,
            "city.warmUpRun" to WarmUpRun,
            "city.warmUpRun2" to WarmUpRun2,
            "city.closingVantage" to ClosingVantage,
            "city.closingFacing" to ClosingFacing,
            "city.closingRun" to ClosingRun,
            "city.closingRun2" to ClosingRun2,
            "city.closingStopFacing" to ClosingStopFacing,
            "city.hobo" to Hobo,
            "city.rat" to Rat,
            "city.aubury" to Aubury,
            "city.man" to Man,
            "city.man2" to Man2,
            "city.man2Dest" to Man2Dest,
            "city.woman" to Woman,
            "city.womanFacing" to WomanFacing,
            "city.tomatoSplat" to TomatoSplat,
            "city.squareMan" to SquareMan,
            "city.baraek" to Baraek,
            "city.dog" to Dog,
            "city.closingMan" to ClosingMan,
            "city.closingBaraek" to ClosingBaraek,
            "city.guardF" to GuardF,
            "city.guardVariant" to GuardVariant,
            "city.guard" to Guard,
        ) +
            listOf(
                    Opening,
                    OpeningCreep,
                    Street,
                    StreetDolly,
                    Square,
                    SquareDolly,
                    Closing,
                    ClosingDolly,
                    ClosingRise,
                    Guards,
                    GuardsCreep,
                    GuardsRise,
                )
                .flatMapIndexed { i, frame -> frame.coords("city.frame$i") }
}

internal object MontageBarbarian {
    val Atlas: CoordGrid = CoordGrid(3074, 3424, 0)

    val Watcher: CoordGrid = CoordGrid(3074, 3420, 0)

    val Watchers: List<CoordGrid> =
        listOf(
            CoordGrid(3073, 3421, 0),
            CoordGrid(3073, 3422, 0),
            CoordGrid(3074, 3420, 0),
            CoordGrid(3073, 3420, 0),
        )

    val PushupMark: CoordGrid = CoordGrid(3076, 3423, 0)

    val PushupFacing: CoordGrid = CoordGrid(3076, 3424, 0)

    val Counting: SceneFrame =
        SceneFrame(CoordGrid(3079, 3425, 0), 300, CoordGrid(3075, 3423, 0), 280)

    val FlexFacing: CoordGrid = CoordGrid(3079, 3425, 0)

    val SparAtlas: CoordGrid = CoordGrid(3079, 3435, 0)

    val SparPlayer: CoordGrid = CoordGrid(3078, 3435, 0)

    val Sparring: SceneFrame =
        SceneFrame(CoordGrid(3078, 3431, 0), 290, CoordGrid(3078, 3438, 0), 280)

    val RoadStart: CoordGrid = CoordGrid(3074, 3417, 0)

    val RoadLeg: CoordGrid = CoordGrid(3040, 3417, 0)

    val RoadLeg2: CoordGrid = CoordGrid(3014, 3429, 0)

    val Running: SceneFrame =
        SceneFrame(CoordGrid(3069, 3411, 0), 600, CoordGrid(3069, 3422, 0), 280)

    val RunningDolly: SceneFrame =
        SceneFrame(CoordGrid(3043, 3411, 0), 400, CoordGrid(3066, 3417, 0), 280)

    val RunningSettle: SceneFrame =
        SceneFrame(CoordGrid(3033, 3427, 0), 400, CoordGrid(3013, 3428, 0), 0)

    val RunningRise: SceneFrame =
        SceneFrame(CoordGrid(3033, 3432, 0), 600, CoordGrid(3013, 3428, 0), 0)

    val RunningEnd: SceneFrame =
        SceneFrame(CoordGrid(3033, 3432, 0), 600, CoordGrid(3000, 3432, 0), 0)

    fun coords(): List<Pair<String, CoordGrid>> =
        listOf(
            "barb.atlas" to Atlas,
            "barb.watcher" to Watcher,
            "barb.pushupMark" to PushupMark,
            "barb.pushupFacing" to PushupFacing,
            "barb.flexFacing" to FlexFacing,
            "barb.sparAtlas" to SparAtlas,
            "barb.sparPlayer" to SparPlayer,
            "barb.roadStart" to RoadStart,
            "barb.roadLeg" to RoadLeg,
            "barb.roadLeg2" to RoadLeg2,
        ) +
            Watchers.mapIndexed { i, coord -> "barb.watcher$i" to coord } +
            listOf(
                    Counting,
                    Sparring,
                    Running,
                    RunningDolly,
                    RunningSettle,
                    RunningRise,
                    RunningEnd,
                )
                .flatMapIndexed { i, frame -> frame.coords("barb.frame$i") }
}

internal object RockPaperScissorsScene {
    val Origin: SceneOrigin = SceneOrigin(11092, 15057, CoordGrid(2956, 3369, 1))

    val Vantage: CoordGrid = Origin.at(11092, 15057)

    val Burntof: CoordGrid = Origin.at(11092, 15058)

    val Tina: CoordGrid = Origin.at(11092, 15061)

    val TinaServePath: List<CoordGrid> =
        listOf(Origin.at(11091, 15061), Origin.at(11091, 15059), Origin.at(11092, 15059))

    val TinaServe: CoordGrid = Origin.at(11092, 15059)

    val Camera: SceneFrame = Origin.frame(11097, 15057, 400, 11091, 15057, 180)

    val Exit: CoordGrid = CoordGrid(2956, 3368, 0)

    fun coords(): List<Pair<String, CoordGrid>> =
        listOf(
            "rps.vantage" to Vantage,
            "rps.burntof" to Burntof,
            "rps.tina" to Tina,
            "rps.tinaServe" to TinaServe,
        ) +
            TinaServePath.mapIndexed { i, coord -> "rps.tinaPath$i" to coord } +
            Camera.coords("rps.frame")
}

internal object ExcavationScene {
    val Origin: SceneOrigin = SceneOrigin(11891, 15079, CoordGrid(2995, 3495, 0))

    val Vantage: CoordGrid = Origin.at(11891, 15079)

    val Checkal: CoordGrid = Origin.at(11890, 15078)

    val Burntof: CoordGrid = Origin.at(11892, 15076)
    val Willow: CoordGrid = Origin.at(11893, 15078)
    val Marley: CoordGrid = Origin.at(11892, 15080)

    val BurntofCharge: CoordGrid = Origin.at(11894, 15078)

    val ChargeMarker: CoordGrid = Origin.at(11895, 15078)

    val FuseShuffles: List<CoordGrid> =
        listOf(Origin.at(11896, 15078), Origin.at(11896, 15077), Origin.at(11896, 15078))

    val CheckalCover: CoordGrid = Origin.at(11889, 15084)

    val WillowCover: CoordGrid = Origin.at(11893, 15072)
    val MarleyCover: CoordGrid = Origin.at(11892, 15084)

    val BurntofRetreat: CoordGrid = Origin.at(11890, 15073)

    val PlayerCover: CoordGrid = Origin.at(11887, 15085)

    val BurntofRegroup: CoordGrid = Origin.at(11891, 15075)

    val WillowRegroup: CoordGrid = Origin.at(11893, 15076)
    val MarleyRegroup: CoordGrid = Origin.at(11891, 15079)
    val CheckalRegroup: CoordGrid = Origin.at(11890, 15077)
    val PlayerRegroup: CoordGrid = Origin.at(11892, 15080)

    val PlayerRegroupFacing: CoordGrid = Origin.at(11892, 15078)

    val CrewRegroupFacing: CoordGrid = Origin.at(11891, 15077)

    val Opening: SceneFrame = Origin.frame(11886, 15074, 600, 11893, 15078, 400)

    val Cover: SceneFrame = Origin.frame(11885, 15085, 700, 11893, 15078, 350)

    val CoverCreep: SceneFrame = Origin.frame(11887, 15084, 800, 11893, 15078, 400)

    val Blast: List<CoordGrid> =
        listOf(Origin.at(11894, 15076), Origin.at(11894, 15077), Origin.at(11894, 15078))

    val BlastSound: CoordGrid = Origin.at(11892, 15077)

    val BlastSoundP2: CoordGrid = Origin.at(11891, 15076)

    val Door: CoordGrid = Origin.at(11895, 15077)

    val Rubble: CoordGrid = Origin.at(11894, 15077)

    val Hauling: List<CoordGrid> =
        listOf(Origin.at(11893, 15078), Origin.at(11890, 15078), Origin.at(11893, 15077))

    fun coords(): List<Pair<String, CoordGrid>> =
        listOf(
            "excav.vantage" to Vantage,
            "excav.checkal" to Checkal,
            "excav.burntof" to Burntof,
            "excav.willow" to Willow,
            "excav.marley" to Marley,
            "excav.burntofCharge" to BurntofCharge,
            "excav.chargeMarker" to ChargeMarker,
            "excav.checkalCover" to CheckalCover,
            "excav.willowCover" to WillowCover,
            "excav.marleyCover" to MarleyCover,
            "excav.burntofRetreat" to BurntofRetreat,
            "excav.playerCover" to PlayerCover,
            "excav.burntofRegroup" to BurntofRegroup,
            "excav.willowRegroup" to WillowRegroup,
            "excav.marleyRegroup" to MarleyRegroup,
            "excav.checkalRegroup" to CheckalRegroup,
            "excav.playerRegroup" to PlayerRegroup,
            "excav.playerRegroupFacing" to PlayerRegroupFacing,
            "excav.crewRegroupFacing" to CrewRegroupFacing,
            "excav.blastSound" to BlastSound,
            "excav.blastSoundP2" to BlastSoundP2,
            "excav.door" to Door,
            "excav.rubble" to Rubble,
        ) +
            FuseShuffles.mapIndexed { i, coord -> "excav.fuseShuffle$i" to coord } +
            Blast.mapIndexed { i, coord -> "excav.blast$i" to coord } +
            Hauling.mapIndexed { i, coord -> "excav.haul$i" to coord } +
            listOf(Opening, Cover, CoverCreep).flatMapIndexed { i, frame ->
                frame.coords("excav.frame$i")
            }
}

internal object CorridorScene {
    val Origin: SceneOrigin = SceneOrigin(11944, 15139, CoordGrid(2952, 5763, 0))

    val Vantage: CoordGrid = Origin.at(11944, 15139)

    val Doorway: CoordGrid = Origin.at(11943, 15137)

    val EntryFacing: CoordGrid = Origin.at(11944, 15141)

    val WalkMid: CoordGrid = Origin.at(11944, 15140)

    val WireTile: CoordGrid = Origin.at(11944, 15142)

    val StepOver: CoordGrid = Origin.at(11944, 15143)

    val Tripwires: List<CoordGrid> = listOf(Origin.at(11944, 15142), Origin.at(11945, 15142))

    val Opening: SceneFrame = Origin.frame(11946, 15147, 500, 11944, 15140, 250)

    val Settle: SceneFrame = Origin.frame(11946, 15148, 500, 11944, 15140, 250)

    val Follow: SceneFrame = Origin.frame(11946, 15149, 500, 11944, 15142, 250)

    fun coords(): List<Pair<String, CoordGrid>> =
        listOf(
            "corridor.vantage" to Vantage,
            "corridor.doorway" to Doorway,
            "corridor.entryFacing" to EntryFacing,
            "corridor.walkMid" to WalkMid,
            "corridor.wireTile" to WireTile,
            "corridor.stepOver" to StepOver,
        ) +
            Tripwires.mapIndexed { i, coord -> "corridor.wire$i" to coord } +
            listOf(Opening, Settle, Follow).flatMapIndexed { i, frame ->
                frame.coords("corridor.frame$i")
            }
}

internal object RuinsInteriorScene {
    val Origin: SceneOrigin = SceneOrigin(12232, 15054, CoordGrid(2952, 5774, 0))

    val Vantage: CoordGrid = Origin.at(12233, 15049)

    val Checkal: CoordGrid = Origin.at(12236, 15050)

    val Marley: CoordGrid = Origin.at(12232, 15052)
    val Willow: CoordGrid = Origin.at(12233, 15051)
    val Burntof: CoordGrid = Origin.at(12230, 15052)

    val Opening: SceneFrame = Origin.frame(12232, 15041, 200, 12232, 15048, 50)

    val Rise: SceneFrame = Origin.frame(12232, 15043, 800, 12232, 15051, 50)

    val DriftEast: SceneFrame = Origin.frame(12239, 15048, 800, 12232, 15051, 50)

    val DriftBack: SceneFrame = Origin.frame(12232, 15043, 800, 12232, 15053, 70)

    val Quake: SceneFrame = Origin.frame(12226, 15048, 700, 12233, 15054, 100)

    val QuakeFalls: List<Pair<CoordGrid, Int>> =
        listOf(
            Origin.at(12237, 15052) to 160,
            Origin.at(12236, 15056) to 0,
            Origin.at(12233, 15057) to 180,
            Origin.at(12230, 15055) to 50,
            Origin.at(12230, 15052) to 90,
        )

    val GuardianReveal: SceneFrame = Origin.frame(12228, 15051, 700, 12233, 15058, 100)

    val GuardianSettle: SceneFrame = Origin.frame(12228, 15051, 700, 12232, 15058, 150)

    val GuardianMark: CoordGrid = Origin.at(12232, 15058)

    val BurntofWanders: List<CoordGrid> =
        listOf(
            Origin.at(12229, 15055),
            Origin.at(12231, 15058),
            Origin.at(12231, 15055),
            Origin.at(12233, 15055),
        )

    val Pillars: List<Pair<CoordGrid, LocAngle>> =
        listOf(
            Origin.at(12227, 15051) to LocAngle.South,
            Origin.at(12227, 15058) to LocAngle.South,
            Origin.at(12238, 15051) to LocAngle.North,
            Origin.at(12238, 15058) to LocAngle.North,
        )

    val ArenaSound: CoordGrid = Origin.at(12237, 15053)

    val BagVantage: CoordGrid = Origin.at(12232, 15053)

    val AftermathFacing: CoordGrid = Origin.at(12232, 15054)

    val BagWalk: CoordGrid = Origin.at(12231, 15049)

    val Bag: CoordGrid = Origin.at(12230, 15049)

    val Exit: CoordGrid = Origin.at(12231, 15041)

    val Aftermath: SceneFrame = Origin.frame(12237, 15057, 450, 12232, 15053, 300)

    val BagCreep: SceneFrame = Origin.frame(12235, 15052, 450, 12231, 15049, 300)

    val DoorReveal: SceneFrame = Origin.frame(12235, 15052, 450, 12232, 15058, 250)

    val DoorClosed: CoordGrid = Origin.at(12231, 15060)

    val DoorLeft: CoordGrid = Origin.at(12231, 15060)
    val DoorRight: CoordGrid = Origin.at(12234, 15060)

    val CutscenePillars: List<Pair<CoordGrid, LocAngle>> = Pillars

    fun coords(): List<Pair<String, CoordGrid>> =
        listOf(
            "ruins.vantage" to Vantage,
            "ruins.checkal" to Checkal,
            "ruins.marley" to Marley,
            "ruins.willow" to Willow,
            "ruins.burntof" to Burntof,
            "ruins.guardian" to GuardianMark,
            "ruins.arenaSound" to ArenaSound,
            "ruins.bagVantage" to BagVantage,
            "ruins.aftermathFacing" to AftermathFacing,
            "ruins.bagWalk" to BagWalk,
            "ruins.bag" to Bag,
            "ruins.exit" to Exit,
            "ruins.doorClosed" to DoorClosed,
            "ruins.doorLeft" to DoorLeft,
            "ruins.doorRight" to DoorRight,
        ) +
            BurntofWanders.mapIndexed { i, coord -> "ruins.burntofWander$i" to coord } +
            Pillars.mapIndexed { i, (coord, _) -> "ruins.pillar$i" to coord } +
            QuakeFalls.mapIndexed { i, (coord, _) -> "ruins.quake$i" to coord } +
            listOf(
                    Opening,
                    Rise,
                    DriftEast,
                    DriftBack,
                    Quake,
                    GuardianReveal,
                    GuardianSettle,
                    Aftermath,
                    BagCreep,
                    DoorReveal,
                )
                .flatMapIndexed { i, frame -> frame.coords("ruins.frame$i") }
}

internal fun assertBelowIceMountainSceneCoords() {
    val templates =
        listOf(
            BelowIceMountainRegions.Montage,
            BelowIceMountainRegions.RisingSun,
            BelowIceMountainRegions.RuinsEntrance,
            BelowIceMountainRegions.RuinsInterior,
        )
    check(templates.size == 4) { "Below Ice Mountain cutscene region templates failed to build." }

    assertInsideMontage(MontageCity.coords() + MontageBarbarian.coords())

    assertInside(
        block = "RisingSun",
        zoneX = BelowIceMountainRegions.RisingSunZoneX,
        zoneZ = BelowIceMountainRegions.RisingSunZoneZ,
        zoneWidth = BelowIceMountainRegions.RisingSunZoneWidth,
        zoneLength = BelowIceMountainRegions.RisingSunZoneLength,
        coords = RockPaperScissorsScene.coords(),
    )

    assertInside(
        block = "RuinsEntrance",
        zoneX = BelowIceMountainRegions.EntranceZoneX,
        zoneZ = BelowIceMountainRegions.EntranceZoneZ,
        zoneWidth = BelowIceMountainRegions.EntranceZoneWidth,
        zoneLength = BelowIceMountainRegions.EntranceZoneLength,
        coords = ExcavationScene.coords(),
    )

    assertInside(
        block = "RuinsInterior",
        zoneX = BelowIceMountainRegions.InteriorZoneX,
        zoneZ = BelowIceMountainRegions.InteriorZoneZ,
        zoneWidth = BelowIceMountainRegions.InteriorZoneWidth,
        zoneLength = BelowIceMountainRegions.InteriorZoneLength,
        coords = CorridorScene.coords() + RuinsInteriorScene.coords(),
    )
}

private fun assertInsideMontage(coords: List<Pair<String, CoordGrid>>) {
    val barbX = BelowIceMountainRegions.BarbZoneX * ZoneSize
    val barbZ = BelowIceMountainRegions.BarbZoneZ * ZoneSize
    val barbXs = barbX until barbX + BelowIceMountainRegions.BarbZoneWidth * ZoneSize
    val barbZs = barbZ until barbZ + BelowIceMountainRegions.BarbZoneLength * ZoneSize
    val varrockX = BelowIceMountainRegions.VarrockZoneX * ZoneSize
    val varrockZ = BelowIceMountainRegions.VarrockZoneZ * ZoneSize
    val varrockXs = varrockX until varrockX + BelowIceMountainRegions.VarrockZoneWidth * ZoneSize
    val varrockZs = varrockZ until varrockZ + BelowIceMountainRegions.VarrockZoneLength * ZoneSize
    for ((name, coord) in coords) {
        val inBarb = coord.x in barbXs && coord.z in barbZs
        val inVarrock = coord.x in varrockXs && coord.z in varrockZs
        check(inBarb || inVarrock) {
            "Below Ice Mountain montage coord is outside both copied bands, so " +
                "`region.normal[...]` would throw mid-scene: $name=$coord " +
                "(allowed: x ${barbXs.first}..${barbXs.last} z ${barbZs.first}..${barbZs.last}, " +
                "or x ${varrockXs.first}..${varrockXs.last} z ${varrockZs.first}..${varrockZs.last})"
        }
    }
}

private fun assertInside(
    block: String,
    zoneX: Int,
    zoneZ: Int,
    zoneWidth: Int,
    zoneLength: Int,
    coords: List<Pair<String, CoordGrid>>,
) {
    val originX = zoneX * ZoneSize
    val originZ = zoneZ * ZoneSize
    val width = zoneWidth * ZoneSize
    val length = zoneLength * ZoneSize
    for ((name, coord) in coords) {
        check(
            coord.x in originX until originX + width && coord.z in originZ until originZ + length
        ) {
            "Below Ice Mountain scene coord is outside the copied $block block, so " +
                "`region.normal[...]` would throw mid-scene: $name=$coord " +
                "(allowed: x ${originX}..${originX + width - 1}, " +
                "z ${originZ}..${originZ + length - 1})"
        }
    }
}

private const val ZoneSize = 8
