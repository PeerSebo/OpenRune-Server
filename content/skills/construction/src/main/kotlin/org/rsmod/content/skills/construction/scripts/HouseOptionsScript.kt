package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.pohDoorsOption
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.skills.construction.HouseOptionsState
import org.rsmod.content.skills.construction.scripts.HousePortalScript.Companion.armBuildingModeDropTrigger
import org.rsmod.content.skills.construction.scripts.HousePortalScript.Companion.disarmBuildingModeDropTrigger
import org.rsmod.content.skills.construction.scripts.HousePortalScript.Companion.exitHouse
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO: implement guest expulsion.
class HouseOptionsScript @Inject constructor(private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { HouseOptionsState.migrate(player) }
        onIfOpen("interface.poh_options") { player.updateRoomCount(manager) }

        onIfOverlayButton("component.poh_options:build_mode_on") { setBuildingMode(enabled = true) }
        onIfOverlayButton("component.poh_options:build_mode_off") {
            setBuildingMode(enabled = false)
        }

        onIfOverlayButton("component.poh_options:leave_house") {
            if (manager.isInOwnHouse(player)) {
                exitHouse(manager)
            } else {
                mes("You can only do that in your own house.")
            }
        }

        onIfOverlayButton("component.poh_options:doors_open") { player.pohDoorsOption = 0 }
        onIfOverlayButton("component.poh_options:doors_closed") { player.pohDoorsOption = 1 }
        onIfOverlayButton("component.poh_options:doors_none") { player.pohDoorsOption = 2 }

        onIfOverlayButton("component.poh_options:tele_on") {
            HouseOptionsState.setTeleportInside(player, 1)
        }
        onIfOverlayButton("component.poh_options:tele_off") {
            HouseOptionsState.setTeleportInside(player, 0)
        }

        onIfOverlayButton("component.poh_options:viewer") {
            mes("The house viewer is not available right now.")
        }

        onIfOverlayButton("component.poh_options:call_servant") { mes("You don't have a servant.") }

        onIfOverlayButton("component.poh_options:expel_guests") {
        }
    }

    private suspend fun ProtectedAccess.setBuildingMode(enabled: Boolean) {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only do that in your own house.")
            return
        }
        manager.setBuildingMode(player, enabled)
        if (enabled) {
            player.armBuildingModeDropTrigger()
            mes("Building mode is now on.")
        } else {
            player.disarmBuildingModeDropTrigger()
            mes("Building mode is now off.")
        }
        player.updateRoomCount(manager)
    }

    private fun Player.updateRoomCount(manager: PohManager) {
        val house = manager.houseOf(this) ?: return
        ifSetText("component.poh_options:roomcount", "Number of rooms: ${house.rooms.size}")
    }
}
