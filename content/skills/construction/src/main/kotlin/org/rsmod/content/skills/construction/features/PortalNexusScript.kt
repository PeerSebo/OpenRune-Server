package org.rsmod.content.skills.construction.features

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO: implement paid nexus attunement and filter destinations by unlock varbits.
class PortalNexusScript @Inject constructor(private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        for (nexus in NEXUS_PORTALS) {
            onOpLoc1(nexus) { openTeleportMenu() }
            onOpLoc2(nexus) { openTeleportMenu() }
        }
    }

    private suspend fun ProtectedAccess.openTeleportMenu() {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only use the portal nexus in your own house.")
            return
        }
        val index = menu(MENU_TITLE, hotkeys = true, choices = DESTINATIONS.map { it.first })
        val destination = DESTINATIONS.getOrNull(index)?.second ?: return
        arriveDelay()
        telejump(destination)
        mes("You step through the portal.")
    }

    private companion object {
        const val MENU_TITLE = "Portal Nexus"

        val NEXUS_PORTALS =
            listOf("loc.poh_nexus_portal_1", "loc.poh_nexus_portal_2", "loc.poh_nexus_portal_3")

        val DESTINATIONS: List<Pair<String, CoordGrid>> =
            listOf(
                "Varrock" to PohTeleportDestinations.VARROCK,
                "Lumbridge" to PohTeleportDestinations.LUMBRIDGE,
                "Falador" to PohTeleportDestinations.FALADOR,
                "Camelot" to PohTeleportDestinations.CAMELOT,
                "Ardougne" to PohTeleportDestinations.ARDOUGNE,
                "Yanille" to PohTeleportDestinations.YANILLE,
                "Kharyrll" to PohTeleportDestinations.KHARYRLL,
                "Kourend Castle" to PohTeleportDestinations.KOUREND,
            )
    }
}
