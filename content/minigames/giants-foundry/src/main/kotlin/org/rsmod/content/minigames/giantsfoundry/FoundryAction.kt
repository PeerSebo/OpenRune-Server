package org.rsmod.content.minigames.giantsfoundry

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.player.ProtectedAccessLostException

internal suspend fun ProtectedAccess.awaitFoundryCycles(cycles: Int = 1) {
    require(cycles > 0)
    val resumeCycle = player.currentMapClock + cycles
    val coords = player.coords
    val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
    coroutine.pause { player.currentMapClock >= resumeCycle }
    if (player.activeCoroutine !== coroutine || player.isDelayed || player.coords != coords ||
        player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal") != modal
    ) {
        throw ProtectedAccessLostException()
    }
}
