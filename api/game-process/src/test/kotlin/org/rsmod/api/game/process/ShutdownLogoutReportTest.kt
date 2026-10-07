package org.rsmod.api.game.process

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player

class ShutdownLogoutReportTest {
    @Test
    fun `preparation failure report includes characters already marked logging out`() {
        val player = Player().apply {
            displayName = "Unsaved character"
            loggingOut = true
        }

        val message = logoutPreparationFailureMessage(listOf(player))

        assertTrue(message.contains("1 players still have unsaved logout state"))
        assertTrue(message.contains("save preparation failed"))
        assertTrue(message.contains("'Unsaved character'"))
    }

    @Test
    fun `preparation failure report bounds names while retaining the full affected count`() {
        val players = List(251) { index ->
            Player().apply {
                displayName = "Unsaved $index"
                loggingOut = true
            }
        }

        val message = logoutPreparationFailureMessage(players)

        assertTrue(message.startsWith("251 players"))
        assertTrue(message.contains("'Unsaved 0'"))
        assertTrue(message.contains("'Unsaved 249'"))
        assertFalse(message.contains("'Unsaved 250'"))
    }
}
