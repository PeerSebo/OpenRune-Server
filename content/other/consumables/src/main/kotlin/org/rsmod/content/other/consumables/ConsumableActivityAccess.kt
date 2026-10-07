package org.rsmod.content.other.consumables

import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

@Singleton
class ConsumableActivityAccess {
    fun canConsume(
        player: Player,
        minigameOnly: String,
        raidOnly: String,
    ): Boolean {
        if (minigameOnly.isNotBlank()) {
            return false
        }

        if (raidOnly.isNotBlank()) {
            return false
        }

        return true
    }
}
