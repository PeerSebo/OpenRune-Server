package org.rsmod.content.skills.agility.scripts

import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.agility.configs.WildernessObstacleCourse
import org.rsmod.map.CoordGrid

internal suspend fun ProtectedAccess.wildernessStoneHops(canFinish: () -> Boolean): Boolean {
    var crossed = false
    player.withAgilityAnimation {
        val hops = WildernessObstacleCourse.STEPPING_STONE_HOPS
        for ((index, tile) in hops.withIndex()) {
            if (index == hops.lastIndex && !canFinish()) return@withAgilityAnimation
            val ticks = if (index == hops.lastIndex) 1 else 2
            player.blockRunEnergyRestore(ticks)
            moveBeat(tile, "seq.human_spot_jump", "synth.JUMP",
                movement = AgilityMovement(15, 30, 512))
            delay(ticks)
        }
        crossed = true
    }
    return crossed
}

internal suspend fun ProtectedAccess.wildernessRockClimb(x: Int) {
    val dest = CoordGrid(x, WildernessObstacleCourse.ROCKS_DEST_Z, 0)
    player.blockRunEnergyRestore(4)
    soundSynth("synth.CLIMB_WALL", loops = 7)
    withBalancePose("seq.human_walk_crumbledwall") {
        resetAnim()
        forcedWalk(dest, 4)
    }
}
