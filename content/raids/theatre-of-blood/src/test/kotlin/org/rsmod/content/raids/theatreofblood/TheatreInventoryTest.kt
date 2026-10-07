@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.raids.theatreofblood

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.inv.InvObj

class TheatreInventoryTest {
    @Test fun `full inventory keeps all rewards and does not charge recovery fee`() {
        val inventory = List(28) { InvObj(2, 1) }
        val stored = listOf(InvObj(3, 1))
        assertNull(plan(inventory, stored))
        assertEquals(stored, listOf(InvObj(3, 1)))
    }

    @Test fun `fee consumes coins and the freed slot can receive an item`() {
        val inventory = List(28) { if (it == 0) InvObj(1, 100_000) else InvObj(2, 1) }
        val result = checkNotNull(plan(inventory, listOf(InvObj(3, 1)), fee = 100_000))
        assertEquals(InvObj(3, 1), result.inventory[0])
        assertTrue(result.stored.all { it == null })
        assertEquals(listOf(TheatreSlotTransfer(0, 100_000)), result.payment)
    }

    @Test fun `insufficient coins leave recovery and carried items untouched`() {
        val inventory = listOf(InvObj(1, 99_999), null)
        val stored = listOf(InvObj(3, 1, 55))
        assertNull(plan(inventory, stored, fee = 100_000))
        assertEquals(99_999, inventory[0]?.count)
        assertEquals(55, stored[0]?.vars)
    }

    @Test fun `partial collection retains the remainder and subsequent claims need no fee`() {
        val inventory = List(28) { if (it == 0) InvObj(1, 100_000) else InvObj(2, 1) }
        val first = checkNotNull(plan(inventory, listOf(InvObj(3, 1), InvObj(4, 1)), 100_000))
        assertNull(first.stored[0])
        assertEquals(InvObj(4, 1), first.stored[1])
        val banked = first.inventory.toMutableList().apply { this[2] = null }
        val second = checkNotNull(plan(banked, first.stored, fee = 0))
        assertTrue(second.payment.isEmpty())
        assertTrue(second.stored.all { it == null })
        assertNull(plan(second.inventory, second.stored))
    }

    @Test fun `stack overflow permits only a safe partial withdrawal`() {
        val inventory = listOf(InvObj(1, Int.MAX_VALUE - 3))
        val result = checkNotNull(plan(inventory, listOf(InvObj(1, 10))))
        assertEquals(Int.MAX_VALUE, result.inventory[0]?.count)
        assertEquals(7, result.stored[0]?.count)
        assertEquals(3, result.withdrawals.single().count)
        assertNull(plan(result.inventory, result.stored))
    }

    @Test fun `different item vars never merge or overwrite each other`() {
        val original = listOf(InvObj(1, 9, 10), null, null)
        val result = checkNotNull(plan(original, listOf(InvObj(1, 3, 20), InvObj(3, 1, 42))))
        assertEquals(InvObj(1, 9, 10), result.inventory[0])
        assertEquals(InvObj(1, 3, 20), result.inventory[1])
        assertEquals(InvObj(3, 1, 42), result.inventory[2])
    }

    @Test fun `non-stackable batches claim as many individual items as fit`() {
        val result = checkNotNull(plan(listOf(null, null), listOf(InvObj(3, 5, 7))))
        assertEquals(listOf(InvObj(3, 1, 7), InvObj(3, 1, 7)), result.inventory)
        assertEquals(InvObj(3, 3, 7), result.stored.single())
    }

    @Test fun `invalid quantities and negative fees fail without a mutation`() {
        assertNull(plan(listOf(null), listOf(InvObj(3, 0))))
        assertNull(plan(listOf(InvObj(1, -1)), listOf(InvObj(3, 1))))
        assertNull(plan(listOf(null), listOf(InvObj(3, 1)), fee = -1))
    }

