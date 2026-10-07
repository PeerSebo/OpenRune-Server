package org.rsmod.api.specials.instant

import org.rsmod.api.player.protect.ProtectedAccess

/**
 * Deduct energy only after activate returns true. Requirements below 10 are specialized and
 * must be checked/consumed by the implementation.
 */
public fun interface InstantSpecialAttack {
    public suspend fun ProtectedAccess.activate(): Boolean
}
