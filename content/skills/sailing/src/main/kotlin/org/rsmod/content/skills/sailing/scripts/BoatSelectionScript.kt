package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseSailingLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.configs.sailingQueue
import org.rsmod.content.skills.sailing.ownedBoatSlots
import org.rsmod.content.skills.sailing.readSailingVar
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BoatSelectionScript : PluginScript() {
    override fun ScriptContext.startup() {
        assertInterfaceAnchors()

        onIfModalButton(BoatSelectionComponents.BOATS) { selectBoatRow(it.comsub) }
    }

    private fun assertInterfaceAnchors() {
        val interfaceId = BoatSelectionComponents.INTERFACE.asRSCM(RSCMType.INTERFACE)
        for (symbol in listOf(BoatSelectionComponents.CONTENT, BoatSelectionComponents.BOATS)) {
            val component = ServerCacheManager.fromComponent(symbol.asRSCM(RSCMType.COMPONENT))
            check(component.interfaceId == interfaceId) {
                "$symbol resolves to interface ${component.interfaceId}, expected " +
                    "${BoatSelectionComponents.INTERFACE} ($interfaceId)."
            }
        }
    }

    private fun ProtectedAccess.selectBoatRow(comsub: Int) {
        val mode = BoatSelectionMode.fromVarValue(player.boatSelectionType) ?: return
        val row = player.boatSelectionRows(mode).firstOrNull { it.index == comsub } ?: return
        val dock = SailingDocks.byDockId(player.boatSelectionDock)

        val args: Any =
            when (mode) {
                BoatSelectionMode.Board ->
                    SailingBoardTask.capture(player, dockDbRow = dock?.dbrow, boatSlot = row.slot ?: return)
                BoatSelectionMode.Purchase ->
                    SailingBoatSelection(mode = mode, slot = row.slot, boat = row.boat, dock = dock)
            }

        // Close the modal before queuing; ifClose clears weak queues.
        ifClose()
        sailingQueue(queue = mode.queue, cadenceTicks = SELECTION_COMMIT_TICKS, args = args)
    }

    private companion object {
        const val SELECTION_COMMIT_TICKS: Int = 1
    }
}

private object BoatSelectionComponents {
    const val INTERFACE: String = "interface.sailing_boat_selection"

    const val CONTENT: String = "component.sailing_boat_selection:content"

    const val BOATS: String = "component.sailing_boat_selection:boats"
}

private var Player.boatSelectionType: Int by intVarBit("varbit.sailing_boat_selection_type")

// Current dock stores dock_id; zero is a valid dock.
private var Player.boatSelectionDock: Int by intVarp("varp.sailing_boat_selection_current_dock")

internal enum class BoatSelectionMode(val varValue: Int, val queue: String) {
    Board(varValue = 0, queue = SailingConstants.QUEUE_BOARD),

    Purchase(varValue = 1, queue = SailingConstants.QUEUE_BUY_BOAT);

    companion object {
        fun fromVarValue(varValue: Int): BoatSelectionMode? =
            entries.firstOrNull { it.varValue == varValue }
    }
}

internal data class BoatSelectionRow(val index: Int, val slot: Int?, val boat: SailingBoatRow)

internal data class SailingBoatSelection(
    val mode: BoatSelectionMode,
    val slot: Int?,
    val boat: SailingBoatRow,
    val dock: SailingDocks.SailingDock?,
)

internal fun ProtectedAccess.openBoatSelection(
    mode: BoatSelectionMode,
    dock: SailingDocks.SailingDock,
): Boolean {
    val rows = player.boatSelectionRows(mode)
    if (rows.isEmpty()) {
        mes(mode.emptyListMessage())
        return false
    }

    player.boatSelectionType = mode.varValue
    player.boatSelectionDock = dock.row.dockId

    ifOpenMainModal(BoatSelectionComponents.INTERFACE)

    ifSetEvents(BoatSelectionComponents.BOATS, rows.indices, IfEvent.Op1)
    return true
}

private fun BoatSelectionMode.emptyListMessage(): String =
    when (this) {
        BoatSelectionMode.Board -> "You don't own a boat to board."
        BoatSelectionMode.Purchase -> "You can't own any more boats."
    }

private fun Player.boatSelectionRows(mode: BoatSelectionMode): List<BoatSelectionRow> =
    when (mode) {
        BoatSelectionMode.Board -> ownedBoatRows()
        BoatSelectionMode.Purchase -> purchasableBoatRows()
    }

private fun Player.ownedBoatRows(): List<BoatSelectionRow> {
    val base = baseSailingLvl
    val entitled = SailingBoats.ownableBoatCount(base)
    val rows = mutableListOf<BoatSelectionRow>()
    for (slot in ownedBoatSlots()) {
        if (rows.size >= entitled) {
            break
        }
        val typeId = readSailingVar(SailingBoatVars.field(slot, "type"))
        val boat = SailingBoats.byTypeId(typeId) ?: continue
        if (boat.levelRequired > base) {
            continue
        }
        rows += BoatSelectionRow(index = rows.size, slot = slot, boat = boat)
    }
    return rows
}

private fun Player.purchasableBoatRows(): List<BoatSelectionRow> {
    val base = baseSailingLvl
    if (ownedBoatSlots().size >= SailingBoats.ownableBoatCount(base)) {
        return emptyList()
    }
    return SailingBoats.all
        .filter { it.levelRequired <= base }
        .mapIndexed { index, boat -> BoatSelectionRow(index = index, slot = null, boat = boat) }
}
