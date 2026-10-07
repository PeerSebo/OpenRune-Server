package org.rsmod.content.quest.area.lumbridge

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.typeCoordVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal object XMarksTheSpotState {

    const val QuestKey: String = "quest_xmarksthespot"

    // The stage shares varp 2111 with eleven other fields; never overwrite the whole varp.
    const val QuestVarp: String = "varp.cluequest_main"

    const val StageVarbit: String = "varbit.cluequest"

    const val NotStarted: Int = 0

    const val Agreed: Int = 1

    const val Clue1Given: Int = 2

    const val Clue2Found: Int = 3

    const val OrbFound: Int = 4

    const val Clue4Found: Int = 5

    const val CasketFound: Int = 6

    const val CasketGiven: Int = 7

    const val Complete: Int = 8

    const val Clue1: String = "obj.cluequest_clue1"
    const val Clue2: String = "obj.cluequest_clue2"
    const val Orb: String = "obj.cluequest_clue3"
    const val Clue4: String = "obj.cluequest_clue4"
    const val AncientCasket: String = "obj.cluequest_casket"
    const val AntiqueLamp: String = "obj.cluequest_lamp"
    const val Coins: String = "obj.coins"

    const val BeginnerScrollBox: String = "obj.league_clue_box_beginner"

    const val CoinReward: Int = 200

    const val LampXp: Double = 300.0

    val Clue1Dig: CoordGrid = CoordGrid(3230, 3209)

    val Clue2Dig: CoordGrid = CoordGrid(3203, 3212)

    val Clue3Dig: CoordGrid = CoordGrid(3109, 3264)

    val Clue4Dig: CoordGrid = CoordGrid(3078, 3259)

    const val VeosLumbridge: String = "npc.veos_lumbridge"

    const val VeosSarim: String = "npc.veos_sarim"

    const val SynthLampReward: String = "synth.FOUND_GEM"

    const val SynthOrbCold: String = "synth.HISTORY_KEY_COLD"
    const val SynthOrbWarm: String = "synth.HISTORY_KEY_WARM"
    const val SynthOrbHot: String = "synth.HISTORY_KEY_HOT"

    const val CompletionJingle: Int = 154
}

// Store distance plus one so zero remains the never-felt sentinel.
internal var ProtectedAccess.cluequestPrevDistance by intVarBit("varbit.cluequest_prev_distance")

internal var ProtectedAccess.cluequestPrevCoord by typeCoordVarp("varp.cluequest_prev_coord")

internal var ProtectedAccess.cluequestCoinReward by boolVarBit("varbit.cluequest_coin_reward")

internal var ProtectedAccess.cluequestLampReward by boolVarBit("varbit.cluequest_lamp_reward")

internal var ProtectedAccess.cluequestClueReward by boolVarBit("varbit.cluequest_clue_reward")

internal var ProtectedAccess.cluequestVeosAlreadyMet by
    boolVarBit("varbit.cluequest_veos_already_met")

internal var Player.veosLumbridgeVis by intVarBit("varbit.veos_lumbridge_vis")

internal var Player.veosSarimVis by intVarBit("varbit.veos_sarim_vis")

internal object VeosLumbridgeVisibility {
    const val Gone: Int = 0

    const val Present: Int = 2
}

internal object VeosSarimVisibility {
    const val Present: Int = 3
}
