package org.rsmod.content.minigames.sorceressgarden.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.minigames.sorceressgarden.configs.GardenCoords
import org.rsmod.content.minigames.sorceressgarden.configs.osmanWantsPomes
import org.rsmod.content.minigames.sorceressgarden.npcs.apprenticeFirstVisit
import org.rsmod.content.minigames.sorceressgarden.npcs.apprenticeRepeatVisit
import org.rsmod.content.minigames.sorceressgarden.npcs.delMontyChat
import org.rsmod.game.entity.Npc
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GardenTravelScript @Inject constructor(private val worldRepo: WorldRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.arabian_sorceress_apprentice") { apprenticeTalk(it.npc) }
        onOpNpc4("npc.arabian_sorceress_apprentice") { apprenticeTeleport(it.npc) }

        onOpNpc1("npc.100_osman_cat") { startDialogue(it.npc) { delMontyChat() } }

        onOpLoc1("loc.100_osman_fountain") { leaveGarden() }
        onOpLoc1("loc.sorceress_shelves") { searchShelves() }
    }

    private suspend fun ProtectedAccess.apprenticeTalk(npc: Npc) {
        if (player.osmanWantsPomes) {
            startDialogue(npc) { apprenticeRepeatVisit(worldRepo) }
        } else {
            startDialogue(npc) { apprenticeFirstVisit(worldRepo) }
        }
    }

    private suspend fun ProtectedAccess.apprenticeTeleport(npc: Npc) {
        if (!player.osmanWantsPomes) {
            startDialogue(npc) { chatNpc(neutral, "I can't do that now, I'm far too busy sweeping.") }
            return
        }
        apprenticeSendOff(npc, worldRepo)
    }

    private suspend fun ProtectedAccess.leaveGarden() {
        delay(2)
        anim(FountainDrinkSeq)
        soundSynth(DrinkSound)
        delay(3)
        anim(TeleportCastSeq)
        spotanim(TeleportCastSpot, height = 92)
        soundArea(worldRepo, player.coords, TeleportSound, radius = 5)
        delay(6)
        telejump(GardenCoords.SorceressHouse)
    }

    private suspend fun ProtectedAccess.searchShelves() {
        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more items.")
            return
        }
        mes("You search the shelves ...")
        delay(4)
        invAdd(inv, "obj.beer_glass", 1)
        mes("... and among the strange paraphernalia, you find an empty beer glass.")
    }

    private companion object {
        private const val FountainDrinkSeq = "seq.100_osman_fountain_drink"
        private const val DrinkSound = "synth.DRINK"
        private const val TeleportCastSeq = "seq.human_castteleport"
        private const val TeleportCastSpot = "spotanim.teleport_casting"
        private const val TeleportSound = "synth.TELEPORT_ALL"
    }
}

suspend fun ProtectedAccess.apprenticeSendOff(npc: Npc, worldRepo: WorldRepository) {
    npc.faceSquare(player.coords)
    npc.say("Senventior Disthinte Molesko!")
    delay(1)
    npc.spotanim(CurseCastingSpot, height = 92)
    spotanim(CurseImpactSpot, delay = 100, height = 124)
    worldRepo.projAnim(sendOffProjectile(npc))
    soundArea(worldRepo, npc.coords, CurseCastSound, radius = 10)
    soundArea(worldRepo, npc.coords, CurseHitSound, delay = 100, radius = 10)
    delay(3)
    telejump(GardenCoords.SendOffArrival)
    player.osmanWantsPomes = true
}

suspend fun ProtectedAccess.ejectAfterPick() {
    mes("An elemental force emanating from the garden teleports you away.")
    telejump(GardenCoords.PickEject)
}

// Projectile times/heights use client units, not game ticks.
private fun ProtectedAccess.sendOffProjectile(npc: Npc): ProjAnim =
    ProjAnim(
        spotanim = CurseTravelSpot.asRSCM(RSCMType.SPOTANIM),
        startHeight = 31,
        endHeight = 31,
        startTime = 61,
        endTime = 100,
        angle = 16,
        progress = 128,
        sourceIndex = 0,
        targetIndex = -(player.slotId + 1),
        startCoord = npc.coords,
        endCoord = player.coords,
    )

private const val CurseCastingSpot = "spotanim.curse_casting"
private const val CurseImpactSpot = "spotanim.curse_impact"
private const val CurseTravelSpot = "spotanim.curse_travel"
private const val CurseCastSound = "synth.CURSE_CAST_AND_FIRE"
private const val CurseHitSound = "synth.CURSE_HIT"
