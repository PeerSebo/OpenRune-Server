package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlinx.coroutines.runBlocking
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.content.skills.sailing.PortTaskManager
import org.rsmod.content.skills.sailing.SailingQuestSupport
import org.rsmod.content.skills.sailing.portTaskOpenedSlot
import org.rsmod.content.skills.sailing.table.PortTaskRewards
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class SailingLogTest {
    @Test
    fun `captains log keeps the native tutorial task and cannot cancel it`() = runBlocking {
        val fixture = Fixture()
        fixture.game.player.writeSailingVar("varbit.sailing_intro", 32)
        val row = SailingQuestSupport.tutorialTask
        val slot = checkNotNull(PortTaskManager.accept(fixture.game.player, row))
        fixture.open()
        assertTrue(fixture.game.player.ui.containsModal("interface.sailing_log"))
        assertEquals(1, fixture.game.player.vars["varbit.sailing_log_tab_opened"])
        assertFalse(fixture.client.messages.filterIsInstance<RunClientScript>().any { it.id == 8658 })
        fixture.button("tasks_click", slot)
        fixture.button("cancel")
        assertEquals(row.taskId, PortTaskManager.slotTaskId(fixture.game.player, slot))
        fixture.button("back")
        assertEquals(PortTaskManager.MAX_TASK_SLOTS, fixture.game.player.portTaskOpenedSlot)
        assertEquals(8709, fixture.client.messages.filterIsInstance<RunClientScript>().last().id)
    }

    @Test
    fun `invalid task child cannot select a cancellable task`() = runBlocking {
        val fixture = Fixture()
        fixture.open()
        fixture.button("tasks_click", PortTaskManager.MAX_TASK_SLOTS)
        assertEquals(PortTaskManager.MAX_TASK_SLOTS, fixture.game.player.portTaskOpenedSlot)
        fixture.button("cancel")
        assertTrue(PortTaskManager.activeTasks(fixture.game.player).isEmpty())
    }

    @Test
    fun `Jim task has its captured experience reward without normal courier coins`() {
        val row = SailingQuestSupport.tutorialTask
        assertEquals(100, PortTaskRewards.xpFor(row))
        assertEquals(0, PortTaskRewards.coinsFor(row))
    }

    private class Fixture {
        val game = SailingBoardingLifecycleTest.Fixture()
        val client = RecordingClient()
        val script = SailingLogScript()
        init {
            game.player.client = client
            with(script) { ScriptContext(game.eventBus, CheatCommandMap(), EngineQueueCache()).startup() }
        }
        fun open() {
            val method = SailingLogScript::class.java.declaredMethods.single { it.name == "openLog" }
            method.isAccessible = true
            method.invoke(script, game.access(), true)
        }
        suspend fun button(name: String, child: Int = -1) {
            val button = ServerCacheManager.fromComponent("component.sailing_log:$name")
            assertTrue(game.eventBus.publish(game.access(), IfModalButton(button, child, null, IfButtonOp.Op1)))
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
        @JvmStatic
        @BeforeAll
        fun loadCache() { ServerCacheManager.init(241).close() }
    }
}
