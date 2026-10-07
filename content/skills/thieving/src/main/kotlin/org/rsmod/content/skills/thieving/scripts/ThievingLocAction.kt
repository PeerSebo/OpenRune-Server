package org.rsmod.content.skills.thieving.scripts

import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo

internal object ThievingLocAction {
    fun start(player: Player, loc: BoundLocInfo, current: LocInfo?, cycles: Int): Boolean {
        if (player.actionDelay > player.currentMapClock || !canFinish(player, loc, current)) {
            return false
        }
        player.actionDelay = player.currentMapClock + cycles
        return true
    }

    fun canFinish(player: Player, loc: BoundLocInfo, current: LocInfo?): Boolean {
        if (current == null || current.coords != loc.coords || current.layer != loc.layer || current.entity != loc.entity) {
            return false
        }
        val coords = player.coords
        return coords.level == loc.level &&
            coords.x in (loc.x - 1)..(loc.x + loc.adjustedWidth) &&
            coords.z in (loc.z - 1)..(loc.z + loc.adjustedLength)
    }
}
