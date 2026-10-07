package org.rsmod.content.bosses.chaoselemental

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.random.GameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChaosElementalRulesTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    private var previousTransactions: Any? = null
    private val transactionField by lazy {
        Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
            .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    }

    @BeforeEach fun setup() {
        previousTransactions = transactionField.get(null)
        transactionField.set(null, InvTransactions.from())
    }

    @AfterEach fun cleanup() { transactionField.set(null, previousTransactions) }

    @Test fun `a single empty slot removes the weapon first and keeps its charges`() {
        val player = player()
        repeat(27) { player.inv[it] = InvObj("obj.piedish") }
        val weapon = InvObj("obj.tots_charged", vars = 77)
        player.worn[Wearpos.RightHand.slot] = weapon
        player.worn[Wearpos.Quiver.slot] = InvObj("obj.bronze_arrow", 100)
        assertEquals(1, disarm(player))
        assertNull(player.worn[Wearpos.RightHand.slot])
        assertEquals(weapon, player.inv[27])
        assertEquals(100, player.worn[Wearpos.Quiver.slot]?.count)
    }

    @Test fun `inventory blocking prevents any equipment loss including stackable ammunition`() {
        val player = player()
        repeat(28) { player.inv[it] = InvObj("obj.piedish") }
        player.inv[0] = InvObj("obj.bronze_arrow", 10)
        player.worn[Wearpos.RightHand.slot] = InvObj("obj.rune_sword")
        player.worn[Wearpos.Quiver.slot] = InvObj("obj.bronze_arrow", 100)
        val before = player.worn.objs.toList()
        assertEquals(0, disarm(player))
        assertEquals(before, player.worn.objs.toList())
        assertEquals(10, player.inv[0]?.count)
    }

    @Test fun `one madness attack removes at most four pieces`() {
        val player = player()
        val equipment = listOf("obj.rune_sword", "obj.rune_full_helm", "obj.rune_platebody",
            "obj.rune_platelegs", "obj.rune_kiteshield")
        val slots = listOf(Wearpos.RightHand.slot, Wearpos.Hat.slot, Wearpos.Torso.slot,
            Wearpos.Legs.slot, Wearpos.LeftHand.slot)
        slots.zip(equipment).forEach { (slot, item) -> player.worn[slot] = InvObj(item) }
        assertEquals(4, disarm(player))
        assertEquals(1, player.worn.objs.count { it != null })
        assertEquals(4, player.inv.objs.count { it != null })
    }

    @Test fun `empty equipment is harmless`() { assertEquals(0, disarm(player())) }

    @Test fun `protected inventory cannot be disarmed during another action`() {
        val player = player().apply { delay = 10; processedMapClock = 0 }
        player.worn[Wearpos.RightHand.slot] = InvObj("obj.tots_charged", vars = 77)
        assertTrue(player.isAccessProtected)
        assertEquals(0, disarm(player))
        assertEquals(77, player.worn[Wearpos.RightHand.slot]?.vars)
    }

    @Test fun `projectile callback waits for protected action before unequipping`() {
        val player = player().apply { delay = 10; processedMapClock = 0 }
        player.worn[Wearpos.RightHand.slot] = InvObj("obj.tots_charged", vars = 77)
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        ChaosSpecialImpact.schedule(queues, 2, { true }, { player }, { disarm(it) })
        process.process()
        process.process()
        assertNotNull(player.worn[Wearpos.RightHand.slot])
        player.processedMapClock = 10
        repeat(2) { process.process() }
        assertNull(player.worn[Wearpos.RightHand.slot])
        assertEquals(77, player.inv[0]?.vars)
    }

    @Test fun `boss death cancels a special waiting for a protected action`() {
        val player = player().apply { delay = 10; processedMapClock = 0 }
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        var alive = true
        var hits = 0
        ChaosSpecialImpact.schedule(queues, 1, { alive }, { player }, { hits++ })
        process.process()
        alive = false
        player.processedMapClock = 10
        repeat(3) { process.process() }
        assertEquals(0, hits)
    }

    @Test fun `long protected action does not retain an old projectile indefinitely`() {
        val player = player().apply { delay = 100; processedMapClock = 0 }
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        var hits = 0
        ChaosSpecialImpact.schedule(queues, 1, { true }, { player }, { hits++ })
        repeat(20) { process.process() }
        player.processedMapClock = 100
        repeat(3) { process.process() }
        assertEquals(0, hits)
    }

    @Test fun `blocked inventory removes madness from attack selection`() {
        val chosen = (0..8).map { ChaosElementalRules.chooseAttack(FixedRandom(it), false) }
        assertFalse(ChaosAttack.MADNESS in chosen)
        assertTrue(ChaosAttack.CONFUSION in chosen)
        assertTrue(ChaosAttack.DISCORD in chosen)
    }

    @Test fun `available inventory allows all three distinct attacks`() {
        assertEquals(ChaosAttack.entries.toSet(), (0..9).map {
            ChaosElementalRules.chooseAttack(FixedRandom(it), true)
        }.toSet())
    }

    @Test fun `confusion stays on the same floor and moves away from the boss`() {
        val boss = CoordGrid(3261, 3927, 0)
        val source = boss.translate(5, 0)
        val destination = ChaosElementalRules.destination(boss, source, FixedRandom(0, 8)) { true }
        assertNotNull(destination)
        assertEquals(source.level, destination!!.level)
        assertTrue(destination.chebyshevDistance(boss) > source.chebyshevDistance(boss))
        assertTrue(source.chebyshevDistance(destination) in 3..8)
    }

    @Test fun `confusion never chooses a blocked landing`() {
        var attempts = 0
        assertNull(ChaosElementalRules.destination(CoordGrid(3261, 3927, 0),
            CoordGrid(3266, 3927, 0), FixedRandom(0, 8)) { attempts++; false })
        assertEquals(40, attempts)
    }

    @Test fun `staircase escape is not teleported across floors`() {
        assertNull(ChaosElementalRules.destination(CoordGrid(3261, 3927, 0),
            CoordGrid(3266, 3927, 1), FixedRandom()) { error("Must not query cross-floor collision") })
    }

    private fun player() = Player().apply {
        inv = Inventory(InventoryServerType(size = 28, flags = InventoryServerType.PROTECT_FLAG), arrayOfNulls(28))
        worn = Inventory(InventoryServerType(size = 14, flags = InventoryServerType.PROTECT_FLAG), arrayOfNulls(14))
    }
    private fun disarm(player: Player) = ChaosElementalRules.disarm(player, FixedRandom(0, 4), WornUnequipOp(EventBus()))
    private class FixedRandom(private val roll: Int = 0, private val inclusive: Int = 0) : GameRandom {
        override fun of(maxExclusive: Int) = roll.coerceIn(0, maxExclusive - 1)
        override fun of(minInclusive: Int, maxInclusive: Int) = inclusive.coerceIn(minInclusive, maxInclusive)
        override fun randomDouble() = 0.0
    }
}
