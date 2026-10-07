package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.hunter.PitfallService
import org.rsmod.content.skills.hunter.configs.PitfallPrey
import org.rsmod.content.skills.hunter.configs.PitfallSpot
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class PitfallScript @Inject constructor(private val pitfalls: PitfallService) : PluginScript() {
    override fun ScriptContext.startup() {
        for (spot in PitfallSpot.ALL) {
            onOpLoc3(spot.loc) { set(it.loc) }
            onOpLoc1(spot.loc) { jump(it.loc) }
            onOpLoc2(spot.loc) { dismantle(it.loc) }
        }
        for (prey in PitfallPrey.entries) onOpNpc1(prey.npc) {
            if (pitfalls.tease(player, it.npc)) {
                try { delay(2) } finally { resetAnim() }
            }
        }
        onEvent<GameLifecycle.LateCycle> { pitfalls.tick() }
        onPlayerLogin { pitfalls.cleanup(player) }
        onPlayerLogout { pitfalls.cleanup(player) }
    }

    private suspend fun ProtectedAccess.set(loc: BoundLocInfo) {
        val trap = pitfalls.beginSet(player, loc) ?: return
        faceLoc(loc)
        resetAnim()
        anim("seq.human_laytrap")
        try {
            delay(3)
            pitfalls.finishSet(trap)
        } finally {
            pitfalls.cancelSet(trap)
            resetAnim()
        }
    }

    private suspend fun ProtectedAccess.jump(loc: BoundLocInfo) {
        val destination = pitfalls.crossing(player, loc) ?: return
        val start = coords
        faceLoc(loc)
        resetAnim()
        anim("seq.human_longjump")
        soundSynth("synth.HUNTING_JUMP")
        exactMove(start, destination, 0, PitfallService.CROSSING_TICKS * 30, PitfallService.crossingDirection(start, destination), TeleportType.Exempt)
        if (coords != destination) { resetAnim(); return }
        pitfalls.crossed(player, loc, start, destination)
        spam("You jump over the spiked pit.")
        try { delay(PitfallService.CROSSING_TICKS) } finally { resetAnim() }
    }

    private suspend fun ProtectedAccess.dismantle(loc: BoundLocInfo) {
        val trap = pitfalls.trap(player, loc) ?: return
        val origin = coords
        faceLoc(loc)
        resetAnim()
        anim("seq.hunting_setting_trap_small")
        try {
            delay(2)
            if (coords == origin && pitfalls.trap(player, loc) === trap) pitfalls.dismantle(player, loc)
        } finally { resetAnim() }
    }
}
