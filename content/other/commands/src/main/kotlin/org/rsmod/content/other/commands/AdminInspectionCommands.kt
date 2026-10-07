package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.cheat.adminNoClip
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.cheat.Cheat
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AdminInspectionCommands @Inject constructor(
    private val npcs: NpcRepository,
    private val locs: LocRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand(
            "debugstate",
            "Debug: report movement, locks, queues and active test flags; ::debugstate",
            ::state,
        )
        onCommand(
            "npcsnear",
            "Debug: inspect up to 8 nearby NPCs; ::npcsnear [radius 0-16] [search]",
            ::npcsNear,
        )
        onCommand(
            "locsnear",
            "Debug: inspect up to 8 nearby objects; ::locsnear [radius 0-16] [search]",
            ::locsNear,
        )
        onCommand("face", "Debug: face a tile while idle without walking; ::face x y", ::face)
    }

    private fun state(cheat: Cheat) = with(cheat) {
        if (!expectArgs(0, "::debugstate")) return
        with(player) {
            mes("Tile: ${coords.x}, ${coords.z}, ${coords.level}; " +
                "tick: $currentMapClock; facing: $faceAngle")
            mes("Protected: $isAccessProtected; " +
                "delay: ${maxOf(0, delay.toLong() - processedMapClock)} ticks")
            mes("Route: ${routeRequest != null}; " +
                "waypoints: ${routeDestination.size}; forced: $forcedRoute")
            mes("Exact move: ${pendingExactMove != null}; " +
                "teleport: ${pendingTeleport || pendingTelejump}")
            mes("Queues: ${queueList.size} normal, ${weakQueueList.size} weak; " +
                "coroutine: ${activeCoroutine != null}")
            mes("Interaction: ${interaction?.javaClass?.simpleName ?: "none"}; " +
                "modal: ${ui.modals.isNotEmpty()}")
            mes("Test flags: god=$adminGodMode, maxhit=$adminMaxHit, " +
                "testwalk=$adminNoClip, invis=$staffInvisible")
            mes("XP: personal ${xpRate}x, global ${globalXpRate}x; logout pending: $loggingOut")
        }
    }

    private fun npcsNear(cheat: Cheat) = with(cheat) {
        val radius = radius("npcsnear") ?: return
        val query = args.drop(1).joinToString(" ").lowercase()
        val nearby = npcs.findAll(ZoneKey.from(player.coords), (radius + 7) / 8)
            .filter { it.isVisible && it.coords.level == player.coords.level }
            .filter { it.coords.chebyshevDistance(player.coords) <= radius }
            .filter { matches(query, it.id, it.name, it.type.internalName) }
            .sortedWith(compareBy({ it.coords.chebyshevDistance(player.coords) }, { it.slotId }))
            .toList()
        player.mes(
            "${nearby.size} matching NPCs within $radius tiles; showing up to $RESULT_LIMIT."
        )
        for (npc in nearby.take(RESULT_LIMIT)) {
            player.mes("${npc.name.debugLabel()}: id=${npc.id}, slot=${npc.slotId}, " +
                npc.type.internalName.debugLabel())
            player.mes("Tile ${npc.coords.x}, ${npc.coords.z}, ${npc.coords.level}; " +
                "spawn ${npc.spawnCoords.x}, ${npc.spawnCoords.z}")
            player.mes("Mode=${npc.mode}; face=${npc.faceAngle}; " +
                "HP=${npc.hitpoints}; route=${npc.routeDestination.size}")
        }
    }

    private fun locsNear(cheat: Cheat) = with(cheat) {
        val radius = radius("locsnear") ?: return
        val query = args.drop(1).joinToString(" ").lowercase()
        val nearby = nearbyZones(player.coords, radius).flatMap(locs::findAll)
            .filter { it.coords.level == player.coords.level }
            .filter { it.coords.chebyshevDistance(player.coords) <= radius }
            .filter {
                val type = ServerCacheManager.getObject(it.id)
                matches(query, it.id, type?.name.orEmpty(), type?.internalName.orEmpty())
            }
            .sortedWith(compareBy(
                { it.coords.chebyshevDistance(player.coords) },
                { it.coords.x }, { it.coords.z }, { it.layer },
            ))
            .toList()
        player.mes(
            "${nearby.size} matching objects within $radius tiles; showing up to $RESULT_LIMIT."
        )
        for (loc in nearby.take(RESULT_LIMIT)) {
            val type = ServerCacheManager.getObject(loc.id)
            val symbol = RSCM.getReverseMapping(RSCMType.LOC, loc.id)
            player.mes("${type?.name.orEmpty().debugLabel()}: id=${loc.id}, ${symbol.debugLabel()}")
            player.mes("Tile ${loc.coords.x}, ${loc.coords.z}, ${loc.coords.level}; " +
                "shape=${loc.shapeId}, angle=${loc.angleId}, layer=${loc.layer}")
        }
    }

    private fun face(cheat: Cheat) = with(cheat) {
        if (!expectArgs(2, "::face x y")) return
        val x = args[0].toIntOrNull()
        val z = args[1].toIntOrNull()
        if (x == null || z == null || x !in 0..CoordGrid.X_BIT_MASK ||
            z !in 0..CoordGrid.Z_BIT_MASK) {
            player.mes("Enter valid tile coordinates: ::face x y")
            return
        }
        if (player.isAccessProtected || player.isBusy2 || player.routeRequest != null ||
            player.forcedRoute || player.pendingExactMove != null || player.pendingTeleport ||
            player.pendingTelejump || player.loggingOut) {
            player.mes("Finish your current action before using ::face.")
            return
        }
        if (x == player.coords.x && z == player.coords.z) {
            player.mes("Choose a different tile to face.")
            return
        }
        player.faceSquare(CoordGrid(x, z, player.coords.level))
        player.mes("Facing tile $x, $z.")
    }

    private fun Cheat.radius(command: String): Int? {
        if (args.isEmpty()) return DEFAULT_RADIUS
        val radius = args[0].toIntOrNull()
        if (radius != null && radius in 0..MAX_RADIUS) return radius
        player.mes("Usage: ::$command [radius 0-$MAX_RADIUS] [name, symbol or id]")
        return null
    }

    private fun matches(query: String, id: Int, name: String, symbol: String): Boolean =
        query.isEmpty() || query == id.toString() || name.contains(query, true) ||
            symbol.contains(query, true)

    private fun nearbyZones(coords: CoordGrid, radius: Int): Sequence<ZoneKey> = sequence {
        val center = ZoneKey.from(coords)
        val zones = (radius + 7) / 8
        for (x in maxOf(0, center.x - zones)..minOf(ZoneKey.X_BIT_MASK, center.x + zones)) {
            for (z in maxOf(0, center.z - zones)..minOf(ZoneKey.Z_BIT_MASK, center.z + zones)) {
                yield(ZoneKey(x, z, center.level))
            }
        }
    }

    private companion object {
        const val DEFAULT_RADIUS = 4
        const val MAX_RADIUS = 16
        const val RESULT_LIMIT = 8
    }
}
