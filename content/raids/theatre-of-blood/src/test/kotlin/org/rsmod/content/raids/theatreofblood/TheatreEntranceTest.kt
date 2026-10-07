package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.inv.InvObj

@ResourceLock("ServerCacheManager")
class TheatreEntranceTest {
    @Test fun `entrance darkens outside plays footsteps and reveals Maiden after map acknowledgement`() {
        val fixture = Fixture()
        val outside = fixture.player.coords
        fixture.start()
        assertEquals(outside, fixture.player.coords)
        assertNull(fixture.world.manager.run(fixture.player))
        assertTrue(fixture.player.isDelayed)
        fixture.assertFades(listOf(fade(0, 0, "The Theatre awaits...")))
        assertEquals(listOf(
            SynthSound("synth.TOB_TRANSITION_CARD".asRSCM(RSCMType.SYNTH), 1, 0),
            SynthSound("synth.HUMAN_FOOTSTEP_01".asRSCM(RSCMType.SYNTH), 1, 0),
            SynthSound("synth.HUMAN_FOOTSTEP_02".asRSCM(RSCMType.SYNTH), 1, 20),
            SynthSound("synth.HUMAN_FOOTSTEP_03".asRSCM(RSCMType.SYNTH), 1, 40),
        ),
            fixture.messages.filterIsInstance<SynthSound>())
        val target = "component.toplevel_osrs_stretch:overlay_atmosphere".asRSCM(RSCMType.COMPONENT)
        val hud = "interface.tob_hud".asRSCM(RSCMType.INTERFACE)
        assertEquals(listOf(IfOpenSub(target shr 16, target and 0xffff, hud, 1).toString()),
            fixture.messages.filterIsInstance<IfOpenSub>().map { it.toString() })
        fixture.advance()
        assertEquals(outside, fixture.player.coords)
        fixture.advance()
        val run = checkNotNull(fixture.world.manager.run(fixture.player))
        assertEquals(TheatreManager.LOBBY, run.stage)
        assertEquals(TheatreData.rules.lobbyEntry, fixture.world.manager.source(run, fixture.player.coords))
        fixture.assertFades(listOf(fade(0, 0, "The Theatre awaits...")))
        fixture.advance(acknowledgeMap = true)
        fixture.assertFades(listOf(fade(0, 0, "The Theatre awaits..."), fade(255, 0, "The Maiden of Sugadinti")))
        assertTrue(fixture.player.isDelayed)
        fixture.advance()
        assertFalse(fixture.player.activeCoroutine?.isSuspended == true)
        assertFalse(fixture.player.isDelayed)
        assertTrue(fixture.player.ui.containsOverlay("interface.tob_hud"))
        assertEquals(1, fixture.messages.filterIsInstance<IfOpenSub>().size)
        fixture.assertGearRetained()
    }

    @Test fun `map acknowledgement timeout still reveals the room and releases the player`() {
        val fixture = Fixture()
        fixture.start()
        repeat(12) { fixture.advance() }
        fixture.assertFades(listOf(fade(0, 0, "The Theatre awaits..."), fade(255, 0, "The Maiden of Sugadinti")))
        fixture.advance()
        assertFalse(fixture.player.activeCoroutine?.isSuspended == true)
        assertNotNull(fixture.world.manager.run(fixture.player))
        assertFalse(fixture.player.isDelayed)
    }

    @Test fun `cancelling before admission releases the pending instance and permits a fresh entry`() {
        val fixture = Fixture()
        fixture.start()
        assertNotNull(fixture.world.instances.sessionForOwner(checkNotNull(fixture.player.uuid)))
        fixture.player.cancelActiveCoroutine()
        assertNull(fixture.world.instances.sessionForOwner(checkNotNull(fixture.player.uuid)))
        assertNull(fixture.world.manager.run(fixture.player))
        assertFalse(fixture.player.ui.containsOverlay("interface.tob_hud"))
        fixture.assertFades(listOf(fade(0, 0, "The Theatre awaits..."), fade(255, 1, "")))
        repeat(2) { fixture.advance() }
        fixture.world.enter(fixture.player)
        assertNotNull(fixture.world.manager.run(fixture.player))
        fixture.assertGearRetained()
    }

    @Test fun `cancelling after admission clears black and text without removing the player or their gear`() {
        val fixture = Fixture()
        fixture.start()
        repeat(2) { fixture.advance() }
        val run = checkNotNull(fixture.world.manager.run(fixture.player))
        fixture.player.cancelActiveCoroutine()
        assertSame(run, fixture.world.manager.run(fixture.player))
        assertTrue(fixture.player.uuid in run.session.occupants)
        assertTrue(fixture.player.ui.containsOverlay("interface.tob_hud"))
        fixture.assertFades(listOf(fade(0, 0, "The Theatre awaits..."), fade(255, 1, "")))
        assertTrue(fixture.messages.filterIsInstance<IfSetHide>().any {
            it.toString() == IfSetHide(hud, "component.tob_hud:atmospheric".asRSCM(RSCMType.COMPONENT) and 0xffff, true).toString()
        })
        fixture.assertGearRetained()
    }

