@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.fishing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.random.GameRandom
import org.rsmod.content.skills.fishing.FishingHarpoonCharges.Kind
import org.rsmod.content.skills.fishing.scripts.FishBarrelScript
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FishingHarpoonChargesTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun setup() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun cleanup() { restore.asReversed().forEach { it() }; restore.clear() }

    @Test fun `native active and depleted partners expose the verified check options`() {
        for (kind in Kind.entries) {
            val active = checkNotNull(ServerCacheManager.getItem(kind.active.asRSCM()))
            val inactive = checkNotNull(ServerCacheManager.getItem(kind.inactive.asRSCM()))
            val slot = if (kind == Kind.CRYSTAL || kind == Kind.INFERNAL) 2 else 1
            assertEquals("Check", active.interfaceOptions[slot])
            assertNull(inactive.interfaceOptions[slot])
            assertEquals(3, active.wearpos1)
        }
    }

    @Test fun `legacy active zero gains initial charges but inactive zero never gains charges`() {
        for (kind in Kind.entries) {
            assertEquals(kind.initial, FishingHarpoonCharges.charges(InvObj(kind.active)))
            assertEquals(0, FishingHarpoonCharges.charges(InvObj(kind.inactive)))
        }
    }

    @Test fun `first successful consumption materializes legacy charges on the actual item`() {
        val p = player(); p.inv[0] = InvObj(Kind.CRYSTAL.active)
        assertTrue(FishingHarpoonCharges.consume(p, select(p, 0), Random()))
        assertEquals(9_999, p.inv[0]?.vars)
    }

    @Test fun `every active last charge becomes the correct inactive style and never refills`() {
        for (kind in Kind.entries) {
            val p = player(); p.inv[0] = InvObj(kind.active, vars = 1)
            assertTrue(FishingHarpoonCharges.consume(p, select(p, 0), Random()))
            assertEquals(kind.inactive.asRSCM(), p.inv[0]?.id)
            assertEquals(0, FishingHarpoonCharges.charges(checkNotNull(p.inv[0])))
            assertFalse(FishingHarpoonCharges.consume(p, select(p, 0), Random()))
        }
    }

    @Test fun `equal type and vars cannot authorize spending a replacement item`() {
        val p = player(); p.inv[0] = InvObj(Kind.INFERNAL.active, vars = 7)
        val selected = select(p, 0)
        p.inv[0] = checkNotNull(p.inv[0]).copy()
        assertFalse(FishingHarpoonCharges.consume(p, selected, Random()))
        assertEquals(7, p.inv[0]?.vars)
    }

    @Test fun `our synchronous fish transaction refreshes unchanged tool identity before depletion`() {
        val p = player(); p.inv[0] = InvObj(Kind.CRYSTAL.active, vars = 1)
        val selected = select(p, 0)
        assertTrue(p.invAdd(p.inv, "obj.tempoross_raw_harpoonfish", 1).success)
        assertFalse(FishingHarpoonCharges.consume(p, selected, Random()))
        val refreshed = FishingHarpoonCharges.refreshAfterTransaction(p, selected)
        assertTrue(FishingHarpoonCharges.consume(p, refreshed, Random()))
        assertEquals(Kind.CRYSTAL.inactive.asRSCM(), p.inv[0]?.id)
    }

    @Test fun `infernal fish deletion reconstructs the tool but consumes exactly once`() {
        val p = player(); p.inv[0] = InvObj(Kind.INFERNAL.active, vars = 1)
        p.inv[1] = InvObj("obj.tempoross_raw_harpoonfish")
        val selected = select(p, 0)
        assertTrue(p.invDel(p.inv, "obj.tempoross_raw_harpoonfish", 1).success)
        assertTrue(FishingHarpoonCharges.consume(p, FishingHarpoonCharges.refreshAfterTransaction(p, selected), Random()))
        assertEquals(Kind.INFERNAL.inactive.asRSCM(), p.inv[0]?.id)
        assertFalse(FishingHarpoonCharges.consume(p, FishingHarpoonCharges.refreshAfterTransaction(p, selected), Random()))
    }

    @Test fun `transaction refresh rejects changed charges missing items and foreign inventories`() {
        val p = player(); p.inv[0] = InvObj(Kind.CRYSTAL.active, vars = 2)
        val selected = select(p, 0)
        p.inv[0] = InvObj(Kind.CRYSTAL.active, vars = 1)
        assertNull(FishingHarpoonCharges.refreshAfterTransaction(p, selected))
        p.inv[0] = null
        assertNull(FishingHarpoonCharges.refreshAfterTransaction(p, selected))
        assertNull(FishingHarpoonCharges.refreshAfterTransaction(player(), selected))
    }

    @Test fun `worn and carried copies keep independent charges and repeated callbacks are rejected`() {
        val p = player(); p.inv[0] = InvObj(Kind.INFERNAL.active, vars = 4)
        p.worn[3] = InvObj(Kind.INFERNAL.active, vars = 9)
        val selected = FishingHarpoonCharges.select(p, p.worn[3])
        assertTrue(FishingHarpoonCharges.consume(p, selected, Random()))
        assertFalse(FishingHarpoonCharges.consume(p, selected, Random()))
        assertEquals(8, p.worn[3]?.vars); assertEquals(4, p.inv[0]?.vars)
    }

    @Test fun `full inventory recharge consumes only the required shards and preserves twenty thousand`() {
        val p = player(); p.inv.objs.fill(InvObj("obj.logs"))
        p.inv[0] = InvObj(Kind.CRYSTAL.active, vars = 19_950)
        p.inv[1] = InvObj("obj.prif_crystal_shard", 5)
        assertTrue(FishingHarpoonCharges.recharge(p, checkNotNull(select(p, 0)), "obj.prif_crystal_shard", Int.MAX_VALUE))
        assertEquals(20_000, p.inv[0]?.vars)
        assertEquals(4, p.inv.count("obj.prif_crystal_shard"))
        assertFalse(FishingHarpoonCharges.recharge(p, checkNotNull(select(p, 0)), "obj.prif_crystal_shard"))
        assertEquals(4, p.inv.count("obj.prif_crystal_shard"))
    }

    @Test fun `inactive crystal recharges with the exact requested shard count`() {
        val p = player(); p.inv[0] = InvObj(Kind.CRYSTAL.inactive)
        p.inv[1] = InvObj("obj.prif_crystal_shard", 7)
        assertTrue(FishingHarpoonCharges.recharge(p, checkNotNull(select(p, 0)), "obj.prif_crystal_shard", 3))
        assertEquals(Kind.CRYSTAL.active.asRSCM(), p.inv[0]?.id)
        assertEquals(300, p.inv[0]?.vars); assertEquals(4, p.inv.count("obj.prif_crystal_shard"))
    }

    @Test fun `infernal empty styles accept either fuel exactly once and cannot top up`() {
        for (kind in Kind.entries.filterNot { it.crystal }) for (fuel in listOf("obj.smouldering_stone", "obj.dragon_harpoon")) {
            val p = player(); p.inv[0] = InvObj(kind.inactive); p.inv[1] = InvObj(fuel)
            assertTrue(FishingHarpoonCharges.recharge(p, checkNotNull(select(p, 0)), fuel))
            assertEquals(5_000, p.inv[0]?.vars); assertEquals(0, p.inv.count(fuel))
            p.inv[1] = InvObj(fuel)
            assertFalse(FishingHarpoonCharges.recharge(p, checkNotNull(select(p, 0)), fuel))
            assertEquals(1, p.inv.count(fuel))
        }
    }

    @Test fun `failed fuel removal leaves inactive harpoon and all other items unchanged`() {
        val p = player(); p.inv[0] = InvObj(Kind.INFERNAL.inactive); p.inv[1] = InvObj("obj.logs")
        val before = p.inv.objs.copyOf()
        assertFalse(FishingHarpoonCharges.recharge(p, checkNotNull(select(p, 0)), "obj.smouldering_stone"))
        assertArrayEquals(before, p.inv.objs)
    }

    @Test fun `cancelled negative or stale recharge requests cannot consume materials`() {
        val p = player(); p.inv[0] = InvObj(Kind.CRYSTAL.inactive); p.inv[1] = InvObj("obj.prif_crystal_shard", 10)
        val selected = checkNotNull(select(p, 0))
        assertFalse(FishingHarpoonCharges.recharge(p, selected, "obj.prif_crystal_shard", 0))
        assertFalse(FishingHarpoonCharges.recharge(p, selected, "obj.prif_crystal_shard", -1))
        p.inv[0] = InvObj(Kind.CRYSTAL.inactive)
        assertFalse(FishingHarpoonCharges.recharge(p, selected, "obj.prif_crystal_shard", 2))
        assertEquals(10, p.inv.count("obj.prif_crystal_shard"))
    }

    @Test fun `crystal signet preservation works for all native ring variants and not carried rings`() {
        for (ring in listOf("obj.elven_signet", "obj.celestial_signet", "obj.celestial_signet_charged")) {
            val p = player(); p.inv[0] = InvObj(Kind.CRYSTAL.active, vars = 1); p.worn[12] = InvObj(ring)
            assertTrue(FishingHarpoonCharges.consume(p, select(p, 0), Random(0)))
            assertEquals(1, p.inv[0]?.vars)
            p.worn[12] = null; p.inv[1] = InvObj(ring)
            assertTrue(FishingHarpoonCharges.consume(p, select(p, 0), Random(0)))
            assertEquals(Kind.CRYSTAL.inactive.asRSCM(), p.inv[0]?.id)
        }
    }

    @Test fun `signet cannot preserve infernal cooking charges`() {
        val p = player(); p.inv[0] = InvObj(Kind.INFERNAL.active, vars = 1); p.worn[12] = InvObj("obj.elven_signet")
        FishingHarpoonCharges.consume(p, select(p, 0), Random(0))
        assertEquals(Kind.INFERNAL.inactive.asRSCM(), p.inv[0]?.id)
    }

    @Test fun `barrel consumption cannot take fish stored before this catch`() {
        val p = player()
        p.attr[AttributeKey<MutableMap<String, Int>>("fish_barrel")] = mutableMapOf("obj.raw_shark" to 4)
        assertTrue(FishBarrelScript.consumeNewlyStored(p, "obj.raw_shark", 3))
        assertFalse(FishBarrelScript.consumeNewlyStored(p, "obj.raw_shark", 3))
        assertEquals(3, FishBarrelScript.storedCount(p, "obj.raw_shark"))
    }

    private fun select(p: Player, slot: Int) = FishingHarpoonCharges.select(p, p.inv[slot])
    private fun player() = Player().apply {
        uuid = 1; observerUUID = 1; slotId = 1; assignUid()
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
    }
    private class Random(val value: Int = 9) : GameRandom {
        override fun of(maxExclusive: Int) = value.coerceAtMost(maxExclusive - 1)
        override fun of(minInclusive: Int, maxInclusive: Int) = (minInclusive + value).coerceAtMost(maxInclusive)
        override fun randomDouble() = value / 10.0
    }
    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null); restore += { field.set(null, previous) }; field.set(null, value)
    }
}
