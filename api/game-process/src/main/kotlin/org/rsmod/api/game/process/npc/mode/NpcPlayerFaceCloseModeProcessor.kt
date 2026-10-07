package org.rsmod.api.game.process.npc.mode

import jakarta.inject.Inject
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

public class NpcPlayerFaceCloseModeProcessor
@Inject
constructor(private val playerList: PlayerList) {
    public fun process(npc: Npc) {
        val interacting = npc.facingTarget(playerList)
        if (interacting == null || !npc.inCloseDistance(interacting)) {
            npc.resetMode()
        } else {
            npc.abortRoute()
        }
    }

    /** faceEntity can retain a target after a plane change; reject it before computing distance. */
    private fun Npc.inCloseDistance(target: Player): Boolean =
        level == target.level && isWithinDistance(target, 1)
}
