package org.rsmod.content.areas.city.draynor

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorOddments @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(Cabbage) { pickCabbage() }
    }

    private suspend fun ProtectedAccess.pickCabbage() {
        arriveDelay()
        anim(PickAnim)
        delay(1)
        invAddOrDrop(objRepo, CabbageObj)
        soundSynth(PickSound)
        spam("You pick a cabbage.")
    }

    private companion object {
        private const val Cabbage = "loc.draynor_magic_cabbage"

        private const val CabbageObj = "obj.magic_cabbage"
        private const val PickAnim = "seq.human_pickupfloor"
        private const val PickSound = "synth.PICK"
    }
}
