@file:OptIn(org.rsmod.annotations.InternalApi::class)
package org.rsmod.content.skills.runecrafting.action

import dev.openrune.ServerCacheManager
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
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.api.table.runecrafting.RunecraftingRunesRow
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RunecraftingDiaryEventTest {
    @Test
    fun `successful ordinary essence combination publishes delivered lava rune count`() {
        val f = Fixture()
        f.craft()
        assertEquals(1, f.player.inv.count("obj.lavarune"))
        assertEquals(1, f.completions.size)
        assertEquals("obj.lavarune", f.completions.single().target)
        assertEquals(1, f.completions.single().count)
        assertEquals(CoordGrid(2585, 4838), f.completions.single().coords)
    }

    @Test
    fun `failed combination rolls and missing materials never publish completion`() {
        val failed = Fixture(miss = true)
        failed.craft()
        assertEquals(0, failed.player.inv.count("obj.lavarune"))
        assertTrue(failed.completions.isEmpty())
        val missing = Fixture()
        missing.player.inv[1] = null
        missing.craft()
        assertTrue(missing.completions.isEmpty())
    }

    @Test
    fun `guardian essence combination does not masquerade as the normal diary action`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.gotr_guardian_essence")
        f.craft()
        assertTrue(f.completions.isEmpty())
    }

    @Test
    fun `ordinary cosmic batch reports rune output rather than essence count`() {
        for (essence in listOf("obj.blankrune_high", "obj.blankrune_daeyalt")) {
            val f = Fixture()
            for (slot in f.player.inv.indices) f.player.inv[slot] = null
            for (slot in 0..27) f.player.inv[slot] = InvObj(essence)
            f.craftCosmics()
            val ordinary = f.completions.single { it.action == PlayerAction.CraftRune }
            val batch = f.completions.single { it.action == PlayerAction.CraftRuneBatch }
            assertEquals(28, ordinary.count)
            assertEquals(56, batch.count)
            assertEquals(56, f.player.inv.count("obj.cosmicrune"))
        }
    }

    @Test
    fun `extract boosted and guardian cosmic batches cannot complete quantity tasks`() {
        val extracted = Fixture()
        for (slot in extracted.player.inv.indices) extracted.player.inv[slot] = null
        for (slot in 0..26) extracted.player.inv[slot] = InvObj("obj.blankrune_high")
        extracted.player.inv[27] = InvObj("obj.scar_extract_twisted")
        extracted.craftCosmics()
        assertTrue(extracted.player.inv.count("obj.cosmicrune") > 56)
        assertTrue(extracted.completions.none { it.action == PlayerAction.CraftRuneBatch })
        val guardian = Fixture()
        for (slot in guardian.player.inv.indices) guardian.player.inv[slot] = null
        for (slot in 0..27) guardian.player.inv[slot] = InvObj("obj.gotr_guardian_essence")
        guardian.craftCosmics()
        assertTrue(guardian.completions.none { it.action == PlayerAction.CraftRuneBatch })
    }

    @Test
    fun `pure and daeyalt essence in one click publish one combined quantity fact`() {
        val f = Fixture()
        for (slot in 0..27) {
            f.player.inv[slot] = InvObj(if (slot < 10) "obj.blankrune_high" else "obj.blankrune_daeyalt")
        }
        f.craftCosmics()
        assertEquals(56, f.player.inv.count("obj.cosmicrune"))
        assertEquals(56, f.completions.single { it.action == PlayerAction.CraftRuneBatch }.count)
    }

    private class Fixture(miss: Boolean = false) {
        val player = Player().apply {
            client = SilentClient()
            coords = CoordGrid(2585, 4838)
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            statMap.setBaseLevel("stat.runecrafting", 99)
            statMap.setCurrentLevel("stat.runecrafting", 99)
            inv[0] = InvObj("obj.blankrune_high")
            inv[1] = InvObj("obj.earthrune", 10)
            inv[2] = InvObj("obj.earth_talisman")
        }
        val completions = mutableListOf<PlayerActionCompleteEvent>()
        private val events = EventBus().apply {
            subscribeUnbound(PlayerActionCompleteEvent::class.java) { completions += this }
        }
        private val random = object : GameRandom {
            override fun randomDouble() = if (miss) 0.99 else 0.0
            override fun of(maxExclusive: Int) = if (miss) maxExclusive - 1 else 0
            override fun of(minInclusive: Int, maxInclusive: Int) = if (miss) maxInclusive else minInclusive
        }
        fun craft() {
            val recipe = RunecraftingAltarsRow.all().first { it.altarObject.internalName == "loc.fire_altar" }
                .combo.first { it.output?.internalName == "obj.lavarune" && it.input?.internalName == "obj.earthrune" }
            run { with(RunecraftAction) { craftCombination(recipe, XpModifiers(emptySet())) } }
        }

        fun craftCosmics() {
            val rune = RunecraftingRunesRow.all().first { it.output.internalName == "obj.cosmicrune" }
            run { with(RunecraftAction) { craftRune(rune, XpModifiers(emptySet())) } }
        }

        private fun run(action: suspend ProtectedAccess.() -> Unit) {
            val coroutine = GameCoroutine("combination-diary-test")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine,
                ProtectedAccessContextFactory.empty().copy(getEventBus = { events }, getRandom = { random }))
            var completed: Result<Unit>? = null
            val block: suspend () -> Unit = { access.action() }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completed = result }
            })
            checkNotNull(completed).getOrThrow()
        }
    }

    private class SilentClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic
        @BeforeAll
        fun setup() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field =
                    Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restore += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun cleanup() { restore.asReversed().forEach { it() } }
    }
}
