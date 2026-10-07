package org.rsmod.content.other.windmill

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WindmillMillingScript @Inject constructor(private val locRepo: LocRepository) :
    PluginScript() {

    private var ProtectedAccess.flourInBin by intVarBit("varbit.mill_flour")

    private var ProtectedAccess.binShowsFlour by boolVarBit("varbit.mill_showflour")

    override fun ScriptContext.startup() {
        onPlayerLogin { WindmillState.migrate(player) }
        onOpLoc1(HopperLoc) { fillHopper() }
        onOpLocU(HopperLoc, Grain) { fillHopper() }

        onOpLoc1(LeverLoc) { operateHopper(it.loc) }

        onOpLoc1(BinFlourLoc) { collectFlour() }
        onOpLocU(BinFlourLoc, EmptyPot) { collectFlour() }
        onOpLoc1(BinEmptyLoc) { reportEmptyBin() }
        onOpLocU(BinEmptyLoc, EmptyPot) { reportEmptyBin() }
    }

    private suspend fun ProtectedAccess.fillHopper() {
        arriveDelay()

        if (WindmillState.hasGrain(player)) {
            mes("There is already grain in the hopper.")
            return
        }

        if (Grain !in inv) {
            objbox(Grain, "You haven't got anything to fill the hopper with.")
            return
        }

        if (invDel(inv, Grain, 1).failure) {
            return
        }

        // Commit the grain removal before suspending for presentation.
        WindmillState.setHasGrain(player, true)
        anim(HopperFillAnim)
        delay(1)
        soundSynth(FillGrainSound)
        mes(
            "You put the grain in the hopper. You should now pull the lever nearby to operate the hopper."
        )
    }

    private suspend fun ProtectedAccess.operateHopper(lever: BoundLocInfo) {
        arriveDelay()

        val hadGrain = WindmillState.hasGrain(player)
        WindmillState.setHasGrain(player, false)

        anim(LeverAnim)
        locRepo.change(lever, InactiveLeverLoc, LeverChangeTicks)
        soundSynth(LeverSound)

        if (!hadGrain) {
            mes("You operate the empty hopper. Nothing interesting happens.")
            return
        }

        soundSynth(MillstonesSound)
        // Transform index 31 is -1, so flour must stay below that index.
        flourInBin = minOf(flourInBin + 1, MaxFlourInBin)
        binShowsFlour = true
        spam("You operate the hopper. The grain slides down the chute.")
    }

    private suspend fun ProtectedAccess.collectFlour() {
        arriveDelay()

        val stored = flourInBin
        if (stored <= 0) {
            reportEmptyBin()
            return
        }

        if (EmptyPot !in inv) {
            mes("You need an empty pot to hold the flour in.")
            return
        }

        if (invReplace(inv, EmptyPot, 1, PotOfFlour).failure) {
            return
        }

        val remaining = stored - 1
        flourInBin = remaining
        if (remaining > 0) {
            spam("You fill a pot with flour.")
            return
        }
        binShowsFlour = false
        spam("You fill a pot with the last of the flour in the bin.")
    }

    private suspend fun ProtectedAccess.reportEmptyBin() {
        arriveDelay()
        mes("The flour bin is already empty. You need to place wheat in the hopper upstairs first.")
    }

    private companion object {
        private const val HopperLoc = "loc.hopper1"
        private const val LeverLoc = "loc.hopperlevers1"
        private const val InactiveLeverLoc = "loc.inactivehopperlevers"
        private const val BinFlourLoc = "loc.millbase_flour"
        private const val BinEmptyLoc = "loc.millbase_empty"

        private const val Grain = "obj.grain"
        private const val EmptyPot = "obj.pot_empty"
        private const val PotOfFlour = "obj.pot_flour"

        private const val HopperFillAnim = "seq.qip_cook_hopper_grain"
        private const val LeverAnim = "seq.qip_cook_hopper_leaver"
        private const val FillGrainSound = "synth.FILL_GRAIN"
        private const val LeverSound = "synth.LEVER"
        private const val MillstonesSound = "synth.MILLSTONES"

        private const val LeverChangeTicks = 2

        private const val MaxFlourInBin = 30
    }
}
