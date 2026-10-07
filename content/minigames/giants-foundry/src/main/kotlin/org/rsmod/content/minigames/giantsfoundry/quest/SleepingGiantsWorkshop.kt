package org.rsmod.content.minigames.giantsfoundry.quest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class SleepingGiantsWorkshop @Inject constructor(
    private val regions: RegionRepository,
    private val locs: LocRepository,
    private val npcs: NpcRepository,
    private val collision: CollisionFlagMap,
) {
    private data class Workshop(val region: Region, val kovac: Npc)
    private val workshops = IdentityHashMap<Player, Workshop>()

    fun contains(player: Player): Boolean {
        val region = workshops[player]?.region ?: return false
        return player.coords.x in region.southWest.x..region.northEast.x &&
            player.coords.z in region.southWest.z..region.northEast.z
    }

    internal fun map(player: Player, coords: CoordGrid): CoordGrid =
        checkNotNull(workshops[player]).region.normal[coords]

    internal fun showCutsceneScenery(player: Player): () -> Unit {
        val region = checkNotNull(workshops[player]).region
        val originals = mutableListOf<LocInfo>()
        val temporary = mutableListOf<LocInfo>()
        val restore = {
            if (regions.isValid(region)) {
                temporary.forEach { locs.del(it, Int.MAX_VALUE) }
                originals.forEach { locs.add(it, Int.MAX_VALUE) }
            }
            Unit
        }
        try {
            for (entry in SleepingGiantsData.scenery) for (source in entry.coords) {
                val origin = region.normal[source.copy(level = entry.originalLevel)]
                val original = checkNotNull(locs.findAll(origin).firstOrNull { it.id == entry.original.asRSCM() }) {
                    "Missing ${entry.original} at $origin in Sleeping Giants workshop."
                }
                originals += original
                check(locs.del(original, Int.MAX_VALUE))
                temporary += locs.add(region.normal[source], entry.cutscene, Int.MAX_VALUE,
                    original.angle, original.shape)
            }
        } catch (failure: Throwable) {
            restore()
            throw failure
        }
        return restore
    }

    fun enter(access: ProtectedAccess): Npc? {
        val player = access.player
        workshops[player]?.takeIf { regions.isValid(it.region) }?.let {
            access.teleport(it.region.normal[ENTRANCE])
            return it.kovac
        }
        val region = regions.add(TEMPLATE) ?: run {
            access.mes("The workshop is unavailable at the moment. Please try again shortly.")
            return null
        }
        regions.protect(region)
        try {
            for (repair in SleepingGiantsData.repairs) {
                val coords = region.normal[repair.coords]
                val old = checkNotNull(locs.findAll(coords).firstOrNull { it.id == repair.original.asRSCM() })
                locs.change(old, checkNotNull(ServerCacheManager.getObject(repair.multi.asRSCM())), Int.MAX_VALUE)
            }
            val kovac = Npc("npc.giants_foundry_kovac_quest", region.normal[KOVAC])
            npcs.add(kovac, Int.MAX_VALUE)
            kovac.noneMode()
            workshops[player] = Workshop(region, kovac)
            player.loginExitCoord = OUTSIDE
            access.teleport(region.normal[ENTRANCE])
            return kovac
        } catch (failure: Exception) {
            regions.unprotect(region)
            throw failure
        }
    }

    internal fun enterPublicWorkshop(player: Player) {
        val region = workshops[player]?.region ?: return
        if (!contains(region, player.coords)) {
            leave(player)
            return
        }
        val origin = ENTRANCE.copy(level = player.coords.level)
        val mapped = region.normal[origin]
        leave(player, origin.translate(player.coords.x - mapped.x, player.coords.z - mapped.z))
    }

    fun leave(player: Player, destination: CoordGrid = OUTSIDE) {
        val workshop = workshops.remove(player) ?: return
        if (contains(workshop.region, player.coords)) {
            PathingEntityCommon.telejump(player, collision, destination)
        }
        player.loginExitCoord = null
        if (workshop.kovac.isSlotAssigned) npcs.del(workshop.kovac, Int.MAX_VALUE)
        regions.unprotect(workshop.region)
    }

    private fun contains(region: Region, coords: CoordGrid) =
        coords.x in region.southWest.x..region.northEast.x &&
            coords.z in region.southWest.z..region.northEast.z

    companion object {
        val OUTSIDE = CoordGrid(3361, 3149, 0)
        val ENTRANCE = CoordGrid(3366, 11483, 0)
        private val KOVAC = CoordGrid(3368, 11483, 0)
        private val TEMPLATE = RegionTemplate.create {
            copyAllLevels(416, 1432) {
                regionZoneX = 4
                regionZoneZ = 4
                zoneWidth = 8
                zoneLength = 8
            }
        }
    }
}
