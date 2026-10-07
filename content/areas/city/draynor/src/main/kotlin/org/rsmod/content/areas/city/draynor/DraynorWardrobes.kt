package org.rsmod.content.areas.city.draynor

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorWardrobes @Inject constructor(
    private val locRepo: LocRepository,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (wardrobe in Wardrobes) {
            onOpLoc1(wardrobe.closed) { open(it.loc, wardrobe) }
            onOpLoc2(wardrobe.open) { mes("You search the wardrobe but find nothing.") }
            onOpLoc2(wardrobe.skeleton) { mes("Eek! There's a skeleton in there!") }
            for (open in listOf(wardrobe.open, wardrobe.skeleton)) {
                onOpLoc3(open) { close(it.loc, wardrobe) }
            }
        }
    }

    private fun ProtectedAccess.open(loc: BoundLocInfo, wardrobe: Wardrobe) {
        faceLoc(loc)
        val opened = if (this@DraynorWardrobes.random.randomBoolean(21)) wardrobe.skeleton else wardrobe.open
        locRepo.change(loc, opened, 100)
        soundSynth("synth.CREAKYDOOR_OPEN")
    }

    private fun ProtectedAccess.close(loc: BoundLocInfo, wardrobe: Wardrobe) {
        faceLoc(loc)
        locRepo.change(loc, wardrobe.closed, Int.MAX_VALUE)
        soundSynth("synth.CREAKYDOOR_CLOSE")
    }

    private data class Wardrobe(val closed: String, val open: String, val skeleton: String)

    private companion object {
        private val Wardrobes = listOf(
            Wardrobe("loc.draynor_wardrobe", "loc.draynor_wardrobe_open", "loc.draynor_wardrobe_open_skeleton"),
            Wardrobe("loc.spookywardrobe", "loc.spookywardrobe_open", "loc.spookywardrobe_open_skeleton"),
        )
    }
}
