package org.rsmod.content.minigames.blastfurnace.core

import org.rsmod.events.UnboundEvent

internal sealed interface BlastFurnaceVisualEvent : UnboundEvent {
    data class Belt(val ores: List<String>) : BlastFurnaceVisualEvent
    data object Pour : BlastFurnaceVisualEvent
}
