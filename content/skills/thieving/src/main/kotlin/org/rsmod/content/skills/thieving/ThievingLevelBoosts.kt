package org.rsmod.content.skills.thieving

import org.rsmod.api.stats.levelmod.InvisibleLevelMod
import org.rsmod.game.entity.Player

class ThievingLevelBoosts : InvisibleLevelMod("stat.thieving") {
    override fun Player.calculateBoost(): Int {
        // TODO: Verify remaining diary-related Thieving boosts.
        return 0
    }
}
