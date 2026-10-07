package org.rsmod.content.minigames.sorceressgarden.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.minigames.sorceressgarden.configs.GardenSeason
import org.rsmod.content.minigames.sorceressgarden.configs.RockyPet.rollRocky
import org.rsmod.content.minigames.sorceressgarden.drops.GardenHerbTables
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GardenPickScript
@Inject
constructor(private val random: GameRandom, private val worldRepo: WorldRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (season in GardenSeason.entries) {
            onOpLoc1(season.treeLoc) { pickTree(it.loc, season) }
            onOpLoc1(season.herbLoc) { pickHerb(it.loc, season) }
        }
    }

    private suspend fun ProtectedAccess.pickTree(tree: BoundLocInfo, season: GardenSeason) {
        if (!meetsLevel(season)) {
            return
        }
        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more items.")
            return
        }

        pickBeat(tree)
        invAdd(inv, season.fruitObj, 1)
        statAdvance("stat.farming", season.farmingXp)
        rollRocky(season)
        ejectAfterPick()
    }

    private suspend fun ProtectedAccess.pickHerb(patch: BoundLocInfo, season: GardenSeason) {
        if (!meetsLevel(season)) {
            return
        }
        if (inv.freeSpace() < HerbsPerPick) {
            mes("Your inventory is too full to hold any more items.")
            return
        }

        pickBeat(patch)
        val herb = GardenHerbTables.roll(random, season)
        invAdd(inv, herb, HerbsPerPick)
        statAdvance("stat.farming", HerbPatchFarmingXp)
        ejectAfterPick()
    }

    private suspend fun ProtectedAccess.pickBeat(loc: BoundLocInfo) {
        anim(PickSeq)
        soundSynth(PickSound)
        delay(2)
        val puffTile = loc.coords.translate(0, 2)
        spotanimMap(worldRepo, PuffSpot, puffTile, height = 124, delay = 10)
        soundArea(worldRepo, puffTile, PuffSound, radius = 5)
        delay(3)
    }

    private fun ProtectedAccess.meetsLevel(season: GardenSeason): Boolean {
        if (player.thievingLvl >= season.thievingLevel) {
            return true
        }
        mes("You need a Thieving level of ${season.thievingLevel} to do this.")
        return false
    }

    private companion object {
        const val HerbsPerPick = 2
        const val HerbPatchFarmingXp = 50.0

        const val PickSeq = "seq.picking_high"
        const val PickSound = "synth.OSMAN_PICK_HIGH"
        const val PuffSpot = "spotanim.smokepuff_large"
        const val PuffSound = "synth.SMOKEPUFF"
    }
}
