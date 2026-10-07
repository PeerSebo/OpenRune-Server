package org.rsmod.api.player

import org.rsmod.api.player.output.UpdateRun
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.RunEnergy

public fun Player.restoreRunEnergyPercent(percent: Int) {
    if (percent <= 0 || runEnergy >= RunEnergy.MAX) {
        return
    }
    runEnergy = (runEnergy.toLong() + percent.toLong() * RunEnergy.UNITS_PER_PERCENT)
        .coerceIn(0L, RunEnergy.MAX.toLong()).toInt()
    UpdateRun.energy(this, runEnergy)
}
