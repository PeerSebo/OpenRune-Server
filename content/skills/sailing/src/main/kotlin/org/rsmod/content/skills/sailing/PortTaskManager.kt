package org.rsmod.content.skills.sailing

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.api.player.stat.baseSailingLvl
import org.rsmod.api.table.PortTaskRow
import org.rsmod.api.table.sailing.SailingDockRow
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.table.PortTaskRewards
import org.rsmod.game.entity.Player

object PortTaskManager {

    const val MAX_TASK_SLOTS: Int = 5

    const val NO_TASK: Int = 0

    const val VARBIT_EXTRA_SLOTS: String = "varbit.port_task_extra_slots_unlocked"

    const val VARBIT_LAST_CARGO_TAKEN: String = "varbit.port_task_last_cargo_taken"

    const val VARBIT_COMPLETED_TODAY: String = "varbit.port_tasks_completed_today"

    const val VARBIT_CARRYING_CARGO: String = "varbit.sailing_carrying_cargo"

    const val VARBIT_CANCEL_WARNING_DISABLED: String =
        "varbit.sailing_port_task_cancel_warning_disabled"

    const val VARBIT_LOADING_BAY_WARNING_DISMISSED: String =
        "varbit.port_task_loading_bay_warning_dismissed"

    const val VARP_COMPLETED_TOTAL: String = "varp.port_tasks_completed"

    fun slotIdVar(slot: Int): String {
        require(slot in 0 until MAX_TASK_SLOTS) { "Task slot out of range: $slot" }
        return "varbit.port_task_slot_${slot}_id"
    }

    fun slotCargoTakenVar(slot: Int): String {
        require(slot in 0 until MAX_TASK_SLOTS) { "Task slot out of range: $slot" }
        return "varbit.port_task_slot_${slot}_cargo_taken"
    }

    fun slotCargoDeliveredVar(slot: Int): String {
        require(slot in 0 until MAX_TASK_SLOTS) { "Task slot out of range: $slot" }
        return "varbit.port_task_slot_${slot}_cargo_delivered"
    }

    const val CARGO_COUNT_MAX: Int = 31

    const val CARGO_STATE_WITHDRAW: Int = 0

    const val CARGO_STATE_DEPOSIT: Int = 1

    const val REQUIRE_BOAT_FOR_DELIVERY: Boolean = false

    // Clientscript 8912 accepts eight DBROW integers, with -1 for unused offers.
    const val OFFERS_PER_BOARD: Int = 8

    const val ROTATION_TASKS_PER_REFRESH: Int = 8

    const val ROTATION_DAILY_RESET_HOUR_UTC: Int = 0

    private const val MILLIS_PER_HOUR: Long = 3_600_000L
    private const val MILLIS_PER_DAY: Long = 86_400_000L

    private const val SHUFFLE_MIX: Long = -0x61c8864680b583ebL

    private val logger = InlineLogger()

    val all: List<PortTaskRow> by lazy { PortTaskRow.all() }

    val byTaskId: Map<Int, PortTaskRow> by lazy { all.associateBy(PortTaskRow::taskId) }

    val byStartingPort: Map<Int, List<PortTaskRow>> by lazy {
        all.groupBy { (it.startingPort ?: it.endingPort).rowId }
    }

    fun tasksAtPort(dock: SailingDockRow): List<PortTaskRow> = byStartingPort[dock.rowId].orEmpty()

    fun unlockedSlots(player: Player): Int =
        SailingConstants.TASK_SLOT_UNLOCK_LEVELS.count { player.baseSailingLvl >= it }
            .coerceIn(1, MAX_TASK_SLOTS)

    fun syncSlotCount(player: Player) {
        player.writeSailingVar(VARBIT_EXTRA_SLOTS, unlockedSlots(player) - 1)
    }

    fun slotTaskId(player: Player, slot: Int): Int = player.readSailingVar(slotIdVar(slot))

    fun slotTask(player: Player, slot: Int): PortTaskRow? {
        val taskId = slotTaskId(player, slot)
        if (taskId == NO_TASK) return null
        return byTaskId[taskId]
    }

    fun activeSlots(player: Player): List<Int> {
        val limit = unlockedSlots(player)
        return (0 until limit).filter { slotTaskId(player, it) != NO_TASK }
    }

    fun activeTasks(player: Player): List<PortTaskRow> =
        activeSlots(player).mapNotNull { slotTask(player, it) }

    fun freeSlot(player: Player): Int? =
        (0 until unlockedSlots(player)).firstOrNull { slotTaskId(player, it) == NO_TASK }

    fun slotOf(player: Player, taskId: Int): Int? =
        (0 until MAX_TASK_SLOTS).firstOrNull { slotTaskId(player, it) == taskId }

    fun hasTask(player: Player, row: PortTaskRow): Boolean = slotOf(player, row.taskId) != null

