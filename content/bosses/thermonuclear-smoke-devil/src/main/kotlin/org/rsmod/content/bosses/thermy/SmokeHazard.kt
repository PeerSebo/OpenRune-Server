package org.rsmod.content.bosses.thermy

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SmokeHazard @Inject constructor(
    private val players: PlayerList,
    private val clock: MapClock,
    private val hits: DamageOnlyPlayerHitProcessor,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { tick() }
        onPlayerLogin { player.nextSmokeDamage = 0 }
        onPlayerLogout { player.nextSmokeDamage = 0 }
    }

    internal fun tick() {
        for (player in players) {
            if (!exposed(player)) {
                player.nextSmokeDamage = 0
                continue
            }
            if (player.nextSmokeDamage == 0) player.nextSmokeDamage = clock.cycle + INTERVAL
            if (clock.cycle < player.nextSmokeDamage) continue
            player.nextSmokeDamage = clock.cycle + INTERVAL
            // Apply now, so leaving or dying cannot leave a delayed environmental hit behind.
            player.takeInstantHit(HitType.Typeless, 10, hits)
            player.mes("The smoke is damaging your lungs! You need a facemask or Slayer helmet.")
        }
    }

    private fun exposed(player: Player): Boolean = player.isValidTarget() &&
        !player.adminGodMode && inCave(player.coords) && !SmokeProtection.environment(player.hat?.id)

    internal companion object {
        const val INTERVAL = 20
        fun inCave(tile: CoordGrid): Boolean = tile.level == 0 &&
            tile.x in 2330..2431 && tile.z in 9408..9471
    }
}

private var Player.nextSmokeDamage by intVarp("varp.thermy_next_smoke_damage")

internal object SmokeProtection {
    private val helmetSymbols = run {
        val colours = listOf("", "_black", "_green", "_red", "_purple", "_turquoise", "_hydra",
            "_twisted", "_jad", "_verzik", "_zuk", "_araxyte", "_hooded")
        colours.map { "obj.slayer_helm$it" } +
            listOf("", "sw_", "pvpa_").flatMap { family -> colours.map { "obj.${family}slayer_helm_i$it" } } +
            listOf("obj.league_6_slayer_helm1", "obj.league_6_slayer_helm2") +
            listOf("", "sw_", "pvpa_").flatMap { family -> (1..2).map { "obj.${family}league_6_slayer_helm${it}_i" } }
    }
    private val faceProtection by lazy { (helmetSymbols + "obj.slayer_facemask").map { it.asRSCM() }.toSet() }
    fun combat(hat: Int?): Boolean = hat in faceProtection
    fun environment(hat: Int?): Boolean = combat(hat) || hat == "obj.gasmask".asRSCM()
}
