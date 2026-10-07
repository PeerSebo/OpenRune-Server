package org.rsmod.content.events.shootingstars

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

var Player.miningShootingStar by boolVarp("varp.shooting_star_mining")

private var Player.starBonus by intVarp("varp.shooting_star_discoverer_bonus")
private var Player.starDust by intVarp("varp.shooting_star_all_time_dust")
private var Player.starSeen by boolVarp("varp.shooting_star_seen")
private var Player.starStateMigrated by boolVarp("varp.shooting_star_state_migrated")

var Player.discovererBonusRemaining: Int
    get() {
        if (!starStateMigrated) migrateShootingStarState()
        return starBonus
    }
    set(value) {
        if (!starStateMigrated) migrateShootingStarState()
        starBonus = value
    }

var Player.allTimeStarDust: Int
    get() {
        if (!starStateMigrated) migrateShootingStarState()
        return starDust
    }
    set(value) {
        if (!starStateMigrated) migrateShootingStarState()
        starDust = value
    }

var Player.seenShootingStar: Boolean
    get() {
        if (!starStateMigrated) migrateShootingStarState()
        return starSeen
    }
    set(value) {
        if (!starStateMigrated) migrateShootingStarState()
        starSeen = value
    }

fun Player.migrateShootingStarState() {
    for ((symbol, name) in listOf(
        "varp.shooting_star_discoverer_bonus" to "shooting_star_discoverer_bonus",
        "varp.shooting_star_all_time_dust" to "shooting_star_all_time_dust",
    )) {
        val legacy = AttributeKey<Int>(name)
        if (!starStateMigrated && symbol !in vars) {
            attr[legacy]?.let { VarPlayerIntMapSetter.set(this, symbol, it) }
        }
        attr.remove(legacy)
    }
    val seen = AttributeKey<Boolean>("shooting_star_seen")
    if (!starStateMigrated && "varp.shooting_star_seen" !in vars) {
        starSeen = attr[seen] == true
    }
    attr.remove(seen)
    starStateMigrated = true
}
