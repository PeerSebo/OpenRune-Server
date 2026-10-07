package org.rsmod.content.minigames.gotr.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.events.skilling.SkillingProductPrepareEvent
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionLoc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GotrMiningScript @Inject constructor(private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SkillingProductPrepareEvent> {
            val source = product.source as? SkillingProductSource.Mining ?: return@onEvent
            val rock = RSCM.getReverseMapping(RSCMType.LOC, source.rock.id)
            val range = YIELD_RANGES[rock] ?: return@onEvent

            val player = product.player
            if (player.vars[IS_PLAYING_VARBIT] == 0 || player.vars[FINISHED_GAME_VARBIT] != 0) {
                product.cancelled = true
                product.depletes = false
                return@onEvent
            }
            val yield = random.of(range.first, range.last)
            product.count = if (product.item == "obj.gotr_guardian_essence") {
                minOf(yield, player.inv.freeSpace())
            } else yield
            if (product.count == 0) {
                product.cancelled = true
                return@onEvent
            }
            val minedSoFar = player.vars[MINING_COUNT_VARBIT]
            if (minedSoFar >= FRAGMENT_XP_CAP) {
                product.grantsExperience = false
                return@onEvent
            }

            val newCount = (minedSoFar + product.count).coerceAtMost(MINING_COUNT_MAX)
            VarPlayerIntMapSetter.set(player, MINING_COUNT_VARBIT, newCount)
        }
    }

    internal companion object {
        fun stopMining(player: Player) {
            val interaction = player.interaction as? InteractionLoc ?: return
            val rock = RSCM.getReverseMapping(RSCMType.LOC, interaction.target.id)
            if (rock !in YIELD_RANGES) return
            player.clearInteractionRoute()
            player.resetAnim()
        }

        private val YIELD_RANGES =
            mapOf(
                "loc.gotr_essence_tier_1" to 1..3,
                "loc.gotr_essence_tier_1b" to 1..3,
                "loc.gotr_essence_tier_1_large" to 1..3,
                "loc.gotr_essence_tier_1_large_b" to 1..3,
                "loc.gotr_essence_tier_2" to 2..4,
                "loc.gotr_essence_tier_3" to 4..6,
            )

        private const val IS_PLAYING_VARBIT = "varbit.gotr_is_playing"
        private const val FINISHED_GAME_VARBIT = "varbit.gotr_player_has_finished_game"
        private const val MINING_COUNT_VARBIT = "varbit.gotr_mining_count"
        private const val FRAGMENT_XP_CAP = 250

        // Truncate the last increment at 250, not the 9-bit ceiling.
        private const val MINING_COUNT_MAX = 250
    }
}
