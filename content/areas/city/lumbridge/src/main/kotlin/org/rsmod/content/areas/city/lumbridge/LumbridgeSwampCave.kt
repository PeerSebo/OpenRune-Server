package org.rsmod.content.areas.city.lumbridge

import jakarta.inject.Inject
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.cinematic.MinimapState
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.table.LightSourcesRow
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.caveDarkness by intVarBit("varbit.darkness_level")
private var Player.caveRope by boolVarBit("varbit.swamp_caves_roped_entrance")

class LumbridgeSwampCave @Inject constructor(private val events: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(ENTRANCE) { enterCave() }
        onOpLocU(ENTRANCE, "obj.rope") { attachRope() }
        onOpLoc1(EXIT) { climbRope() }
        onIfModalButton("component.cws_warning_13:warn1") {
            ifClose()
            descend()
        }
        onIfModalButton("component.cws_warning_13:warn2") { ifClose() }
        onPlayerLogin {
            if (inCave(player.coords)) {
                player.updateLighting()
                player.softTimer(LIGHT_TIMER, LIGHT_CHECK_TICKS)
            } else {
                player.clearDarkness()
            }
        }
        onPlayerSoftTimer(LIGHT_TIMER) {
            if (!inCave(player.coords)) {
                player.clearDarkness()
                player.clearSoftTimer(LIGHT_TIMER)
                return@onPlayerSoftTimer
            }
            player.updateLighting()
            player.softTimer(LIGHT_TIMER, LIGHT_CHECK_TICKS)
        }
        onPlayerSoftTimer(INSECT_TIMER) {
            player.clearSoftTimer(INSECT_TIMER)
            if (inCave(player.coords) && player.caveDarkness != 0 && !hasLight(player)) {
                player.mes("You hear tiny insects skittering over the ground...")
                player.soundSynth("synth.CAVE_INSECTS_01")
            }
        }
    }

    private fun ProtectedAccess.attachRope(): Boolean {
        if (player.caveRope) return true
        if (!invDel(inv, "obj.rope", 1).success) {
            mes("You'll need a rope to climb down into the cave.")
            return false
        }
        player.caveRope = true
        mes("You tie the rope securely to the entrance.")
        return true
    }

    private suspend fun ProtectedAccess.enterCave() {
        arriveDelay()
        if (!player.caveRope && "obj.rope" !in inv) {
            mes("You'll need a rope to climb down into the cave.")
            return
        }
        if (hasLight(player)) {
            descend()
            return
        }
        ifOpenMainModal("interface.cws_warning_13")
        ifSetHide("component.cws_warning_13:cws_warning_layer", hide = false)
        ifSetText(
            "component.cws_warning_13:cws_test_warning",
            "The cave is very dark and you don't have a light source. Are you sure you want to go down?",
        )
    }

    private suspend fun ProtectedAccess.descend() {
        if (player.coords.chebyshevDistance(SWAMP_EXIT) > 3) return
        if (!attachRope()) return
        faceSquare(SWAMP_EXIT)
        anim("seq.emote_climbing_rope")
        delay(2)
        telejump(CAVE_LANDING)
        resetAnim()
        player.updateLighting()
        player.softTimer(LIGHT_TIMER, LIGHT_CHECK_TICKS)
    }

    private suspend fun ProtectedAccess.climbRope() {
        arriveDelay()
        faceSquare(CAVE_LANDING)
        anim("seq.emote_climbing_rope")
        delay(2)
        player.clearDarkness()
        player.clearSoftTimer(LIGHT_TIMER)
        telejump(SWAMP_EXIT)
        resetAnim()
    }

    private fun Player.updateLighting() {
        if (hasLight(this)) {
            clearDarkness()
        } else if (caveDarkness == 0) {
            caveDarkness = 3
            Cinematic.setMinimapState(this, MinimapState.MinimapHidden)
            ifOpenFullOverlay(DARKNESS, events)
            softTimer(INSECT_TIMER, INSECT_WARNING_TICKS)
        }
    }

    private fun Player.clearDarkness() {
        if (caveDarkness != 0) {
            caveDarkness = 0
            Cinematic.setMinimapState(this, MinimapState.Normal)
            ifCloseOverlay(DARKNESS, events)
        }
        clearSoftTimer(INSECT_TIMER)
    }

    internal companion object {
        const val ENTRANCE = "loc.goblin_cave_entrance"
        const val EXIT = "loc.swamp_cave_climbing_rope"
        const val DARKNESS = "interface.darkness_dark"
        const val LIGHT_TIMER = "timer.swamp_cave_light_check"
        const val INSECT_TIMER = "timer.swamp_cave_insects"
        const val LIGHT_CHECK_TICKS = 4
        const val INSECT_WARNING_TICKS = 16
        val CAVE_LANDING = CoordGrid(3167, 9573)
        val SWAMP_EXIT = CoordGrid(3168, 3172)

        fun hasLight(player: Player): Boolean =
            LightSourcesRow.all().any { it.lit in player.inv || it.lit in player.worn }

        fun inCave(tile: CoordGrid): Boolean =
            tile.level == 0 && tile.x in 3136..3263 && tile.z in 9472..9599
    }
}
