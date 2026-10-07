package org.rsmod.content.skills.farming.scripts

import org.rsmod.api.farming.FarmingClock
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.skills.farming.configs.FarmingParams
import org.rsmod.game.entity.Player

private var Player.seedlingDeadline by intVarp(FarmingParams.VARP_SEEDLING_READY)

public fun ProtectedAccess.scheduleWateredSeedlings() {
    val now = FarmingClock.currentMinute()
    val deadline = player.seedlingDeadline
    if (deadline <= 0 || now >= deadline.toLong()) {
        player.seedlingDeadline = (now + FarmingParams.SEEDLING_TO_SAPLING_MINUTES).toInt()
        weakQueue(FarmingParams.QUEUE_SEEDLING_GROW, FarmingParams.SEEDLING_QUEUE_TICKS + 1,
            FarmingPatchTask(""))
    }
}
