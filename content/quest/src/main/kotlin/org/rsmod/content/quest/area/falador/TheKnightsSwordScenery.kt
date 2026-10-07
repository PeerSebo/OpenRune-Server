package org.rsmod.content.quest.area.falador

import org.rsmod.api.config.constants
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.ScriptContext

internal class TheKnightsSwordScenery(
    private val quest: Quest,
    private val locRepo: LocRepository,
) {

    fun ScriptContext.register() {
        onOpLoc1(TheKnightsSwordRefs.CupboardShut) { openCupboard(it.loc) }
        onOpLoc1(TheKnightsSwordRefs.CupboardOpen) { searchCupboard() }
        onOpLoc2(TheKnightsSwordRefs.CupboardOpen) { shutCupboard(it.loc) }
    }

    private suspend fun ProtectedAccess.openCupboard(loc: BoundLocInfo) {
        arriveDelay()
        anim(OpenAnim)
        soundSynth(OpenSound)
        delay(SwapDelayTicks)
        locRepo.change(loc, TheKnightsSwordRefs.CupboardOpen, duration = CupboardOpenTicks)
    }

    private suspend fun ProtectedAccess.shutCupboard(loc: BoundLocInfo) {
        arriveDelay()
        anim(ShutAnim)
        soundSynth(ShutSound)
        delay(SwapDelayTicks)
        locRepo.change(loc, TheKnightsSwordRefs.CupboardShut, duration = CupboardOpenTicks)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()

        if (quest.getQuestStage(player) != TheKnightsSwordStage.Stage5PortraitNamed) {
            mes(constants.dm_default, ChatType.Engine)
            return
        }

        if (invTotal(inv, TheKnightsSwordRefs.Portrait) > 0) {
            mes(constants.dm_default, ChatType.Engine)
            return
        }

        mesbox("You find a small portrait in here which you take.")

        if (invAdd(inv, TheKnightsSwordRefs.Portrait, 1).failure) {
            mes(constants.dm_take_invspace)
        }
    }

    private companion object {
        private const val OpenAnim = "seq.human_openchest"

        private const val ShutAnim = "seq.human_closechest"

        private const val OpenSound = "synth.CUPBOARD_OPEN"

        private const val ShutSound = "synth.CUPBOARD_CLOSE"

        private const val SwapDelayTicks = 1

        private const val CupboardOpenTicks = 200
    }
}
