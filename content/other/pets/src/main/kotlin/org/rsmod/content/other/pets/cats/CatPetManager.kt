package org.rsmod.content.other.pets.cats

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class CatPetManager
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
) {

    fun following(player: Player): Npc? = player.attr[PET_NPC]

    fun followingPet(player: Player): CatPet? {
        CatPetState.migrate(player)
        return CatPet.fromObj(player.catFollowingObj)
    }

    fun hasFollower(player: Player): Boolean = following(player) != null

    fun isOwnedBy(npc: Npc, player: Player): Boolean =
        npc.isSpawnOwnedBy(player) && following(player) === npc

    fun spawn(player: Player, pet: CatPet): Npc {
        CatPetState.migrate(player)
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
        player.catFollowingObj = pet.objId
        player.softTimer(TICK_TIMER, 1)
        return npc
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

    fun despawn(player: Player) {
        following(player)?.let { npc ->
            remove(npc)
            clearFollowerVarp(player)
        }
        player.attr.remove(PET_NPC)
        player.clearSoftTimer(TICK_TIMER)
        CatPetState.clearSession(player)
    }

    // Follower options require follower_npc: type ID in the high 16 bits, slot index in the low 16 bits.
    private fun setFollowerVarp(player: Player, npc: Npc) {
        val packed = (npc.visType.id shl 16) or (npc.slotId and 0xFFFF)
        VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, packed)
        // Login spawns precede the first processed cycle, so the next tick must transmit again.
        player.catResyncFollower = true
    }

    private fun clearFollowerVarp(player: Player) {
        VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, NO_FOLLOWER)
    }

    fun release(player: Player) {
        despawn(player)
        CatPetState.clear(player)
    }

    fun runAway(player: Player) {
        val npc = following(player)
        if (npc == null) {
            release(player)
            return
        }
        clearFollowerVarp(player)
        player.attr.remove(PET_NPC)
        player.clearSoftTimer(TICK_TIMER)
        CatPetState.clear(player)

        npc.mode = NpcMode.None
        npc.resetFaceEntity()
        val away = Direction.CARDINAL[mapClock.cycle % Direction.CARDINAL.size]
        npc.walk(npc.coords.translate(away.xOff * RUN_AWAY_DISTANCE, away.zOff * RUN_AWAY_DISTANCE))
        npc.lifecycleDelCycle = mapClock.cycle + RUN_AWAY_CYCLES
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
        if (player.catResyncFollower) {
            player.catResyncFollower = false
            player.resyncVar(FOLLOWER_VARP)
        }
        if (!player.catChasing) {
            ensureFollowing(npc, player)
        }
        if (tickGrowth(player, pet)) {
            // The cat was replaced by its grown form; `npc` and `pet` are stale from here on.
            return
        }
        if (pet.isKitten) {
            tickHunger(player, npc)
            if (following(player) !== npc) return
            tickAttention(player, npc)
            if (following(player) !== npc) return
        }
        tickAmbientNoise(player, npc, pet)
    }

    private fun tickAmbientNoise(player: Player, npc: Npc, pet: CatPet) {
        if (random.of(maxExclusive = AMBIENT_NOISE_CHANCE) != 0) {
            return
        }
        if (random.of(maxExclusive = 3) == 0) {
            npc.say("Purr...purr...")
            player.soundSynth("synth.PURR")
        } else {
            npc.say(if (pet.isKitten) "Meow!" else "Miaow!")
            player.soundSynth(if (pet.isKitten) "synth.KITTENS_MEW" else "synth.MEEOOW")
        }
    }

    private fun ensureFollowing(npc: Npc, player: Player) {
        npc.facePlayer(player)
        npc.mode = NpcMode.None
    }

    private fun tickGrowth(player: Player, pet: CatPet): Boolean {
        val threshold = pet.stage.growthCycles ?: return false
        val grown = pet.grown() ?: return false
        val growth = player.catGrowth + 1
        if (growth < threshold) {
            player.catGrowth = growth
            return false
        }
        val grownNpc = spawn(player, grown)
        CatPetState.resetCounters(player)
        val message =
            when (grown.stage) {
                CatStage.CAT -> "Your kitten has grown into a healthy cat that can hunt for itself."
                CatStage.OVERGROWN ->
                    "Your cat has grown into a mighty feline, but it will no longer be able to " +
                        "chase vermin."
                else -> "Your cat has grown."
            }
        player.mes(message)
        grownNpc.say("Meeoow!")
        player.soundSynth("synth.MEEOOW")
        player.soundSynth("synth.PURR", delay = GROWTH_PURR_DELAY)
        return true
    }

    fun forceGrowth(player: Player): Boolean {
        val pet = followingPet(player) ?: return false
        val threshold = pet.stage.growthCycles ?: return false
        player.catGrowth = threshold - 1
        return true
    }

    fun status(player: Player): String {
        val pet = followingPet(player) ?: return "No cat is following you."
        val growth = player.catGrowth
        val threshold = pet.stage.growthCycles
        val growthText =
            if (threshold == null) "fully grown" else "growth $growth/$threshold cycles"
        val kittenText =
            if (pet.isKitten) {
                ", hunger ${player.catHunger}/$HUNGER_RUN" +
                    ", attention ${player.catAttention}/$ATTENTION_RUN"
            } else {
                ""
            }
        return "${pet.stage.name.lowercase()} (${pet.colour.name.lowercase()}): $growthText$kittenText"
    }

    private fun tickHunger(player: Player, npc: Npc) {
        val hunger = player.catHunger + 1
        player.catHunger = hunger
        when (hunger) {
            HUNGER_WARN -> player.mes("Your kitten is hungry.")
            HUNGER_CRY -> {
                npc.say("Meeeooowww!")
                player.soundSynth("synth.SAD_MEEOOW")
                player.mes("Your kitten is very hungry. Feed it some fish or milk before it runs away.")
            }
            HUNGER_RUN -> {
                npc.say("Meeeooowww!")
                player.soundSynth("synth.SAD_MEEOOW")
                player.mes("Your kitten has run away because you didn't feed it.")
                runAway(player)
            }
        }
    }

    private fun tickAttention(player: Player, npc: Npc) {
        val attention = player.catAttention + 1
        player.catAttention = attention
        when (attention) {
            ATTENTION_WARN -> player.mes("Your kitten wants some attention.")
            ATTENTION_CRY -> {
                npc.say("Meeeooowww...")
                player.soundSynth("synth.SAD_MEEOOW")
                player.mes("Your kitten is lonely. Stroke it or give it a ball of wool.")
            }
            ATTENTION_RUN -> {
                npc.say("Meeeooowww...")
                player.soundSynth("synth.SAD_MEEOOW")
                player.mes("Your kitten has run away because it felt neglected.")
                runAway(player)
            }
        }
    }

    fun isHungry(player: Player): Boolean {
        CatPetState.migrate(player)
        return player.catHunger >= HUNGER_WARN
    }

    fun isLonely(player: Player): Boolean {
        CatPetState.migrate(player)
        return player.catAttention >= ATTENTION_WARN
    }

    fun growthPercent(player: Player, pet: CatPet): Int {
        CatPetState.migrate(player)
        val threshold = pet.stage.growthCycles ?: return 100
        return (player.catGrowth * 100 / threshold).coerceIn(0, 100)
    }

    fun feed(player: Player) {
        CatPetState.migrate(player)
        player.catHunger = 0
    }

    fun stroke(player: Player) {
        CatPetState.migrate(player)
        val attention = player.catAttention
        player.catAttention = if (attention <= STROKE_ATTENTION) 0 else STROKE_ATTENTION
    }

    fun play(player: Player) {
        CatPetState.migrate(player)
        player.catAttention = WOOL_ATTENTION
    }

    fun setChasing(player: Player, chasing: Boolean) {
        player.catChasing = chasing
        if (!chasing) {
            following(player)?.let { npc ->
                npc.abortRoute()
                npc.resetPendingFaceSquare()
                npc.resetAnim()
                ensureFollowing(npc, player)
            }
        }
    }

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    companion object {
        const val TICK_TIMER: String = "timer.pet_cat_tick"

        private const val FOLLOWER_VARP: String = "varp.follower_npc"
        private const val NO_FOLLOWER: Int = -1

        private const val MINUTE: Int = 100

        const val HUNGER_WARN: Int = 24 * MINUTE
        const val HUNGER_CRY: Int = 27 * MINUTE
        const val HUNGER_RUN: Int = 30 * MINUTE

        const val ATTENTION_WARN: Int = 25 * MINUTE
        const val ATTENTION_CRY: Int = 32 * MINUTE
        const val ATTENTION_RUN: Int = 39 * MINUTE

        private const val STROKE_ATTENTION: Int = ATTENTION_WARN - 18 * MINUTE

        private const val WOOL_ATTENTION: Int = ATTENTION_WARN - 51 * MINUTE

        private const val GROWTH_PURR_DELAY: Int = 50

        private const val AMBIENT_NOISE_CHANCE: Int = 500

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

        private const val RUN_AWAY_DISTANCE: Int = 6
        private const val RUN_AWAY_CYCLES: Int = 6

        private val PET_NPC = AttributeKey<Npc>(temp = true)
    }
}
