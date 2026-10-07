package org.rsmod.content.quest.area.goblinvillage

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit

internal object GoblinDiplomacyState {
    const val QuestKey: String = "quest_goblindiplomacy"

    const val QuestVarp: String = "varp.goblinquest"

    const val StageVarbit: String = "varbit.gobdip_main"

    // Stages 1 and 2 are unused; incrementing from zero would strand the quest.

    const val NotStarted: Int = 0

    const val WantOrange: Int = 3

    const val WantBlue: Int = 4

    const val WantBrown: Int = 5

    const val Complete: Int = 6

    const val BrownMail: String = "obj.goblin_armour"

    const val OrangeMail: String = "obj.goblin_armour_orange"

    const val BlueMail: String = "obj.goblin_armour_darkblue"

    const val BlueDye: String = "obj.bluedye"
    const val OrangeDye: String = "obj.orangedye"

    const val GoldBar: String = "obj.gold_bar"

    const val CraftingXp: Double = 200.0

    const val CompletionJingle: Int = 153

    const val VillageMusic: Int = 313

    const val DyeSound: String = "synth.DRIP_LIQUID"
}

internal enum class GoblinMailColour(
    val obj: String,
    val spokenName: String,
    val grubfootVis: Int,
    val cutsceneNpc: String,
    val requestedAtStage: Int,
    val nextStage: Int,
) {
    Orange(
        obj = GoblinDiplomacyState.OrangeMail,
        spokenName = "orange",
        grubfootVis = 1,
        cutsceneNpc = "npc.catwalk_goblin_orange_noop",
        requestedAtStage = GoblinDiplomacyState.WantOrange,
        nextStage = GoblinDiplomacyState.WantBlue,
    ),
    Blue(
        obj = GoblinDiplomacyState.BlueMail,
        spokenName = "blue",
        grubfootVis = 2,
        cutsceneNpc = "npc.catwalk_goblin_blue_noop",
        requestedAtStage = GoblinDiplomacyState.WantBlue,
        nextStage = GoblinDiplomacyState.WantBrown,
    ),
    Brown(
        obj = GoblinDiplomacyState.BrownMail,
        spokenName = "brown",
        grubfootVis = 0,
        cutsceneNpc = "npc.catwalk_goblin_brown_noop",
        requestedAtStage = GoblinDiplomacyState.WantBrown,
        nextStage = GoblinDiplomacyState.Complete,
    );

    companion object {
        fun requestedAt(stage: Int): GoblinMailColour? =
            entries.find { it.requestedAtStage == stage }

        fun leafForVis(vis: Int): String =
            entries.find { it.grubfootVis == vis }?.cutsceneNpc ?: Brown.cutsceneNpc
    }
}

internal enum class GoblinMailCrate(val loc: String, val varbit: String) {
    One("loc.goblin_outpost_large_crate_armour1", "varbit.gobdip_crate1_searched"),
    Two("loc.goblin_outpost_large_crate_armour2", "varbit.gobdip_crate2_searched"),
    Three("loc.goblin_outpost_large_crate_armour3", "varbit.gobdip_crate3_searched"),
}

internal var ProtectedAccess.gobdipGrubfootVis by intVarBit("varbit.gobdip_grubfoot_vis")

internal var ProtectedAccess.gobdipKnowsArmour by intVarBit("varbit.gobdip_know_about_armour")

internal var ProtectedAccess.gobdipKnowsDye by intVarBit("varbit.gobdip_know_about_dye")

internal var ProtectedAccess.gobdipMetAggie by intVarBit("varbit.gobdip_met_aggie")

internal fun ProtectedAccess.carryingObj(obj: String): Boolean = invTotal(inv, obj) > 0
