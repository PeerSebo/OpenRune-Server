package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.incoming.misc.user.CloseModal
import net.rsprot.protocol.game.incoming.resumed.ResumePCountDialog
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.net.rsprot.handlers.CloseModalHandler
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.net.rsprot.handlers.ResumePCountDialogHandler
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.table.PortTaskRow
import org.rsmod.content.skills.sailing.PortTaskManager
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.readSailingVar
import org.rsmod.content.skills.sailing.table.PortTaskRewards
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortTaskBoardTest {
    private lateinit var scripts: Map<Int, ByteArray>

    @BeforeAll
    fun nativeCache() {
        val cache = ServerCacheManager.init(241)
        try {
            scripts = listOf(8912, 8917, 8901, 8902, 8903)
                .associateWith { checkNotNull(cache.data(12, it, 0)) }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `native board ABI accepts eight rows and reviewed card layout remains unchanged`() {
        assertEquals(8, scripts.getValue(8912).intArgs())
        assertEquals(2, scripts.getValue(8901).intArgs())
        assertEquals(1, scripts.getValue(8903).intArgs())
        // Script 8917 advances six children per card; recheck mapping if that script changes.
        val hash = MessageDigest.getInstance("SHA-256").digest(scripts.getValue(8917))
            .joinToString("") { "%02X".format(it) }
        assertEquals("102F29C6E3C15F99219AA26FCD235A93D733657BAD3B1AA7901C3B12A9E74ED0", hash)
    }

    @Test
    fun `Inspect sends native board initialization with offered DBROW ids and no chat listing`() = runBlocking {
        val f = Fixture(level = 99)
        val offers = f.open()
        val init = f.scripts(8912).single()
        assertEquals(8, init.values.size)
        assertEquals(offers.map { it.rowId }, init.values.take(offers.size))
        assertTrue(init.values.drop(offers.size).all { it == -1 })
        assertTrue(f.player.ui.containsModal(BOARD))
        assertTrue(f.client.messages.any { it is IfOpenSub })
        assertFalse(f.client.messages.joinToString().contains("Tasks available at"))
        assertFalse(f.player.activeCoroutine?.isSuspended == true, "Inspect must not wait for a pause-button packet")
    }

    @Test
    fun `native second card click shows its details and count one accepts exactly that offer`() = runBlocking {
        val f = Fixture()
        val offers = f.open()
        assertTrue(offers.size >= 2)
        f.select(6)
        assertEquals(listOf(POPUP.asRSCM(), offers[1].rowId), f.scripts(8901).single().values)
        assertTrue(f.player.ui.containsModal(INFO))
        assertTrue(f.player.activeCoroutine?.isAwaiting(ResumePCountDialogInput::class) == true)
        assertTrue(PortTaskManager.activeTasks(f.player).isEmpty(), "Selecting a card only opens its details")

        f.confirm(1)
        assertEquals(listOf(offers[1].taskId), PortTaskManager.activeTasks(f.player).map { it.taskId })
        assertFalse(f.player.ui.containsModal(BOARD))
        assertFalse(f.player.ui.containsModal(INFO))
        f.confirm(1)
        assertEquals(1, PortTaskManager.activeTasks(f.player).size, "A repeated confirmation cannot duplicate a task")
    }

    @Test
    fun `decorative children out of range clicks and unsolicited confirmation cannot accept`() = runBlocking {
        val f = Fixture()
        f.confirm(1)
        f.select(0)
        f.open()
        for (child in listOf(-1, 1, 5, 7, 43, 48, 9999)) f.select(child)
        f.confirm(1)
        assertTrue(f.scripts(8901).isEmpty())
        assertTrue(PortTaskManager.activeTasks(f.player).isEmpty())
        assertTrue(f.player.ui.containsModal(BOARD))
    }

    @Test
    fun `Back and invalid confirmation leave the board available without accepting`() = runBlocking {
        val f = Fixture()
        f.open()
        f.select(0)
        f.confirm(0)
        assertTrue(f.player.ui.containsModal(BOARD))
        assertFalse(f.player.ui.containsModal(INFO))
        assertTrue(PortTaskManager.activeTasks(f.player).isEmpty())
        f.select(0)
        f.confirm(99)
        assertTrue(PortTaskManager.activeTasks(f.player).isEmpty())
        assertTrue(f.player.ui.containsModal(BOARD))
    }

    @Test
    fun `closing or moving away invalidates a pending confirmation`() = runBlocking {
        for (move in listOf(false, true)) {
            val f = Fixture()
            f.open()
            f.select(0)
            if (move) f.player.coords = f.player.coords.translateX(20) else f.player.ifClose(f.events)
            f.confirm(1)
            assertTrue(PortTaskManager.activeTasks(f.player).isEmpty())
            assertFalse(f.player.ui.containsModal(INFO))
            if (!move) {
                f.select(0)
                f.confirm(1)
                assertTrue(PortTaskManager.activeTasks(f.player).isEmpty())
            }
        }
    }

    @Test
    fun `close packet blocks later card selection and confirmation before UI teardown`() = runBlocking {
        val selected = Fixture()
        selected.open()
        selected.select(0)
        CloseModalHandler().handle(selected.player, CloseModal)
        assertTrue(selected.player.ui.closeModal)
        assertTrue(selected.player.ui.containsModal(BOARD), "The input batch has not processed modal teardown yet")
        selected.confirm(1)
        assertTrue(PortTaskManager.activeTasks(selected.player).isEmpty())
        assertFalse(selected.player.ui.containsModal(INFO))

        val unselected = Fixture()
        unselected.open()
        CloseModalHandler().handle(unselected.player, CloseModal)
        unselected.select(0)
        unselected.confirm(1)
        assertTrue(unselected.scripts(8901).isEmpty(), "A card click after CloseModal cannot open its details")
        assertTrue(PortTaskManager.activeTasks(unselected.player).isEmpty())
    }

    @Test
    fun `acceptance rechecks current skill level and free slots`() = runBlocking {
        val high = Fixture(level = 99)
        val offers = high.open()
        val index = offers.indexOfFirst { it.levelRequired > 1 }
        assertTrue(index >= 0)
        high.select(index * 6)
        high.player.statMap.setBaseLevel("stat.sailing", 1)
        high.confirm(1)
        assertTrue(PortTaskManager.activeTasks(high.player).isEmpty())

        val full = Fixture(level = 1)
        val offered = full.open()
        full.select(0)
        val other = PortTaskManager.all.first { it.taskId != offered.first().taskId && it.levelRequired == 1 }
        full.player.writeSailingVar(PortTaskManager.slotIdVar(0), other.taskId)
        full.confirm(1)
        assertEquals(listOf(other.taskId), PortTaskManager.activeTasks(full.player).map { it.taskId })
    }

    @Test
    fun `closing and opening another port replaces the trusted offer set`() = runBlocking {
        val f = Fixture(level = 99)
        val old = f.open()
        f.select(0)
        f.player.ifClose(f.events)
        val current = f.open("catherby")
        f.confirm(1)
        assertTrue(PortTaskManager.activeTasks(f.player).isEmpty())
        f.select(0)
        f.confirm(1)
        assertEquals(listOf(current.first().taskId), PortTaskManager.activeTasks(f.player).map { it.taskId })
        assertTrue(old.none { it.taskId == current.first().taskId })
    }

    @Test
    fun `ledger cargo flag stays boolean across mixed tasks and only clears after final cargo`() {
        val player = Fixture(level = 99).player
        fun cargoFlag() = player.readSailingVar(PortTaskManager.VARBIT_CARRYING_CARGO)

        PortTaskManager.syncCarryingCargo(player)
        assertEquals(0, cargoFlag(), "An empty task list still shows the withdrawal table")
        val bounty = PortTaskManager.all.first(PortTaskRewards::isBounty)
        val bountySlot = checkNotNull(PortTaskManager.accept(player, bounty))
        assertEquals(0, cargoFlag(), "Accepting a bounty must not write an invalid hidden state")

        val couriers = PortTaskManager.all.filter { !PortTaskRewards.isBounty(it) && it.cargo.isNotEmpty() }.take(2)
        assertEquals(2, couriers.size)
        val first = checkNotNull(PortTaskManager.accept(player, couriers[0]))
        val second = checkNotNull(PortTaskManager.accept(player, couriers[1]))
        assertEquals(0, cargoFlag(), "Accepting courier tasks does not pick up their cargo")
        PortTaskManager.setCargoTaken(player, first, couriers[0].cargo.size)
        PortTaskManager.setCargoTaken(player, second, couriers[1].cargo.size)
        assertEquals(1, cargoFlag())

        PortTaskManager.recordCompletion(player, first)
        assertEquals(1, cargoFlag(), "The other courier still requires the deposit table")
        PortTaskManager.cancel(player, bountySlot)
        assertEquals(1, cargoFlag(), "Cancelling an unrelated bounty must not hide remaining cargo")
        PortTaskManager.recordCompletion(player, second)
        assertEquals(0, cargoFlag(), "Completing the final courier restores the withdrawal table")
        assertTrue(PortTaskManager.activeTasks(player).isEmpty())

        val uncollected = checkNotNull(PortTaskManager.accept(player, couriers[0]))
        PortTaskManager.cancel(player, uncollected)
        assertEquals(0, cargoFlag(), "Cancelling the final uncollected task must not overflow either")
    }

    private class Fixture(level: Int = 1) {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3043, 3197)
            currentMapClock = 100
            processedMapClock = 100
            statMap.setBaseLevel("stat.sailing", level.toByte())
            statMap.setCurrentLevel("stat.sailing", level.toByte())
        }
        val events = EventBus()
        private val factory = unused<ProtectedAccessContextFactory>().also { field(it, "eventBus", events) }
        private val buttons = If3ButtonHandler(events, ProtectedAccessLauncher(factory))
        private val resume = ResumePCountDialogHandler(events)
        private val script = PortTaskBoardScript(ObjRepository(MapClock(100), ObjRegistry(ZoneUpdateMap())))
        private val interactions = LocInteractions(BoundValidator(CollisionFlagMap()), events)

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        suspend fun open(port: String = "port_sarim"): List<PortTaskRow> {
            val dock = checkNotNull(SailingDocks.byDbRow("dbrow.sailing_dock_$port"))
            val offers = PortTaskManager.offeredTasks(player, dock.row)
            val type = checkNotNull(ServerCacheManager.getObject("loc.port_task_board_$port".asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, player.coords.translateZ(1), LocEntity(type.id, 10, 0)), type)
            val operation = checkNotNull(interactions.opTrigger(player, loc, InteractionOp.Op1))
            assertTrue(operation is LocEvents.Op1)
            assertTrue(events.publish(ProtectedAccess(player, GameCoroutine(), factory.create()), operation))
            return offers
        }

        fun select(child: Int) {
            buttons.handle(player, If3Button(CombinedId(941, 3), child, -1, 1))
        }

        fun confirm(count: Int) { resume.handle(player, ResumePCountDialog(count)) }

        fun scripts(id: Int): List<RunClientScript> = client.messages.filterIsInstance<RunClientScript>().filter { it.id == id }
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
        private const val BOARD = "interface.port_task_board"
        private const val INFO = "interface.port_task_info"
        private const val POPUP = "component.port_task_board:popup"

        private fun ByteArray.intArgs(): Int {
            val bytes = ByteBuffer.wrap(this)
            val footer = size - 2 - (bytes.getShort(size - 2).toInt() and 0xFFFF) - 16
            return bytes.getShort(footer + 10).toInt() and 0xFFFF
        }

        private fun field(target: Any, name: String, value: Any) {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