    @Test fun `departure during the map wait cannot reopen the Theatre overlay or room title`() {
        val fixture = Fixture()
        fixture.start()
        repeat(2) { fixture.advance() }
        fixture.world.manager.leave(fixture.player)
        fixture.advance(acknowledgeMap = true)
        assertFalse(fixture.player.activeCoroutine?.isSuspended == true)
        assertNull(fixture.world.manager.run(fixture.player))
        assertEquals(TheatreData.rules.exit, fixture.player.coords)
        assertFalse(fixture.player.ui.containsOverlay("interface.tob_hud"))
        fixture.assertFades(listOf(fade(0, 0, "The Theatre awaits...")))
        fixture.assertGearRetained()
    }

    @Test fun `full parties refuse entry before showing the fade or playing footsteps`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val run = world.party(0, size = 5, lobby = true)
        run.members.first().displayName = "Leader"
        val fixture = Fixture(world)
        fixture.start("Leader")
        assertFalse(fixture.player.activeCoroutine?.isSuspended == true)
        assertNull(world.manager.run(fixture.player))
        assertFalse(fixture.player.ui.containsOverlay("interface.tob_hud"))
        fixture.assertFades(emptyList())
        assertTrue(fixture.messages.filterIsInstance<SynthSound>().isEmpty())
        assertEquals(5, run.session.occupants.size)
    }

    @Test fun `party formation limits the roster before a sixth member can enter`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val run = world.party(0, size = 4, lobby = true)
        run.members.first().displayName = "Leader"
        val first = Fixture(world)
        val second = Fixture(world)
        first.start("Leader")
        second.start("Leader")
        repeat(2) { first.advance() }
        repeat(2) { second.advance() }
        assertSame(run, world.manager.run(first.player))
        assertNull(world.manager.run(second.player))
        assertEquals(5, run.session.occupants.size)
        assertEquals(TheatreData.rules.exit, second.player.coords)
        assertFalse(second.player.ui.containsOverlay("interface.tob_hud"))
        second.assertFades(emptyList())
        first.advance(acknowledgeMap = true)
        first.advance()
        assertFalse(first.player.activeCoroutine?.isSuspended == true)
        first.assertGearRetained()
        second.assertGearRetained()
    }

    @Test fun `a party that starts during the fade refuses the join without moving the player`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val run = world.party(0, lobby = true)
        run.members.first().displayName = "Leader"
        val fixture = Fixture(world)
        fixture.start("Leader")
        world.manager.startEncounter(run.members.first())
        repeat(2) { fixture.advance() }
        assertNull(world.manager.run(fixture.player))
        assertEquals(TheatreData.rules.exit, fixture.player.coords)
        assertFalse(fixture.player.ui.containsOverlay("interface.tob_hud"))
        assertEquals(1, run.session.occupants.size)
        fixture.assertFades(listOf(fade(0, 0, "The Theatre awaits..."), fade(255, 1, "")))
        fixture.assertGearRetained()
    }

    private class Fixture(val world: TheatreEncounterIntegrationTest.Fixture = TheatreEncounterIntegrationTest.Fixture()) {
        val player = world.player().apply {
            displayName = "Entrant$uuid"
            coords = TheatreData.rules.exit
            processedMapClock = currentMapClock
        }
        val messages = mutableListOf<Any>()
        private val carried = InvObj("obj.rune_platebody", 1, 77)
        private val equipped = InvObj("obj.bronze_dagger", 1, 31)

        init {
            player.inv[0] = carried
            player.worn[3] = equipped
            player.client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
        }

        fun start(leader: String? = null) {
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { world.events }, getCollision = { world.flags },
            )
            if (world.parties.get(player) == null) {
                if (leader == null) world.parties.create(player) else world.parties.join(player, leader)
            }
            player.launch { world.manager.enter(ProtectedAccess(player, this, context)) }
        }

        fun advance(acknowledgeMap: Boolean = false) {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            if (acknowledgeMap) player.lastMapBuildComplete = player.currentMapClock
            player.activeCoroutine?.advance()
        }

        fun assertFades(expected: List<RunClientScript>) {
            assertEquals(expected.map { it.toString() }, messages.filterIsInstance<RunClientScript>()
                .filter { it.id == fadeScript }.map { it.toString() })
        }

        fun assertGearRetained() {
            assertEquals(carried, player.inv[0])
            assertEquals(equipped, player.worn[3])
            assertTrue(world.inventory.recovery(player).isEmpty())
        }
    }

    companion object {
        private val hud get() = "interface.tob_hud".asRSCM(RSCMType.INTERFACE)
        private val fadeScript get() = "clientscript.[clientscript,tob_hud_fade]".asRSCM(RSCMType.CLIENTSCRIPT)
        private fun fade(transparency: Int, immediate: Int, text: String) =
            RunClientScript(fadeScript, listOf(transparency, immediate, text))

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
