package org.rsmod.content.skills.agility.shortcuts.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.scripts.AGILITY_STAT

internal fun ProtectedAccess.meetsShortcutReq(levelReq: Int, verbPhrase: String): Boolean {
    if (player.agilityLvl >= levelReq) {
        return true
    }
    mes("You need an Agility level of at least $levelReq to $verbPhrase.")
    return false
}

internal fun ProtectedAccess.awardShortcutXp(xp: Double, xpMods: XpModifiers) {
    if (xp <= 0.0) {
        return
    }
    statAdvance(AGILITY_STAT, xp * xpMods.get(player, AGILITY_STAT))
}
