package org.rsmod.content.minigames.tempoross

import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.stat.strengthLvl
import org.rsmod.content.skills.fishing.configs.FishingEquipment.HarpoonTier
import org.rsmod.game.entity.Player

object TemporossFishing {
    fun catchChance(level: Int, tier: HarpoonTier): Int {
        val (low, high) = when (tier) {
            HarpoonTier.Standard -> 170 to 255
            HarpoonTier.Dragon -> 178 to 267
            HarpoonTier.Crystal -> 187 to 280
        }
        return (1 + low + (high - low) * (level.coerceIn(1, 99) - 1) / 98).coerceAtMost(256)
    }

    fun canBarehand(player: Player): Boolean = player.fishingLvl >= 55 && player.strengthLvl >= 35 && player.vars["varbit.brut_fishing_s"] >= 1
}
