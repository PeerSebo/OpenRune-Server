package org.rsmod.content.other.commandbrowser

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.cheat.register
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class CommandBrowserDispatchTest {
    @Test
    fun `packed modal and emitted components fit the previously published client widget table`() {
        // The client allocates its interface table once; groups above that capacity crash an already-running client.
        val startupWidgetGroups = arrayOfNulls<Any>(1100)
        val packed = checkNotNull(ServerCacheManager.getInterface(INTERFACE_ID))
        startupWidgetGroups[INTERFACE_ID] = packed

        val f = Fixture()
        f.registry.execute(f.player, "commands", emptyList())
        f.click("next")
        f.click("previous")
        f.player.ifClose(f.events)
        f.registry.execute(f.player, "commands", listOf("a12"))

        for (packet in f.client.messages.filterIsInstance<IfOpenSub>()) {
            if (packet.interfaceId == INTERFACE_ID) {
                assertSame(packed, startupWidgetGroups[packet.interfaceId])
            }
        }
        for (packet in f.client.messages.filterIsInstance<IfSetText>()) {
            assertSame(packed, startupWidgetGroups[packet.combinedId ushr 16])
            assertEquals(packet.combinedId,
                ServerCacheManager.fromComponent(packet.combinedId).packed)
        }
        val border = checkNotNull(packed.components[0]).onLoad!![1] as Int
        assertSame(packed, startupWidgetGroups[border ushr 16])
        assertEquals("component.command_browser:border".asRSCM(), border)
    }

    @Test
    fun `public command opens custom packed modal and includes restricted commands with rights`() {
        val f = Fixture()
        assertTrue(f.registry.execute(f.player, "commands", emptyList()))
        assertTrue(f.player.ui.containsModal(CommandBrowserScript.INTERFACE))
        assertTrue(f.client.messages.filterIsInstance<IfOpenSub>().any { it.interfaceId == INTERFACE_ID })
        assertTrue(f.text("command0").contains("::a00"))
        assertEquals("Administrator", f.text("privilege0"))
        assertTrue(f.text("command0").contains("ffb080"))
        assertEquals("Page 1 of 3", f.text("page"))
        assertFalse(f.executed)
    }

    @Test
    fun `native next previous buttons reach final page and clear unused rows`() {
        val f = Fixture()
        f.registry.execute(f.player, "commands", emptyList())
        f.click("next")
        assertEquals("Page 2 of 3", f.text("page"))
        assertTrue(f.text("command0").contains("::a06"))
        f.click("next")
        assertEquals("Page 3 of 3", f.text("page"))
        assertTrue(f.text("command0").contains("::a12"))
        assertTrue(f.text("command1").contains("::commands"))
        for (row in 2 until CommandCatalog.PAGE_SIZE) {
            assertEquals("", f.text("command$row"))
            assertEquals("", f.text("privilege$row"))
            assertEquals("", f.text("description$row"))
        }
        f.click("next")
        assertEquals("Page 3 of 3", f.text("page"))
        f.click("previous")
        assertEquals("Page 2 of 3", f.text("page"))
        assertFalse(f.executed)
    }

    @Test
    fun `filtering reopening and closing do not retain stale page or accept closed window clicks`() {
        val f = Fixture()
        f.registry.execute(f.player, "commands", emptyList())
        f.click("next")
        f.registry.execute(f.player, "commands", listOf("a12"))
        assertEquals("Page 1 of 1", f.text("page"))
        assertTrue(f.text("command0").contains("::a12"))
        assertEquals("", f.text("command1"))
        f.player.ifClose(f.events)
        val count = f.client.messages.size
        f.click("next")
        assertEquals(count, f.client.messages.size)
        f.registry.execute(f.player, "commands", listOf("no-such-command"))
        assertEquals("Page 1 of 1", f.text("page"))
        assertEquals("", f.text("command0"))
        assertTrue(f.text("summary").startsWith("0 matches"))
    }

    @Test
    fun `catalog rights match execution gate for ordinary and administrator accounts`() {
        val f = Fixture()
        f.registry.execute(f.player, "a00", emptyList())
        assertFalse(f.executed)
        f.player.modLevel = Rights.ADMINISTRATOR
        f.registry.execute(f.player, "commands", listOf("a00"))
        assertTrue(f.text("command0").contains("80ff80"))
        f.registry.execute(f.player, "a00", emptyList())
        assertTrue(f.executed)
    }

    private class Fixture {
        val events = EventBus()
        val registry = CheatCommandMap()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            currentMapClock = 100
            processedMapClock = 100
        }
        var executed = false

        private val contextFactory = (UNSAFE.allocateInstance(ProtectedAccessContextFactory::class.java) as ProtectedAccessContextFactory).apply {
            javaClass.getDeclaredField("eventBus").apply { isAccessible = true }.set(this, events)
        }
        private val access = ProtectedAccessLauncher(contextFactory)

        init {
            repeat(13) { index -> registry.register("a${index.toString().padStart(2, '0')}") {
                desc = "An administrator test command"
                requiredRights = Rights.ADMINISTRATOR
                cheat { executed = true }
            } }
            with(CommandBrowserScript(access)) {
                ScriptContext(events, registry, EngineQueueCache()).startup()
            }
        }

        fun click(name: String) {
            val component = ServerCacheManager.fromComponent("component.command_browser:$name".asRSCM())
            assertTrue(component.hasEvent(IfEvent.Op1), "Packed $name button must enable Op1")
            If3ButtonHandler(events, access).handle(player, If3Button(CombinedId(component.packed), -1, -1, 1))
        }

        fun text(name: String): String {
            val id = "component.command_browser:$name".asRSCM()
            return client.messages.filterIsInstance<IfSetText>().last { it.combinedId == id }.text
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private val INTERFACE_ID: Int get() = CommandBrowserScript.INTERFACE.asRSCM()
        private val UNSAFE = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
