package org.rsmod.content.quest.area.crandor

import org.rsmod.map.CoordGrid

// Only the stage occupies this varp, so a whole-varp stage mirror is safe.
public object DragonSlayerStage {
    const val NotStarted: Int = 0

    const val Started: Int = 1

    const val OziachSpoken: Int = 2

    const val ShipBought: Int = 3

    // TODO: Verify the meaning of stages 4 and 5.
    const val UncapturedFour: Int = 4

    const val UncapturedFive: Int = 5

    const val ShipRepaired: Int = 6

    const val MapGivenToNed: Int = 7

    const val Shipwrecked: Int = 8

    const val ElvargSlain: Int = 9

    const val Complete: Int = 10
}

internal object DragonSlayerRefs {
    const val QuestKey: String = "quest_dragonslayer1"

    const val QuestVarp: String = "varp.dragonquest"

    const val CompletionJingle: Int = 152

    const val Guildmaster: String = "npc.guildmaster"

    const val Oziach: String = "npc.oziach"

    const val Oracle: String = "npc.oracle"

    const val Wormbrain: String = "npc.wormbrain"

    const val Klarense: String = "npc.klarense"

    const val Ned: String = "npc.ned"

    const val NedOnShip: String = "npc.dragonslayer_ned_on_ship"

    const val NedCutscene: String = "npc.dragonslayer_ned_cutscene"

    const val JenkinsCutscene: String = "npc.dragonslayer_jenkins_cutscene"

    // The corpse is a separate visible scene actor, not Elvarg's live multinpc leaf.
    const val ElvargCutscene: String = "npc.dragonslayer_elvarg_cutscene"

    // Elvarg renders only at stages 1-8; the client resolves the parent and hides it at stages 9-10.
    const val Elvarg: String = "npc.elvarg"

    const val MelzarKey: String = "obj.melzarkey"

    const val RedKey: String = "obj.redkey"

    const val OrangeKey: String = "obj.orangekey"

    const val YellowKey: String = "obj.yellowkey"

    const val BlueKey: String = "obj.bluekey"

    const val MagentaKey: String = "obj.magentakey"

    const val GreenKey: String = "obj.greenkey"

    const val MapPartMelzar: String = "obj.mappart1"

    const val MapPartLozar: String = "obj.mappart2"

    const val MapPartThalzar: String = "obj.mappart3"

    const val DragonMap: String = "obj.dragonmap"

    const val AntiDragonShield: String = "obj.antidragonbreathshield"

    const val ElvargHead: String = "obj.dragon_slayer_qip_elvargs_head"

    const val WoodPlank: String = "obj.woodplank"

    const val Nails: String = "obj.nails"

    const val Hammer: String = "obj.hammer"

    const val Coins: String = "obj.coins"

    const val Silk: String = "obj.silk"

    const val LobsterPot: String = "obj.lobster_pot"

    const val UnfiredBowl: String = "obj.bowl_unfired"

    const val MindBomb: String = "obj.wizards_mind_bomb"

    const val BeerGlass: String = "obj.beer_glass"

    const val MelzarDoor: String = "loc.melzardoor"

    const val MelzarDoorOpen: String = "loc.inactivepoordoor"

    const val RedDoor: String = "loc.reddoor"

    const val RedDoorOpen: String = "loc.reddoor_open"

    const val OrangeDoor: String = "loc.orangedoor"

    const val OrangeDoorOpen: String = "loc.orangedoor_open"

    const val YellowDoor: String = "loc.yellowdoor"

    const val YellowDoorOpen: String = "loc.yellowdoor_open"

    const val BlueDoor: String = "loc.bluedoor"

    const val BlueDoorOpen: String = "loc.bluedoor_open"

    const val MagentaDoor: String = "loc.magentadoor"

    const val MagentaDoorOpen: String = "loc.magentadoor_open"

    const val GreenDoor: String = "loc.greendoor"

    const val GreenDoorOpen: String = "loc.greendoor_open"

    const val FunExit: String = "loc.funexit"

    const val FunExitOpen: String = "loc.funexit_open"

    const val FunChestShut: String = "loc.funchestshut"

    const val FunChestOpen: String = "loc.funchestopen"

    const val FunLadderTop: String = "loc.funladdertop"

    const val SecretDoor: String = "loc.dragonsecretdoor"

    const val SecretDoorOpen: String = "loc.dragonsecretdoor_open"

    const val OracleChestShut: String = "loc.oraclechestshut"

    const val OracleChestOpen: String = "loc.oraclechestopen"

    const val MagicDoor: String = "loc.dragon_slayer_qip_magic_door"

    const val MagicDoorOpen: String = "loc.dragon_slayer_qip_magic_door_open"

    const val MagicDoorRight: String = "loc.dragon_slayer_qip_magic_door_right"

    const val MagicDoorLeft: String = "loc.dragon_slayer_qip_magic_door_left"

    const val MagicDoorFiller: String = "loc.dragon_slayer_qip_magic_door2"

    const val GangplankOn: String = "loc.dragonshipgangplank_on"

    const val GangplankOff: String = "loc.dragonshipgangplank_off"

    const val ShipLadderTop: String = "loc.dragonshipladdertop"

    const val ShipLadder: String = "loc.dragonshipladder"

