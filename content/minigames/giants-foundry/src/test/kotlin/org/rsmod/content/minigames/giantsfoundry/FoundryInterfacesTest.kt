package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.events.EventBus
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoundryInterfacesTest {
    @BeforeAll
    fun cache() {
        ServerCacheManager.init(241).close()
    }

    @Test
    fun `mould window matches the captured native interface and dynamic click ranges`() {
        val fixture = Fixture()
        fixture.interfaces.openMould(fixture.player)

        assertEquals(718, fixture.client.packets.filterIsInstance<IfOpenSub>().single().interfaceId)
        assertEquals(
            listOf(listOf(718, 12, 0, 63, 0, 1), listOf(718, 9, 0, 544, 0, 1)),
            fixture.client.packets.filterIsInstance<IfSetEventsV2>().map { it.fields() },
        )
        assertTrue(fixture.player.ui.containsModal(FoundryInterfaces.MOULD))
        assertEquals(listOf(2524), fixture.client.packets.filterIsInstance<RunClientScript>().map { it.id })
        fixture.interfaces.closeMould(fixture.player)
        assertFalse(fixture.player.ui.containsModal(FoundryInterfaces.MOULD))
    }

    @Test
    fun `reward window sends the native initializer with no arguments and all purchase options`() {
        val fixture = Fixture()
        fixture.interfaces.openRewards(fixture.player)

        assertEquals(753, fixture.client.packets.filterIsInstance<IfOpenSub>().single().interfaceId)
        val init = fixture.client.packets.filterIsInstance<RunClientScript>().last()
        assertEquals(6082, init.id)
        assertTrue(init.values.isEmpty())
        assertEquals(
            listOf(listOf(753, 22, 0, 312, 0, 15), listOf(753, 25, 0, 12, 0, 1)),
            fixture.client.packets.filterIsInstance<IfSetEventsV2>().map { it.fields() },
        )
    }

    @Test
    fun `rejected selections can restore native views without reopening or replacing component trees`() {
        val fixture = Fixture()
        fixture.interfaces.refreshMould(fixture.player)
        fixture.interfaces.refreshRewards(fixture.player)
        fixture.interfaces.selectReward(fixture.player, 8)

        val scripts = fixture.client.packets.filterIsInstance<RunClientScript>()
        assertEquals(listOf(6093, 6083, 6088), scripts.map { it.id })
        assertEquals(listOf(emptyList(), listOf(0), listOf(8)), scripts.map { it.values })
        assertEquals(3, fixture.client.packets.size)
    }

    @Test
    fun `HUD opens once and relies on the native var transmit rather than recurring rebuilds`() {
        val fixture = Fixture()
        fixture.interfaces.showHud(fixture.player)
        fixture.interfaces.showHud(fixture.player)

        assertEquals(754, fixture.client.packets.filterIsInstance<IfOpenSub>().single().interfaceId)
        assertTrue(fixture.client.packets.none { it is RunClientScript })
        assertTrue(fixture.player.ui.containsOverlay(FoundryInterfaces.HUD))
        fixture.interfaces.hideHud(fixture.player)
        assertFalse(fixture.player.ui.containsOverlay(FoundryInterfaces.HUD))
    }

    @Test
    fun `closing foundry HUD preserves a replacement overlay`() {
        val fixture = Fixture()
        fixture.interfaces.showHud(fixture.player)
        fixture.player.ifOpenOverlay("interface.gotr_hud", FoundryInterfaces.HUD_TARGET, fixture.bus)
        fixture.interfaces.hideHud(fixture.player)

        assertTrue(fixture.player.ui.containsOverlay("interface.gotr_hud"))
    }

    @Test
    fun `sweet spot uses native yellow green and clear states`() {
        val fixture = Fixture()
        for (state in FoundryInterfaces.SweetSpot.entries) fixture.interfaces.sweetSpot(fixture.player, state)
        val scripts = fixture.client.packets.filterIsInstance<RunClientScript>()
        assertEquals(listOf(6122, 6122, 6122), scripts.map { it.id })
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), scripts.map { it.values })
    }

    @Test
    fun `cached HUD initializes itself and watches both preform state parents`() {
        val component = ServerCacheManager.fromComponent("component.giants_foundry_hud:universe")
        assertArrayEquals(arrayOf(6111), component.onLoad)
        assertArrayEquals(arrayOf(6113), component.onVarTransmit)
        assertArrayEquals(
            intArrayOf("varp.giants_foundry_preform_1".asRSCM(), "varp.giants_foundry_preform_2".asRSCM()),
            component.onVarTransmitList,
        )
        assertArrayEquals(
            arrayOf(6092),
            ServerCacheManager.fromComponent("component.giants_foundry_mould:universe").onLoad,
        )
    }

    @Test
    fun `captured mould and tab click slots decode to their native enum positions`() {
        assertEquals(6, FoundryInterfaces.mouldIndex(85, 11))
        assertEquals(7, FoundryInterfaces.mouldIndex(102, 11))
        assertEquals(8, FoundryInterfaces.mouldIndex(119, 11))
        assertEquals(9, FoundryInterfaces.mouldIndex(136, 11))
        assertEquals(10, FoundryInterfaces.mouldIndex(153, 11))
        assertEquals(11, FoundryInterfaces.mouldIndex(170, 11))
        assertEquals(listOf(0, 1, 2), listOf(0, 9, 18).map(FoundryInterfaces::mouldTab))
    }

    @Test
    fun `dynamic list decoding rejects negative overflow trailing and decorative slots`() {
        for (slot in listOf(-1, Int.MIN_VALUE, 1, 16, 18, 186, 187, 544, Int.MAX_VALUE)) {
            assertNull(FoundryInterfaces.mouldIndex(slot, 11), "mould slot $slot")
        }
        for (slot in listOf(-1, 1, 12, 14, 311, 312, Int.MAX_VALUE)) {
            assertNull(FoundryInterfaces.rewardIndex(slot, 24), "reward slot $slot")
        }
        assertEquals(1, FoundryInterfaces.rewardIndex(0, 24))
        assertEquals(24, FoundryInterfaces.rewardIndex(299, 24))
        assertNull(FoundryInterfaces.mouldIndex(0, 0))
        assertNull(FoundryInterfaces.rewardIndex(0, 0))
    }

    @Test
    fun `tab and filter decoding ignores their noninteractive child components`() {
        for (slot in listOf(-1, 1, 8, 10, 17, 19, 27, 63)) {
            assertNull(FoundryInterfaces.mouldTab(slot))
        }
        assertEquals((0..5).toList(), listOf(1, 3, 5, 7, 9, 11).map(FoundryInterfaces::rewardFilter))
        for (slot in listOf(-1, 0, 2, 4, 6, 8, 10, 12, 13)) {
            assertNull(FoundryInterfaces.rewardFilter(slot))
        }
    }

    private fun IfSetEventsV2.fields(): List<Int> =
        listOf(interfaceId, componentId, start, end, events1, events2)

    private class Fixture {
        val bus = EventBus()
        val interfaces = FoundryInterfaces(bus)
        val client = RecordingClient()
        val player = Player(client = client)
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val packets = mutableListOf<Any>()
        override fun write(message: Any) {
            packets += message
        }
    }
}
