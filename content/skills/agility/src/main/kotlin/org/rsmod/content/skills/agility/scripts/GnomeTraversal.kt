package org.rsmod.content.skills.agility.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.agility.configs.GnomeStrongholdCourse
import org.rsmod.map.CoordGrid

internal suspend fun ProtectedAccess.gnomeBranchClimb(dest: CoordGrid, face: CoordGrid) {
    climbTo(dest, GnomeStrongholdCourse.BRANCH_TICKS, "seq.human_reachforladder",
        "synth.CLIMBING_LOOP", face = face)
}
