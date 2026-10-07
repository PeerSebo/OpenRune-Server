package org.rsmod.content.skills.sailing

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.table.PortTaskRow
import org.rsmod.api.table.sailing.SailingBoatFacilityRow
import org.rsmod.content.skills.sailing.SailingSessionState.shipyardReturn
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

enum class SailingQuestMilestone { CargoHoldBuilt, CargoCollected, CargoDelivered }

data class SailingQuestProgressEvent(
    val access: ProtectedAccess,
    val milestone: SailingQuestMilestone,
) : UnboundEvent

class SailingQuestSupport @Inject internal constructor(private val ownership: BoatOwnershipService) {
    fun grantRaft(access: ProtectedAccess): Boolean {
        val player = access.player
        val saved = tutorialSlot(player)
        if (saved != null) return true
        val owned = ownership.ownedBoats(player)
        val raft = owned.firstOrNull { it.boat.typeId == SailingBoats.raft.typeId }
        val free = (1..SailingConstants.MAX_BOAT_SLOTS).firstOrNull { !player.ownsBoat(it) }
        val slot = raft?.slot ?: free ?: owned.firstOrNull()?.slot ?: return false
        if (raft == null && free == null) {
            access.mes("Your fleet is full, so you can use one of your existing boats for Jim's lesson.")
        }
        if (!player.ownsBoat(slot)) {
            ownership.registerPurchase(player, slot, SailingBoats.raft, SailingDocks.THE_PANDEMONIUM)
        } else {
            ownership.moveTo(player, slot, SailingDocks.THE_PANDEMONIUM)
            ownership.repairFully(player, slot)
        }
        player.writeSailingVar(TUTORIAL_BOAT, slot)
        player.sailingSelectedBoatSlot = slot
        return true
    }

    fun enterShipyard(access: ProtectedAccess): Boolean {
        val player = access.player
        val slot = tutorialSlot(player) ?: return false
        player.sailingSelectedBoatSlot = slot
        val origin = player.coords
        access.telejump(SHIPYARD_ARRIVAL)
        if (player.coords != SHIPYARD_ARRIVAL) return false
        if (MapSquareKey.from(origin) != MapSquareKey.from(SHIPYARD_ARRIVAL)) {
            player.shipyardReturn = origin
        }
        return true
    }

    fun hasCargoHold(player: Player): Boolean =
        tutorialSlot(player)?.let { SailingCargoHold.capacity(player, it) > 0 } == true

    fun assignCargoTask(player: Player): Boolean {
        if (player.vars[QUEST_PROGRESS] !in 32..42) return false
        if (PortTaskManager.hasTask(player, tutorialTask)) return true
        return PortTaskManager.accept(player, tutorialTask) != null
    }

    fun giveChartingSupplies(access: ProtectedAccess): Boolean {
        val player = access.player
        val slot = tutorialSlot(player) ?: return false
        val cargo = player.cargoHold(slot)
        if (SailingCargoHold.capacity(player, slot) - cargo.occupiedSpace() < 3) {
            access.mes("Please make room for three items in your cargo hold first.")
            return false
        }
        if (access.invAdd(cargo, "obj.boat_repair_kit", 2).failure) return false
        if (access.invAdd(cargo, "obj.sailing_charting_spyglass", 1).failure) {
            access.invDel(cargo, "obj.boat_repair_kit", 2)
            return false
        }
        return true
    }

    companion object {
        private const val QUEST_PROGRESS = "varbit.sailing_intro"
        private const val TUTORIAL_BOAT = "varp.pandemonium_boat_slot"
        private val SHIPYARD_ARRIVAL = CoordGrid(2084, 2730)
        internal val tutorialTask: PortTaskRow by lazy { PortTaskRow.getRow("dbrow.port_task_sailing_intro") }
        private val basicCargoHolds by lazy {
            setOf("raft", "2x5", "large").map {
                "dbrow.sailing_boat_facility_cargo_hold_regular_$it".asRSCM()
            }.toSet()
        }

        internal fun tutorialSlot(player: Player): Int? =
            player.vars[TUTORIAL_BOAT].takeIf { it in 1..SailingConstants.MAX_BOAT_SLOTS && player.ownsBoat(it) }

        internal fun isTutorialTask(row: PortTaskRow): Boolean = row.rowId == tutorialTask.rowId

        internal fun isFreeCargoBuild(player: Player, slot: Int, row: SailingBoatFacilityRow): Boolean =
            player.vars[QUEST_PROGRESS] == 28 && tutorialSlot(player) == slot &&
                row.rowId in basicCargoHolds && SailingCargoHold.capacity(player, slot) == 0

        internal fun canCollectTutorialCargo(player: Player): Boolean = player.vars[QUEST_PROGRESS] in 38..42

        internal fun ownsTutorialCargo(player: Player): Boolean {
            val cargo = "obj.sailing_intro_cargo_crate"
            return player.inv.count(cargo) > 0 || player.worn.count(cargo) > 0 ||
                (1..SailingConstants.MAX_BOAT_SLOTS).any { player.cargoHold(it).count(cargo) > 0 }
        }

        internal fun restoreLostCargoPickup(player: Player) {
            if (!canCollectTutorialCargo(player) || ownsTutorialCargo(player)) return
            val slot = PortTaskManager.slotOf(player, tutorialTask.taskId) ?: return
            PortTaskManager.setCargoTaken(player, slot, 0)
        }
    }
}
