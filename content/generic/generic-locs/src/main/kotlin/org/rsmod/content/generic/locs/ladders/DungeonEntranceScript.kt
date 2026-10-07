package org.rsmod.content.generic.locs.ladders

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DungeonEntranceScript @Inject constructor(private val locRepo: LocRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(TrapdoorShut) { openTrapdoor(it.loc) }
        onOpLoc1(TrapdoorOpen) { climb(it.loc, EdgevilleTrapdoorTile, EdgevilleDungeonLanding) }
        onOpLoc2(TrapdoorOpen) { closeTrapdoor(it.loc) }

        onOpLoc1(FaladorMineStairs) { useStairs(it.loc, FaladorMineStairsTile, DwarvenMineLanding) }
        onOpLoc1(DwarvenMineStairs) { useStairs(it.loc, DwarvenMineStairsTile, FaladorLanding) }

        onOpLoc1(VolcanoEntrance) { climb(it.loc, VolcanoEntranceTile, KaramjaDungeonLanding) }
        onOpLoc1(VolcanoExitRope) { climb(it.loc, VolcanoExitRopeTile, VolcanoMouthLanding) }
    }

    private suspend fun ProtectedAccess.useStairs(
        loc: BoundLocInfo,
        placement: CoordGrid,
        destination: CoordGrid,
    ) {
        if (loc.coords != placement) return
        arriveDelay()
        telejump(destination)
    }

    private suspend fun ProtectedAccess.climb(
        loc: BoundLocInfo,
        placement: CoordGrid,
        destination: CoordGrid,
    ) {
        if (loc.coords != placement) {
            return
        }
        arriveDelay()
        anim(ClimbAnim)
        delay(1)
        telejump(destination)
    }

    private suspend fun ProtectedAccess.openTrapdoor(trapdoor: BoundLocInfo) {
        if (trapdoor.coords != EdgevilleTrapdoorTile) {
            return
        }
        arriveDelay()
        soundSynth(SynthTrapdoorOpen)
        locRepo.change(trapdoor, TrapdoorOpen, TrapdoorSwapDuration)
    }

    private suspend fun ProtectedAccess.closeTrapdoor(trapdoor: BoundLocInfo) {
        if (trapdoor.coords != EdgevilleTrapdoorTile) {
            return
        }
        arriveDelay()
        soundSynth(SynthTrapdoorClose)
        locRepo.change(trapdoor, TrapdoorShut, TrapdoorSwapDuration)
    }

    private companion object {
        private const val TrapdoorShut = "loc.trapdoor"

        private const val TrapdoorOpen = "loc.trapdoor_open"

        private const val FaladorMineStairs = "loc.stairs_cellar"

        private const val DwarvenMineStairs = "loc.stairs_falador"

        private const val VolcanoEntrance = "loc.volcano_entrance"

        private const val VolcanoExitRope = "loc.climbing_rope2"

        private const val ClimbAnim = "seq.human_reachforladder"

        private const val SynthTrapdoorOpen = "synth.TRAPDOOR_OPEN"

        private const val SynthTrapdoorClose = "synth.TRAPDOOR_CLOSE"

        private const val TrapdoorSwapDuration = 500

        private val EdgevilleTrapdoorTile = CoordGrid(3097, 3468, 0)

        private val EdgevilleDungeonLanding = CoordGrid(3097, 9868, 0)

        private val FaladorMineStairsTile = CoordGrid(3058, 3376, 0)

        private val DwarvenMineLanding = CoordGrid(3058, 9776, 0)

        private val DwarvenMineStairsTile = CoordGrid(3059, 9776, 0)

        private val FaladorLanding = CoordGrid(3061, 3376, 0)

        private val VolcanoEntranceTile = CoordGrid(2856, 3168, 0)

        private val KaramjaDungeonLanding = CoordGrid(2856, 9568, 0)

        private val VolcanoExitRopeTile = CoordGrid(2856, 9569, 0)

        private val VolcanoMouthLanding = CoordGrid(2856, 3169, 0)
    }
}
