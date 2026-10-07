package org.rsmod.content.raids.theatreofblood

import jakarta.inject.Inject
import jakarta.inject.Provider
import org.rsmod.api.death.PlayerDeathItemStorageHook
import org.rsmod.api.death.PlayerDeathSequenceHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal class TheatreDeathSequenceHook @Inject constructor(private val manager: TheatreManager) :
    PlayerDeathSequenceHook {
    override suspend fun handle(access: ProtectedAccess): Boolean = manager.die(access)
}

internal class TheatreDeathStorageHook @Inject constructor(
    private val manager: Provider<TheatreManager>,
    private val inventory: TheatreInventory,
) : PlayerDeathItemStorageHook {
    override fun store(player: Player, items: List<InvObj>): Boolean {
        if (!manager.get().isWiping(player)) return false
        check(inventory.storeRecovery(player, items)) {
            "Theatre recovery rejected the death inventory; carried items were retained."
        }
        return true
    }
}
