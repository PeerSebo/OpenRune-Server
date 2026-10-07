package org.rsmod.api.net.rsprot.handlers

import net.rsprot.protocol.game.incoming.players.OpPlayer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerAccountCommit
import org.rsmod.game.entity.PlayerTradeSocial

class AccountCommitInputTest {
    @Test fun `packet consumer holds input even after SQL success until game acknowledgement`() {
        val player = Player()
        var calls = 0
        val handler = MessageHandler<OpPlayer> { _, _ -> calls++ }
        val commit = object : PlayerAccountCommit {
            var result: Boolean? = null
            override val succeeded: Boolean? get() = result
            override fun release() { player.pendingAccountCommit = null }
        }
        handler.consume(player, OpPlayer(1, false, 4))
        assertEquals(1, calls)
        player.pendingAccountCommit = commit
        handler.consume(player, OpPlayer(1, false, 4))
        assertEquals(1, calls)
        commit.result = true
        handler.consume(player, OpPlayer(1, false, 4))
        assertEquals(1, calls)
        commit.release()
        handler.consume(player, OpPlayer(1, false, 4))
        assertEquals(2, calls)
    }

    @Test fun `trade filter and ignore names apply after normalization`() {
        assertTrue(PlayerTradeSocial(0, emptySet(), emptySet()).accepts("Anyone"))
        assertFalse(PlayerTradeSocial(2, setOf("friend"), emptySet()).accepts("Friend"))
        assertFalse(PlayerTradeSocial(7, setOf("friend"), emptySet()).accepts("Friend"))
        val friends = PlayerTradeSocial(1, setOf("a friend"), emptySet())
        assertTrue(friends.accepts(" A_Friend "))
        assertTrue(friends.accepts("A\u00a0Friend"))
        assertFalse(friends.accepts("Stranger"))
        assertFalse(friends.copy(ignores = setOf("a friend")).accepts("A Friend"))
        assertFalse(PlayerTradeSocial(0, emptySet(), setOf("ignored")).accepts("IGNORED"))
    }
}
