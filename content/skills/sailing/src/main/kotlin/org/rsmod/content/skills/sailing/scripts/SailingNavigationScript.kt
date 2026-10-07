package org.rsmod.content.skills.sailing.scripts

import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.events.PlayerSetHeadingEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc5
import org.rsmod.content.skills.sailing.BoatWorldEntityService
import org.rsmod.content.skills.sailing.SailingNavigationService
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class SailingNavigationScript
@Inject
constructor(private val navigation: SailingNavigationService, private val boats: BoatWorldEntityService) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PlayerSetHeadingEvent> { navigation.heading(player, heading) }
        onEvent<GameLifecycle.LateCycle> { navigation.tick() }
        onIfOverlayButton(SailingNavigationService.COMPONENT_CONTROLS) {
            if (it.op == IfButtonOp.Op1) navigation.control(player, it.comsub)
        }
        onIfOpen("interface.sailing_boat_cargohold") { navigation.releaseHelm(player) }
        for (size in listOf("1x3", "2x5", "3x8")) {
            for (cloth in listOf("linen", "canvas", "cotton", "black", "yellow", "purple", "green", "blue", "pink", "red")) {
                val sail = "loc.sailing_boat_sail_kandarin_${size}_$cloth"
                onOpLoc1(sail) { if (onOwnDeck(it.loc)) navigation.trim(player) }
                onOpLoc2(sail) { if (onOwnDeck(it.loc)) navigation.setSails(player, true) }
                onOpLoc5(sail) { if (onOwnDeck(it.loc)) navigation.setSails(player, false) }
            }
        }
    }

    private fun ProtectedAccess.onOwnDeck(loc: BoundLocInfo): Boolean {
        val boat = boats.boarded(player)
        if (boat == null || player.coords !in boat || loc.coords !in boat) {
            mes("You must be aboard your own boat to operate its sails.")
            return false
        }
        faceLoc(loc)
        return true
    }
}
