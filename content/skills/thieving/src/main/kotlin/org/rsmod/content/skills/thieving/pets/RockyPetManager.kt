package org.rsmod.content.skills.thieving.pets

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.skills.thieving.pets.RockyPetState.followingRocky
import org.rsmod.content.skills.thieving.pets.RockyPetState.resyncRockyFollower
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class RockyPetManager
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
    private val collision: CollisionFlagMap,
) {
    fun following(player: Player): Npc? = player.attr[PET_NPC]

    fun followingPet(player: Player): RockyPet? = player.followingRocky

    fun isOwnedBy(npc: Npc, player: Player): Boolean =
        npc.isSpawnOwnedBy(player) && following(player) === npc

    fun hasAnyFollower(player: Player): Boolean =
        following(player) != null || player.vars[FOLLOWER_VARP] > 0

    fun roll(access: ProtectedAccess, base: Int) {
        val player = access.player
        val chance = RockyPetRates.denominator(base, player.statBase(THIEVING))
        if (access.random.of(maxExclusive = chance) != 0) {
            return
        }
        if (followingPet(player) != null) {
            return
        }
        if (!hasAnyFollower(player)) {
            spawn(player, RockyPet.RACCOON)
            access.mes("You have a funny feeling like you're being followed.")
            return
        }
        if (access.inv.hasFreeSpace()) {
            access.invAdd(access.inv, RockyPet.RACCOON.obj, 1)
            access.mes("You feel something weird sneaking into your backpack.")
        }
    }

    fun spawn(player: Player, pet: RockyPet): Npc {
        following(player)?.let { remove(it) }
        val npc = Npc(pet.npc, spawnTile(player))
        npc.mode = NpcMode.None
        npcRepo.add(npc, Int.MAX_VALUE)
        // `add` with a permanent duration flags the npc as respawning; a pet never does.
        npc.respawns = false
        npc.assignSpawnOwner(player, mapClock.cycle)
        npc.facePlayer(player)
        setFollowerVarp(player, npc)

        player.attr[PET_NPC] = npc
        player.followingRocky = pet
        player.softTimer(TICK_TIMER, 1)
        return npc
    }

    fun transform(player: Player, pet: RockyPet): Npc = spawn(player, pet)

    fun despawn(player: Player) {
        player.resyncRockyFollower = false
        val npc = following(player) ?: return
        remove(npc)
        clearFollowerVarp(player)
        player.attr.remove(PET_NPC)
        player.clearSoftTimer(TICK_TIMER)
    }

    fun release(player: Player) {
        despawn(player)
        player.followingRocky = null
    }

    fun tick(player: Player) {
        val pet = followingPet(player)
        val npc = following(player)
        if (pet == null || npc == null) {
            despawn(player)
            return
        }
        if (!npc.isSlotAssigned) {
            spawn(player, pet)
            return
        }
        if (player.resyncRockyFollower) {
            player.resyncRockyFollower = false
            player.resyncVar(FOLLOWER_VARP)
        }
        if (!npc.isFacingPlayer) {
            npc.facePlayer(player)
        }
        npc.mode = NpcMode.None
    }

    private fun spawnTile(player: Player): CoordGrid {
        val origin = player.coords
        for (direction in SPAWN_DIRECTIONS) {
            val tile = origin.translate(direction.xOff, direction.zOff)
            if (collision.isZoneValid(tile) && !collision.isWalkBlocked(tile)) {
                return tile
            }
        }
        return origin
    }

    // Follower options require follower_npc with type ID in the high 16 bits and slot in the low 16 bits.
    private fun setFollowerVarp(player: Player, npc: Npc) {
        val packed = (npc.visType.id shl 16) or (npc.slotId and 0xFFFF)
        VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, packed)
        // Login setters do not transmit yet; resend follower state on the first processed tick.
        player.resyncRockyFollower = true
    }

    private fun clearFollowerVarp(player: Player) {
        VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, NO_FOLLOWER)
    }

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    companion object {
        const val TICK_TIMER: String = "timer.pet_rocky_tick"

        private const val THIEVING: String = "stat.thieving"

        private const val FOLLOWER_VARP: String = "varp.follower_npc"
        private const val NO_FOLLOWER: Int = -1

        private val SPAWN_DIRECTIONS: List<Direction> =
            listOf(
                Direction.South,
                Direction.West,
                Direction.East,
                Direction.North,
                Direction.SouthWest,
                Direction.SouthEast,
                Direction.NorthWest,
                Direction.NorthEast,
            )

        private val PET_NPC = AttributeKey<Npc>(temp = true)
    }
}
