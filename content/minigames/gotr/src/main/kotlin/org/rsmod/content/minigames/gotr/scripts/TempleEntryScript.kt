package org.rsmod.content.minigames.gotr.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Until Temple of the Eye is implemented, login forces its completed varbit to expose the portal.
// The quest must take ownership of this variable when added; login must set it before vars are sent.
class TempleEntryScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.Login> { player.markTempleOfTheEyeComplete() }
        onOpLoc1(BASEMENT_PORTAL) { enterTemple() }
        onOpLoc1(TEMPLE_EXIT_PORTAL) { exitTemple() }
    }

    private fun Player.markTempleOfTheEyeComplete() {
        VarPlayerIntMapSetter.set(this, TOTE_QUEST_VARBIT, TOTE_QUEST_COMPLETE)
    }

    private fun ProtectedAccess.enterTemple() {
        mes("You step through the portal...", ChatType.Spam)
        soundSynth("synth.PORTAL_TELEPORT")
        telejump(TEMPLE_ARRIVAL)
    }

    private fun ProtectedAccess.exitTemple() {
        mes("You step through the portal...", ChatType.Spam)
        soundSynth("synth.PORTAL_TELEPORT")
        telejump(BASEMENT_ARRIVAL)
    }

    private companion object {
        const val BASEMENT_PORTAL = "loc.tote_portal_to_gotr_parent"
        const val TEMPLE_EXIT_PORTAL = "loc.gotr_entry"
        const val TOTE_QUEST_VARBIT = "varbit.tote"

        const val TOTE_QUEST_COMPLETE = 130

        val TEMPLE_ARRIVAL = CoordGrid(3615, 9470, 0)

        val BASEMENT_ARRIVAL = CoordGrid(3105, 9573, 0)
    }
}
