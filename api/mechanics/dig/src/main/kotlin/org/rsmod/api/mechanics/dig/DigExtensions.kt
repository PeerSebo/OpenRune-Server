package org.rsmod.api.mechanics.dig

import org.rsmod.api.player.protect.ProtectedAccess

/**
 * Resolve a registered dig site before the generic failure response; preserve the dig
 * animation's action delay.
 */
public suspend fun ProtectedAccess.digWithSpade(): Boolean {
    anim(DigSites.DIG_SEQ)
    soundSynth(DigSites.DIG_SYNTH)

    if (DigSites.dispatch(this)) {
        return true
    }

    delay(DigSites.EMPTY_DIG_TICKS)
    resetAnim()
    return false
}