    const val ShipHole: String = "loc.dragonslayer_shiphole"

    const val ShipHull: String = "loc.dragonslayer_shiphull"

    const val RuinEntrance: String = "loc.dragon_slayer_qip_ruin_entrance"

    const val ClimbingRope: String = "loc.dragon_slayer_qip_climbing_rope"

    const val PrisonCellDoor: String = "loc.prisonbarsdoor_locked"

    const val StalagtiteJump: String = "loc.dragon_slayer_qip_stalagtite_jump"

    const val DeckFireLarge: String = "loc.dragon_slayer_qip_fire_2x2"

    const val SpotanimDeckFire: String = "spotanim.dragon_slayer_qip_fire_spotanim"

    const val DeckFireSmall: String = "loc.dragon_slayer_qip_fire_1x1"

    const val ElvargCorpseFullBody: String = "loc.dragon_slayer_qip_elvarg_full_body"

    const val ElvargCorpseNoHead: String = "loc.dragon_slayer_qip_elvarg_body_no_head"

    const val SeqClimbLadder: String = "seq.human_reachforladder"

    const val SeqTrapdoor: String = "seq.human_pickupfloor"

    const val SeqNailPlank: String = "seq.human_poh_build"

    const val SeqOpenChest: String = "seq.human_openchest"

    const val SeqClimbWall: String = "seq.human_walk_style"

    const val SeqRemoveHead1: String = "seq.dragon_slayer_qip_remove_head_part1"

    const val SeqRemoveHead2: String = "seq.dragon_slayer_qip_remove_head_part2"

    const val SeqPanic: String = "seq.emote_panic"

    const val SeqPanicFlap: String = "seq.emote_panic_flap"

    const val SeqJenkinsScared: String = "seq.dragon_slayer_qip_jen_scared"

    const val SeqHumanDeath: String = "seq.human_death"

    const val SynthDoorOpen: String = "synth.DOOR_OPEN"

    const val SynthLocked: String = "synth.LOCKED"

    const val SynthChestOpen: String = "synth.CHEST_OPEN"

    const val SynthChestClose: String = "synth.CHEST_CLOSE"

    const val SynthNailPlank: String = "synth.POH_BUILD_WOOD"

    const val SynthClimbWall: String = "synth.CLIMB_WALL"

    const val SynthThunder: String = "synth.DRAGONSLAYER_THUNDER"

    const val SynthFlapWings: String = "synth.DRAGONSLAYER_FLAPWINGS"

    const val SynthDragonBreath: String = "synth.DRAGONSLAYER_DRAGONBREATH"

    const val SynthBurningFire: String = "synth.DRAGONSLAYER_BURNINGFIRE"

    const val SynthDragonScream: String = "synth.DRAGONSLAYER_DRAGONSCREAM"

    const val SynthCrashShip: String = "synth.DRAGONSLAYER_CRASH_SHIP"

    const val SynthDragonWalk: String = "synth.DRAGONSLAYER_DRAGONWALK"

    const val SynthHumanHit: String = "synth.HUMAN_HIT"

    const val SynthHumanDeath: String = "synth.HUMAN_DEATH"

    const val SynthCutOffHead1: String = "synth.DRAGONSLAYER_CUTOFFHEAD1"

    const val SynthCutOffHead2: String = "synth.DRAGONSLAYER_CUTOFFHEAD2"

    const val JenkinsDeathHitmark: String = "hitmark.regular_damage_tint"

    const val JenkinsDeathHeadbar: String = "headbar.health_30"

    const val OverlayClouds: String = "interface.dragon_slayer_qip_clouds"

    const val OverlayElvarg: String = "interface.dragon_slayer_qip_elvarg"

    const val OverlayElvargFly: String = "interface.dragon_slayer_qip_elvarg_fly"

    const val CrandorMapInterface: String = "interface.dragon_slayer_qip_map"

    const val VoyageJingle: Int = 172

    const val ElvargSlainJingle: Int = 267

    const val MusicCrestOfAWave: String = "Crest of a Wave"

    const val MusicEyeOfTheStorm: String = "Eye of the Storm"

    const val MusicTheShadow: String = "The Shadow"

    const val MusicAttack2: String = "Attack 2"

    const val MusicDangerousRoad: String = "Dangerous Road"

    val ShipDeck: CoordGrid = CoordGrid(3047, 3207, 1)

    val ShipQuay: CoordGrid = CoordGrid(3047, 3204, 0)

    val ShipHold: CoordGrid = CoordGrid(3048, 9640, 1)

    val ShipDeckFromHold: CoordGrid = CoordGrid(3048, 3208, 1)

    val MazeBasement: CoordGrid = CoordGrid(2933, 9640, 0)

    val CrandorBeach: CoordGrid = CoordGrid(2849, 3238, 0)

    val CrandorDungeon: CoordGrid = CoordGrid(2833, 9658, 0)

    val CrandorSurface: CoordGrid = CoordGrid(2833, 3254, 0)

    val BeachPlanks: List<CoordGrid> =
        listOf(
            CoordGrid(2851, 3239, 0),
            CoordGrid(2856, 3236, 0),
            CoordGrid(2847, 3232, 0),
            CoordGrid(2847, 3238, 0),
        )
}
