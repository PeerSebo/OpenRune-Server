package org.rsmod.api.poh

import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal fun Player.beginHouseRecovery(exit: CoordGrid) {
    require(PohLocation.entries.any { it.exitCoords == exit })
    houseExitPacked = exit.packed
    loginExitCoord = exit
}

internal fun Player.endHouseRecovery() {
    val exit = houseExitPacked.takeIf { it != 0 }?.let(::CoordGrid)
    if (exit != null && loginExitCoord == exit) loginExitCoord = null
    houseExitPacked = 0
}

private var Player.houseExitPacked by intVarp("varp.poh_login_exit_coord")
