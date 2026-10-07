package org.rsmod.api.player

import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.constants
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.RunEnergy

class RunEnergyTest {
    @Test
    fun `fresh players share the full energy value used by gameplay and saved accounts`() {
        assertEquals(10_000, Player().runEnergy)
        assertEquals(Player().runEnergy, constants.run_max_energy)
        assertEquals(100, RunEnergy.UNITS_PER_PERCENT)
    }

    @Test
    fun `restoration adds percentage points to an existing raw saved value and sends that exact value`() {
        val client = RecordingClient()
        val player = Player().apply { runEnergy = 543; this.client = client }
        player.restoreRunEnergyPercent(10)
        assertEquals(1_543, player.runEnergy)
        assertEquals(listOf(UpdateRunEnergy(1_543)), client.messages)
    }

    @Test
    fun `one percent restoration never lowers valid energy and saturates at full`() {
        for ((start, expected) in listOf(0 to 100, 5_000 to 5_100, 9_950 to 10_000)) {
            val client = RecordingClient()
            val player = Player().apply { runEnergy = start; this.client = client }
            player.restoreRunEnergyPercent(1)
            assertEquals(expected, player.runEnergy)
            assertTrue(player.runEnergy >= start)
            assertEquals(listOf(UpdateRunEnergy(expected)), client.messages)
        }
    }

    @Test
    fun `full energy and nonpositive restoration do not send redundant updates`() {
        val client = RecordingClient()
        val player = Player().apply { this.client = client }
        player.restoreRunEnergyPercent(50)
        assertEquals(10_000, player.runEnergy)
        player.runEnergy = 5_000
        player.restoreRunEnergyPercent(0)
        player.restoreRunEnergyPercent(-1)
        player.restoreRunEnergyPercent(Int.MIN_VALUE)
        assertEquals(5_000, player.runEnergy)
        assertTrue(client.messages.isEmpty())
    }

    @Test
    fun `large restoration cannot overflow into energy loss`() {
        val client = RecordingClient()
        val player = Player().apply { runEnergy = 5_000; this.client = client }
        player.restoreRunEnergyPercent(Int.MAX_VALUE)
        assertEquals(10_000, player.runEnergy)
        assertEquals(listOf(UpdateRunEnergy(10_000)), client.messages)
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
}
