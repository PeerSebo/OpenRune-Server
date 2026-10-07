package org.rsmod.content.minigames.gotr

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.map.CoordGrid

@Singleton
public class GotrPortalPresentation
@Inject
constructor(private val locRepo: LocRepository, private val worldRepo: WorldRepository) {
    private var active: Set<String> = emptySet()
    private var nextPulse = Int.MIN_VALUE

    public fun sync(elementalRune: String?, catalyticRune: String?, cycle: Int) {
        val next = listOfNotNull(GotrHudState.normalize(elementalRune), GotrHudState.normalize(catalyticRune))
            .filterTo(linkedSetOf()) { it in PLACEMENTS }
        if (next != active || cycle >= nextPulse) {
            // Zone animations are transient; returning from an altar needs the closed states too.
            for (rune in PLACEMENTS.keys) animate(rune, enabled = rune in next)
            nextPulse = cycle + 3
        }
        active = next
    }

    private fun animate(rune: String, enabled: Boolean) {
        val coords = PLACEMENTS.getValue(rune)
        val id = "loc.gotr_portal_$rune".asRSCM(RSCMType.LOC)
        val loc = locRepo.findAll(coords).firstOrNull { it.id == id } ?: return
        worldRepo.locAnim(loc, INACTIVE_ANIM)
        if (enabled) worldRepo.locAnim(loc, ACTIVE_ANIM)
    }

    internal companion object {
        const val INACTIVE_ANIM = "seq.statue_runestone01_inactive"
        const val ACTIVE_ANIM = "seq.statue_runestone01_active"

        // Use packed map shapes/rotations; four diagonal statues use shape 11.
        val PLACEMENTS = mapOf(
            "air" to CoordGrid(3617, 9494),
            "water" to CoordGrid(3623, 9500),
            "earth" to CoordGrid(3623, 9505),
            "fire" to CoordGrid(3617, 9511),
            "mind" to CoordGrid(3612, 9494),
            "body" to CoordGrid(3608, 9496),
            "cosmic" to CoordGrid(3621, 9496),
            "chaos" to CoordGrid(3606, 9500),
            "nature" to CoordGrid(3621, 9509),
            "law" to CoordGrid(3608, 9509),
            "death" to CoordGrid(3606, 9505),
            "blood" to CoordGrid(3612, 9511),
        )
    }
}
