package org.rsmod.content.skills.slayer.core

import org.rsmod.api.table.slayer.SlayerMasterTaskRow

sealed class AssignmentRoll {
    data class Boss(val masterTask: SlayerBossTasks.Selection) : AssignmentRoll()
    data class Regular(val masterTask: SlayerMasterTaskRow, val amount: Int) : AssignmentRoll()
}
