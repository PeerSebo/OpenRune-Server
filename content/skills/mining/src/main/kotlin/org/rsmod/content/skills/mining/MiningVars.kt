package org.rsmod.content.skills.mining

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.vars.typeIntVarp
import org.rsmod.game.entity.Player

internal var Player.lastMiningPickaxe by miningItemVarp("varp.mining_last_pickaxe")
internal var Player.preSmeltOre by miningItemVarp("varp.mining_pre_smelt_ore")

private fun miningItemVarp(symbol: String) = typeIntVarp<String?>(
    symbol,
    toType = { if (it == 0) null else RSCM.getReverseMapping(RSCMType.OBJ, it - 1) },
    fromType = { it?.asRSCM(RSCMType.OBJ)?.plus(1) ?: 0 },
)
