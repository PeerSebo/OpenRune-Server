package org.rsmod.content.generic.locs.stile

import jakarta.inject.Inject
import org.rsmod.api.config.Constants as constants
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StileScript @Inject constructor(private val worldRepo: WorldRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for (stile in Stiles) {
            onOpLoc1(stile) { climbOver(it.vis) }
        }
    }

    private suspend fun ProtectedAccess.climbOver(stile: BoundLocInfo) {
        arriveDelay()

        val crossing = stileCrossing(stile, player.coords) ?: return
        if (coords != crossing.start) {
            // Ordinary loc reach stops beside the blocking model, not on its first step.
            forcedWalk(crossing.start, crossTiles = 1)
            if (coords != crossing.start) return
        }
        val start = crossing.start
        val destination = crossing.destination

        val angle = crossingAngle(start, destination)
        player.blockRunEnergyRestore(ClimbTicks)
        exactMove(start, destination, ClimbStartDelay, ClimbEndDelay, angle, TeleportType.Exempt)
        resetAnim()
        anim(ClimbAnim, delay = ClimbStartDelay)
        worldRepo.locMerge(
            stile,
            player,
            start = ClimbStartDelay,
            end = ClimbEndDelay,
            maxX = stile.adjustedWidth - 1,
            maxZ = stile.adjustedLength - 1,
        )
        var completed = false
        try {
            delay(ClimbTicks)
            resetAnim()
            forcedWalk(crossing.exit, crossTiles = 1)
            completed = true
        } finally {
            if (!completed) resetAnim()
            player.resetPendingFaceSquare()
        }
    }

    private fun crossingAngle(from: CoordGrid, to: CoordGrid): Int =
        when {
            to.z > from.z -> constants.em_face_north
            to.z < from.z -> constants.em_face_south
            to.x > from.x -> constants.em_face_east
            else -> constants.em_face_west
        }

    private companion object {
        private val Stiles =
            listOf(
                "loc.qip_sheep_shearer_fullstyle",
                "loc.fullstyle",
                "loc.death_fullstyle",
                "loc.farming_style",
                "loc.brain_farm_fullstyle",
            )

        // Match the Falador crumbling wall; this sequence includes its climbing sounds.
        private const val ClimbAnim = "seq.human_walk_style"

        private const val ClimbStartDelay = 30
        private const val ClimbEndDelay = 94
        private const val ClimbTicks = 4
    }
}

internal data class StileCrossing(val start: CoordGrid, val destination: CoordGrid) {
    val exit: CoordGrid
        get() = destination.translate(
            (destination.x - start.x).compareTo(0),
            (destination.z - start.z).compareTo(0),
        )
}

internal fun stileCrossing(stile: BoundLocInfo, from: CoordGrid): StileCrossing? {
    if (from.level != stile.level) return null
    val near = stile.coords
    val far = near.translate(stile.adjustedWidth - 1, stile.adjustedLength - 1)
    if (near == far) return null
    fun distance(tile: CoordGrid) = kotlin.math.abs(from.x - tile.x) + kotlin.math.abs(from.z - tile.z)
    val start = if (distance(near) <= distance(far)) near else far
    if (from.chebyshevDistance(start) > 1) return null
    return StileCrossing(start, if (start == near) far else near)
}
