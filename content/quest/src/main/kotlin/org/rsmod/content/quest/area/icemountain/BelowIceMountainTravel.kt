package org.rsmod.content.quest.area.icemountain

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.plugin.scripts.ScriptContext

internal class BelowIceMountainTravel(
    private val quest: Quest,
    private val cutscene: BelowIceMountainCutscene,
) {

    fun ScriptContext.register() {
        onOpLoc1(BelowIceMountainRefs.EntranceMulti) { enterRuinsClicked() }
        onOpLoc1(BelowIceMountainRefs.EntranceDoor) { enterRuinsClicked() }

        onOpLoc1(BelowIceMountainRefs.RuinsExit) { exitToSurface() }
        onOpLoc1(BelowIceMountainRefs.WillowsBagStatic) { investigateBag() }
    }

    private suspend fun ProtectedAccess.enterRuinsClicked() {
        val stage = quest.getQuestStage(player)
        when {
            stage >= BelowIceMountainStage.Complete -> descendToCamdozaal()
            stage >= BelowIceMountainStage.GuardianSlain -> cutscene.resumeAftermath(this)
            stage >= BelowIceMountainStage.GuardianAwake -> cutscene.resumeFight(this)
            stage >= BelowIceMountainStage.TrapDisarmed -> cutscene.resumeAtBetrayal(this)
            // Stages < 30: the multiloc resolves to the op-less sealed rock client-side, so a
            // vanilla client cannot send this op. Nothing to do for one that does.
            else -> {}
        }
    }

    private suspend fun ProtectedAccess.descendToCamdozaal() {
        travelFade { telejump(BelowIceMountainRefs.CamdozaalArrival) }
    }

    private suspend fun ProtectedAccess.exitToSurface() {
        travelFade { telejump(BelowIceMountainRefs.RuinsSurfaceReturn) }
    }

    private suspend fun ProtectedAccess.investigateBag() {
        startDialogue {
            mesbox("Willow's abandoned bag. You already took everything of value from it.")
        }
    }

    private suspend fun ProtectedAccess.travelFade(move: ProtectedAccess.() -> Unit) {
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = TravelFadeDuration,
        )
        delay(TravelFadeTicks)
        move()
        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = TravelFadeDuration,
        )
        delay(TravelFadeTicks)
        closeFadeOverlayNow()
    }

    private companion object {
        private const val TravelFadeDuration = 50

        private const val TravelFadeTicks = 2
    }
}
