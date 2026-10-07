package org.rsmod.content.minigames.gotr.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.content.minigames.gotr.GotrEnterResult
import org.rsmod.content.minigames.gotr.GotrGame
import org.rsmod.content.minigames.gotr.GotrPhase
import org.rsmod.content.minigames.gotr.GotrSettings
import org.rsmod.content.minigames.gotr.gotrCatalyticPoints
import org.rsmod.content.minigames.gotr.gotrElementalPoints
import org.rsmod.content.minigames.gotr.migrateGotrRewards
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GotrScript
@Inject
constructor(private val game: GotrGame) : PluginScript() {
    private val settings: GotrSettings
        get() = GotrSettings.load()

    override fun ScriptContext.startup() {
        val settings = settings
        if (!settings.isEnabled) {
            return
        }
        game.bindSettings(settings)
        // Wait until the first tick, after static map entities spawn, before creating defenses.
        onEvent<GameLifecycle.LateCycle> { game.tick() }
        onEvent<SessionStateEvent.Logout> { game.leave(player) }
        onEvent<SessionStateEvent.Login> {
            // Persisted flags do not prove membership in this process's round.
            game.leave(player)
        }

        for (barrier in BARRIER_LOCS) {
            onOpLoc1(barrier) { pass() }
            onOpLoc2(barrier) { peek() }
            onOpLoc3(barrier) { quickPass() }
            onOpLoc4(barrier) { visualEffects() }
        }

        onCommand("gotr") {
            desc = "GOTR debug cheat: grant points/pearls, or force a phase transition"
            invalidArgs = "Usage: ::gotr points <N> | ::gotr pearls <N> | ::gotr status"
            cheat { runCheat() }
        }
    }

    private suspend fun ProtectedAccess.pass() = crossBarrier()

    private suspend fun ProtectedAccess.quickPass() = crossBarrier()

    // The five-tile barrier runs east-west; land on the first clear north/south row, not inside its footprint.
    private suspend fun ProtectedAccess.crossBarrier() {
        if (player.coords.z >= ARENA_MIN_Z) {
            delay(CROSS_PAUSE_TICKS)
            game.leave(player)
            telejump(barrierFarSide(toArena = false))
            return
        }
        // The barrier row itself counts as the lobby side, allowing stranded players to cross out.
        if (game.phase != GotrPhase.ACTIVE) {
            val denied = game.tryEnter(player) as? GotrEnterResult.Denied ?: return
            mesbox(denied.message)
            return
        }
        delay(CROSS_PAUSE_TICKS)
        when (val result = game.tryEnter(player)) {
            is GotrEnterResult.Allowed -> telejump(barrierFarSide(toArena = true))
            is GotrEnterResult.Denied -> mesbox(result.message)
        }
    }

    private fun ProtectedAccess.barrierFarSide(toArena: Boolean): CoordGrid {
        val x = player.coords.x.coerceIn(BARRIER_MIN_X, BARRIER_MAX_X)
        return CoordGrid(x, if (toArena) ARENA_MIN_Z else LOBBY_MAX_Z, player.coords.level)
    }

    private fun ProtectedAccess.peek() {
        mes(game.statusMessage())
    }

    private fun ProtectedAccess.visualEffects() {
        val disabled = vars["varbit.gotr_colourful_fade_disabled"] == 0
        VarPlayerIntMapSetter.set(player, "varbit.gotr_colourful_fade_disabled", if (disabled) 1 else 0)
        mes(if (disabled) "Portal visual effects are now disabled." else "Portal visual effects are now enabled.")
    }

    private fun Cheat.runCheat() {
        val sub = args.getOrNull(0)?.lowercase()
        when (sub) {
            "points" -> {
                val amount = args.getOrNull(1)?.toIntOrNull() ?: 10
                player.migrateGotrRewards()
                val elemental = player.gotrElementalPoints + amount
                val catalytic = player.gotrCatalyticPoints + amount
                player.gotrElementalPoints = elemental
                player.gotrCatalyticPoints = catalytic
                player.mes("Granted $amount elemental and $amount catalytic points ($elemental/$catalytic total).")
            }
            "pearls" -> {
                val amount = args.getOrNull(1)?.toIntOrNull() ?: 1000
                player.invAdd(inv = player.inv, type = "obj.abyssal_pearl", count = amount)
                player.mes("Granted $amount abyssal pearls.")
            }
            "status" ->
                player.mes(
                    "GOTR phase=${game.phase} power=${game.powerPercent()}% " +
                        "target=${game.target()} members=${game.memberCount()} " +
                        "elemental=${game.currentElementalRune()} " +
                        "catalytic=${game.currentCatalyticRune()}",
                )
            else -> player.mes("Usage: ::gotr points <N> | ::gotr pearls <N> | ::gotr status")
        }
    }

    private companion object {
        val BARRIER_LOCS = listOf("loc.gotr_barrier", "loc.gotr_barrier_closed", "loc.gotr_barrier_noentry")

        const val BARRIER_MIN_X = 3613
        const val BARRIER_MAX_X = 3617

        const val CROSS_PAUSE_TICKS = 3

        const val ARENA_MIN_Z = 9484

        const val LOBBY_MAX_Z = 9482
    }
}
