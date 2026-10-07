package org.rsmod.api.net.rsprot.ext

import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerAvatarExtendedInfo
import org.rsmod.game.entity.util.EntityFaceTarget

/**
 * [fallbackAngle] is the bearing toward [target], used by the client only while that avatar is
 * outside its high-resolution set. It is a required parameter rather than a defaulted one so that
 * a new call site cannot silently reintroduce the hardcoded 0 this replaced.
 */
fun PlayerAvatarExtendedInfo.setFaceTarget(target: EntityFaceTarget, fallbackAngle: Int) {
    when {
        target.entitySlot == -1 -> resetFacing()
        target.isNpc ->
            setFaceNpc(
                target.npcSlot,
                instant = false,
                walkMode = 0,
                entityFallbackAngle = fallbackAngle,
            )

        target.isPlayer ->
            setFacePlayer(
                target.playerSlot,
                instant = false,
                walkMode = 0,
                entityFallbackAngle = fallbackAngle,
            )
    }
}
