package org.rsmod.api.player

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

private var Player.runEnergyRestoreBlockedUntil by intVarp("varp.player_run_restore_blocked_until")

public const val MAX_RUN_ENERGY_RESTORE_BLOCK_CYCLES: Int = 100

public val Player.isRunEnergyRestoreBlocked: Boolean
    get() = runEnergyRestoreBlockedUntil > 0 && currentMapClock < runEnergyRestoreBlockedUntil

public fun Player.blockRunEnergyRestore(cycles: Int) {
    require(cycles >= 0) { "`cycles` must not be negative: $cycles" }
    val deadline = currentMapClock + cycles
    if (runEnergyRestoreBlockedUntil < deadline) {
        runEnergyRestoreBlockedUntil = deadline
    }
}

public fun Player.clearRunEnergyRestoreBlock() {
    runEnergyRestoreBlockedUntil = 0
}

public suspend fun ProtectedAccess.withoutRunEnergyRestore(block: suspend () -> Unit) {
    player.blockRunEnergyRestore(MAX_RUN_ENERGY_RESTORE_BLOCK_CYCLES)
    try {
        block()
    } finally {
        player.clearRunEnergyRestoreBlock()
    }
}
