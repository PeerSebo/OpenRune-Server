package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.objtx.TransactionObj
import org.rsmod.objtx.TransactionResult

@Singleton
internal class TheatreInventory @Inject constructor(private val random: GameRandom) {
    private val transactions by lazy { InvTransactions.from() }

    fun rewards(player: Player): Inventory = player.invMap.getOrPut(REWARDS)
    fun recovery(player: Player): Inventory = player.invMap.getOrPut(RECOVERY)

    fun hasPending(player: Player): Boolean =
        rewards(player).isNotEmpty() || recovery(player).isNotEmpty() ||
            player.vars[CLAIM_READY] != 0

    fun canEnter(player: Player): Boolean {
        if (recovery(player).isNotEmpty()) {
            player.mes("Recover your items from the Theatre before starting another raid.")
            return false
        }
        if (rewards(player).isNotEmpty() || player.vars[CLAIM_READY] != 0) {
            player.mes("Collect your Theatre rewards before starting another raid.")
            return false
        }
        return true
    }

    fun generateRewards(
        player: Player,
        rolls: Int = 3,
        unique: Boolean = false,
        contributed: Boolean = true,
        quantityPercent: Int = 100,
    ): Boolean {
        if (rolls !in 1..3 || quantityPercent !in 0..100 ||
            rewards(player).isNotEmpty() || player.vars[CLAIM_READY] != 0) {
            return false
        }
        val rows = TheatreData.rewardsList.filter { it.unique == unique }
        val weight = rows.sumOf { it.weight.toLong() }
        check(rows.isNotEmpty() && weight in 1..Int.MAX_VALUE.toLong())
        check(rows.all { it.weight > 0 && it.min > 0 && it.max >= it.min })
        val items = if (!contributed) listOf(InvObj("obj.cabbage"),
            InvObj("obj.tob_vampyre_message")) else List(if (unique) 1 else rolls) {
            var roll = random.of(weight.toInt())
            val row = rows.first { candidate ->
                roll -= candidate.weight
                roll < 0
            }
            val rolled = random.of(row.min, row.max)
            val quantity = if (unique) rolled else
                (rolled.toLong() * quantityPercent / 100).coerceAtLeast(1).toInt()
            InvObj(row.obj, quantity)
        }
        val chest = rewards(player)
        if (items.size > chest.size) return false
        val slots = List(chest.size) { items.getOrNull(it) }
        if (!commit(chest to slots)) return false
        set(player, CLAIM_READY, 1)
        return true
    }

    fun claimRewards(player: Player): Int {
        val chest = rewards(player)
        val before = chest.toList()
        val moved = collect(player, chest, fee = 0)
        if (moved > 0) {
            for (slot in before.indices) {
                val item = before[slot] ?: continue
                val obtained = item.count - (chest[slot]?.count ?: 0)
                if (obtained > 0) CollectionLog.grant(player, item.id, obtained)
            }
        }
        if (chest.isEmpty()) set(player, CLAIM_READY, 0)
        return moved
    }

    fun storeRecovery(player: Player, items: List<InvObj>): Boolean {
        if (items.isEmpty()) return true
        val chest = recovery(player)
        if (chest.isNotEmpty()) return false
        if (items.size > chest.size) return false
        if (!commit(chest to List(chest.size) { items.getOrNull(it) })) return false
        set(player, RECOVERY_FEE, TheatreData.rules.recoveryFee)
        return true
    }

    fun reclaim(player: Player): Int {
        val chest = recovery(player)
        if (chest.isEmpty()) {
            player.mes("You have no items to recover from the Theatre.")
            return 0
        }
        val fee = player.vars[RECOVERY_FEE].coerceAtLeast(0)
        val moved = collect(player, chest, fee)
        if (moved > 0) set(player, RECOVERY_FEE, 0)
        return moved
    }

    fun buySupply(player: Player, index: Int, quantity: Int = 1): Boolean {
        val supply = TheatreData.suppliesList.getOrNull(index) ?: return false
        if (quantity !in 1..28) return false
        val total = supply.cost.toLong() * quantity
        val points = player.vars[SUPPLY_POINTS]
        if (total < 0 || total > points) {
            player.mes("You do not have enough points to buy that supply.")
            return false
        }
        val result = transactions.transaction(autoCommit = true) {
            add(select(player.inv), supply.item.asRSCM(), quantity)
        }
        if (!result.success) {
            player.mes("You do not have enough inventory space.")
            return false
        }
        set(player, SUPPLY_POINTS, points - total.toInt())
        return true
    }

