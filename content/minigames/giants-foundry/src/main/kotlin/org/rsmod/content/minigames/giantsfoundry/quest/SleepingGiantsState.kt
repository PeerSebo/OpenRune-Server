package org.rsmod.content.minigames.giantsfoundry.quest

import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.sleepingGiantsStage by intVarBit("varbit.sleeping_giants")
internal var Player.sleepingGiantsTutorial by intVarBit("varbit.sleeping_giants_tutorial")
internal var Player.sleepingGiantsTutorialCommission by boolVarp("varp.sleeping_giants_tutorial_commission")

internal object SleepingGiantsState {
    const val KEY = "quest_sleepinggiants"
    const val STAGE_VARBIT = "varbit.sleeping_giants"
    const val ACCEPTED = 5
    const val INSIDE = 10
    const val REPAIRS = 15
    const val REPAIRED = 20
    const val TUTORIAL = 25
    const val COMPLETE = 30

    fun inTutorial(player: Player) =
        player.sleepingGiantsStage == TUTORIAL && player.sleepingGiantsTutorialCommission
}
