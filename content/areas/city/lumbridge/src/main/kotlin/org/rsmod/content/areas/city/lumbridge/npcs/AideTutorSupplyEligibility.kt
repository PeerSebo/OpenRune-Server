package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.stat.statBase
import org.rsmod.game.entity.Player

internal object AideTutorSupplyEligibility {
    const val LevelLimit = 20

    fun magic(player: Player): Boolean = player.statBase("stat.magic") < LevelLimit

    fun ranged(player: Player): Boolean = player.statBase("stat.ranged") < LevelLimit

    fun melee(player: Player): Boolean =
        player.statBase("stat.attack") < LevelLimit &&
            player.statBase("stat.strength") < LevelLimit &&
            player.statBase("stat.defence") < LevelLimit
}
