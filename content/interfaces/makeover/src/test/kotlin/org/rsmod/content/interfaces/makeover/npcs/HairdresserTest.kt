package org.rsmod.content.interfaces.makeover.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.content.interfaces.makeover.MakeoverPage
import org.rsmod.content.interfaces.makeover.MakeoverSessions
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class HairdresserTest {
    @Test
    fun `Emma talks and the decline branch leaves appearance unchanged`() {
        val f = Fixture("npc.hairdresser_boutique")
        val before = f.player.appearance.identKitSnapshot()
        f.op(1)
        assertTrue(f.messages.any { "I got scissors" in it.toString() })
        f.finish(3)
        assertNull(MakeoverSessions.current(f.player))
        assertEquals(before, f.player.appearance.identKitSnapshot())
    }

    @Test
    fun `both hairdressers open the correct page through direct and dialogue options`() {
        for (npc in listOf("npc.hairdresser", "npc.hairdresser_boutique")) {
            for ((op, choice, page) in listOf(
                Triple(4, 1, MakeoverPage.Hair), Triple(5, 2, MakeoverPage.FacialHair),
            )) {
                for (viaDialogue in listOf(false, true)) {
                    val f = Fixture(npc)
                    f.op(if (viaDialogue) 1 else op)
                    if (viaDialogue) f.finish(choice)
                    assertEquals(page, MakeoverSessions.current(f.player)?.page)
                    assertTrue(f.player.ui.containsModal("interface.makeover"))
                }
            }
        }
    }

    @Test
    fun `headwear blocks both pages through direct and dialogue options`() {
        for (npc in listOf("npc.hairdresser", "npc.hairdresser_boutique")) {
            for ((op, choice) in listOf(4 to 1, 5 to 2)) {
                for (viaDialogue in listOf(false, true)) {
                    val f = Fixture(npc)
                    f.player.worn[0] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.bronze_full_helm".asRSCM())))
                    f.op(if (viaDialogue) 1 else op)
                    if (viaDialogue) f.finish(choice)
                    assertNull(MakeoverSessions.current(f.player))
                    assertTrue(f.messages.any { "take off your hat" in it.toString() })
                }
            }
        }
    }

    private class Fixture(symbol: String) {
        val messages = mutableListOf<Any>()
        val player = Player().apply {
            coords = CoordGrid(3205, 3416)
            currentMapClock = 100
            processedMapClock = 100
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
        }
        private val npc = Npc(symbol, player.coords.translateX(1))
        private val events = EventBus()
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAlignment = { TextAlignment() },
        )

        init {
            with(Hairdresser()) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun op(op: Int) {
            val event = when (op) {
                1 -> NpcEvents.Op1(npc)
                4 -> NpcEvents.Op4(npc)
                5 -> NpcEvents.Op5(npc)
                else -> error("Unsupported option")
            }
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
                    else -> error("Unexpected hairdresser dialogue")
                }
                player.activeCoroutine?.resumeWith(input)
            }
            assertFalse(player.activeCoroutine?.isSuspended == true)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
