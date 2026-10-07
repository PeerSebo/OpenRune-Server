package org.rsmod.content.bosses.wildernessarchaeologists

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList

internal class FanaticDisarm @Inject constructor(private val unequip: WornUnequipOp) {
    fun schedule(
        queues: WorldQueueList,
        delay: Int,
        random: GameRandom,
        active: () -> Boolean,
        victim: () -> Player?,
    ) {
        require(delay >= 1)
        fun attempt(remainingRetries: Int) {
            if (!active()) return
            val player = victim() ?: return
            if (player.isAccessProtected) {
                // Two queue cycles prevent an appended retry from executing in the current queue pass.
                if (remainingRetries > 0) queues.add(2) { attempt(remainingRetries - 1) }
                return
            }
            removeEquipment(player, random)
        }
        queues.add(delay) { attempt(MAX_ACCESS_RETRIES) }
    }

    fun removeEquipment(player: Player, random: GameRandom): Boolean {
        // A world callback must not steal access from an active player action or modal.
        if (player.isAccessProtected) return false
        // Inventory-blocking food is the native defence, even if an ammunition stack could merge.
        if (player.inv.objs.none { it == null }) return false
        val slots = Wearpos.entries.map { it.slot }.filter { player.worn[it] != null }
        val slot = random.pickOrNull(slots) ?: return false
        return unequip.unequip(player, slot, player.worn, player.inv) == WornUnequipResult.Success
    }

    companion object { const val MAX_ACCESS_RETRIES = 5 }
}
