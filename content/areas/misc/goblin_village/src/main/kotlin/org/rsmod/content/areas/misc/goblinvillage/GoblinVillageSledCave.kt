package org.rsmod.content.areas.misc.goblinvillage

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GoblinVillageSledCave @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(CaveEntrance) { enterCave() }
        onOpLoc1(CaveExit) { leaveCave() }
        onOpLoc1(SledPile) { takeSled() }
        onOpHeld2(Sled) { rideSled(it.slot) }
    }

    private suspend fun ProtectedAccess.enterCave() {
        arriveDelay()
        anim(CrawlSeq)
        delay(EnterAnimTicks)
        telejump(CaveArrival)
        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = FadeDuration,
        )
        minimapReset()
        delay(1)
        closeFadeOverlay()
    }

    private suspend fun ProtectedAccess.leaveCave() {
        arriveDelay()
        delay(1)
        soundSynth(ClimbSound, loops = ClimbSoundLoops, delay = ClimbSoundDelay)
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FadeDuration,
        )
        delay(ExitFadeTicks)

        if (removeSled()) {
            mes("You remove the sled.")
        }
        telejump(VillageArrival)

        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = FadeDuration,
        )
        minimapReset()
        delay(1)
        closeFadeOverlay()
    }

    private fun ProtectedAccess.removeSled(): Boolean {
        val worn = player.worn[Wearpos.RightHand.slot] ?: return false
        if (!worn.isType(Sled)) {
            return false
        }
        val result = wornUnequip(Wearpos.RightHand.slot)
        if (result is WornUnequipResult.Fail) {
            result.message?.let(::mes)
            return false
        }
        soundSynth(EquipDefaultSound)
        return true
    }

    private suspend fun ProtectedAccess.takeSled() {
        arriveDelay()
        anim(PickupSeq)
        invAddOrDrop(objRepo, Sled)
        spam("You pick up a sled. There seems to be more available, should you ever lose it.")
        soundSynth(PickupSound)
    }

    // The engine owns the shield unequip sound; send only the sled sound here.
    private suspend fun ProtectedAccess.rideSled(invSlot: Int) {
        if (!inSledCave()) {
            mes("You cannot use that here!")
            return
        }

        val result = invEquip(invSlot)
        if (result is HeldEquipResult.Fail) {
            result.messages.forEach(::mes)
            return
        }
        soundSynth(EquipDefaultSound)
    }

    private fun ProtectedAccess.inSledCave(): Boolean {
        val coords = player.coords
        return coords.level == CaveLevel &&
            coords.x in CaveMinX..CaveMaxX &&
            coords.z in CaveMinZ..CaveMaxZ
    }

    private companion object {
        private const val CaveEntrance = "loc.xmas20_goblin_cave_entrance"

        private const val CaveExit = "loc.xmas20_goblin_cave_exit"

        private const val SledPile = "loc.xmas20_sled_loc"

        private const val Sled = "obj.xmas20_sled"

        private const val CrawlSeq = "seq.human_longcrawl"
        private const val PickupSeq = "seq.human_pickupfloor"

        private const val PickupSound = "synth.PICK"
        private const val ClimbSound = "synth.CLIMBING_LOOP"

        private const val EquipDefaultSound = "synth.EQUIP_DEFAULT"

        private const val ClimbSoundLoops = 3
        private const val ClimbSoundDelay = 4

        private const val FadeDuration = 50

        private const val EnterAnimTicks = 2

        private const val ExitFadeTicks = 2

        private val CaveArrival = CoordGrid(1975, 6220, 3)

        private val VillageArrival = CoordGrid(2949, 3508, 0)

        private const val CaveLevel = 3
        private const val CaveMinX = 1920
        private const val CaveMaxX = 1983
        private const val CaveMinZ = 6208
        private const val CaveMaxZ = 6271
    }
}
