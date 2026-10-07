package org.rsmod.content.skills.agility.shortcuts

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.BalanceStyle
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.line
import org.rsmod.content.skills.agility.scripts.approachMount
import org.rsmod.content.skills.agility.scripts.hopAcross
import org.rsmod.content.skills.agility.scripts.withAgilityAnimation
import org.rsmod.content.skills.agility.seqGlideTicks
import org.rsmod.content.skills.agility.seqTicks
import org.rsmod.content.skills.agility.shortcuts.scripts.awardShortcutXp
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AgilityShortcutScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        val byLoc = mutableMapOf<String, MutableList<AgilityShortcut>>()
        for (shortcut in AgilityShortcuts.all) {
            for (loc in shortcut.locs) {
                if (loc in EXISTING_HANDLERS) continue
                byLoc.getOrPut(loc) { mutableListOf() } += shortcut
            }
        }
        for ((loc, shortcuts) in byLoc) {
            if (loc == STEPPING_STONE_LOC) {
                // This shape-22 target is in the river; neither bank can reach ordinary adjacency.
                onApLoc1(loc) {
                    if (isWithinApRange(it.loc, STEPPING_STONE_RANGE)) use(shortcuts)
                }
            } else {
                onOpLoc1(loc) { use(shortcuts) }
            }
        }
    }

    private suspend fun ProtectedAccess.use(candidates: List<AgilityShortcut>) {
        arriveDelay()
        val shortcut = candidates.minBy { it.distanceTo(coords) }
        if (player.agilityLvl < shortcut.level) {
            mes("You need an Agility level of ${shortcut.level} to use this shortcut.")
            return
        }
        val fromA = shortcut.startsFromA(coords)
        val from = if (fromA) shortcut.sideA else shortcut.sideB
        val dest = if (fromA) shortcut.sideB else shortcut.sideA
        // Scenery reused in detached map copies must not route players back to the mainland.
        if (coords.level != from.level || coords.chebyshevDistance(from) > 16) return
        if (!approachMount(from)) return
        player.withAgilityAnimation {
            try {
                cross(shortcut.move, dest, fromA)
            } finally {
                // Interrupted crossings must not strand players inside walls.
                if (player.coords != dest) telejump(dest, TeleportType.Exempt)
            }
        }
        awardShortcutXp(shortcut.xp, xpMods)
    }

    private suspend fun ProtectedAccess.cross(move: ShortcutMove, dest: CoordGrid, fromA: Boolean) {
        when (move) {
            is ShortcutMove.Climb -> {
                val ticks = move.ticks ?: seqTicks(move.seq, fallback = 2)
                move.sound?.let { soundSynth(it) }
                climbTo(dest, move.seq, ticks)
            }
            is ShortcutMove.Jump -> {
                val ticks = move.ticks ?: seqGlideTicks(move.seq, fallback = 2)
                leapTo(dest, move.seq, ticks)
            }
            is ShortcutMove.Squeeze -> {
                faceSquare(dest)
                anim(move.enter)
                delay(move.ticks)
                telejump(dest, TeleportType.Exempt)
                anim(move.leave)
            }
            is ShortcutMove.Tunnel -> {
                faceSquare(dest)
                anim(move.enter)
                delay(seqTicks(move.enter, fallback = 2))
                val path = line(coords, dest)
                for (tile in path.dropLast(1)) {
                    teleport(tile, TeleportType.Exempt)
                    anim(move.walk)
                    delay(1)
                }
                telejump(dest, TeleportType.Exempt)
                anim(move.leave)
            }
            is ShortcutMove.Balance -> {
                val path = (if (fromA) move.tiles else move.tiles.reversed()) + dest
                balanceAlong(path, BalanceStyle.Tightrope)
            }
            is ShortcutMove.Hop -> {
                val stones = (if (fromA) move.stones else move.stones.reversed()) + dest
                hopAcross(stones)
            }
        }
    }

    private companion object {
        const val STEPPING_STONE_LOC = "loc.lumbridge_sc_stepstone"
        const val STEPPING_STONE_RANGE = 3

        val EXISTING_HANDLERS = setOf(
            "loc.fai_falador_castle_crumble_mid",
            "loc.lumbridge_sc_fencejump",
            "loc.varrock_sc_tunnel_west",
            "loc.varrock_sc_tunnel_east",
        )
    }
}
