package org.rsmod.content.bosses.thermy

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SmokeDevilCave : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.smokedevil_cave_entrance") { cross(Inside) }
        onOpLoc1("loc.slayer_cave_passage_smokedevil_exit") { cross(Outside) }
        onOpLoc1("loc.slayer_cave_smokedevil_boss_entrance") { cross(BossInside) }
        onOpLoc1("loc.slayer_cave_smokedevil_boss_exit") { cross(BossOutside) }
        onOpLoc2("loc.slayer_cave_smokedevil_boss_entrance") {
            mes("Through the crevice you can see the thermonuclear smoke devil's cavern.")
        }
    }

    private suspend fun ProtectedAccess.cross(destination: CoordGrid) {
        arriveDelay()
        telejump(destination, TeleportType.Exempt)
    }

    internal companion object {
        val Inside = CoordGrid(2404, 9415)
        val Outside = CoordGrid(2412, 3060)
        val BossInside = CoordGrid(2376, 9452)
        val BossOutside = CoordGrid(2379, 9452)
    }
}
