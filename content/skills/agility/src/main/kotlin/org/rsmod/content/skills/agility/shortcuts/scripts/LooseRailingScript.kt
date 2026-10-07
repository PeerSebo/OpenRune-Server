package org.rsmod.content.skills.agility.shortcuts.scripts

import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.scripts.CLIENT_CYCLES_PER_TICK
import org.rsmod.content.skills.agility.scripts.approachMount
import org.rsmod.content.skills.agility.scripts.withAgilityAnimation
import org.rsmod.content.skills.agility.shortcuts.configs.McGruborRailing
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LooseRailingScript : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.mcgruborlooserailing") {
            player.withAgilityAnimation(preserveOnCompletion = true) { squeezeThrough() }
        }
    }

    private suspend fun ProtectedAccess.squeezeThrough() {
        arriveDelay()
        // Shape 0, angle 0 places the railing on its tile's west edge.
        val insideWood = player.coords.x < McGruborRailing.EAST_SIDE.x
        val start = if (insideWood) McGruborRailing.WEST_SIDE else McGruborRailing.EAST_SIDE
        val dest = if (insideWood) McGruborRailing.EAST_SIDE else McGruborRailing.WEST_SIDE
        val dir = if (insideWood) constants.em_face_east else constants.em_face_west
        // Align with the fence before moving to the fixed crossing tile.
        if (!approachMount(start)) {
            return
        }
        // Play the opening frames before exactMove; same-tick movement clips them.
        faceSquare(dest)
        resetAnim()
        // No verified railing synth exists; the sequence is silent. Reset its loop after crossing.
        anim("seq.railing_squeeze")
        delay(1)
        exactMove(
            start = player.coords,
            end = dest,
            delay1 = 0,
            delay2 = CLIENT_CYCLES_PER_TICK,
            dir = dir,
            teleportType = TeleportType.Exempt,
        )
        delay(McGruborRailing.SQUEEZE_TICKS - 1)
        resetAnim()
    }
}
