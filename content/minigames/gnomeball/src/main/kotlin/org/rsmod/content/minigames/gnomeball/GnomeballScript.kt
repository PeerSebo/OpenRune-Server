package org.rsmod.content.minigames.gnomeball

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.events.PlayerHitpointsChangedEvent
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.advanced.onApPlayer5
import org.rsmod.api.script.advanced.onOpPlayer5
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class GnomeballScript @Inject constructor(
    private val game: GnomeballGame,
    private val locs: LocRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        GnomeballData.goalXp
        onOpNpc1("npc.gnomereferee") { referee(it.npc) }
        onOpNpc1("npc.gnome_ball_coach") { rules(it.npc) }
        for (type in GnomeballData.players) {
            for (id in listOf(type.base.id, type.ball.id, type.floor.id).distinct()) {
                val symbol = RSCM.getReverseMapping(RSCMType.NPC, id)
                onOpNpc1(symbol) {
                    when {
                        type.winger && id == type.ball.id -> startDialogue(it.npc) {
                            chatNpc(happy, "I'll pass the ball back to you in a moment.")
                        }
                        type.winger -> game.pass(player, it.npc)
                        else -> game.tackle(player, it.npc)
                    }
                }
                if (type.winger && id == type.base.id) onApNpc1(symbol) {
                    if (game.canThrow(player, it.npc.coords, GnomeballData.passRange)) game.pass(player, it.npc)
                    else apRange(GnomeballData.passRange)
                }
            }
        }
        onApLoc1("loc.goal_gnomeball_game") {
            if (game.canThrow(player, GnomeballData.goal, GnomeballData.shotRange)) game.shoot(player)
            else apRange(GnomeballData.shotRange)
        }
        onOpLoc1("loc.goal_gnomeball_game") { game.shoot(player) }
        for (symbol in listOf("loc.gate_gnomeball_game", "loc.gate_open_gnomeball_game")) {
            onOpLoc1(symbol) { crossGate(it.loc) }
        }
        onApPlayer5 {
            if (GnomeballState.active(player)) {
                if (game.canThrow(player, it.target.coords, GnomeballData.passRange)) game.pass(player, it.target)
                else apRange(GnomeballData.passRange)
            }
        }
        onOpPlayer5 { game.pass(player, it.target) }
        val ball = checkNotNull(ServerCacheManager.getItem(GnomeballState.Ball.asRSCM()))
        onOpPlayerU(ball) { game.pass(player, it.target) }
        onEvent<GameLifecycle.LateCycle> { game.tick() }
        onEvent<NpcStateEvents.Create> { game.spawned(npc) }
        onEvent<NpcStateEvents.Respawn> { game.spawned(npc) }
        onEvent<NpcStateEvents.Delete> { game.deleted(npc) }
        onEvent<HeldEquipEvents.WearposChange> { game.equipmentChanged(player) }
        onEvent<PlayerMovementEvent.CoordsMovedEvent> {
            if (GnomeballState.active(player) && !GnomeballData.contains(player.coords)) game.leave(player)
        }
        onEvent<PlayerHitpointsChangedEvent> { if (newHitpoints <= 0) game.leave(player) }
        onPlayerLogin { game.leave(player) }
        onPlayerLogout { game.leave(player) }
    }

    private suspend fun ProtectedAccess.referee(npc: Npc) {
        var option = 0
        startDialogue(npc) {
            chatNpc(happy, "Welcome to Gnomeball! Take a ball from me, dodge the defence and throw it through the net. Score five goals to win.")
            option = choice4("Let's play!", 1, "How do I play?", 2, "Collect my Gnomeballs.", 3, "Not right now.", 4)
        }
        when (option) {
            1 -> game.requestBall(player, npc)
            2 -> rules(npc)
            3 -> game.claim(player)
        }
    }

    private suspend fun ProtectedAccess.rules(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Throw the ball into the net at the east end of the field. Get close for a better shot; your Ranged skill helps you score.")
            chatNpc(happy, "The other gnomes will try to tackle you. You can tackle them back to recover the ball. Your Agility helps you dodge them.")
            chatNpc(happy, "Pass to a green winger and he'll throw the ball back. You can also pass to another player who's taking part.")
            chatNpc(happy, "Ask the referee for another ball after a shot. You need five goals to win. Leave the field when you're finished to collect your Gnomeball.")
        }
    }

    private suspend fun ProtectedAccess.crossGate(loc: BoundLocInfo) {
        if (loc.coords != GnomeballData.gate) return
        val leaving = GnomeballData.contains(coords)
        if (!leaving && player.inv.freeSpace() < listOf(3, 5).count { player.worn[it] != null }) {
            mes("Please make room to remove your weapon and shield before playing Gnomeball.")
            return
        }
        if (leaving) game.leave(player)
        val destination = GnomeballData.gate.translateX(if (leaving) -1 else 1)
        faceSquare(destination)
        locs.del(loc, 5)
        exactMove(coords, destination, 0, 60, if (leaving) 512 else 1536)
        delay(2)
        if (!leaving) game.enter(player)
    }
}
