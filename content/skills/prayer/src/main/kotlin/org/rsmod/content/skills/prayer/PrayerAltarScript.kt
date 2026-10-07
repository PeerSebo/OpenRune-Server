package org.rsmod.content.skills.prayer

import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PrayerAltarScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(Altar) { pray(altarCoords = it.loc.coords) }
        onOpLoc1("loc.guthix_altar") { pray() }
        onOpLoc1("loc.monks_altar") { pray(bonus = 2) }
    }

    private suspend fun ProtectedAccess.pray(bonus: Int = 0, altarCoords: CoordGrid? = null) {
        arriveDelay()
        if (altarCoords != null && vars["varbit.prayer_smite"] != 0) {
            publish(PlayerActionCompleteEvent(player, PlayerAction.PraySmite, Altar, altarCoords))
        }

        if (stat(PrayerStat) >= statBase(PrayerStat) + bonus) {
            mes("You already have full Prayer Points.")
            return
        }

        statRestore(PrayerStat)
        if (bonus > 0) statBoost(PrayerStat, bonus, 0)
        anim(PrayAnim)
        soundSynth(RechargeSound)
        spam("You recharge your Prayer Points.")
    }

    private companion object {
        private const val Altar = "loc.altar"
        private const val PrayerStat = "stat.prayer"
        private const val PrayAnim = "seq.human_pray"
        private const val RechargeSound = "synth.PRAYER_RECHARGE"
    }
}
