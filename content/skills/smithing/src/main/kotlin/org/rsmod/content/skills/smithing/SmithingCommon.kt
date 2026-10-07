package org.rsmod.content.skills.smithing

import org.rsmod.api.config.Constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.smithing.configs.SmithingParams

// Queue and interface callbacks enqueue before the same-tick sweep; add one tick to preserve the cadence.
internal fun ProtectedAccess.smithQueue(queue: String, cadenceTicks: Int, task: Any) {
    weakQueue(queue, cadenceTicks + 1, task)
}

private val smithsUniformPieces =
    listOf(
        listOf("obj.smithing_uniform_torso"),
        listOf("obj.smithing_uniform_legs"),
        listOf("obj.smithing_uniform_boots"),
        listOf("obj.smithing_uniform_gloves", "obj.smithing_uniform_gloves_ice"),
    )

internal fun ProtectedAccess.anvilCadenceTicks(): Int {
    val pieces =
        smithsUniformPieces.count { alternatives -> alternatives.any { it in player.worn } }
    return when {
        pieces == 0 -> SmithingParams.ANVIL_TICKS
        // Full set: guaranteed proc (wiki), not 4 * 20%.
        pieces == smithsUniformPieces.size -> SmithingParams.ANVIL_TICKS_UNIFORM_PROC
        random.of(5) < pieces -> SmithingParams.ANVIL_TICKS_UNIFORM_PROC
        else -> SmithingParams.ANVIL_TICKS
    }
}

// Roll back every input if output insertion fails; do not reject a full inventory before deleting materials.
internal fun ProtectedAccess.tryProduce(
    inputs: List<Pair<String, Int>>,
    output: String,
    outputCount: Int,
): Boolean {
    val removed = mutableListOf<Pair<String, Int>>()
    for ((item, amount) in inputs) {
        if (amount <= 0) {
            continue
        }
        if (invDel(inv, item, amount).success) {
            removed += item to amount
        } else {
            rollbackProduce(removed)
            return false
        }
    }
    if (invAdd(inv, output, outputCount).failure) {
        rollbackProduce(removed)
        mes(Constants.dm_invspace)
        return false
    }
    return true
}

private fun ProtectedAccess.rollbackProduce(removed: List<Pair<String, Int>>) {
    for ((item, amount) in removed) {
        invAdd(inv, item, amount)
    }
}
