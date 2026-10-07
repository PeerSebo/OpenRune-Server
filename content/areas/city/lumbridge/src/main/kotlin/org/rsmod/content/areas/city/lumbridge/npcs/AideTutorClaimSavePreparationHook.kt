package org.rsmod.content.areas.city.lumbridge.npcs

import jakarta.inject.Inject
import org.rsmod.api.account.saver.PlayerSavePreparationHook
import org.rsmod.game.entity.Player

class AideTutorClaimSavePreparationHook @Inject constructor() : PlayerSavePreparationHook {
    override fun prepare(player: Player) {
        if (!player.loggingOut) AideTutorClaimState.snapshot(player)
    }
}