    private fun collect(player: Player, chest: Inventory, fee: Int): Int {
        val plan = TheatreClaimPlan.build(
            inventory = player.inv.toList(),
            stored = chest.toList(),
            coins = "obj.coins".asRSCM(),
            fee = fee,
            stackable = { ServerCacheManager.getItem(it)?.isStackable == true },
        )
        if (plan == null) {
            player.mes(if (fee > 0 && player.inv.count("obj.coins") < fee)
                "You need $fee coins to recover your items."
            else "You need more inventory space to collect your items.")
            return 0
        }
        if (!commit(player.inv to plan.inventory, chest to plan.stored)) return 0
        if (fee > 0) player.mes("You pay $fee coins to recover your items.")
        if (chest.isNotEmpty()) player.mes("The remaining items are still safely stored at the Theatre.")
        return plan.withdrawals.sumOf { it.count.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun set(player: Player, key: String, value: Int) =
        VarPlayerIntMapSetter.set(player, key, value)

    private fun commit(vararg changes: Pair<Inventory, List<InvObj?>>): Boolean {
        if (changes.any { (inventory, slots) ->
            inventory.size != slots.size || slots.any { it != null && it.count <= 0 }
        }) return false
        check(changes.map { it.first }.distinct().size == changes.size)
        val old = changes.map { it.first.toList() }
        val result = transactions.transaction(autoCommit = true) {
            val inventories = changes.map { select(it.first) }
            execute {
                for ((index, change) in changes.withIndex()) {
                    for (slot in change.second.indices) {
                        inventories[index].image[slot] = change.second[slot]?.let {
                            TransactionObj(it.id, it.count, it.vars)
                        }
                    }
                }
                TransactionResult.Ok(requested = 1, completed = 1)
            }
        }
        if (!result.success) return false
        for ((index, change) in changes.withIndex()) {
            for (slot in change.second.indices) {
                if (old[index][slot] != change.second[slot]) change.first.modifiedSlots.set(slot)
            }
        }
        return true
    }

    companion object {
        const val REWARDS = "inv.theatre_rewards"
        const val RECOVERY = "inv.theatre_recovery"
        const val CLAIM_READY = "varp.theatre_claim_ready"
        const val RECOVERY_FEE = "varp.theatre_recovery_fee"
        const val SUPPLY_POINTS = "varp.theatre_supply_points"
    }
}

internal data class TheatreSlotTransfer(val slot: Int, val count: Int)

internal data class TheatreClaimPlan(
    val payment: List<TheatreSlotTransfer>,
    val withdrawals: List<TheatreSlotTransfer>,
    val inventory: List<InvObj?>,
    val stored: List<InvObj?>,
) {
    companion object {
        fun build(
            inventory: List<InvObj?>,
            stored: List<InvObj?>,
            coins: Int,
            fee: Int,
            stackable: (Int) -> Boolean,
        ): TheatreClaimPlan? {
            if (fee < 0 || (inventory + stored).any { it != null && it.count <= 0 }) return null
            val destination = inventory.toMutableList()
            val remaining = stored.toMutableList()
            val payment = mutableListOf<TheatreSlotTransfer>()
            var unpaid = fee
            for (slot in destination.indices) {
                val item = destination[slot] ?: continue
                if (item.id != coins || unpaid == 0) continue
                val count = minOf(item.count, unpaid)
                payment += TheatreSlotTransfer(slot, count)
                destination[slot] = if (item.count == count) null else item.copy(count = item.count - count)
                unpaid -= count
            }
            if (unpaid != 0) return null
            val withdrawals = mutableListOf<TheatreSlotTransfer>()
            for (source in remaining.indices) {
                val item = remaining[source] ?: continue
                val count = if (stackable(item.id)) {
                    val existing = destination.indexOfFirst { it?.id == item.id && it.vars == item.vars }
                    val slot = if (existing >= 0) existing else destination.indexOfFirst { it == null }
                    if (slot < 0) continue
                    val current = destination[slot]?.count ?: 0
                    val amount = minOf(item.count, Int.MAX_VALUE - current)
                    if (amount == 0) continue
                    destination[slot] = item.copy(count = current + amount)
                    amount
                } else {
                    val amount = minOf(item.count, destination.count { it == null })
                    repeat(amount) {
                        destination[destination.indexOfFirst { it == null }] = item.copy(count = 1)
                    }
                    amount
                }
                if (count == 0) continue
                withdrawals += TheatreSlotTransfer(source, count)
                remaining[source] = if (item.count == count) null else item.copy(count = item.count - count)
            }
            if (withdrawals.isEmpty()) return null
            return TheatreClaimPlan(payment, withdrawals, destination, remaining)
        }
    }
}
