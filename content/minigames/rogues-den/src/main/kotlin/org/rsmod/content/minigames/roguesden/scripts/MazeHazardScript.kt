package org.rsmod.content.minigames.roguesden.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants as constants
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerSoftQueue
import org.rsmod.content.minigames.roguesden.configs.RoguesDenExit
import org.rsmod.content.minigames.roguesden.configs.RoguesDenMaze
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.disarmedTrap
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.inRoguesDenMaze
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.get
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class MazeHazardScript
@Inject
constructor(private val locRepo: LocRepository, private val collision: CollisionFlagMap) :
    PluginScript() {

    private var instantEjectTiles: Set<CoordGrid> = emptySet()
    private var autoJumpHazards: Map<CoordGrid, AutoJumpHazard> = emptyMap()
    private var pendulumJumps: Map<CoordGrid, PendulumJump> = emptyMap()

    private var searchableTrapTiles: Map<CoordGrid, CoordGrid> = emptyMap()

    override fun ScriptContext.startup() {
        onGameStartup { buildGeometry() }

        onPlayerSoftQueue(RoguesDenMaze.PENDULUM_JUMP_QUEUE) { player.resetAnim() }

        onPlayerCoordsChanged {
            if (lastKnownCoords == player.coords || !player.inRoguesDenMaze) {
                return@onPlayerCoordsChanged
            }
            checkHazards(player)
        }
    }

    private fun checkHazards(player: Player) {
        val here = player.coords

        if (here in instantEjectTiles) {
            eject(player, "You're caught by the spinning blades and thrown from the maze!")
            return
        }

        val trapAnchor = searchableTrapTiles[here]
        if (trapAnchor != null && !player.disarmedTrap(trapAnchor)) {
            eject(player, RoguesDenMaze.HAZARD_EJECT_MESSAGE)
            return
        }

        val pendulumJump = pendulumJumps[here]
        if (pendulumJump != null) {
            crossPendulum(player, here, pendulumJump)
            return
        }

        // Only end-of-tick occupancy triggers hazards; this preserves the run-through trick.
        val autoJump = autoJumpHazards[here] ?: return
        player.statSub(autoJump.stat, autoJump.cost, 0)
        if (autoJump.knockdown) {
            player.abortRoute()
            player.clearMapFlag()
        }
    }

    private fun eject(player: Player, message: String) {
        RoguesDenExit.restoreAndLeaveMazeState(player, message)
        PathingEntityCommon.telejump(player, collision, RoguesDenExit.LOBBY_LANDING)
    }

    private fun crossPendulum(player: Player, from: CoordGrid, jump: PendulumJump) {
        if (player.stat(RoguesDenMaze.AGILITY_STAT) < RoguesDenMaze.PENDULUM_COST) {
            eject(player, RoguesDenMaze.HAZARD_EJECT_MESSAGE)
            return
        }
        player.statSub(RoguesDenMaze.AGILITY_STAT, RoguesDenMaze.PENDULUM_COST, 0)
        player.anim(RoguesDenMaze.PENDULUM_JUMP_ANIM)
        player.soundSynth(RoguesDenMaze.PENDULUM_JUMP_SOUND)
        PathingEntityCommon.exactMove(
            player,
            from,
            jump.dest,
            EXACT_MOVE_START_CC,
            EXACT_MOVE_END_CC,
            jump.faceDir,
            collision,
        )
        player.softQueue(RoguesDenMaze.PENDULUM_JUMP_QUEUE, JUMP_ANIM_RESET_TICKS)
        player.clearMapFlag()
    }

    private fun buildGeometry() {
        val ejectTiles = mutableSetOf<CoordGrid>()
        val autoJump = mutableMapOf<CoordGrid, AutoJumpHazard>()
        val pendulums = mutableMapOf<CoordGrid, PendulumJump>()
        val searchTiles = mutableMapOf<CoordGrid, CoordGrid>()

        val scytheBladeId = RoguesDenMaze.TRAP_FLOOR_SCYTHEBLADE.asRSCM(RSCMType.LOC)
        val pendulumId = RoguesDenMaze.TRAP_FLOOR_PENDULUM.asRSCM(RSCMType.LOC)
        val floorBladeId = RoguesDenMaze.TRAP_FLOOR_TIMEDBLADE.asRSCM(RSCMType.LOC)
        val wallBladeId = RoguesDenMaze.TRAP_WALL_SCYTHEBLADE.asRSCM(RSCMType.LOC)
        val wallSawId = RoguesDenMaze.TRAP_WALL_SAW.asRSCM(RSCMType.LOC)
        val searchableIds = RoguesDenMaze.SEARCHABLE_TRAPS.map { it.asRSCM(RSCMType.LOC) }.toSet()

        val minZoneX = MIN_X / ZoneGrid.LENGTH
        val maxZoneX = MAX_X / ZoneGrid.LENGTH
        val minZoneZ = MIN_Z / ZoneGrid.LENGTH
        val maxZoneZ = MAX_Z / ZoneGrid.LENGTH

        for (level in 0..3) {
            for (zoneX in minZoneX..maxZoneX) {
                for (zoneZ in minZoneZ..maxZoneZ) {
                    val zone = ZoneKey(zoneX, zoneZ, level)
                    for (loc in locRepo.findAll(zone)) {
                        val id = loc.id
                        when {
                            id == scytheBladeId -> {
                                val type = ServerCacheManager.getObject(id) ?: continue
                                ejectTiles.addAll(footprintTiles(BoundLocInfo(loc, type)))
                            }
                            id == pendulumId -> {
                                val type = ServerCacheManager.getObject(id) ?: continue
                                registerPendulumLanes(pendulums, BoundLocInfo(loc, type))
                            }
                            id == floorBladeId -> {
                                val type = ServerCacheManager.getObject(id) ?: continue
                                val hazard =
                                    AutoJumpHazard(
                                        RoguesDenMaze.AGILITY_STAT,
                                        RoguesDenMaze.FLOOR_BLADE_COST,
                                        knockdown = true,
                                    )
                                footprintTiles(BoundLocInfo(loc, type)).forEach { autoJump[it] = hazard }
                            }
                            id == wallBladeId || id == wallSawId -> {
                                val type = ServerCacheManager.getObject(id) ?: continue
                                val hazard =
                                    AutoJumpHazard(
                                        RoguesDenMaze.AGILITY_STAT,
                                        RoguesDenMaze.WALL_BLADE_SAW_COST,
                                        knockdown = false,
                                    )
                                footprintTiles(BoundLocInfo(loc, type)).forEach { autoJump[it] = hazard }
                            }
                            id in searchableIds -> {
                                // Do not eject from adjacent search-reach tiles, or disarming becomes impossible.
                                searchTiles[loc.coords] = loc.coords
                            }
                        }
                    }
                }
            }
        }

        instantEjectTiles = ejectTiles
        autoJumpHazards = autoJump
        pendulumJumps = pendulums
        searchableTrapTiles = searchTiles
    }

    // Derive pendulum lanes from wall collision, not rotation alone; both edge tiles must be walkable.
    private fun registerPendulumLanes(out: MutableMap<CoordGrid, PendulumJump>, loc: BoundLocInfo) {
        val width = loc.adjustedWidth
        val length = loc.adjustedLength
        if (width != 3 || length != 3) {
            return
        }
        for (i in 0 until 3) {
            val south = loc.coords.translate(i, 0)
            val northMid = loc.coords.translate(i, 1)
            val north = loc.coords.translate(i, 2)
            if (wallSealedLane(south, northMid, north, CollisionFlag.WALL_NORTH, CollisionFlag.WALL_SOUTH)) {
                out[south] = PendulumJump(dest = north, faceDir = constants.em_face_north)
                out[north] = PendulumJump(dest = south, faceDir = constants.em_face_south)
            }
            val west = loc.coords.translate(0, i)
            val eastMid = loc.coords.translate(1, i)
            val east = loc.coords.translate(2, i)
            if (wallSealedLane(west, eastMid, east, CollisionFlag.WALL_EAST, CollisionFlag.WALL_WEST)) {
                out[west] = PendulumJump(dest = east, faceDir = constants.em_face_east)
                out[east] = PendulumJump(dest = west, faceDir = constants.em_face_west)
            }
        }
    }

    // Collision writes both sides of a wall; either matching edge proves the sealed middle.
    private fun wallSealedLane(
        from: CoordGrid,
        mid: CoordGrid,
        to: CoordGrid,
        wallOnFrom: Int,
        wallOnMid: Int,
    ): Boolean {
        if (collision.isWalkBlocked(from) || collision.isWalkBlocked(to)) {
            return false
        }
        if (collision.isWalkBlocked(mid)) {
            return false
        }
        val sealedFromEdge = collision[from] and wallOnFrom != 0
        val sealedFromMid = collision[mid] and wallOnMid != 0
        return sealedFromEdge || sealedFromMid
    }

    private fun footprintTiles(loc: BoundLocInfo): List<CoordGrid> {
        val width = loc.adjustedWidth.coerceAtLeast(1)
        val length = loc.adjustedLength.coerceAtLeast(1)
        val tiles = ArrayList<CoordGrid>(width * length)
        for (dx in 0 until width) {
            for (dz in 0 until length) {
                tiles.add(loc.coords.translate(dx, dz))
            }
        }
        return tiles
    }

    private data class AutoJumpHazard(val stat: String, val cost: Int, val knockdown: Boolean)

    private data class PendulumJump(val dest: CoordGrid, val faceDir: Int)

    private companion object {
        const val MIN_X = 2944
        const val MAX_X = 3071
        const val MIN_Z = 4928
        const val MAX_Z = 5119

        const val EXACT_MOVE_START_CC = 0

        const val EXACT_MOVE_END_CC = 60

        const val JUMP_ANIM_RESET_TICKS = 2
    }
}
