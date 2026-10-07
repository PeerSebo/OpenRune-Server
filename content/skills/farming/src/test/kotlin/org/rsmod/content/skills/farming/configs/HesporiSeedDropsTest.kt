package org.rsmod.content.skills.farming.configs

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HesporiSeedDropsTest {
    @BeforeAll fun cache() {
        ServerCacheManager.init(241).close()
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @Test fun `repeat fruit harvests cannot roll a second check health seed`() {
        val f = Fixture()
        val fruit = checkNotNull(FarmingCrops.byId("apple"))
        HesporiSeedDrops.roll(f.access, fruit, atCheckHealth = true)
        repeat(6) { HesporiSeedDrops.roll(f.access, fruit, atCheckHealth = false) }
        assertEquals(listOf(18), f.rolls)
        assertEquals(1, f.player.inv.count("obj.hespori_seed"))
    }

    @Test fun `harvest only crops cannot roll at check health`() {
        val f = Fixture()
        val herb = checkNotNull(FarmingCrops.byId("guam"))
        HesporiSeedDrops.roll(f.access, herb, atCheckHealth = true)
        assertTrue(f.rolls.isEmpty())
        HesporiSeedDrops.roll(f.access, herb, atCheckHealth = false)
        assertEquals(listOf(196), f.rolls)
        assertEquals(1, f.player.inv.count("obj.hespori_seed"))
    }

    @Test fun `Arno opt out prevents seed rolls`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.farmguild_hespori_seeds", 1)
        HesporiSeedDrops.roll(f.access, checkNotNull(FarmingCrops.byId("magic")), atCheckHealth = true)
        assertTrue(f.rolls.isEmpty())
        assertTrue(f.player.inv.objs.all { it == null })
    }

    @Test fun `a full inventory loses the bonus seed and preserves all produce`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.potato")
        f.player.inv[1] = InvObj("obj.potato")
        val before = f.player.inv.objs.toList()
        HesporiSeedDrops.roll(f.access, checkNotNull(FarmingCrops.byId("potato")), atCheckHealth = false)
        assertEquals(listOf(562), f.rolls)
        assertEquals(before, f.player.inv.objs.toList())
    }

    @Test fun `unsupported crops and placed scarecrows receive no invented rate`() {
        val excluded = FarmingCrops.ALL.filter { HesporiSeedDrops.denominator(it) == null }.map { it.id }.toSet()
        assertEquals(setOf("scarecrow", "flax", "hemp", "cotton"), excluded)
    }

    private class Fixture {
        val rolls = mutableListOf<Int>()
        val random = object : GameRandom {
            override fun of(maxExclusive: Int): Int { rolls += maxExclusive; return 0 }
            override fun of(minInclusive: Int, maxInclusive: Int): Int = error("unused")
            override fun randomDouble(): Double = error("unused")
        }
        val player = Player().apply { inv = Inventory(InventoryServerType(size = 2), arrayOfNulls(2)) }
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { random }, getEventBus = { error("unused") }, getNpcList = { error("unused") }, getPlayerList = { error("unused") },
            getCollision = { error("unused") }, getAreaChecker = { error("unused") }, getAlignment = { error("unused") },
            getLocInteractions = { error("unused") }, getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") },
            getMarketPrices = { error("unused") }, getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
            getWorldRepo = { error("unused") }, getHitModifier = { error("unused") }))
    }
}
