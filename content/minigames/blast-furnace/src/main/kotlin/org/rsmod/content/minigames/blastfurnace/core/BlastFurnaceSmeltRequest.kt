package org.rsmod.content.minigames.blastfurnace.core

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

internal data class BlastFurnaceSmeltRequest(val player: Player) : UnboundEvent
