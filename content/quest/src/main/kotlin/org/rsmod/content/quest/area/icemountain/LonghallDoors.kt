package org.rsmod.content.quest.area.icemountain

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LonghallDoors @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(ClosedLeft) { openDoors(it.loc, clickedLeft = true) }
        onOpLoc1(ClosedRight) { openDoors(it.loc, clickedLeft = false) }
        onOpLoc1(OpenLeft) { closeDoors(it.loc, clickedLeft = true) }
        onOpLoc1(OpenRight) { closeDoors(it.loc, clickedLeft = false) }
    }

    private fun ProtectedAccess.openDoors(clicked: BoundLocInfo, clickedLeft: Boolean) {
        soundSynth(OpenSound)

        val partner = findAdjacent(clicked, if (clickedLeft) ClosedRight else ClosedLeft)
        locRepo.del(clicked, Duration)
        partner?.let { locRepo.del(it, Duration) }

        locRepo.add(
            clicked.coords,
            if (clickedLeft) OpenLeft else OpenRight,
            Duration,
            clicked.turnAngle(rotations = if (clickedLeft) 3 else 1),
            clicked.shape,
        )
        partner?.let {
            locRepo.add(
                it.coords,
                if (clickedLeft) OpenRight else OpenLeft,
                Duration,
                it.turnAngle(rotations = if (clickedLeft) 1 else 3),
                it.shape,
            )
        }
    }

    private fun ProtectedAccess.closeDoors(clicked: BoundLocInfo, clickedLeft: Boolean) {
        soundSynth(CloseSound)

        val partner = findAdjacent(clicked, if (clickedLeft) OpenRight else OpenLeft)
        locRepo.del(clicked, Duration)
        partner?.let { locRepo.del(it, Duration) }

        locRepo.add(
            clicked.coords,
            if (clickedLeft) ClosedLeft else ClosedRight,
            Duration,
            clicked.turnAngle(rotations = if (clickedLeft) 1 else 3),
            clicked.shape,
        )
        partner?.let {
            locRepo.add(
                it.coords,
                if (clickedLeft) ClosedRight else ClosedLeft,
                Duration,
                it.turnAngle(rotations = if (clickedLeft) 3 else 1),
                it.shape,
            )
        }
    }

    private fun findAdjacent(loc: BoundLocInfo, partner: String): LocInfo? {
        val partnerId = partner.asRSCM(RSCMType.LOC)
        val neighbours =
            listOf(
                loc.coords.translateX(1),
                loc.coords.translateX(-1),
                loc.coords.translateZ(1),
                loc.coords.translateZ(-1),
            )
        return neighbours.firstNotNullOfOrNull { coords ->
            locRepo.findExact(coords, loc.shape)?.takeIf { it.id == partnerId }
        }
    }

    private companion object {
        private const val ClosedLeft = "loc.fai_barbarian_large_doubledoor_l"
        private const val ClosedRight = "loc.fai_barbarian_large_doubledoor_r"
        private const val OpenLeft = "loc.fai_barbarian_open_large_doubledoor_l"
        private const val OpenRight = "loc.fai_barbarian_open_large_doubledoor_r"

        private const val OpenSound = "synth.DOOR_OPEN"

        private const val CloseSound = "synth.DOOR_CLOSE"

        private const val Duration = 500
    }
}