    fun meetsRequirements(player: Player, row: PortTaskRow): Boolean {
        if (player.baseSailingLvl < row.levelRequired) return false
        if (PortTaskRewards.isBounty(row)) {
            if (player.baseSailingLvl < SailingConstants.BOUNTY_TASK_MIN_LEVEL) return false
        }
        return true
    }

    fun eligibleTasks(player: Player, dock: SailingDockRow): List<PortTaskRow> =
        tasksAtPort(dock).filter {
            !SailingQuestSupport.isTutorialTask(it) && meetsRequirements(player, it) && !hasTask(player, it)
        }

    fun rotationDay(nowMillis: Long = System.currentTimeMillis()): Long =
        (nowMillis - ROTATION_DAILY_RESET_HOUR_UTC * MILLIS_PER_HOUR) / MILLIS_PER_DAY

    // Derive rotation deterministically from day, dock and completion bracket so offers remain stable.
    fun rotationSeed(player: Player, dock: SailingDockRow): Long {
        val rotations = completedToday(player) / ROTATION_TASKS_PER_REFRESH
        return rotationDay() * 1_000L + dock.dockId.toLong() * 17L + rotations.toLong()
    }

    fun offeredTasks(player: Player, dock: SailingDockRow): List<PortTaskRow> {
        val seed = rotationSeed(player, dock)
        return eligibleTasks(player, dock)
            .sortedBy { shuffleKey(it.taskId, seed) }
            .take(OFFERS_PER_BOARD)
    }

