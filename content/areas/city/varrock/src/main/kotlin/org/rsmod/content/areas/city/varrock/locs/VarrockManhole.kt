package org.rsmod.content.areas.city.varrock.locs

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Climbing out must land beside the blocking cover, not on the ladder's translated tile.
class VarrockManhole @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(ManholeClosed) { openCover(it.loc) }
        onOpLoc1(ManholeOpen) { climbDown(it.loc) }
        onOpLoc2(ManholeOpen) { closeCover(it.loc) }
        onOpLoc1(ManholeLadder) { climbUp() }
    }

    private suspend fun ProtectedAccess.openCover(cover: BoundLocInfo) {
        arriveDelay()
        faceSquare(cover.coords)
        spam("You pull back the cover from over the manhole.")
        soundSynth(OpenSound)
        locRepo.change(cover, ManholeOpen, OpenDurationTicks)
    }

    private suspend fun ProtectedAccess.closeCover(cover: BoundLocInfo) {
        arriveDelay()
        faceSquare(cover.coords)
        spam("You place the cover back over the manhole.")
        locRepo.change(cover, ManholeClosed, ClosedDurationTicks)
    }

    private suspend fun ProtectedAccess.climbDown(cover: BoundLocInfo) {
        arriveDelay()
        faceSquare(cover.coords)
        spam("You climb down through the manhole.")
        telejump(cover.coords.translateZ(UndergroundOffsetZ))
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        anim(ClimbUpSeq)
        delay(1)
        telejump(SurfaceLanding)
    }

    private companion object {
        const val ManholeClosed = "loc.manholeclosed"
        const val ManholeOpen = "loc.manholeopen"
        const val ManholeLadder = "loc.fai_varrock_manhole_ladder"

        const val ClimbUpSeq = "seq.human_reachforladder"

        val SurfaceLanding = CoordGrid(3236, 3458, 0)

        const val OpenSound = "synth.COFFIN_OPEN"

        const val OpenDurationTicks = 100

        const val ClosedDurationTicks = 100

        const val UndergroundOffsetZ = 6400
    }
}
