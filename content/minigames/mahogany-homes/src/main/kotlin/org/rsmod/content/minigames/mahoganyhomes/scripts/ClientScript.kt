package org.rsmod.content.minigames.mahoganyhomes.scripts

import org.rsmod.api.config.constants
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesHouse
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesManager
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ClientScript : PluginScript() {

    override fun ScriptContext.startup() {
        for (house in MahoganyHomesHouse.entries) {
            onOpNpc1(house.clientNpc) { talk(house) }
        }
    }

    private fun ProtectedAccess.talk(house: MahoganyHomesHouse) {
        if (MahoganyHomesManager.currentHouse(player) != house) {
            mes("Hello there.")
            return
        }

        val remaining = MahoganyHomesManager.remainingSlots(player)
        if (remaining.isNotEmpty()) {
            mes("You still have ${remaining.size} thing${if (remaining.size == 1) "" else "s"} left to fix here.")
            return
        }

        val award = MahoganyHomesManager.completeContract(player)
        player.statAdvance("stat.construction", award.xp)
        restoreRunEnergy()

        mes(
            "Thank you! You receive ${award.points} carpenter point${if (award.points == 1) "" else "s"} " +
                "and a nice cup of tea.",
        )
    }

    private fun ProtectedAccess.restoreRunEnergy() {
        if (player.runEnergy >= constants.run_max_energy) return
        player.runEnergy = constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
    }
}
