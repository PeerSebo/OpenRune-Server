package org.rsmod.content.minigames.sorceressgarden.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.content.minigames.sorceressgarden.configs.GardenSeason
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class SqirkJuiceDispatchTest {
    @Test
    fun `registered drink operations preserve per-season boosts and consume only one juice`() {
        for ((index, season) in GardenSeason.entries.withIndex()) {
            val f = Fixture(season)
            f.player.inv[0] = InvObj(season.juiceObj)
            f.player.inv[1] = InvObj(season.juiceObj)
            val type = checkNotNull(ServerCacheManager.getItem(season.juiceObj.asRSCM()))
            f.run { assertTrue(f.events.publish(this, HeldObjEvents.Op1(0, InvObj(season.juiceObj), type, f.player.inv))) }
            assertEquals(1, f.player.inv.count(season.juiceObj))
            assertEquals(50 + index, f.player.statMap.getCurrentLevel("stat.thieving").toInt())
            assertEquals(listOf(500, 1000, 1500, 2000)[index], f.player.runEnergy)
            assertEquals(0, f.player.statMap.getFineXP("stat.thieving"))
            assertEquals("seq.human_eat".asRSCM(), f.player.pendingSequence.id)
        }
    }

    @Test
    fun `registered crushing retains original ingredient quantities three-tick cadence and XP`() {
        for ((index, season) in GardenSeason.entries.withIndex()) {
            val f = Fixture(season)
            val count = listOf(5, 4, 3, 2)[index]
            f.ingredients(count * 2, 2)
            f.crush()
            assertEquals(0, f.player.inv.count(season.juiceObj))
            f.finishTick()
            repeat(2) { f.nextTick(); assertEquals(0, f.player.inv.count(season.juiceObj)) }
            f.nextTick()
            assertEquals(1, f.player.inv.count(season.juiceObj))
            assertEquals(count, f.player.inv.count(season.fruitObj))
            assertEquals(1, f.player.inv.count("obj.beer_glass"))
            assertEquals(50, f.player.statMap.getFineXP("stat.cooking"))
            repeat(2) { f.nextTick(); assertEquals(1, f.player.inv.count(season.juiceObj)) }
            f.nextTick()
            assertEquals(2, f.player.inv.count(season.juiceObj))
            assertEquals(0, f.player.inv.count(season.fruitObj))
            assertEquals(0, f.player.inv.count("obj.beer_glass"))
            assertEquals(100, f.player.statMap.getFineXP("stat.cooking"))
            assertEquals(1, f.player.inv.count("obj.pestle_and_mortar"))
            assertTrue(f.player.weakQueueList.isEmpty)
        }
    }

    @Test
    fun `insufficient fruit or a missing glass cannot consume ingredients or schedule production`() {
        for (season in GardenSeason.entries) {
            for ((fruit, glass) in listOf(season.sqirkCount - 1 to 1, season.sqirkCount to 0)) {
                val f = Fixture(season)
                f.ingredients(fruit, glass)
                val before = f.player.inv.objs.toList()
                f.crush()
                repeat(6) { f.nextTick() }
                assertEquals(before, f.player.inv.objs.toList())
                assertEquals(0, f.player.statMap.getFineXP("stat.cooking"))
                assertTrue(f.player.weakQueueList.isEmpty)
            }
        }
    }

    @Test
    fun `queued production rechecks ingredients when the glass disappears before the commit`() {
        val f = Fixture(GardenSeason.SUMMER)
        f.ingredients(2, 1)
        f.crush()
        val glass = f.player.inv.objs.indexOfFirst { it?.id == "obj.beer_glass".asRSCM() }
        f.player.inv[glass] = null
        repeat(6) { f.nextTick() }
        assertEquals(2, f.player.inv.count(GardenSeason.SUMMER.fruitObj))
        assertEquals(0, f.player.inv.count(GardenSeason.SUMMER.juiceObj))
        assertEquals(0, f.player.statMap.getFineXP("stat.cooking"))
    }

    private class Fixture(private val season: GardenSeason) {
        val player = Player().apply {
            inv = Inventory.create("inv.inv")
            currentMapClock = 100
            processedMapClock = 100
            runEnergy = 0
            statMap.setBaseLevel("stat.thieving", 50)
            statMap.setCurrentLevel("stat.thieving", 50)
        }
        val events = EventBus()
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
        private val factory = unused<ProtectedAccessContextFactory>().also {
            ProtectedAccessContextFactory::class.java.getDeclaredField("eventBus").apply { isAccessible = true }.set(it, events)
        }
        private val queues = PlayerQueueProcessor(events, ProtectedAccessLauncher(factory))

        init {
            with(SqirkJuiceScript()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun run(action: suspend ProtectedAccess.() -> Unit) {
            val coroutine = player.launch { ProtectedAccess(player, this, context).action() }
            assertTrue(coroutine.isIdle)
        }

        fun ingredients(fruit: Int, glasses: Int) {
            player.inv[0] = InvObj("obj.pestle_and_mortar")
            repeat(fruit) { player.inv[it + 1] = InvObj(season.fruitObj) }
            repeat(glasses) { player.inv[fruit + it + 1] = InvObj("obj.beer_glass") }
        }

        fun crush() = run {
            HeldUInteractions(events).interact(
                this, player.inv,
                checkNotNull(ServerCacheManager.getItem("obj.pestle_and_mortar".asRSCM())), 0,
                checkNotNull(ServerCacheManager.getItem(season.fruitObj.asRSCM())), 1,
            )
        }

        fun finishTick() { queues.process(player) }
        fun nextTick() {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            finishTick()
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
