package org.rsmod.api.net.rsprot.player

import dev.or2.central.account.Rights
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal fun Player.protectedTelejump(
    contextFactory: ProtectedAccessContextFactory,
    collision: CollisionFlagMap,
    dest: CoordGrid,
): Boolean {
    if (isAccessProtected) {
        return false
    }
    launch {
        // A full context is required as `telejump` closes any open interfaces, which relies on the
        // context's event bus.
        val access = ProtectedAccess(this@protectedTelejump, this, contextFactory.create())
        access.telejump(dest, collision, TeleportType.Exempt)
    }
    return true
}

internal fun Player.modLevelTeleMoveSpeed(developmentMode: Boolean): MoveSpeed? =
    if (modLevel.isAtLeast(Rights.ADMINISTRATOR) || developmentMode) {
        MoveSpeed.Stationary
    } else {
        null
    }
