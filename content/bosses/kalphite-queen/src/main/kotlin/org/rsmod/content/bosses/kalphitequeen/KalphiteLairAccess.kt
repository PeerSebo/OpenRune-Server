package org.rsmod.content.bosses.kalphitequeen

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KalphiteLairAccess @Inject constructor(private val players: PlayerList) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLocU("loc.kalphite_burrow_entrance_norope", "obj.rope") { attachRope(SURFACE_ROPE) }
        onOpLocU("loc.kalphite_chamber_entrance_norope", "obj.rope") { attachRope(CHAMBER_ROPE) }
        onOpLoc1("loc.kalphite_burrow_entrance_withrope") { descend(SURFACE_ROPE, UPPER_LAIR) }
        for (loc in listOf("loc.kalphite_chamber_entrance_withrope_normal", "loc.kalphite_chamber_entrance_withrope_private")) {
            onOpLoc1(loc) { descend(CHAMBER_ROPE, QUEEN_LAIR) }
            onOpLoc3(loc) { lookInside() }
        }
        onOpLoc3("loc.kalphite_chamber_entrance_norope") { lookInside() }
        onOpLoc1("loc.kalphite_burrow_exit") { climbOut(SURFACE_ROPE, SURFACE) }
        onOpLoc1("loc.kalphite_chamber_exit") { climbOut(CHAMBER_ROPE, CHAMBER_TOP) }
        onEvent<SessionStateEvent.Login> { KalphiteRopes.login(player) }
    }

    internal fun ProtectedAccess.attachRope(flag: String) {
        when (KalphiteRopes.attach(player, flag)) {
            KalphiteRopes.Attachment.Attached -> mes("You attach the rope to the tunnel entrance.")
            KalphiteRopes.Attachment.AlreadyAttached -> mes("There is already a rope attached.")
            KalphiteRopes.Attachment.MissingRope -> mes("You need a rope to climb down here.")
        }
    }

    private suspend fun ProtectedAccess.descend(flag: String, destination: CoordGrid) {
        if (player.vars[flag] == 0) { mes("You need to attach a rope first."); return }
        arriveDelay()
        anim("seq.human_reachforladder")
        delay(1)
        telejump(destination, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climbOut(flag: String, destination: CoordGrid) {
        arriveDelay()
        anim("seq.human_reachforladder")
        delay(1)
        telejump(destination, TeleportType.Exempt)
        if (coords != destination) return
        KalphiteRopes.leave(player, flag)
    }

    private fun ProtectedAccess.lookInside() {
        val count = players.count { !it.staffInvisible && it.level == 0 && it.coords.x in 3456..3519 && it.coords.z in 9472..9535 }
        mes(if (count == 0) "There are no adventurers in the chamber." else "There are $count adventurers in the chamber.")
    }

    companion object {
        const val SURFACE_ROPE = "varbit.kalphite_rope_1"
        const val CHAMBER_ROPE = "varbit.kalphite_rope_2"
        const val HARD_DIARY = "varbit.desert_diary_hard_complete"
        const val ELITE_DIARY = "varbit.desert_diary_elite_complete"
        val UPPER_LAIR = CoordGrid(3484, 9510, 2)
        val QUEEN_LAIR = CoordGrid(3507, 9494, 0)
        val SURFACE = CoordGrid(3227, 3106, 0)
        val CHAMBER_TOP = CoordGrid(3509, 9496, 2)
    }
}
