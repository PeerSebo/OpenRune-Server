package org.rsmod.content.skills.farming.configs

import org.rsmod.api.player.protect.ProtectedAccess

internal object HesporiSeedDrops {
    fun roll(access: ProtectedAccess, crop: FarmingCropDef, atCheckHealth: Boolean) = with(access) {
        // Only the health claim rolls a seed for repeatedly harvestable bushes/fruit trees.
        if ((crop.checkHealthXp > 0) != atCheckHealth) return@with
        if (player.vars["varbit.farmguild_hespori_seeds"] != 0) return@with
        val denominator = denominator(crop) ?: return@with
        if (random.of(denominator) != 0) return@with
        if (invAdd(inv, "obj.hespori_seed").success) {
            mes("You find a Hespori seed!")
        }
    }

    internal fun denominator(crop: FarmingCropDef): Int? = crop.hesporiSeedDenominator
}
