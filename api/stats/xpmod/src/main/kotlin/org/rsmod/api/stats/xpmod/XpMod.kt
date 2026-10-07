package org.rsmod.api.stats.xpmod

import org.rsmod.game.entity.Player

fun interface XpMod {
    /**
     * Additive bonus above 1.0: +4% returns 0.04. Applies only where callers explicitly use this modifier.
     */
    fun Player.modifier(stat: String): Double
}
