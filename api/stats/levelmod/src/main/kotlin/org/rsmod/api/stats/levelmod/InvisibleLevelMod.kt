package org.rsmod.api.stats.levelmod

import org.rsmod.game.entity.Player

abstract class InvisibleLevelMod(internal val stat: String) {
    abstract fun Player.calculateBoost(): Int
}
