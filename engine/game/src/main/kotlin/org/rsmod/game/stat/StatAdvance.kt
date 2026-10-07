package org.rsmod.game.stat

import dev.openrune.types.StatType

/**
 * Snapshots the new base level at enqueue time: protected access can delay celebration delivery
 * until after later level-ups, making the current level unsuitable.
 */
public data class StatAdvance(public val stat: StatType, public val level: Int)
