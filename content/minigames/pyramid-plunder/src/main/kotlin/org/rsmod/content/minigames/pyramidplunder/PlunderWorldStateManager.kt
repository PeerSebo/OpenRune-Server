package org.rsmod.content.minigames.pyramidplunder

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderRooms
import org.rsmod.game.MapClock

@Singleton
class PlunderWorldStateManager @Inject constructor(private val mapClock: MapClock) {
    private var doorChamber: IntArray = IntArray(4) { it }

    private var nextOuterDoorRotationCycle: Int = 0

    private var correctTombDoors: IntArray = IntArray(PlunderRooms.ALL.size) { 1 }

    private var random: GameRandom? = null

    fun bind(random: GameRandom) {
        this.random = random
        if (nextOuterDoorRotationCycle == 0) {
            rerollOuterDoors()
        }
    }

    fun tick() {
        if (nextOuterDoorRotationCycle != 0 && mapClock.cycle >= nextOuterDoorRotationCycle) {
            rerollOuterDoors()
        }
    }

    fun outerDoorChamber(direction: Int): Int = doorChamber[direction - 1]

    val correctOuterDoor: Int
        get() = doorChamber.indexOf(PlunderLocs.GUARDIAN_CHAMBER) + 1

    fun correctTombDoor(room: Int): Int = correctTombDoors.getOrElse(room - 1) { 1 }

    fun rerollTombDoors() {
        val rng = random ?: return
        correctTombDoors = IntArray(PlunderRooms.ALL.size) { rng.of(1, 4) }
    }

    private fun rerollOuterDoors() {
        val rng = random
        if (rng != null) {
            val perm = IntArray(4) { it }
            for (i in perm.lastIndex downTo 1) {
                val j = rng.of(i + 1)
                val tmp = perm[i]
                perm[i] = perm[j]
                perm[j] = tmp
            }
            doorChamber = perm
        }
        nextOuterDoorRotationCycle = mapClock.cycle + ROTATION_CYCLES
    }

    private companion object {
        const val ROTATION_CYCLES = 2500
    }
}
