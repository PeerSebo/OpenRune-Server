package org.rsmod.content.skills.hunter

import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.content.skills.hunter.configs.HunterPrey
import org.rsmod.content.skills.hunter.configs.NetTrapColour
import org.rsmod.content.skills.hunter.configs.TrapFamily
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

enum class TrapState {
    LAYING,

    SET,

    TRAPPING,

    FULL,

    FAILING,

    FAILED,
}

class ActiveTrap(
    val coords: CoordGrid,
    val family: TrapFamily,
    val owner: PlayerUid,
    val angle: LocAngle,
    val shape: LocShape,
    val netColour: NetTrapColour?,
) {
    var state: TrapState = TrapState.LAYING

    var locName: String = ""

    var armedCycle: Int = 0

    var transitionCycle: Int = 0

    var prey: HunterPrey? = null

    // Generation guards stale loc timers after swaps; spawned-loc deletion does not cancel their add timers.
    var generation: Int = 0

    // The reservation owns materials until arming or refund.
    var layMaterials: List<String> = emptyList()

    var equipment: List<String> = HunterParams.recoveredTools(family)

    var layDeadline: Int = Int.MAX_VALUE

    var bait: String? = null
    var smoked: Boolean = false
}
