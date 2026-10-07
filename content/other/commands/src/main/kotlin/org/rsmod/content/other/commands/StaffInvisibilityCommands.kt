package org.rsmod.content.other.commands

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.output.mes
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.StaffVisibilityChanged
import org.rsmod.game.entity.util.EntityFaceTarget
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class StaffInvisibilityCommands @Inject constructor(
    private val collision: CollisionFlagMap,
    private val players: PlayerList,
    private val npcs: NpcList,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("invis", "Toggle staff invisibility", { toggle() }) {
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::invis"
        }
    }

    private fun Cheat.toggle() {
        if (args.isNotEmpty()) {
            player.mes("Usage: ::invis")
            return
        }
        // Update collision before changing the flag: invisible staff do not block wandering NPCs.
        if (player.isVisible) player.removeBlockWalkCollision(collision, player.coords)
        player.staffInvisible = !player.staffInvisible
        if (player.isVisible) player.addBlockWalkCollision(collision, player.coords)
        if (player.staffInvisible) {
            player.clearInteractionRoute()
            player.resetFaceEntity()
            player.publicMessage = null
            val faceTarget = EntityFaceTarget(player)
            for (observer in players) {
                if ((observer.interaction as? InteractionPlayer)?.target === player) {
                    observer.clearInteractionRoute()
                }
                if (observer.faceEntity == faceTarget) observer.resetFaceEntity()
            }
            for (npc in npcs) {
                if ((npc.interaction as? InteractionPlayer)?.target === player) {
                    npc.clearInteraction()
                    npc.abortRoute()
                }
                if (npc.faceEntity == faceTarget) npc.resetFaceEntity()
            }
        }
        eventBus.publish(StaffVisibilityChanged(player))
        player.mes(if (player.staffInvisible) "Staff invisibility enabled." else "Staff invisibility disabled.")
    }
}
