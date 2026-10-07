package org.rsmod.content.quest.area.draynor

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MorganHouse @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(Stairs) { climb(it.loc, up = true) }
        onOpLoc1(StairsTop) { climb(it.loc, up = false) }

        onOpLoc1(CupboardShut) { openCupboard(it.loc) }
        onOpLoc1(CupboardOpen) { searchCupboard() }
        onOpLoc2(CupboardOpen) { closeCupboard(it.loc) }
    }

    private suspend fun ProtectedAccess.climb(loc: BoundLocInfo, up: Boolean) {
        arriveDelay()

        val landingX = if (up) MorganStairs[loc.coords] else MorganStairsTop[loc.coords]
        if (landingX == null) {
            mes("Nothing interesting happens.")
            return
        }

        val z = player.coords.z.coerceIn(MorganStairsSouthRow, MorganStairsNorthRow)
        val level = if (up) UpperLevel else GroundLevel

        telejump(CoordGrid(landingX, z, level))
    }

    private suspend fun ProtectedAccess.openCupboard(loc: BoundLocInfo) {
        arriveDelay()
        anim(ReachAnim)
        locRepo.change(loc, CupboardOpen, duration = CupboardOpenTicks)
        soundSynth(CupboardSound)
    }

    private suspend fun ProtectedAccess.closeCupboard(loc: BoundLocInfo) {
        arriveDelay()
        anim(ReachAnim)
        locRepo.change(loc, CupboardShut, duration = CupboardOpenTicks)
        soundSynth(CupboardCloseSound)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()

        if (invTotal(inv, Garlic) > 0) {
            mes("You already have some garlic.")
            return
        }

        mes("You search the cupboard...")
        delay(SearchRevealTicks)

        if (invAdd(inv, Garlic, 1).failure) {
            mes("You don't have enough inventory space to take that.")
            return
        }

        anim(ReachAnim)
        soundSynth(TakeSound)
        mes("You find a clove of garlic.")
    }

    private companion object {
        private const val Stairs = "loc.stairs"
        private const val StairsTop = "loc.stairstop"

        private const val CupboardShut = "loc.garliccupboardshut"
        private const val CupboardOpen = "loc.garliccupboardopen"

        private const val Garlic = "obj.garlic"

        private const val CupboardSound = "synth.CUPBOARD_OPEN"
        private const val CupboardCloseSound = "synth.CUPBOARD_CLOSE"
        private const val TakeSound = "synth.PICK2"

        private const val ReachAnim = "seq.human_pickuptable"

        private const val CupboardOpenTicks = 200

        private const val SearchRevealTicks = 4

        private val MorganStairs = mapOf(CoordGrid(3099, 3266, 0) to 3102)

        private val MorganStairsTop = mapOf(CoordGrid(3100, 3266, 1) to 3098)

        private const val MorganStairsSouthRow = 3266
        private const val MorganStairsNorthRow = 3267

        private const val GroundLevel = 0
        private const val UpperLevel = 1
    }
}