    @Test fun `random partial claims conserve every object and var combination`() {
        val random = Random(63019)
        repeat(2_000) {
            fun randomItems(size: Int): List<InvObj?> = List(size) {
                if (random.nextInt(3) == 0) null
                else InvObj(random.nextInt(1, 6), random.nextInt(1, 8), random.nextInt(3))
            }
            val inventory = randomItems(28)
            val stored = randomItems(42)
            val result = plan(inventory, stored) ?: return@repeat
            assertEquals(totals(inventory + stored), totals(result.inventory + result.stored))
            assertTrue(result.inventory.filterNotNull().all { it.count > 0 })
            assertTrue(result.stored.filterNotNull().all { it.count > 0 })
            assertEquals(28, result.inventory.size)
            assertEquals(42, result.stored.size)
        }
    }

    private fun plan(inventory: List<InvObj?>, stored: List<InvObj?>, fee: Int = 0) =
        TheatreClaimPlan.build(inventory, stored, coins = 1, fee = fee, stackable = { it == 1 })

    private fun totals(items: List<InvObj?>) = items.filterNotNull()
        .groupBy { it.id to it.vars }
        .mapValues { (_, group) -> group.sumOf { it.count.toLong() } }
}

@org.junit.jupiter.api.parallel.ResourceLock("ServerCacheManager")
class TheatreInventoryIntegrationTest {
    private val random = object : org.rsmod.api.random.GameRandom {
        override fun of(maxExclusive: Int) = 0
        override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
        override fun randomDouble() = 0.0
    }

    @Test fun `recovery stores original vars and rejects replacing an existing chest`() {
        val player = player()
        val service = TheatreInventory(random)
        val first = listOf(InvObj("obj.coins", 200_000, 11), InvObj("obj.bones", 1, 90))
        assertTrue(service.storeRecovery(player, first))
        assertEquals(first, service.recovery(player).filterNotNull { true })
        assertEquals(100_000, player.vars[TheatreInventory.RECOVERY_FEE])
        org.junit.jupiter.api.Assertions.assertFalse(service.storeRecovery(player, listOf(InvObj("obj.bones"))))
        assertEquals(first, service.recovery(player).filterNotNull { true })
        assertEquals(dev.openrune.types.InvScope.Perm, service.recovery(player).type.scope)
        org.junit.jupiter.api.Assertions.assertFalse(service.canEnter(player))
    }

    @Test fun `full inventory and poor accounts keep recovery items without paying`() {
        val player = player()
        val service = TheatreInventory(random)
        service.storeRecovery(player, listOf(InvObj("obj.bones", 1, 33)))
        for (slot in player.inv.indices) player.inv[slot] = InvObj("obj.bones")
        player.inv[0] = InvObj("obj.coins", 200_000)
        val before = player.inv.toList()
        assertEquals(0, service.reclaim(player))
        assertEquals(before, player.inv.toList())
        assertEquals(100_000, player.vars[TheatreInventory.RECOVERY_FEE])
        player.inv[1] = null
        player.inv[0] = InvObj("obj.coins", 99_999)
        assertEquals(0, service.reclaim(player))
        assertEquals(99_999, player.inv[0]?.count)
        assertEquals(33, service.recovery(player)[0]?.vars)
    }

    @Test fun `partial recovery pays once and repeated collection cannot duplicate items`() {
        val player = player()
        val service = TheatreInventory(random)
        val first = InvObj("obj.bones", 1, 33)
        val second = InvObj("obj.bones", 1, 44)
        service.storeRecovery(player, listOf(first, second))
        for (slot in player.inv.indices) player.inv[slot] = InvObj("obj.bones")
        player.inv[0] = InvObj("obj.coins", 100_000)
        assertEquals(1, service.reclaim(player))
        assertEquals(first, player.inv[0])
        assertEquals(0, player.vars[TheatreInventory.RECOVERY_FEE])
        assertEquals(second, service.recovery(player)[1])
        player.inv[1] = null
        assertEquals(1, service.reclaim(player))
        assertEquals(second, player.inv[1])
        assertEquals(0, service.reclaim(player))
        assertTrue(service.recovery(player).isEmpty())
        assertTrue(service.canEnter(player))
    }

