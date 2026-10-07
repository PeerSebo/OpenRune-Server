package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent

public class PlayerLogoutProcessor
@Inject
constructor(private val eventBus: EventBus, private val accountRegistry: AccountRegistry) {
    public fun process(player: Player) {
        player.completeLogout()
    }

    // Publish logout before queueing the save; the player stays registered until its callback.
    private fun Player.completeLogout() {
        eventBus.publish(SessionStateEvent.Logout(this))
        accountRegistry.queueLogout(this)
    }
}
