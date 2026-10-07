package org.rsmod.content.areas.city.lumbridge.locs

import jakarta.inject.Inject
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HamHideoutTrapdoor @Inject constructor(
    private val locRepo: LocRepository,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(TrapdoorClosed) { attemptOpen(it.loc, usingLockpick = false) }
        onOpLoc5(TrapdoorClosed) { attemptOpen(it.loc, usingLockpick = true) }
        onOpLoc1(TrapdoorOpen) { climbDown(it.loc) }
        onOpLoc2(TrapdoorOpen) { closeTrapdoor(it.loc) }
        onOpLoc1(HideoutLadder) { climbUp(it.loc) }
    }

    private suspend fun ProtectedAccess.attemptOpen(loc: BoundLocInfo, usingLockpick: Boolean) {
        if (loc.coords != SurfaceTile) {
            return
        }
        arriveDelay()

        if (usingLockpick && invTotal(inv, Lockpick) == 0) {
            mes("You need a lockpick to pick this lock.")
            return
        }

        anim(PickLockAnim)
        soundSynth(PickLockSound)
        delay(2)

        val (low, high) =
            if (usingLockpick) HamTrapdoorRates.WithLockpick else HamTrapdoorRates.WithoutLockpick

        if (!statRandom("stat.thieving", low, high, invisibleLvls)) {
            mes("You fail to open the trapdoor.")
            player.statSub("stat.thieving", 1, 0)
            if (random.randomBoolean(FailureBonusXpChance)) {
                statAdvance("stat.thieving", FailureBonusXp)
            }
            return
        }

        locRepo.change(loc, TrapdoorOpen, SwapDuration)
        mes("You open the trapdoor.")
        statAdvance("stat.thieving", SuccessXp)
    }

    private suspend fun ProtectedAccess.closeTrapdoor(loc: BoundLocInfo) {
        if (loc.coords != SurfaceTile) {
            return
        }
        arriveDelay()
        locRepo.change(loc, TrapdoorClosed, SwapDuration)
        mes("You close the trapdoor.")
    }

    private suspend fun ProtectedAccess.climbDown(loc: BoundLocInfo) {
        if (loc.coords != SurfaceTile) {
            return
        }
        arriveDelay()
        mes("You climb down through the trapdoor.")
        telejump(UndergroundLanding)
        publish(PlayerActionCompleteEvent(player, PlayerAction.EnterUnderground, TrapdoorOpen))
    }

    private suspend fun ProtectedAccess.climbUp(loc: BoundLocInfo) {
        if (loc.coords != HideoutLadderTile) {
            return
        }
        arriveDelay()
        anim(ClimbAnim)
        delay(1)
        telejump(SurfaceTile)
    }

    private companion object {
        private const val TrapdoorClosed = "loc.osf_trapdoor_closed"
        private const val TrapdoorOpen = "loc.osf_trapdoor_open"
        private const val HideoutLadder = "loc.osf_ham_ladder"
        private const val Lockpick = "obj.lockpick"

        private const val PickLockAnim = "seq.human_picklock_cagedoor"
        private const val PickLockSound = "synth.PICK_LOCK"

        private const val ClimbAnim = "seq.human_reachforladder"

        private const val SwapDuration = 500

        private const val SuccessXp = 3.8

        private const val FailureBonusXpChance = 5
        private const val FailureBonusXp = 1.0

        val SurfaceTile = CoordGrid(3166, 3252, 0)

        val UndergroundLanding = CoordGrid(3150, 9653, 0)

        val HideoutLadderTile = CoordGrid(3149, 9653, 0)
    }
}

private object HamTrapdoorRates {
    val WithoutLockpick: Pair<Int, Int> = 9 to 250
    val WithLockpick: Pair<Int, Int> = 60 to 252
}
