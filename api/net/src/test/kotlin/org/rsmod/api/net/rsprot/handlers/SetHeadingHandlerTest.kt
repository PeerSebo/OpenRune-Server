package org.rsmod.api.net.rsprot.handlers

import net.rsprot.protocol.game.incoming.misc.user.SetHeading
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.rsmod.api.player.events.PlayerSetHeadingEvent
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid

class SetHeadingHandlerTest {
    @Test
    fun `all native headings publish the corresponding angle and requesting player`() {
        val fixture = Fixture()
        for (heading in 0..15) {
            fixture.handler.handle(fixture.player, SetHeading(heading))
        }

        assertEquals((0..15).toList(), fixture.requests.map { it.heading })
        assertEquals((0..15).map { it * 128 }, fixture.requests.map { it.angle })
        fixture.requests.forEach { assertSame(fixture.player, it.player) }
    }

    @ParameterizedTest
    @ValueSource(ints = [-2147483648, -1, 16, 128, 2047, 2147483647])
    fun `malformed headings are ignored before publishing an event`(heading: Int) {
        val fixture = Fixture()
        fixture.handler.handle(fixture.player, SetHeading(heading))

        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `requests retain the connection player identity and preserve deck walking`() {
        val fixture = Fixture()
        val otherPlayer = Player()
        val route = RouteRequestCoord(CoordGrid(15940, 13510, 1), clientRequest = true)
        otherPlayer.routeRequest = route

        fixture.handler.handle(otherPlayer, SetHeading(8))

        assertSame(otherPlayer, fixture.requests.single().player)
        assertSame(route, otherPlayer.routeRequest)
        assertEquals(1024, fixture.requests.single().angle)
    }

    @Test
    fun `invalid headings cannot enter the event through another publisher`() {
        val player = Player()
        assertThrows<IllegalArgumentException> { PlayerSetHeadingEvent(player, -1) }
        assertThrows<IllegalArgumentException> { PlayerSetHeadingEvent(player, 16) }
    }

    private class Fixture {
        val player = Player()
        val requests = mutableListOf<PlayerSetHeadingEvent>()
        val eventBus = EventBus().apply {
            subscribeUnbound(PlayerSetHeadingEvent::class.java) { requests += this }
        }
        val handler = SetHeadingHandler(eventBus)
    }
}
