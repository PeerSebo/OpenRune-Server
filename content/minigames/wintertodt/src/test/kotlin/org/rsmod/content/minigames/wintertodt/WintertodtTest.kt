package org.rsmod.content.minigames.wintertodt

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@ResourceLock("ServerCacheManager")
@Execution(ExecutionMode.SAME_THREAD)
class WintertodtTest {
    private val restored = mutableListOf<() -> Unit>()

    @BeforeEach fun transactions() {
        replace("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replace("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun cleanup() { restored.asReversed().forEach { it() } }
    private fun replace(owner: String, key: String, value: Any) {
        val f = Class.forName(owner).getDeclaredField(key).apply { isAccessible = true }
        val old = f.get(null)
        restored += { f.set(null, old) }
        f.set(null, value)
    }

    @Test fun `settings use current warmth and reward cart rules`() {
        val r = WintertodtData.rules
        assertEquals(50, r.level)
        assertEquals(3500, r.maxEnergy)
        assertEquals(100, r.respawnTicks)
        assertEquals(8000, r.rewardCap)
        assertEquals(500, r.minimumPoints)
        assertTrue(r.contains(r.entry))
        assertFalse(r.contains(r.exit))
        assertFalse(r.exposed(r.entry))
        assertTrue(r.entry.z > 3966)
        assertTrue(r.exit.z < 3964)
    }

    @Test fun `only lit braziers with healthy pyromancers drain energy`() {
        val round = WintertodtRound(WintertodtData.rules)
        round.begin(100)
        repeat(10) { assertFalse(round.advance()) }
        assertEquals(3500, round.energy)
        round.camps[0].light()
        round.advance()
        assertEquals(3498, round.energy)
        round.hurtPyro(0); round.hurtPyro(0)
        repeat(10) { assertFalse(round.advance()) }
        assertEquals(3498, round.energy)
        round.camps[0].heal()
        round.advance()
        assertEquals(3496, round.energy)
    }

    @Test fun `feeding does not drain extra energy`() {
        val r = WintertodtRound(WintertodtData.rules)
        r.begin(10)
        r.camps.forEach { it.light() }
        r.advance()
        assertEquals(3492, r.energy)
        assertEquals(10, WintertodtData.actions.getValue("root").points)
        assertEquals(25, WintertodtData.actions.getValue("kindling").points)
    }

    @Test fun `one round ends at zero and cooldown prevents further drain`() {
        val r = WintertodtRound(WintertodtData.rules)
        r.begin(10)
        r.camps.forEach { it.light() }
        var cycles = 0
        while (!r.advance()) { cycles++; assertTrue(cycles < 500) }
        assertEquals(0, r.energy)
        r.finish(448)
        assertEquals(548, r.deadline)
        repeat(10) { assertFalse(r.advance()) }
        assertTrue(r.camps.all { it.state == BrazierState.Unlit && it.health == 14 })
    }

    @Test fun `brazier actions cannot award duplicate state transitions`() {
        val camp = WinterCamp()
        assertFalse(camp.repair())
        assertTrue(camp.light())
        assertFalse(camp.light())
        camp.state = BrazierState.Broken
        assertTrue(camp.repair())
        assertFalse(camp.repair())
        camp.health = 0
        assertFalse(camp.light())
        assertTrue(camp.heal())
        assertFalse(camp.heal())
    }

    @Test fun `hud matches the ten native integer arguments in compass order`() {
        val r = WintertodtRound(WintertodtData.rules)
        r.begin(10)
        r.camps[0].light()
        r.camps[1].state = BrazierState.Broken
        r.camps[2].health = 0
        assertArrayEquals(arrayOf(525, 3500, 1, 1, 0, 1, 1, 2, 0, 0), r.hud(525))
    }

    @Test fun `new round invalidates previous action generations and resets danger history`() {
        val r = WintertodtRound(WintertodtData.rules)
        r.begin(10)
        val old = r.id
        r.hurtPyro(0); r.hurtPyro(0)
        repeat(4) { r.extinguish(it, true) }
        assertTrue(r.pyroFell); assertTrue(r.allBroken)
        r.finish(100); r.begin(200)
        assertNotEquals(old, r.id)
        assertFalse(r.pyroFell); assertFalse(r.allBroken)
    }

    @Test fun `warm clothing and firemaking level reduce cold damage`() {
        assertEquals(32, WintertodtGame.damagePercent(WinterDamage.Cold, 50, 0, 0))
        assertEquals(4, WintertodtGame.damagePercent(WinterDamage.Cold, 99, 4, 4))
        assertEquals(12, WintertodtGame.damagePercent(WinterDamage.Brazier, 99, 4, 4))
        assertEquals(18, WintertodtGame.damagePercent(WinterDamage.Snow, 99, 4, 4))
    }

    @Test fun `special damage is unaffected by lit brazier count`() {
        for (kind in listOf(WinterDamage.Brazier, WinterDamage.Snow))
            assertEquals(WintertodtGame.damagePercent(kind, 70, 2, 0),
                WintertodtGame.damagePercent(kind, 70, 2, 4))
    }

    @Test fun `only equipped warming items qualify in the packed table`() {
        for (obj in listOf("obj.clue_hunter_gloves", "obj.pyromancer_top", "obj.wint_torch_offhand",
            "obj.santa_hat", "obj.staff_of_fire"))
            assertTrue(obj.asRSCM() in WintertodtData.warmItems)
        for (obj in listOf("obj.bunnyears", "obj.logs", "obj.bronze_axe"))
            assertFalse(obj.asRSCM() in WintertodtData.warmItems)
    }

    @Test fun `sub threshold points never yield a roll`() {
        for (points in listOf(0, 1, 499)) assertEquals(0, WintertodtRewards.rolls(points, Rolls(0)))
    }

    @Test fun `reward thresholds and fractional rolls are exact`() {
        assertEquals(2, WintertodtRewards.rolls(500, Rolls(0)))
        assertEquals(3, WintertodtRewards.rolls(1000, Rolls(0)))
        assertEquals(4, WintertodtRewards.rolls(1200, Rolls(199)))
        assertEquals(3, WintertodtRewards.rolls(1200, Rolls(200)))
        assertEquals(3, WintertodtRewards.rolls(999, Rolls(498)))
        assertEquals(2, WintertodtRewards.rolls(999, Rolls(499)))
    }

    @Test fun `full inventory retains the pending reward and permit without rerolling`() {
        val p = player(1)
        p.inv[0] = InvObj("obj.hammer")
        WintertodtState.set(p, WintertodtState.RewardPool, 1)
        val dice = Rolls(0)
        assertFalse(WintertodtRewards.collect(p, dice))
        assertEquals(1, p.vars[WintertodtState.RewardPool])
        assertEquals("obj.phoenixpet".asRSCM(), p.vars[WintertodtState.PendingObj])
        p.inv[0] = null
        assertTrue(WintertodtRewards.collect(p, Rolls(4999)))
        assertEquals("obj.phoenixpet".asRSCM(), p.inv[0]!!.id)
        assertEquals(0, p.vars[WintertodtState.RewardPool])
        assertEquals(0, p.vars[WintertodtState.PendingObj])
        assertFalse(WintertodtRewards.collect(p, dice))
    }

    @Test fun `pending rewards survive a restored player state`() {
        val p = player()
        WintertodtState.set(p, WintertodtState.RewardPool, 1)
        WintertodtState.set(p, WintertodtState.PendingObj, "obj.coins".asRSCM())
        WintertodtState.set(p, WintertodtState.PendingCount, 2500)
        assertTrue(WintertodtRewards.collect(p, Rolls(0)))
        assertEquals(2500, p.inv.count("obj.coins"))
        assertEquals(0, p.vars[WintertodtState.RewardPool])
    }

    @Test fun `pyromancer outfit grants missing pieces in native order`() {
        val p = player()
        val dice = { Rolls(1, 1, 1, 1, 1, 0) }
        assertEquals("obj.pyromancer_top", WintertodtRewards.roll(p, dice()).obj)
        p.inv[0] = InvObj("obj.pyromancer_top")
        assertEquals("obj.pyromancer_hood", WintertodtRewards.roll(p, dice()).obj)
        p.inv[1] = InvObj("obj.pyromancer_hood")
        assertEquals("obj.pyromancer_bottom", WintertodtRewards.roll(p, dice()).obj)
    }

    @Test fun `three warm gloves convert subsequent glove rolls to magic seeds`() {
        val p = player()
        repeat(3) { p.inv[it] = InvObj("obj.pyromancer_gloves") }
        assertEquals("obj.magic_tree_seed", WintertodtRewards.roll(p, Rolls(1, 1, 1, 0)).obj)
    }

    @Test fun `collection log entries do not count as owned equipment`() {
        val p = player()
        p.invMap["inv.collection_transmit"]!![0] = InvObj("obj.pyromancer_top", 100)
        assertEquals(0, WintertodtRewards.owned(p, "obj.pyromancer_top"))
        p.invMap["inv.bank"]!![0] = InvObj("obj.pyromancer_top", 2)
        assertEquals(2, WintertodtRewards.owned(p, "obj.pyromancer_top"))
    }

    @Test fun `offhand bruma torches count toward the duplicate cap`() {
        val p = player()
        repeat(3) { p.inv[it] = InvObj("obj.wint_torch_offhand") }
        assertEquals(3, WintertodtRewards.owned(p, "obj.wint_torch"))
        assertEquals("obj.torstol_seed", WintertodtRewards.roll(p, Rolls(1, 1, 1, 1, 0)).obj)
    }

    @Test fun `low level accounts can roll every material category`() {
        for (category in 0..24) {
            val p = player()
            val r = WintertodtRewards.roll(p, Rolls(1, 1, 1, 1, 1, 1, 1, category, 0, 0))
            assertTrue(r.count > 0)
            assertTrue(ServerCacheManager.getItem(r.obj.asRSCM()) != null)
            assertTrue(r.noted || r.obj == "obj.coins")
        }
    }

    @Test fun `materials carry viable quantities and noted equivalents`() {
        assertEquals((0..8).toSet(), WintertodtData.rewards.map { it.category }.toSet())
        for (row in WintertodtData.rewards) {
            val item = ServerCacheManager.getItem(row.obj.asRSCM())!!
            assertTrue(row.quantity.first > 0 && row.quantity.last >= row.quantity.first)
            assertTrue(item.stackable || item.certlink > 0, row.obj)
        }
    }

    @Test fun `low level players can receive the highest eligible gem tier`() {
        val p = player()
        val reward = WintertodtRewards.roll(p, Rolls(1, 1, 1, 1, 1, 1, 1, 3, 99, 99))
        assertEquals("obj.uncut_diamond", reward.obj)
    }

    @Test fun `permanent reward variables are excluded from session clearing`() {
        val p = player()
        WintertodtState.set(p, WintertodtState.RewardPool, 25)
        WintertodtState.set(p, WintertodtState.PendingObj, "obj.coins".asRSCM())
        WintertodtState.set(p, WintertodtState.Points, 800)
        WintertodtState.clearSession(p)
        assertEquals(25, p.vars[WintertodtState.RewardPool])
        assertEquals("obj.coins".asRSCM(), p.vars[WintertodtState.PendingObj])
        assertEquals(0, p.vars[WintertodtState.Points])
        assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(WintertodtState.PendingObj.asRSCM())!!.scope)
    }
    private fun player(size: Int = 28) = Player().apply {
        uuid = 1L
        inv = Inventory(InventoryServerType(size = size), arrayOfNulls(size))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        invMap["inv.bank"] = Inventory(InventoryServerType(size = 800), arrayOfNulls(800))
        invMap["inv.collection_transmit"] = Inventory(InventoryServerType(size = 2000), arrayOfNulls(2000))
        for (stat in listOf("firemaking", "woodcutting", "fletching", "herblore", "mining", "farming", "fishing", "crafting"))
            statMap.setBaseLevel("stat.$stat", 1)
    }
    private class Rolls(vararg values: Int) : GameRandom {
        private val values = ArrayDeque(values.toList())
        override fun of(maxExclusive: Int) = (values.removeFirstOrNull() ?: 1).coerceIn(0, maxExclusive - 1)
        override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
        override fun randomDouble() = 0.9
    }
    companion object { @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() } }
}
