package org.rsmod.content.areas.city.draynor

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.script.onAiTimer
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorPossessedChairs @Inject constructor(
    private val players: PlayerRepository,
    private val random: GameRandom,
    private val routes: RouteFactory,
    private val rays: RayCastValidator,
) : PluginScript() {
    private val chairs = WeakHashMap<Npc, Following>()

    override fun ScriptContext.startup() {
        onAiTimer(Chair) { follow(npc) }
    }

    internal fun follow(npc: Npc) {
        if (!inside(npc.spawnCoords) || !npc.isVisible || npc.isBusy) return
        val state = chairs.getOrPut(npc) { Following() }
        if (npc.routeDestination.isNotEmpty() && state.lastPosition == npc.coords) {
            if (++state.blockedTicks >= 3) {
                npc.resetMovement()
                state.blockedTicks = 0
            }
        } else {
            state.blockedTicks = 0
        }
        state.lastPosition = npc.coords
        if (state.rest > 0) {
            state.rest--
            return
        }
        val target = state.target
        if (target == null) {
            if (random.randomBoolean(4)) {
                val nearby = players.findAll(ZoneKey.from(npc.coords), 1)
                    .filter { eligible(npc, it) }.toList()
                val selected = random.pickOrNull(nearby)
                if (selected != null) {
                    state.target = selected
                    state.remaining = random.of(10, 30)
                    npc.facePlayer(selected)
                }
            }
            if (state.target == null) {
                roam(npc)
                return
            }
        } else if (--state.remaining <= 0 || !eligible(npc, target)) {
            rest(npc, state)
            return
        }
        val following = checkNotNull(state.target)
        npc.facePlayer(following)
        val route = routes.create(npc.avatar, following.avatar)
        val waypoints = route.map { CoordGrid(it.x, it.z, it.level) }
        if (!route.success || route.alternative || waypoints.any { !inside(it) }) {
            rest(npc, state)
        } else {
            npc.walk(waypoints)
        }
    }

    private fun eligible(npc: Npc, player: Player): Boolean =
        player.isValidTarget() && !player.staffInvisible && inside(player.coords) &&
            npc.level == player.level && npc.isWithinDistance(player, 6) &&
            rays.hasLineOfSight(npc.coords, player.coords)

    private fun rest(npc: Npc, state: Following) {
        npc.resetMovement()
        npc.resetFaceEntity()
        state.target = null
        state.rest = random.of(8, 20)
    }

    private fun roam(npc: Npc) {
        if (!npc.routeDestination.isEmpty() || !random.randomBoolean(8)) return
        val destination = npc.spawnCoords.translate(random.of(-4, 4), random.of(-4, 4))
        if (!inside(destination) || destination == npc.coords) return
        val route = routes.create(npc.avatar, destination)
        val waypoints = route.map { CoordGrid(it.x, it.z, it.level) }
        if (route.success && !route.alternative && waypoints.all(::inside)) npc.walk(waypoints)
    }

    private class Following(
        var target: Player? = null,
        var remaining: Int = 0,
        var rest: Int = 0,
        var lastPosition: CoordGrid = CoordGrid.NULL,
        var blockedTicks: Int = 0,
    )

    internal companion object {
        const val Chair = "npc.draynor_possesed_chair"
        fun inside(coords: CoordGrid): Boolean =
            coords.level in 0..1 && coords.x in 3092..3123 && coords.z in 3354..3373
    }
}
