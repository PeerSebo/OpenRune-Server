package org.rsmod.content.skills.sailing.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.sailing.BoatWorldEntityService
import org.rsmod.content.skills.sailing.ON_PLAYER_BOAT_ASHORE
import org.rsmod.content.skills.sailing.SailingCargoWipe
import org.rsmod.content.skills.sailing.SailingNavigationService
import org.rsmod.content.skills.sailing.boardedBoat
import org.rsmod.content.skills.sailing.boardedBoatWorld
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.isOnPlayerBoat
import org.rsmod.content.skills.sailing.sailingFacilityLockedIn
import org.rsmod.content.skills.sailing.sailingLastDock
import org.rsmod.content.skills.sailing.sailingOnPlayerBoat
import org.rsmod.events.EventBus
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal data class SailingEscapeTask(val dockDbrow: String, val departure: SailingBoatDeparture)

internal class HelmScript
@Inject
constructor(private val eventBus: EventBus, private val boats: BoatWorldEntityService, private val navigation: SailingNavigationService) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (helm in HELM_PARENTS) {
            onOpLoc1(helm) { opNavigate(it.loc) }
            onOpLoc4(helm) { opEscape(it.loc) }
        }

        onPlayerQueueWithArgs<SailingEscapeTask>(SailingConstants.QUEUE_ESCAPE) {
            finishEscape(it.args)
        }
    }

    private fun ProtectedAccess.opNavigate(loc: BoundLocInfo) {
        val boat = boats.boarded(player)
        if (!player.isOnPlayerBoat() || boat == null || loc.coords !in boat) {
            mes(constants.dm_default)
            return
        }
        faceLoc(loc)
        navigation.navigate(this)
    }

    private fun ProtectedAccess.opEscape(loc: BoundLocInfo) {
        val departure = SailingBoatDeparture.capture(player, boats)
        if (departure == null) {
            mes(constants.dm_default)
            return
        }

        val dock = escapeDestination()
        if (dock == null) {
            mes(ESCAPE_NO_DESTINATION)
            return
        }

        faceLoc(loc)
        queue(SailingConstants.QUEUE_ESCAPE, ESCAPE_TICKS, SailingEscapeTask(dock.dbrow, departure))
    }

    private fun ProtectedAccess.finishEscape(task: SailingEscapeTask) {
        if (!task.departure.isCurrent(player, boats, requireStationary = false)) {
            return
        }
        val dock = SailingDocks.byDbRow(task.dockDbrow) ?: return
        if (dock.row.dockId != task.departure.lastDock) {
            return
        }
        val dest = dock.landCoords ?: return

        // Preserve cargo and the occupied deck until teleport arrival succeeds.
        telejump(dest)
        if (player.coords != dest || player.coords in task.departure.entity) {
            return
        }

        SailingCargoWipe.wipe(player, eventBus, task.departure.boatSlot)

        player.sailingFacilityLockedIn = FACILITY_LOCKEDIN_IDLE
        player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ASHORE
        player.boardedBoat = 0
        player.boardedBoatWorld = 0

        boats.despawn(player)
        SailingSidepanel.close(this)

        mes(ESCAPE_MESSAGE)
        mes(ESCAPE_CARGO_LOST_MESSAGE)
    }

    private fun ProtectedAccess.escapeDestination(): SailingDocks.SailingDock? {
        val dock = SailingDocks.byDockId(player.sailingLastDock) ?: return null
        return if (dock.landCoords != null) dock else null
    }

    private companion object {
        val HELM_SIZES: List<String> = listOf("1x3", "2x5", "3x8")

        val HELM_MATERIALS: List<String> =
            listOf("wood", "oak", "teak", "mahogany", "camphor", "ironwood", "rosewood")

        val HELM_PARENTS: List<String> =
            HELM_SIZES.flatMap { size ->
                HELM_MATERIALS.map { material ->
                    "loc.sailing_boat_steering_kandarin_${size}_$material"
                }
            }

        const val FACILITY_LOCKEDIN_IDLE: Int = 0

        const val ESCAPE_TICKS: Int = 1

        const val ESCAPE_MESSAGE: String = "You abandon ship and swim for the shore."

        const val ESCAPE_CARGO_LOST_MESSAGE: String = "Any cargo aboard has been lost."

        const val ESCAPE_NO_DESTINATION: String =
            "<col=ff0000>You have nowhere to escape to - this dock is not implemented yet.</col>"
    }
}
