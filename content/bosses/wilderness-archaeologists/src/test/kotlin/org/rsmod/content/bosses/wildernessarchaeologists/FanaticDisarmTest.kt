package org.rsmod.content.bosses.wildernessarchaeologists

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
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.random.GameRandom
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.getInvObj

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FanaticDisarmTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    private var previousTransactions: Any? = null
    private val transactionsField by lazy {
        Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
            .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    }

    @BeforeEach fun services() {
        previousTransactions = transactionsField.get(null)
        transactionsField.set(null, InvTransactions.from())
    }

    @AfterEach fun restoreServices() { transactionsField.set(null, previousTransactions) }

    @Test fun `disarm transfers charged equipment intact and notifies worn listeners`() {
        val f = Fixture()
        val weapon = InvObj("obj.tots_charged", vars = 77)
        f.player.worn[Wearpos.RightHand.slot] = weapon
        assertTrue(f.disarm())
        assertNull(f.player.worn[Wearpos.RightHand.slot])
        assertEquals(weapon, f.player.inv[0])
        assertEquals(1, f.unequipped)
        assertEquals(1, f.changed)
    }

    @Test fun `inventory blocking food prevents disarm without losing equipment`() {
        val f = Fixture()
        repeat(28) { f.player.inv[it] = InvObj("obj.piedish") }
        val weapon = InvObj("obj.tots_charged", vars = 42)
        f.player.worn[Wearpos.RightHand.slot] = weapon
        val before = f.player.inv.objs.toList()
        assertFalse(f.disarm())
        assertEquals(weapon, f.player.worn[Wearpos.RightHand.slot])
        assertEquals(before, f.player.inv.objs.toList())
        assertEquals(0, f.unequipped)
    }

    @Test fun `full inventory blocks a merging ammunition stack too`() {
        val f = Fixture()
        repeat(28) { f.player.inv[it] = InvObj("obj.piedish") }
        f.player.inv[0] = InvObj("obj.bronze_arrow", 10)
        f.player.worn[Wearpos.Quiver.slot] = InvObj("obj.bronze_arrow", 100)
        assertFalse(f.disarm())
        assertEquals(10, f.player.inv[0]?.count)
        assertEquals(100, f.player.worn[Wearpos.Quiver.slot]?.count)
    }

    @Test fun `empty equipment is a harmless disarm miss`() {
        val f = Fixture()
        assertFalse(f.disarm())
        assertTrue(f.player.inv.objs.all { it == null })
        assertEquals(0, f.changed)
    }

    @Test fun `one available slot accepts the whole ammunition stack`() {
        val f = Fixture()
        repeat(27) { f.player.inv[it] = InvObj("obj.piedish") }
        f.player.worn[Wearpos.Quiver.slot] = InvObj("obj.bronze_arrow", 100)
        assertTrue(f.disarm())
        assertNull(f.player.worn[Wearpos.Quiver.slot])
        assertEquals(100, f.player.inv[27]?.count)
        assertEquals(1, f.changed)
    }

    @Test fun `equipment removal occurs at the projectile landing callback`() {
        val f = Fixture()
        val weapon = InvObj("obj.tots_charged", vars = 17)
        f.player.worn[Wearpos.RightHand.slot] = weapon
        f.launchDisarm()
        f.advance(2)
        assertEquals(weapon, f.player.worn[Wearpos.RightHand.slot])
        f.advance(1)
        assertNull(f.player.worn[Wearpos.RightHand.slot])
        assertEquals(weapon, f.player.inv[0])
    }

    @Test fun `stale fight and missing player cancel a queued disarm`() {
        val deadBoss = Fixture()
        deadBoss.player.worn[Wearpos.RightHand.slot] = InvObj("obj.rune_sword")
        deadBoss.launchDisarm()
        deadBoss.active = false
        deadBoss.advance(3)
        assertNotNull(deadBoss.player.worn[Wearpos.RightHand.slot])
        assertEquals(0, deadBoss.changed)

        val logout = Fixture()
        logout.player.worn[Wearpos.RightHand.slot] = InvObj("obj.rune_sword")
        logout.launchDisarm()
        logout.present = false
        logout.advance(3)
        assertNotNull(logout.player.worn[Wearpos.RightHand.slot])
        assertEquals(0, logout.changed)
    }

    @Test fun `inventory becoming full during flight blocks a queued disarm`() {
        val f = Fixture()
        f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.rune_sword")
        f.launchDisarm()
        repeat(28) { f.player.inv[it] = InvObj("obj.piedish") }
        f.advance(3)
        assertNotNull(f.player.worn[Wearpos.RightHand.slot])
        assertEquals(0, f.changed)
    }

    @Test fun `queued disarm cannot interrupt a protected player action`() {
        val f = Fixture()
        f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.tots_charged", vars = 17)
        val action = f.player.launch { pause { false } }
        assertTrue(f.player.isAccessProtected)
        f.launchDisarm()
        f.advance(3)
        assertTrue(action.isSuspended)
        assertNotNull(f.player.worn[Wearpos.RightHand.slot])
        assertEquals(0, f.changed)
        f.player.cancelActiveCoroutine()
        f.advance(2)
        assertNull(f.player.worn[Wearpos.RightHand.slot])
        assertEquals(1, f.changed)
    }

    @Test fun `a long protected action exhausts disarm retries without interruption`() {
        val f = Fixture()
        f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.tots_charged", vars = 17)
        val action = f.player.launch { pause { false } }
        f.launchDisarm()
        f.advance(20)
        assertTrue(action.isSuspended)
        assertNotNull(f.player.worn[Wearpos.RightHand.slot])
        f.player.cancelActiveCoroutine()
        f.advance(20)
        assertNotNull(f.player.worn[Wearpos.RightHand.slot])
        assertEquals(0, f.changed)
    }

    @Test fun `fight cancellation during a protected disarm retry preserves equipment`() {
        val f = Fixture()
        f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.rune_sword")
        f.player.launch { pause { false } }
        f.launchDisarm()
        f.advance(3)
        f.active = false
        f.player.cancelActiveCoroutine()
        f.advance(3)
        assertNotNull(f.player.worn[Wearpos.RightHand.slot])
        assertEquals(0, f.changed)
    }

    private class Fixture {
        var unequipped = 0
        var changed = 0
        var active = true
        var present = true
        private val queues = WorldQueueList()
        private val process = WorldQueueListProcess(queues)
        private val events = EventBus().apply {
            listOf("obj.tots_charged", "obj.rune_sword", "obj.bronze_arrow")
                .map { getInvObj(InvObj(it)).contentGroup }.toSet().forEach { group ->
                    subscribeKeyed(HeldEquipEvents.Unequip::class.java, group.toLong()) { unequipped++ }
                }
            subscribeUnbound(HeldEquipEvents.WearposChange::class.java) { changed++ }
        }
        private val operation = FanaticDisarm(WornUnequipOp(events))
        private val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        val player = Player().apply {
            inv = Inventory(InventoryServerType(size = 28, flags = InventoryServerType.PROTECT_FLAG), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14, flags = InventoryServerType.PROTECT_FLAG), arrayOfNulls(14))
        }
        fun disarm() = operation.removeEquipment(player, random)
        fun launchDisarm() = operation.schedule(queues, 3, random, { active }, { player.takeIf { present } })
        fun advance(ticks: Int) { repeat(ticks) { process.process() } }
    }
}
