package org.rsmod.content.skills.hunter.tracking

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.hunter.herbiboarCount
import org.rsmod.content.skills.hunter.migrateHunterPlayerState
import org.rsmod.content.skills.hunter.pursuitRingUsed
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TrackingScript @Inject constructor(private val tracking: TrackingService) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in TrackingRoutes.all.flatMap { it.starts }.map { it.type }.distinct()) onOpLoc1(type) { start(it.loc) }
        for (type in TrackingRoutes.all.flatMap { it.clues }.map { it.type }.distinct()) onOpLoc1(type) { inspect(it.loc) }
        for (area in TrackingRoutes.all) for (type in area.dens.map { it.type }.distinct()) {
            if (area.prey == TrackingPrey.HERBIBOAR) {
                onOpLoc1(type) { capture(it.loc) }
                onOpLoc2(type) { tracking.searchDen(player, it.loc) }
            } else {
                onOpLoc1(type) { tracking.searchDen(player, it.loc) }
                onOpLoc2(type) { capture(it.loc) }
            }
        }
        for (start in TrackingRoutes.herbiboar.starts) {
            onOpLoc2(start.type) { mes("You have harvested ${player.herbiboarCount} herbiboars.") }
            onOpLoc3(start.type) {
                val key = "varbit.fossil_herbiboar_already_caught_ignore_warning"
                vars[key] = if (vars[key] == 0) 1 else 0
                mes(if (vars[key] == 0) "You will be warned before abandoning a herbiboar trail." else "You will no longer be warned before abandoning a herbiboar trail.")
            }
        }
        for (index in 1..9) onOpNpc1("npc.fossil_herbiboar_$index") { harvest(it.npc) }
        onOpNpc1("npc.fossil_herbiboar_visible") { harvest(it.npc) }
        onOpHeld2(TrackingService.RING) { ringCharges() }
        onOpWorn2(TrackingService.RING) { ringCharges() }
        onOpHeld3(TrackingService.RING) {
            if (inv[it.slot]?.id == TrackingService.RING.asRSCM()) {
                inv[it.slot] = null
                player.pursuitRingUsed = 0
                mes("You break the ring. Your next ring of pursuit will have ten charges.")
            }
        }
        onPlayerLogin {
            player.migrateHunterPlayerState()
            tracking.clear(player)
        }
        onPlayerLogout { tracking.clear(player) }
    }

    private suspend fun ProtectedAccess.start(loc: BoundLocInfo) {
        val initial = coords
        val existing = tracking.hunt(player)
        if (existing != null && tracking.area(loc)?.prey == TrackingPrey.HERBIBOAR && vars["varbit.fossil_herbiboar_already_caught_ignore_warning"] == 0) {
            var restart = false
            startDialogue {
                restart = choice2("Abandon this trail and start another.", true, "Keep following this trail.", false)
            }
            if (!restart || coords != initial || tracking.hunt(player) !== existing) return
        }
        if (actionDelay > mapClock) return
        actionDelay = mapClock + 2
        faceSquare(loc.coords)
        anim("seq.hunting_searching_bushes_norestart")
        delay(2)
        resetAnim()
        if (coords == initial && tracking.hunt(player) === existing) tracking.start(player, loc)
    }

    private suspend fun ProtectedAccess.inspect(loc: BoundLocInfo) {
        if (actionDelay > mapClock) return
        val hunt = tracking.hunt(player)
        val initial = coords
        actionDelay = mapClock + 2
        faceSquare(loc.coords)
        anim("seq.hunting_searching_bushes_norestart")
        delay(2)
        resetAnim()
        if (coords == initial && tracking.hunt(player) === hunt) tracking.inspect(player, loc)
    }

    private suspend fun ProtectedAccess.capture(loc: BoundLocInfo) {
        val hunt = tracking.canCatch(player, loc) ?: return
        if (actionDelay > mapClock) return
        val initial = coords
        actionDelay = mapClock + 3
        faceSquare(loc.coords)
        anim(if (hunt.area.prey == TrackingPrey.HERBIBOAR) "seq.human_unarmedkick" else hunt.area.prey.animation)
        delay(3)
        resetAnim()
        if (coords == initial) tracking.catch(player, loc, hunt)
    }

    private suspend fun ProtectedAccess.harvest(npc: Npc) {
        val hunt = tracking.canHarvest(player, npc) ?: return
        if (actionDelay > mapClock) return
        val initial = coords
        val uid = npc.uid
        actionDelay = mapClock + 3
        faceEntitySquare(npc)
        anim("seq.pruning_low")
        delay(3)
        resetAnim()
        if (coords == initial && npc.uid == uid) tracking.harvest(player, npc, hunt)
    }

    private fun ProtectedAccess.ringCharges() = mes("Your ring of pursuit has ${10 - player.pursuitRingUsed} charges remaining.")
}
