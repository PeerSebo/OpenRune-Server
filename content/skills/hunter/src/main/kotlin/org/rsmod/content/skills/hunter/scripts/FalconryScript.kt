package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.events.PlayerHitpointsChangedEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.hunter.FalconryService
import org.rsmod.content.skills.hunter.configs.FalconryPrey
import org.rsmod.content.skills.hunter.migrateHunterPlayerState
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class FalconryScript @Inject constructor(private val falconry: FalconryService) : PluginScript() {
    override fun ScriptContext.startup() {
        for (matthias in listOf("npc.hunting_npc_falconer", "npc.hunting_npc_falconer_nobird")) {
            onOpNpc1(matthias) { talk(it.npc) }
            onOpNpc3(matthias) { falconry.rent(player) }
        }
        for (prey in FalconryPrey.entries) {
            onApNpc1(prey.npc) {
                if (falconry.withinLaunchRange(player, it.npc)) falconry.launch(player, it.npc)
                else apRange(FalconryService.LAUNCH_RANGE)
            }
            onOpNpc1(prey.npc) { falconry.launch(player, it.npc) }
            onOpNpc1(prey.falcon) { falconry.retrieve(player, it.npc) }
        }
        onEvent<GameLifecycle.LateCycle> { falconry.tick() }
        onPlayerLogin {
            player.migrateHunterPlayerState()
            falconry.cleanup(player)
        }
        onPlayerLogout { falconry.cleanup(player) }
        onEvent<PlayerHitpointsChangedEvent> {
            if (newHitpoints <= 0) falconry.cleanup(player)
        }
        onOpLoc1("loc.hunting_fence_style") { crossStile(it.loc) }
        onOpLoc3("loc.hunting_fence_style") { crossStile(it.loc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        var option = 4
        startDialogue(npc) {
            chatNpcNoTurn(happy, "Would you like to try falconry? A falcon costs 500 coins for your visit. You can also invest 500,000 coins for free future rentals.")
            option = choice4("I'd like a falcon.", 1, "Invest 500,000 coins.", 2, "Return my falcon.", 3, "No, thank you.", 4)
        }
        when (option) {
            1 -> falconry.rent(player)
            2 -> falconry.rent(player, invest = true)
            3 -> { falconry.cleanup(player); mes("You return your falcon and glove to Matthias.") }
        }
    }

    private suspend fun ProtectedAccess.crossStile(loc: BoundLocInfo) {
        // Accept only this native field placement; copied scenery must not route to the mainland.
        if (loc.coords.x != 2371 || loc.coords.z != 3620 || coords.level != 0) return
        val leaving = coords.z <= 3620
        if (leaving) falconry.cleanup(player)
        val destination = CoordGrid(2371, if (leaving) 3621 else 3619)
        faceSquare(destination)
        delay(1)
        anim("seq.human_walk_style")
        exactMove(coords, destination, 0, 60, if (leaving) 1024 else 0)
        delay(2)
        resetAnim()
    }
}
