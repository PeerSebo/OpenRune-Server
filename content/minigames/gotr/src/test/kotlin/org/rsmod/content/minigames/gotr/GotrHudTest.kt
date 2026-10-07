package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
class GotrHudTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `native update uses the exact eleven arguments and catalytic ordering`() {
        val state = GotrHudState(2643, 23800, "obj.firerune", "obj.naturerune", 10, 10, 32)
        assertArrayEquals(arrayOf(48, 0, 2643, 23800, 0, 4, 5, 10, 10, 32, -1), state.arguments(48, 0))
    }

    @Test fun `preparation and closed portals use native inactive icons and hidden timers`() {
        val state = GotrHudState(2180, 21800, null, null, 10, 10, 198)
        assertArrayEquals(arrayOf(0, 0, 2180, 21800, 0, 0, 0, 10, 10, 198, -1), state.arguments(0, 0))
        assertArrayEquals(
            arrayOf(0, 0, 0, 21800, 0, 0, 0, 0, 10, -1, -1),
            state.copy(power = 0, guardianCount = 0, portalTicksRemaining = -500).arguments(0, 0),
        )
    }

    @Test fun `native compass and both rune key forms have the same ABI`() {
        val native = GotrHudState(14336, 25000, "obj.earthrune", "obj.deathrune", 10, 10, 32, 4, 36)
        val short = native.copy(elementalRune = "earth", catalyticRune = "death")
        assertArrayEquals(native.arguments(97, 103), short.arguments(97, 103))
        assertArrayEquals(arrayOf(97, 103, 14336, 25000, 4, 3, 7, 10, 10, 32, 36), native.arguments(97, 103))
    }

    @Test fun `sync opens the actual HUD once and exit removes only its owned overlays`() {
        val bus = EventBus()
        val hud = GotrHud(bus)
        val client = RecordingClient()
        val player = Player(client = client)
        val state = GotrHudState(25, 250, null, null, 0, 2, 201)
        hud.sync(player, state)
        hud.sync(player, state)
        assertTrue(player.ui.containsOverlay(GotrHud.HUD))
        assertEquals(1, client.packets.count { it.javaClass.simpleName == "IfOpenSub" })
        assertEquals(2, client.packets.count { it.javaClass.simpleName == "RunClientScript" })
        hud.hide(player)
        assertFalse(player.ui.containsOverlay(GotrHud.HUD))
        assertFalse(player.ui.containsOverlay(GotrHud.ATMOSPHERE))
    }

    @Test fun `lobby atmosphere stays when HUD closes and does not replace teleport fade`() {
        val bus = EventBus()
        val hud = GotrHud(bus)
        val player = Player()
        hud.syncAtmosphere(player, insideTemple = true)
        assertTrue(player.ui.containsOverlay(GotrHud.ATMOSPHERE))
        hud.hideHud(player)
        assertTrue(player.ui.containsOverlay(GotrHud.ATMOSPHERE))
        player.ifOpenOverlay("interface.fade_overlay", GotrHud.ATMOSPHERE_TARGET, bus)
        hud.syncAtmosphere(player, insideTemple = true)
        assertTrue(player.ui.containsOverlay("interface.fade_overlay"))
        assertFalse(player.ui.containsOverlay(GotrHud.ATMOSPHERE))
        hud.hide(player)
        assertTrue(player.ui.containsOverlay("interface.fade_overlay"))
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val packets = mutableListOf<Any>()
        override fun write(message: Any) { packets += message }
    }
}
