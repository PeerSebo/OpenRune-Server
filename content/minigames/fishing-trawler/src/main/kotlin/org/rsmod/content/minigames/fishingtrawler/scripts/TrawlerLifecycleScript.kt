package org.rsmod.content.minigames.fishingtrawler.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.minigames.fishingtrawler.TrawlerConstants
import org.rsmod.content.minigames.fishingtrawler.TrawlerManager
import org.rsmod.game.MapClock
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Logout forfeits only that player's voyage; remaining occupants continue.
class TrawlerLifecycleScript
@Inject
constructor(
    private val manager: TrawlerManager,
    private val instanceManager: InstanceManager,
    private val mapClock: MapClock,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { manager.tick(mapClock.cycle) }

        onOpNpc1(TrawlerConstants.MURPHY_LOBBY) { joinVoyage() }

        onOpNpc1("npc.murphy_normal") { forfeitVoyage() }
        onOpNpc1("npc.murphy_halfsunk") { forfeitVoyage() }
        onOpNpc1("npc.murphy_sunk") { forfeitVoyage() }
    }

    private fun ProtectedAccess.joinVoyage() {
        when (val result = manager.joinOrCreateVoyage(player, mapClock)) {
            is InstanceManager.Result.Joined -> {
                telejump(result.enter)
                instanceManager.finalizeEntry(player, result.session, mapClock)
                mes("Murphy waves you aboard the trawler.")
            }
            is InstanceManager.Result.Failed -> mes(result.reason)
            else -> Unit
        }
    }

    private fun ProtectedAccess.forfeitVoyage() {
        val voyage = manager.voyageForPlayer(player)
        if (voyage == null) {
            mes("Murphy shrugs - you're not signed on for a voyage right now.")
            return
        }
        instanceManager.leave(player, voyage.session, mapClock)
        telejump(CoordGrid(TrawlerConstants.LOBBY_X, TrawlerConstants.LOBBY_Z, 0))
        mes("You clamber off the trawler.")
    }
}