    @Test fun `rewards are generated once and remain pending when inventory is full`() {
        val player = player()
        val service = TheatreInventory(random)
        assertTrue(service.generateRewards(player))
        val generated = service.rewards(player).toList()
        org.junit.jupiter.api.Assertions.assertFalse(service.generateRewards(player))
        assertEquals(generated, service.rewards(player).toList())
        assertEquals(dev.openrune.types.InvScope.Perm, service.rewards(player).type.scope)
        for (slot in player.inv.indices) player.inv[slot] = InvObj("obj.bones")
        assertEquals(0, service.claimRewards(player))
        assertEquals(1, player.vars[TheatreInventory.CLAIM_READY])
        assertEquals(generated, service.rewards(player).toList())
        player.inv.fillNulls()
        assertTrue(service.claimRewards(player) > 0)
        val after = player.inv.toList()
        assertEquals(0, service.claimRewards(player))
        assertEquals(after, player.inv.toList())
        assertEquals(0, player.vars[TheatreInventory.CLAIM_READY])
    }

    @Test fun `common rolls cannot award uniques and a unique selection awards one item`() {
        val common = player()
        val purple = player()
        val service = TheatreInventory(random)
        assertTrue(service.generateRewards(common, rolls = 3))
        assertTrue(service.generateRewards(purple, rolls = 3, unique = true))
        val commonItems = service.rewards(common).filterNotNull { true }
        val purpleItems = service.rewards(purple).filterNotNull { true }
        assertEquals(3, commonItems.size)
        assertEquals(1, purpleItems.size)
        val commonIds = TheatreData.rewardsList.filter { !it.unique }.map { InvObj(it.obj).id }
        val uniqueIds = TheatreData.rewardsList.filter { it.unique }.map { InvObj(it.obj).id }
        assertTrue(commonItems.all { it.id in commonIds })
        assertTrue(purpleItems.all { it.id in uniqueIds })
    }

    @Test fun `zero contribution receives cabbage and message without a unique`() {
        val player = player()
        val service = TheatreInventory(random)
        assertTrue(service.generateRewards(player, unique = true, contributed = false))
        assertEquals(listOf(InvObj("obj.cabbage"), InvObj("obj.tob_vampyre_message")),
            service.rewards(player).filterNotNull { true })
    }

    @Test fun `common quantity penalties preserve the roll and never affect uniques`() {
        val full = player()
        val reduced = player()
        val uniqueFull = player()
        val uniqueReduced = player()
        val first = TheatreInventory(org.rsmod.api.random.DefaultGameRandom(9))
        val second = TheatreInventory(org.rsmod.api.random.DefaultGameRandom(9))
        assertTrue(first.generateRewards(full))
        assertTrue(second.generateRewards(reduced, quantityPercent = 25))
        val fullItems = first.rewards(full).filterNotNull { true }
        val reducedItems = second.rewards(reduced).filterNotNull { true }
        assertEquals(fullItems.map { it.id }, reducedItems.map { it.id })
        assertEquals(fullItems.map { (it.count / 4).coerceAtLeast(1) },
            reducedItems.map { it.count })
        assertTrue(first.generateRewards(uniqueFull, unique = true))
        assertTrue(second.generateRewards(uniqueReduced, unique = true, quantityPercent = 0))
        assertEquals(first.rewards(uniqueFull).toList(), second.rewards(uniqueReduced).toList())
    }

    private fun player() = org.rsmod.game.entity.Player().apply {
        inv = org.rsmod.game.inv.Inventory.create("inv.inv")
        worn = org.rsmod.game.inv.Inventory.create("inv.worn")
        invMap["inv.inv"] = inv
        invMap["inv.worn"] = worn
        client = object : org.rsmod.game.client.Client<Any, Any> {
            override fun write(message: Any) {}
            override fun close() {}
            override fun read(player: org.rsmod.game.entity.Player) {}
            override fun flush() {}
            override fun flushHighPriority() {}
            override fun unregister(service: Any, player: org.rsmod.game.entity.Player) {}
        }
    }

    companion object {
        @JvmStatic @org.junit.jupiter.api.BeforeAll fun cache() {
            dev.openrune.ServerCacheManager.init(241).close()
        }
    }
}
