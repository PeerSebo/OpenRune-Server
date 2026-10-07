package org.rsmod.content.skills.hunter

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

data class HunterCatchEvent(val player: Player, val prey: String) : UnboundEvent
