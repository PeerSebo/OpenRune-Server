package org.rsmod.content.areas.city.falador

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
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
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcUEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class EmilyTest {
    @Test
    fun `all barmaids buy every glass for two coins even with a full inventory`() {
        for (symbol in Barmaids) {
            val f = Fixture(symbol)
            repeat(28) { f.player.inv[it] = InvObj("obj.beer_glass") }
            f.useGlass()
            f.finish(1)
            assertEquals(0, f.player.inv.count("obj.beer_glass"))
            assertEquals(56, f.player.inv.count("obj.coins"))
        }
    }

    @Test
    fun `declining or removing glasses while dialogue is open grants no coins`() {
        for (remove in listOf(false, true)) {
            val f = Fixture(Barmaids.first())
            f.player.inv[0] = InvObj("obj.beer_glass")
            f.useGlass()
            if (remove) f.player.inv[0] = null
            f.finish(if (remove) 1 else 2)
            assertEquals(0, f.player.inv.count("obj.coins"))
            assertEquals(if (remove) 0 else 1, f.player.inv.count("obj.beer_glass"))
        }
    }

    @Test
    fun `coin overflow rolls back the glass exchange`() {
        val f = Fixture(Barmaids.first())
        f.player.inv[0] = InvObj("obj.beer_glass")
        f.player.inv[1] = InvObj("obj.coins", Int.MAX_VALUE)
        f.useGlass()
        f.finish(1)
        assertEquals(1, f.player.inv.count("obj.beer_glass"))
        assertEquals(Int.MAX_VALUE, f.player.inv.count("obj.coins"))
    }

    @Test
    fun `glass exchange uses the existing bar counter reach without remote dialogue`() {
        for (symbol in Barmaids) {
            for (distance in listOf(2, 4, 5, 20)) {
                val f = Fixture(symbol)
                f.player.inv[0] = InvObj("obj.beer_glass")
                f.player.coords = f.npc.coords.translateZ(distance)
                f.useGlass(approach = true)
                assertEquals(distance <= 4, f.player.activeCoroutine?.isSuspended == true)
                if (distance <= 4) f.finish(1)
                assertEquals(if (distance <= 4) 2 else 0, f.player.inv.count("obj.coins"))
            }
        }
    }

    private class Fixture(symbol: String) {
        val player = Player().apply {
            coords = CoordGrid(2950, 3370)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
        }
        val npc = Npc(symbol, player.coords.translateX(1))
        private val events = EventBus()
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
        )

        init {
            with(Emily(ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap())))) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun useGlass(approach: Boolean = false) {
            val glass = checkNotNull(ServerCacheManager.getItem("obj.beer_glass".asRSCM()))
            val event = if (approach) NpcUEvents.Ap(npc, 0, glass, npc.type)
                else NpcUEvents.Op(npc, 0, glass, npc.type)
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context) {
                assertTrue(events.publish(this, event))
            })
        }

        fun finish(choice: Int) {
            var steps = 0
            while (player.activeCoroutine?.isSuspended == true && steps++ < 10) {
                val input = when {
                    player.ui.containsModal("interface.chatmenu") ->
                        ResumePauseButtonInput("component.chatmenu:options", choice)
                    player.ui.containsModal("interface.chat_left") ->
                        ResumePauseButtonInput("component.chat_left:continue", -1)
                    player.ui.containsModal("interface.chat_right") ->
                        ResumePauseButtonInput("component.chat_right:continue", -1)
                    else -> error("Unexpected barmaid dialogue")
                }
                player.activeCoroutine?.resumeWith(input)
            }
            assertFalse(player.activeCoroutine?.isSuspended == true)
        }
    }

    companion object {
        private val Barmaids = listOf("npc.risingsun_barmaid", "npc.risingsun_barmaid2", "npc.risingsun_barmaid3")
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restore += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll
        fun cleanup() { restore.asReversed().forEach { it() } }
    }
}
