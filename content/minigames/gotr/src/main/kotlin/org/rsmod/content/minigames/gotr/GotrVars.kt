package org.rsmod.content.minigames.gotr

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.gotrElementalPoints by intVarp("varp.gotr_elemental_points")
internal var Player.gotrCatalyticPoints by intVarp("varp.gotr_catalytic_points")
internal var Player.gotrTotalSearches by intVarp("varp.gotr_total_searches")
internal var Player.gotrRiftsClosed by intVarp("varp.gotr_rifts_closed")
private var Player.gotrRewardsMigrated by boolVarp("varp.gotr_rewards_migrated")

internal fun Player.migrateGotrRewards() {
    val shouldImport = !gotrRewardsMigrated
    for (name in listOf("gotr_elemental_points", "gotr_catalytic_points", "gotr_total_searches", "gotr_rifts_closed")) {
        val key = AttributeKey<Int>(persistenceKey = name)
        val value = attr[key]
        val symbol = "varp.$name"
        if (shouldImport && value != null && symbol !in vars) {
            VarPlayerIntMapSetter.set(this, symbol, value.coerceAtLeast(0))
        }
        if (attr.has(key)) attr.remove(key)
    }
    if (shouldImport) gotrRewardsMigrated = true
}

internal fun Player.gotrEnergySearches(): Int {
    migrateGotrRewards()
    return minOf(gotrElementalPoints, gotrCatalyticPoints).coerceAtLeast(0)
}

internal fun Player.spendGotrEnergy(): Boolean {
    if (gotrEnergySearches() <= 0) return false
    gotrElementalPoints--
    gotrCatalyticPoints--
    return true
}
