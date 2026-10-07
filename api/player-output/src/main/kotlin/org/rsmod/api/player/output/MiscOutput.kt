package org.rsmod.api.player.output

import net.rsprot.protocol.game.outgoing.logout.Logout
import net.rsprot.protocol.game.outgoing.logout.LogoutWithReason
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import net.rsprot.protocol.game.outgoing.misc.client.ServerTickEnd
import net.rsprot.protocol.game.outgoing.misc.client.UpdateRebootTimerV2
import net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp
import org.rsmod.game.entity.Player

public object MiscOutput {
    public fun hintArrowNpc(player: Player, npcSlot: Int) {
        player.client.write(HintArrow(HintArrow.NpcHintArrow(npcSlot)))
    }

    public fun hintArrowReset(player: Player) {
        player.client.write(HintArrow(HintArrow.ResetHintArrow))
    }

    public fun setPlayerOp(player: Player, slot: Int, op: String?, priority: Boolean = false) {
        while (player.options.size <= slot) {
            player.options.add(null)
        }
        player.options[slot] = op
        player.client.write(SetPlayerOp(slot, priority, op))
    }

    public fun findPlayerOption(player: Player, query: String): Int? {
        val index = player.options.indexOfFirst { it == query }
        return if (index >= 0) index else null
    }

    public fun clearPlayerOp(player: Player, slot: Int, query: String) {
        val optionIdx = findPlayerOption(player, query)
        if (optionIdx == slot) {
            setPlayerOp(player, slot, null)
        }
    }

    public fun serverTickEnd(player: Player) {
        player.client.write(ServerTickEnd)
    }

    public fun logout(player: Player) {
        player.client.write(Logout)
    }

    public fun logoutKicked(player: Player) {
        player.client.write(LogoutWithReason(reason = 1))
    }

    public fun logoutUpdating(player: Player) {
        player.client.write(LogoutWithReason(reason = 2))
    }

    public fun updateRebootTimer(player: Player, cycles: Int, message: String = "") {
        require(cycles in 0..65535) { "`cycles` must be within range [0..65535]. (cycles=$cycles)" }
        if (message.isEmpty()) {
            player.client.write(UpdateRebootTimerV2(cycles, UpdateRebootTimerV2.SetUpdateMessage("")))
        } else {
            player.client.write(UpdateRebootTimerV2(cycles, UpdateRebootTimerV2.SetUpdateMessage(message)))
        }
    }

    public fun clearUpdateRebootTimer(player: Player) {
        updateRebootTimer(player, cycles = 0)
    }
}
