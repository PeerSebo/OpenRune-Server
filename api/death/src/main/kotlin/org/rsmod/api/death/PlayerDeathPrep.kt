package org.rsmod.api.death

import org.rsmod.api.player.death.DeathCause
import org.rsmod.api.player.death.recordDeathCause
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.nullableIntVarp
import org.rsmod.game.entity.Player

private var Player.lastPvpHitCycle by nullableIntVarp("varp.player_last_pvp_hit_cycle")
private var Player.bypassAdminDeathDrops by boolVarp("varp.player_death_drops_bypass_admin")

public fun Player.recordPvpHit() {
    lastPvpHitCycle = currentMapClock
}

internal fun Player.wasRecentlyHitByPlayer(cycle: Int): Boolean {
    val lastHit = lastPvpHitCycle ?: return false
    return (cycle - lastHit) <= RECENT_PVP_HIT_TICKS
}

public fun Player.preparePvpDeath(killer: Player) {
    recordPvpHit()
    recordDeathCause(DeathCause.ByPlayer(killer))
}

public fun Player.prepareAdminDieTest() {
    bypassAdminDeathDrops = true
}

internal fun Player.consumeAdminDeathDropBypass(): Boolean {
    val bypass = bypassAdminDeathDrops
    bypassAdminDeathDrops = false
    return bypass
}
