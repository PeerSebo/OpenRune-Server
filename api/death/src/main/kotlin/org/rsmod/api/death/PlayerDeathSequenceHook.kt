package org.rsmod.api.death

import org.rsmod.api.player.protect.ProtectedAccess

public fun interface PlayerDeathSequenceHook {
    public suspend fun handle(access: ProtectedAccess): Boolean
}
