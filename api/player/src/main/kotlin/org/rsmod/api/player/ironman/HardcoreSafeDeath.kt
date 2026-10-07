package org.rsmod.api.player.ironman

import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Player

private var Player.nextDeathSafe by boolVarp("varp.player_next_death_safe")

public fun Player.markNextDeathSafe() {
    nextDeathSafe = true
}

public fun Player.clearSafeDeathMark() {
    nextDeathSafe = false
}

public fun Player.hasSafeDeathMark(): Boolean = nextDeathSafe
