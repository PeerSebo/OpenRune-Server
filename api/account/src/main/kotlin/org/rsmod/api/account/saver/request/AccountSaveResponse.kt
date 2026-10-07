package org.rsmod.api.account.saver.request

import org.rsmod.game.entity.Player

public sealed class AccountSaveResponse {
    public abstract val player: Player

    public data class Success(override val player: Player) : AccountSaveResponse()

    public sealed class Failure : AccountSaveResponse()

    public data class ExcessiveRetries(override val player: Player) : Failure()

    public data class RecoveryFailure(override val player: Player) : Failure()

    public data class InternalShutdownError(override val player: Player) : Failure()

    public data class PreparationFailure(override val player: Player) : Failure()

    public data class QueueFull(override val player: Player) : Failure()
}
