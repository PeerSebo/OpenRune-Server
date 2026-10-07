package org.rsmod.api.player.protect

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

/**
 * These interface buttons run without interrupting the current action. Register only controls
 * that must remain usable during an active script.
 */
public object NonInterruptingComponents {
    private val components = mutableSetOf<Int>()

    public fun register(component: String) {
        components += component.asRSCM(RSCMType.COMPONENT)
    }

    public fun contains(packedComponent: Int): Boolean = packedComponent in components
}
