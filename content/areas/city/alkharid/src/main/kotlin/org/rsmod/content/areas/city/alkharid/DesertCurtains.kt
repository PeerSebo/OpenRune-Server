package org.rsmod.content.areas.city.alkharid

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Curtains replace in place; rotating and moving them like door leaves leaves a blocker behind.
class DesertCurtains @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(Closed) { swap(it.loc, Open) }
        onOpLoc1(Open) { swap(it.loc, Closed) }
    }

    private suspend fun ProtectedAccess.swap(loc: BoundLocInfo, into: String) {
        arriveDelay()
        locRepo.change(loc, into, duration = Forever)
        soundSynth(CurtainSound)
    }

    private companion object {
        private const val Closed = "loc.desertdoorclosed"
        private const val Open = "loc.desertdooropen"

        private const val CurtainSound = "synth.CURTAIN_OPEN"

        private const val Forever = Int.MAX_VALUE
    }
}
