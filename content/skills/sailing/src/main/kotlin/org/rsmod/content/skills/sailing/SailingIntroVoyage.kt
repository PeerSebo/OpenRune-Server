package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

@Singleton
class SailingIntroVoyage @Inject internal constructor(
    private val boats: BoatWorldEntityService,
    private val navigation: SailingNavigationService,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val world: WorldRepository,
    private val clock: MapClock,
) {
    private val sessions = mutableMapOf<Player, Session>()

    fun entity(player: Player): WorldEntity? =
        boats.boarded(player)?.takeIf { boats.isTutorial(player) && player.coords in it }

    fun aboardOwnedBoat(player: Player): Boolean =
        boats.boarded(player)?.let { !boats.isTutorial(player) && player.coords in it } == true

    fun board(access: ProtectedAccess, atWreck: Boolean): Boolean {
        val player = access.player
        if (boats.boarded(player) != null) return false
        val entity = boats.spawnTutorial(player, template, if (atWreck) WRECK_BERTH else SARIM_BERTH)
            ?: return false
        val placed = fittings.associate { fitting ->
            fitting.symbol to
            locs.add(
                CoordGrid(entity.instanceSouthWest.x + fitting.x, entity.instanceSouthWest.z + fitting.z, fitting.level),
                fitting.symbol, Int.MAX_VALUE, LocAngle[fitting.angle], LocShape[fitting.shape],
            )
        }
        val arrival = tile(entity, 4, 4)
        access.telejump(arrival)
        if (player.coords != arrival) {
            boats.despawn(player)
            return false
        }
        player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ABOARD
        player.boardedBoat = 1
        player.boardedBoatType = 1
        player.boardedBoatWorld = entity.slotId
        player.sailingLastDock = SailingDocks.PORT_SARIM.row.dockId
        clearActors(player)
        val actors = listOf(
            actor(player, "npc.sailing_intro_will_boat", tile(entity, 3, 2)),
            actor(player, "npc.sailing_intro_anne_boat", tile(entity, 4, 3)),
        )
        sessions[player] = Session(actors, placed.getValue("loc.sailing_boat_mast_intro"), placed.getValue("loc.sailing_boat_sails_intro"))
        navigation.registerTutorial(player, entity)
        return true
    }

    fun takeHelm(access: ProtectedAccess) = navigation.navigate(access)

    fun setSails(player: Player, raised: Boolean) = navigation.setSails(player, raised)

    fun trim(player: Player) = navigation.trim(player)

    fun halt(player: Player) {
        navigation.halt(player)
        navigation.releaseHelm(player)
    }

    fun reachedWreck(player: Player): Boolean =
        entity(player)?.rootCoords?.chebyshevDistance(WRECK_BERTH)?.let { it <= 4 } == true

    fun players(): List<Player> = sessions.keys.toList()

    fun land(access: ProtectedAccess, destination: CoordGrid): Boolean {
        val player = access.player
        halt(player)
        access.telejump(destination)
        if (player.coords != destination) return false
        clearActors(player)
        boats.despawn(player)
        player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ASHORE
        player.boardedBoat = 0
        player.boardedBoatWorld = 0
        return true
    }

    fun sweep() {
        for (player in sessions.keys.toList()) {
            if (!player.isSlotAssigned || entity(player) == null) {
                clearActors(player)
                continue
            }
            val session = sessions.getValue(player)
            val pose = when (player.readSailingVar(SailingNavigationService.VAR_MOVE_MODE)) {
                SailingMoveMode.Stopped.value -> "down"
                SailingMoveMode.Slow.value -> "half"
                else -> "full"
            }
            if (session.pose != pose) {
                animateSail(session, "${session.pose}_to_$pose")
                session.pose = pose
                session.loopCycle = clock.cycle + 1
            } else if (session.loopCycle != null && clock.cycle >= session.loopCycle!!) {
                animateSail(session, pose)
                session.loopCycle = null
            }
        }
    }

    fun clearActors(player: Player) {
        sessions.remove(player)?.actors?.forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
    }

    private fun animateSail(session: Session, pose: String) {
        world.locAnim(session.mast, "seq.sailing_boat_sail_kandarin_2x5_$pose")
        world.locAnim(session.sail, "seq.sailing_boat_sail_kandarin_2x5_${pose}_offset")
    }

    private fun actor(player: Player, symbol: String, coords: CoordGrid): Npc =
        Npc(symbol, coords).also {
            npcs.add(it, Int.MAX_VALUE)
            it.respawns = false
            it.noneMode()
            it.assignSpawnOwner(player, clock.cycle)
        }

    companion object {
        val SARIM_BERTH = CoordGrid(3054, 3193, 0)
        val WRECK_BERTH = CoordGrid(3031, 3047, 0)

        fun tile(entity: WorldEntity, x: Int, z: Int): CoordGrid =
            CoordGrid(entity.instanceSouthWest.x + x, entity.instanceSouthWest.z + z, 1)

        private val template = RegionTemplate.create {
            for (level in 0..3) this[0, 0, level] = ZoneKey(480, 800, level)
        }

        private val fittings = listOf(
            Fitting("loc.sailing_keel_kandarin_2x5_bronze", 2, 1, 0),
            Fitting("loc.sailing_boat_skiff_trim_wooden", 1, 1, 0),
            Fitting("loc.sailing_intro_navigating", 4, 6, 1),
            Fitting("loc.sailing_boat_mast_intro", 4, 4, 1),
            Fitting("loc.sailing_boat_sails_intro", 4, 5, 1),
            Fitting("loc.sailing_intro_salvaging_hook", 3, 2, 1, angle = 1),
            Fitting("loc.randomsound_ardent_ocean_gulls", 5, 3, 1, shape = 22),
            Fitting("loc.bgsound_sailing_ocean_water_loop_01", 2, 4, 1, shape = 22),
            Fitting("loc.randomsound_ardent_ocean_crashing_waves", 5, 4, 1, shape = 22),
        )
    }

    private data class Fitting(
        val symbol: String, val x: Int, val z: Int, val level: Int,
        val angle: Int = 0, val shape: Int = 10,
    )

    private class Session(val actors: List<Npc>, val mast: LocInfo, val sail: LocInfo) {
        var pose = "down"
        var loopCycle: Int? = null
    }
}
