package org.rsmod.content.skills.woodcutting

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.stats.levelmod.InvisibleLevelMod
import org.rsmod.game.entity.Player

class WoodcuttingLevelBoosts @Inject constructor(private val areas: AreaChecker) :
    InvisibleLevelMod("stat.woodcutting") {
    override fun Player.calculateBoost(): Int {
        var boost = 0
        if (areas.inArea("area.woodcutting_guild", coords)) {
            boost += 7
        }
        // TODO: Add the nearby-chopper boost using tree identity; verify exclusions for normal, guild and Farming trees.
        return boost
    }
}
