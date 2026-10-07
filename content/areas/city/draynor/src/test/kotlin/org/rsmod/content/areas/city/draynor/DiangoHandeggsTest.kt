package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.events.interact.PlayerUEvents
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DiangoHandeggsTest {
    @Test
    fun `each native handegg is passed once into the recipients free weapon slot`() {
        for (egg in DiangoHandeggs.eggs) {
            val f = Fixture()
            f.source.inv[4] = InvObj(egg.item)
            repeat(28) { f.target.inv[it] = InvObj("obj.spade") }
            val targetInventory = f.target.inv.toList()
            f.pass(egg, 4)
            assertNull(f.source.inv[4])
            assertEquals(egg.item.id, f.target.worn[Wearpos.RightHand.slot]?.id)
            assertNull(f.target.worn[Wearpos.LeftHand.slot])
            assertEquals(targetInventory, f.target.inv.toList())
            assertEquals(1, f.equipmentChanges)
            assertEquals(egg.throwAnimation.asRSCM(), f.source.pendingSequence.id)
            assertEquals(egg.catchAnimation.asRSCM(), f.target.pendingSequence.id)
            assertEquals(1, f.updates.updatedZones.values.sumOf { it.size })
            f.pass(egg, 4)
            assertEquals(1, f.equipmentChanges)
            assertEquals(1, f.updates.updatedZones.values.sumOf { it.size })
            assertEquals(1, f.target.worn.count(egg.item.internalName))
        }
    }

    @Test
    fun `occupied hands never displace equipment or consume the senders egg`() {
        for (slot in listOf(Wearpos.RightHand.slot, Wearpos.LeftHand.slot)) {
            val f = Fixture()
            val egg = DiangoHandeggs.eggs.first()
            f.source.inv[0] = InvObj(egg.item)
            f.target.worn[slot] = InvObj(if (slot == Wearpos.RightHand.slot) "obj.bronze_sword" else "obj.wooden_shield")
            val original = f.target.worn.toList()
            f.pass(egg)
            assertEquals(original, f.target.worn.toList())
            assertEquals(1, f.source.inv.count(egg.item.internalName))
            assertEquals(0, f.equipmentChanges)
            assertTrue(f.updates.updatedZones.isEmpty())
        }
    }

    @Test
    fun `unavailable distant and different plane recipients preserve item ownership`() {
        val conditions = listOf<(Player) -> Unit>(
            { it.delay(4) }, { it.pendingLogout = true }, { it.loggingOut = true },
            { it.coords = CoordGrid(3080, 3258) }, { it.coords = CoordGrid(3080, 3251, 1) },
            { it.staffInvisible = true },
        )
        for (condition in conditions) {
            val f = Fixture()
            val egg = DiangoHandeggs.eggs.first()
            f.source.inv[0] = InvObj(egg.item)
            condition(f.target)
            f.pass(egg)
            assertEquals(1, f.source.inv.count(egg.item.internalName))
            assertEquals(0, f.target.worn.count(egg.item.internalName))
            assertEquals(0, f.equipmentChanges)
        }
    }

    @Test
    fun `stale item slots self targets and repeated clicks cannot manufacture eggs`() {
        val f = Fixture()
        val egg = DiangoHandeggs.eggs.first()
        f.source.inv[0] = InvObj("obj.spade")
        f.pass(egg)
        assertEquals("obj.spade".asRSCM(), f.source.inv[0]?.id)
        assertNull(f.target.worn[Wearpos.RightHand.slot])
        f.source.inv[0] = InvObj(egg.item)
        f.pass(egg, target = f.source)
        assertEquals(1, f.source.inv.count(egg.item.internalName))
        assertNull(f.source.worn[Wearpos.RightHand.slot])
    }

    @Test
    fun `recipient equipment restrictions are applied before transferring an egg`() {
        val f = Fixture(blockEquipment = true)
        val egg = DiangoHandeggs.eggs.first()
        f.source.inv[0] = InvObj(egg.item)
        f.pass(egg)
        assertEquals(1, f.source.inv.count(egg.item.internalName))
        assertNull(f.target.worn[Wearpos.RightHand.slot])
        assertEquals(0, f.equipmentChanges)
        assertTrue(f.updates.updatedZones.isEmpty())
    }

    @Test
    fun `handegg passing respects existing ironman item transfer rules`() {
        for (sourceIron in listOf(false, true)) {
            val f = Fixture()
            val egg = DiangoHandeggs.eggs.first()
            f.source.inv[0] = InvObj(egg.item)
            (if (sourceIron) f.source else f.target).setGamemode(PlayerGamemode.IRONMAN)
            f.pass(egg)
            assertEquals(1, f.source.inv.count(egg.item.internalName))
            assertNull(f.target.worn[Wearpos.RightHand.slot])
        }
    }

    @Test
    fun `completed transfers do not depend on a coroutine or delayed queue`() {
        val f = Fixture()
        val egg = DiangoHandeggs.eggs.first()
        f.source.inv[0] = InvObj(egg.item)
        f.pass(egg)
        f.source.activeCoroutine?.cancel()
        f.source.pendingLogout = true
        f.target.pendingLogout = true
        assertEquals(0, f.source.inv.count(egg.item.internalName))
        assertEquals(1, f.target.worn.count(egg.item.internalName))
        assertTrue(f.source.queueList.isEmpty)
        assertTrue(f.target.queueList.isEmpty)
    }

    private class Fixture(blockEquipment: Boolean = false) {
        val source = player(1, CoordGrid(3080, 3250))
        val target = player(2, CoordGrid(3080, 3251))
        val updates = ZoneUpdateMap()
        private val events = EventBus()
        private val restrictions = PlayerRestrictions(if (blockEquipment) setOf(
            PlayerRestrictionHook { _, action -> if (action is RestrictedAction.Equip) "You cannot equip that now." else null },
        ) else emptySet())
        var equipmentChanges = 0

        init {
            events.subscribeUnbound(HeldEquipEvents.WearposChange::class.java) {
                assertSame(target, player)
                equipmentChanges++
            }
            with(DiangoHandeggs(HeldEquipOp(events, restrictions), WorldRepository(updates))) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun pass(egg: DiangoHandeggs.Egg, slot: Int = 0, target: Player = this.target) {
            val coroutine = GameCoroutine("handegg-test")
            source.activeCoroutine = coroutine
            var completion: Result<Unit>? = null
            val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
            val access = ProtectedAccess(source, coroutine, context)
            val action: suspend () -> Unit = {
                assertTrue(events.publish(access, PlayerUEvents.Op(target, slot, egg.item)))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            checkNotNull(completion).getOrThrow()
            assertTrue(coroutine.isIdle)
        }

        private fun player(slot: Int, coords: CoordGrid) = Player().apply {
            slotId = slot
            uuid = slot.toLong()
            assignUid()
            this.coords = coords
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            pendingSequence = EntitySeq.NULL
            statMap.setBaseLevel("stat.hitpoints", 10)
            statMap.setCurrentLevel("stat.hitpoints", 10)
        }
    }

    companion object {
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restore += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll
        fun cleanup() { restore.asReversed().forEach { it() } }
    }
}
