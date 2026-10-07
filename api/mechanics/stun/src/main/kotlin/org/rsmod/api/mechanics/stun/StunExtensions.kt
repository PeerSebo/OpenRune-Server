package org.rsmod.api.mechanics.stun

import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Player

public val Player.isStunned: Boolean
    get() = Stun.isStunned(this)

public val Player.isStunMovementBlocked: Boolean
    get() = Stun.isMovementBlocked(this)

public val Player.isStunActionBlocked: Boolean
    get() = Stun.isActionBlocked(this)

public val Player.stunMovementCyclesRemaining: Int
    get() = Stun.movementCyclesRemaining(this)

public val Player.stunActionCyclesRemaining: Int
    get() = Stun.actionCyclesRemaining(this)

public val ProtectedAccess.isStunned: Boolean
    get() = player.isStunned

public val ProtectedAccess.isStunActionBlocked: Boolean
    get() = player.isStunActionBlocked

public val ProtectedAccess.stunActionCyclesRemaining: Int
    get() = player.stunActionCyclesRemaining

public fun ProtectedAccess.stun(
    movementCycles: Int,
    actionCycles: Int = movementCycles,
    spotanim: String? = null,
    seq: String? = null,
    message: String? = null,
    spotanimHeight: Int = Stun.DEFAULT_SPOTANIM_HEIGHT,
    messageType: ChatType = ChatType.Spam,
) {
    Stun.apply(
        player = player,
        movementCycles = movementCycles,
        actionCycles = actionCycles,
        spotanim = spotanim,
        seq = seq,
        message = message,
        spotanimHeight = spotanimHeight,
        messageType = messageType,
    )
}

public fun ProtectedAccess.clearStun() {
    Stun.clear(player)
}
