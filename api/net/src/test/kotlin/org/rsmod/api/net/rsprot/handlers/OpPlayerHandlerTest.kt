package org.rsmod.api.net.rsprot.handlers

import net.rsprot.protocol.game.incoming.players.OpPlayer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

class OpPlayerHandlerTest {
    @Test fun `report player menu option is ignored without disconnecting`() {
        val events = EventBus()
        val handler = OpPlayerHandler(events, PlayerList(), PlayerInteractions(events))

        assertDoesNotThrow { handler.handle(Player(), OpPlayer(1, false, 8)) }
    }
}
