package org.rsmod.api.game.process.npc.hunt

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Aggression tolerance is tracked by player and map square; changing squares resets the
 * ten-minute window.
 */
@Singleton
public class AggressionTolerance @Inject constructor() {
    private class Stay(var square: MapSquareKey, var since: Int, var lastSeen: Int)

    private val stays = HashMap<PlayerUid, Stay>()
    private var lastPrune = 0

    public fun tick(player: Player, cycle: Int) {
        record(player.uid, player.coords, cycle)
        if (cycle - lastPrune >= PRUNE_INTERVAL) {
            lastPrune = cycle
            stays.values.removeIf { cycle - it.lastSeen > PRUNE_INTERVAL }
        }
    }

    public fun isTolerant(player: Player, cycle: Int): Boolean = isTolerant(player.uid, cycle)

    internal fun record(uid: PlayerUid, coords: CoordGrid, cycle: Int) {
        val square = MapSquareKey.from(coords)
        val stay = stays[uid]
        if (stay == null || stay.square != square) {
            stays[uid] = Stay(square, since = cycle, lastSeen = cycle)
            return
        }
        stay.lastSeen = cycle
    }

    internal fun isTolerant(uid: PlayerUid, cycle: Int): Boolean {
        val stay = stays[uid] ?: return false
        return cycle - stay.since >= TOLERANCE_CYCLES
    }

    public companion object {
        public const val TOLERANCE_CYCLES: Int = 1000

        private const val PRUNE_INTERVAL = 500
    }
}
