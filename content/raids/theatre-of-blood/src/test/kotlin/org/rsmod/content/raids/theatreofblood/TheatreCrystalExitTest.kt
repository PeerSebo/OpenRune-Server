package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.inv.InvObj

@ResourceLock("ServerCacheManager")
class TheatreCrystalExitTest {
    @Test fun `Orator exit fades both ways plays teleport sound and preserves lobby gear`() {
        val fixture = Fixture()
        fixture.start()
        assertTrue(fixture.player.ui.containsOverlay("interface.fade_overlay"))
        assertNotNull(fixture.world.manager.run(fixture.player))
        fixture.advance()
        assertNull(fixture.world.manager.run(fixture.player))
        assertEquals(TheatreData.rules.exit, fixture.player.coords)
        fixture.advance()
        assertFalse(fixture.player.ui.containsOverlay("interface.fade_overlay"))
        val script = "clientscript.[clientscript,fade_overlay]".asRSCM(RSCMType.CLIENTSCRIPT)
        val fades = fixture.messages.filterIsInstance<RunClientScript>().filter { it.id == script }
        assertEquals(listOf(
            RunClientScript(script, listOf(0, 255, 0, 0, 20)).toString(),
            RunClientScript(script, listOf(0, 0, 0, 255, 20)).toString(),
        ), fades.map { it.toString() })
        assertEquals(listOf(SynthSound("synth.TELEPORT_ALL".asRSCM(RSCMType.SYNTH), 1, 0)),
            fixture.messages.filterIsInstance<SynthSound>())
        fixture.assertGearRetained()
    }

    @Test fun `cancelling before Orator departure closes the fade and leaves party membership intact`() {
        val fixture = Fixture()
        fixture.start()
        fixture.player.cancelActiveCoroutine()
        assertFalse(fixture.player.ui.containsOverlay("interface.fade_overlay"))
        assertTrue(fixture.player.ui.containsOverlay("interface.tob_hud"))
        assertSame(fixture.run, fixture.world.manager.run(fixture.player))
        fixture.assertGearRetained()
    }

    @Test fun `cancelling after Orator departure closes the fade without repeating recovery`() {
        val fixture = Fixture()
        fixture.start()
        fixture.advance()
        fixture.player.cancelActiveCoroutine()
        assertFalse(fixture.player.ui.containsOverlay("interface.fade_overlay"))
        assertNull(fixture.world.manager.run(fixture.player))
        fixture.assertGearRetained()
    }

    @Test fun `Orator departure during a fight retains items and does not count a death`() {
        val fixture = Fixture(lobby = false)
        fixture.world.encounters.start(fixture.run)
        fixture.start()
        fixture.advance()
        fixture.advance()
        fixture.assertGearRetained()
        assertNull(fixture.world.manager.run(fixture.player))
        assertEquals(TheatreData.rules.exit, fixture.player.coords)
        assertFalse(fixture.player.ui.containsOverlay("interface.fade_overlay"))
    }

    private class Fixture(lobby: Boolean = true) {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val run = world.party(0, lobby = lobby)
        val player = run.members.single()
        val messages = mutableListOf<Any>()
        private val carried = InvObj("obj.rune_platebody", 1, 77)
        private val equipped = InvObj("obj.bronze_dagger", 1, 31)

        init {
            player.processedMapClock = player.currentMapClock
            world.presentation.open(player, run, world.events)
            player.inv[0] = carried
            player.worn[3] = equipped
            player.client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
        }

        fun start() {
            world.roomObjects.enterRoom(run)
            val orator = world.npcs.first { it.isType(TheatreRoomObjects.ORATOR) }
            val event = NpcEvents.Op1(orator)
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { world.events }, getAlignment = { TextAlignment() },
                getCollision = { world.flags },
            )
            player.launch { world.events.publish(ProtectedAccess(player, this, context), event) }
            player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 2))
            assertTrue(player.activeCoroutine?.isSuspended == true)
        }

        fun advance() {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.activeCoroutine?.advance()
        }

        fun assertGearRetained() {
            assertEquals(carried, player.inv[0])
            assertEquals(equipped, player.worn[3])
            assertTrue(world.inventory.recovery(player).isEmpty())
            assertEquals(0, player.vars[TheatreInventory.RECOVERY_FEE])
            assertEquals(0, player.vars[TheatreManager.DEATHS])
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