    private fun shuffleKey(taskId: Int, seed: Long): Long {
        var z = seed + taskId.toLong() * SHUFFLE_MIX
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun accept(player: Player, row: PortTaskRow): Int? {
        if (SailingQuestSupport.isTutorialTask(row) && player.vars["varbit.sailing_intro"] !in 32..42) return null
        if (!meetsRequirements(player, row)) return null
        if (hasTask(player, row)) return null
        val slot = freeSlot(player) ?: return null

        player.writeSailingVar(slotIdVar(slot), row.taskId)
        player.writeSailingVar(slotCargoTakenVar(slot), 0)
        player.writeSailingVar(slotCargoDeliveredVar(slot), 0)
        syncCarryingCargo(player)
        return slot
    }

    fun cancel(player: Player, slot: Int) {
        if (!isCancellable(player, slot)) return
        player.writeSailingVar(slotIdVar(slot), NO_TASK)
        player.writeSailingVar(slotCargoTakenVar(slot), 0)
        player.writeSailingVar(slotCargoDeliveredVar(slot), 0)
        syncCarryingCargo(player)
    }

    fun isCancellable(player: Player, slot: Int): Boolean =
        slotTask(player, slot)?.let { !SailingQuestSupport.isTutorialTask(it) && it.cancellable != false } ?: true

    fun cargoTaken(player: Player, slot: Int): Int = player.readSailingVar(slotCargoTakenVar(slot))

    fun cargoDelivered(player: Player, slot: Int): Int =
        player.readSailingVar(slotCargoDeliveredVar(slot))

    fun setCargoTaken(player: Player, slot: Int, count: Int) {
        player.writeSailingVar(slotCargoTakenVar(slot), count.coerceIn(0, CARGO_COUNT_MAX))
        syncCarryingCargo(player)
    }

    fun setCargoDelivered(player: Player, slot: Int, count: Int) {
        player.writeSailingVar(slotCargoDeliveredVar(slot), count.coerceIn(0, CARGO_COUNT_MAX))
    }

    fun lastCargoTaken(player: Player): Int = player.readSailingVar(VARBIT_LAST_CARGO_TAKEN)

    fun setLastCargoTaken(player: Player, taskId: Int) {
        player.writeSailingVar(VARBIT_LAST_CARGO_TAKEN, taskId)
    }

    fun isCarryingCargo(player: Player): Boolean =
        courierSlots(player).any { cargoTaken(player, it) > cargoDelivered(player, it) }

    fun courierSlots(player: Player): List<Int> =
        activeSlots(player).filter { slot ->
            slotTask(player, slot)?.let { !PortTaskRewards.isBounty(it) } == true
        }

    fun syncCarryingCargo(player: Player) {
        val state = if (isCarryingCargo(player)) CARGO_STATE_DEPOSIT else CARGO_STATE_WITHDRAW
        player.writeSailingVar(VARBIT_CARRYING_CARGO, state)
    }

    fun slotAwaitingPickupAt(player: Player, dock: SailingDockRow): Int? =
        courierSlots(player).firstOrNull { slot ->
            val row = slotTask(player, slot) ?: return@firstOrNull false
            cargoTaken(player, slot) == 0 && pickupPort(row).rowId == dock.rowId
        }

    fun slotsAwaitingPickupAt(player: Player, dock: SailingDockRow): List<Int> =
        courierSlots(player).filter { slot ->
            val row = slotTask(player, slot) ?: return@filter false
            cargoTaken(player, slot) == 0 && pickupPort(row).rowId == dock.rowId
        }

    fun slotAwaitingDeliveryAt(player: Player, dock: SailingDockRow): Int? =
        courierSlots(player).firstOrNull { slot ->
            val row = slotTask(player, slot) ?: return@firstOrNull false
            cargoTaken(player, slot) > cargoDelivered(player, slot) &&
                row.endingPort.rowId == dock.rowId
        }

    fun pickupPort(row: PortTaskRow): SailingDockRow =
        row.cargoPort ?: row.startingPort ?: row.endingPort

    fun recordCompletion(player: Player, slot: Int) {
        player.writeSailingVar(slotIdVar(slot), NO_TASK)
        player.writeSailingVar(slotCargoTakenVar(slot), 0)
        player.writeSailingVar(slotCargoDeliveredVar(slot), 0)

        val today = (completedToday(player) + 1) % (COMPLETED_TODAY_MAX + 1)
        player.writeSailingVar(VARBIT_COMPLETED_TODAY, today)
        val lifetime = player.readSailingVar(VARP_COMPLETED_TOTAL) + 1
        player.writeSailingVar(VARP_COMPLETED_TOTAL, lifetime)

        syncCarryingCargo(player)
    }

    // TODO: reset the daily completion count at midnight instead of only wrapping its field.
    const val COMPLETED_TODAY_MAX: Int = 1023

    fun completedToday(player: Player): Int = player.readSailingVar(VARBIT_COMPLETED_TODAY)

    fun completedTotal(player: Player): Int = player.readSailingVar(VARP_COMPLETED_TOTAL)

    fun assertCacheAnchors() {
        check(all.size >= MIN_EXPECTED_TASK_ROWS) {
            "dbtable.port_task returned ${all.size} rows; at least $MIN_EXPECTED_TASK_ROWS were " +
                "expected (607 dbrow.port_task_* symbols exist in the merged gameval table). " +
                "A short read usually means the SERVER cache is a partial LIVE clone."
        }
        if (byTaskId.size != all.size) {
            logger.warn {
                "dbcol.port_task:task_id is not unique across ${all.size} rows - " +
                    "${all.size - byTaskId.size} collision(s); the later row wins for each. " +
                    "Sailing port tasks still work; the shadowed tasks are unreachable."
            }
        }

        val ternBounty = byTaskId[ANCHOR_BOUNTY_TASK_ID]
        checkNotNull(ternBounty) { "port_task $ANCHOR_BOUNTY_TASK_ID (dbrow 9101) is missing." }
        check(ternBounty.taskType == PortTaskRewards.TASK_TYPE_BOUNTY) {
            "port_task $ANCHOR_BOUNTY_TASK_ID expected task_type " +
                "${PortTaskRewards.TASK_TYPE_BOUNTY}, got ${ternBounty.taskType}."
        }
        check(ternBounty.levelRequired == SailingConstants.BOUNTY_TASK_MIN_LEVEL) {
            "port_task $ANCHOR_BOUNTY_TASK_ID expected level_required " +
                "${SailingConstants.BOUNTY_TASK_MIN_LEVEL}, got ${ternBounty.levelRequired}."
        }
        checkNotNull(ternBounty.bountyTargetAlive) {
            "port_task $ANCHOR_BOUNTY_TASK_ID has no bounty_target_alive."
        }

        val beerRun = byTaskId[ANCHOR_COURIER_TASK_ID]
        checkNotNull(beerRun) { "port_task $ANCHOR_COURIER_TASK_ID (dbrow 8949) is missing." }
        check(beerRun.taskType == PortTaskRewards.TASK_TYPE_COURIER) {
            "port_task $ANCHOR_COURIER_TASK_ID expected task_type " +
                "${PortTaskRewards.TASK_TYPE_COURIER}, got ${beerRun.taskType}."
        }
        check(beerRun.cargo.isNotEmpty()) {
            "port_task $ANCHOR_COURIER_TASK_ID has no cargo; the courier flow has nothing to move."
        }

        // Reward columns are absent from the cache; PortTaskRewards supplies them.
        check(SailingConstants.TASK_SLOT_UNLOCK_LEVELS.size == MAX_TASK_SLOTS) {
            "TASK_SLOT_UNLOCK_LEVELS has " +
                "${SailingConstants.TASK_SLOT_UNLOCK_LEVELS.size} entries but " +
                "$MAX_TASK_SLOTS slot varbits exist."
        }

        PortTaskRewards.assertCacheAnchors()
    }

    const val MIN_EXPECTED_TASK_ROWS: Int = 500

    const val ANCHOR_BOUNTY_TASK_ID: Int = 438

    const val ANCHOR_COURIER_TASK_ID: Int = 286
}
