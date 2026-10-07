package org.rsmod.api.mechanics.stun

import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.nullableIntVarp
import org.rsmod.game.entity.Player

/**
 * Stuns block movement independently of action suppression. A rejected movement request clears
 * its route rather than pausing it like a freeze.
 */
public object Stun {
    public const val DEFAULT_SPOTANIM_HEIGHT: Int = 124

    public const val BLOCKED_MOVEMENT_MESSAGE: String = "You're stunned!"

    private var Player.movementLockUntil by nullableIntVarp("varp.player_stun_movement_until")
    private var Player.actionLockUntil by nullableIntVarp("varp.player_stun_action_until")
    private var Player.blockedMessageClock by nullableIntVarp("varp.player_stun_message_cycle")

    /**
     * Movement and action stun deadlines are independent. Reapplying a stun must not shorten
     * either active deadline.
     */
    public fun apply(
        player: Player,
        movementCycles: Int,
        actionCycles: Int = movementCycles,
        spotanim: String? = null,
        seq: String? = null,
        message: String? = null,
        spotanimHeight: Int = DEFAULT_SPOTANIM_HEIGHT,
        messageType: ChatType = ChatType.Spam,
    ) {
        require(movementCycles >= 0) {
            "`movementCycles` must not be negative. (movementCycles=$movementCycles)"
        }
        require(actionCycles >= 0) {
            "`actionCycles` must not be negative. (actionCycles=$actionCycles)"
        }
        require(movementCycles > 0 || actionCycles > 0) {
            "At least one of `movementCycles` or `actionCycles` must be greater than 0."
        }

        if (movementCycles > 0) {
            player.movementLockUntil = extendedDeadline(player, player.movementLockUntil, movementCycles)
            player.abortRoute()
            player.clearMapFlag()
        }

        if (actionCycles > 0) {
            player.actionLockUntil = extendedDeadline(player, player.actionLockUntil, actionCycles)
        }

        if (seq != null) {
            player.anim(seq)
        }

        if (spotanim != null) {
            player.spotanim(spotanim, height = spotanimHeight)
        }

        if (message != null) {
            player.mes(message, messageType)
        }
    }

    public fun extendActionLock(player: Player, cycles: Int): Boolean {
        require(cycles >= 0) { "`cycles` must not be negative. (cycles=$cycles)" }
        if (cycles == 0) {
            return false
        }
        val current = player.actionLockUntil ?: return false
        if (current <= player.currentMapClock) {
            return false
        }
        player.actionLockUntil = current + cycles
        return true
    }

    public fun rejectBlockedMovement(player: Player, message: Boolean = true): Boolean {
        if (!isMovementBlocked(player)) {
            return false
        }
        if (player.routeDestination.isNotEmpty()) {
            player.abortRoute()
            player.clearMapFlag()
        }
        if (message && player.blockedMessageClock != player.currentMapClock) {
            player.blockedMessageClock = player.currentMapClock
            player.mes(BLOCKED_MOVEMENT_MESSAGE, ChatType.Spam)
        }
        return true
    }

    public fun clear(player: Player) {
        player.movementLockUntil = null
        player.actionLockUntil = null
        player.blockedMessageClock = null
    }

    public fun isStunned(player: Player): Boolean =
        isMovementBlocked(player) || isActionBlocked(player)

    public fun isMovementBlocked(player: Player): Boolean = isActive(player, player.movementLockUntil)

    public fun isActionBlocked(player: Player): Boolean = isActive(player, player.actionLockUntil)

    public fun movementCyclesRemaining(player: Player): Int = remaining(player, player.movementLockUntil)

    public fun actionCyclesRemaining(player: Player): Int = remaining(player, player.actionLockUntil)

    private fun extendedDeadline(player: Player, current: Int?, cycles: Int): Int {
        val expiry = player.currentMapClock + cycles
        return maxOf(current ?: expiry, expiry)
    }

    private fun isActive(player: Player, expiry: Int?): Boolean {
        if (expiry == null) return false
        return expiry > player.currentMapClock
    }

    private fun remaining(player: Player, expiry: Int?): Int {
        if (expiry == null) return 0
        return (expiry - player.currentMapClock).coerceAtLeast(0)
    }
}
