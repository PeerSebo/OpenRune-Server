package org.rsmod.api.game.process.player

import dev.openrune.util.Wearpos
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.inv.weight.InvWeight
import org.rsmod.api.player.isRunEnergyRestoreBlocked
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.type.getOrNull

public class PlayerRunUpdateProcessor {
    public fun process(player: Player) {
        player.updateRunWeight()
        player.updateRunEnergy()
    }

    private fun Player.updateRunEnergy() {
        val startRunEnergy = runEnergy

        // Wiki (Energy): "Run energy will not regenerate while the player is running, crossing an
        // agility obstacle, or flying on a Magic carpet."
        // Running takes the drain branch, but agility can use telejumps or one walking step per
        // tick. Those scripts must suppress restoration explicitly through the map-clock deadline
        // in api/player RunEnergyRestore.kt; the gate does not alter running's drain formula.
        if (pendingStepCount > 1) {
            decreaseRunEnergy()
        } else if (!isRunEnergyRestoreBlocked) {
            restoreRunEnergy()
        }

        if (runEnergy != startRunEnergy) {
            UpdateRun.energy(this, runEnergy)
        }
    }

    private fun Player.decreaseRunEnergy() {
        // Retain fractional kilograms until the drain calculation; rounding weight first
        // changes energy loss. Cap effective weight at 64 kg.
        val grams = runWeight.coerceIn(0, MAX_RUN_WEIGHT_GRAMS).toLong()
        val agilityFactor = (300 - agilityLvl).toLong()
        val numerator = (60L * MAX_RUN_WEIGHT_GRAMS + 67L * grams) * agilityFactor
        var loss = (numerator / (MAX_RUN_WEIGHT_GRAMS.toLong() * 300L)).toInt()
        if (hasImprovedStaminaEffect()) {
            loss = (loss * 85) / 100
        } else if (hasStaminaEffect()) {
            loss = (loss * 30) / 100
        }

        runEnergy = max(0, runEnergy - loss)
        if (runEnergy == 0 && isRunning()) {
            setActiveMoveSpeed(MoveSpeed.Walk)
        }
    }

    private fun Player.hasImprovedStaminaEffect(): Boolean {
        return vars["varbit.improved_stamina_passive"] == 1
    }

    private fun Player.hasStaminaEffect(): Boolean {
        return vars["varbit.stamina_active"] == 1
    }

    private fun Player.isRunning(): Boolean {
        return varMoveSpeed == MoveSpeed.Run || moveSpeed == MoveSpeed.Run
    }

    private fun Player.restoreRunEnergy() {
        if (runEnergy >= constants.run_max_energy) {
            return
        }
        val baseRecover = 15 + (agilityLvl / 10)
        val recover = (restorationRateMod() * baseRecover).toInt()
        runEnergy = min(constants.run_max_energy, runEnergy + recover)
    }

    private fun Player.restorationRateMod(): Double {
        var wornRate = 0
        var pieces = 0

        for (wearpos in gracefulWearpos) {
            val type = getOrNull(worn[wearpos.slot]) ?: continue
            val rate = type.paramOrNull(params.graceful_restore_rate) ?: continue
            wornRate += rate
            pieces++
        }

        if (pieces == gracefulWearpos.size) {
            wornRate = FULL_GRACEFUL_RESTORE_RATE
        }

        return 1.0 + (wornRate / 10_000.0)
    }

    private fun Player.updateRunWeight() {
        if (!pendingRunWeight) {
            return
        }
        val currentGrams = calculateWeightInGrams()
        val previousGrams = runWeight
        runWeight = currentGrams

        val currentKg = currentGrams / 1000
        val previousKg = previousGrams / 1000
        if (previousKg != currentKg) {
            UpdateRun.weight(this, kg = currentKg)
        }
    }

    private fun Player.calculateWeightInGrams(): Int {
        return InvWeight.calculateWeightInGrams(this)
    }

    private companion object {
        /** Weight above 64 kg does not drain any faster; live caps the drain at 126/tick. */
        private const val MAX_RUN_WEIGHT_GRAMS = 64_000

        private const val FULL_GRACEFUL_RESTORE_RATE = 3000

        private val gracefulWearpos =
            listOf(
                Wearpos.Hat,
                Wearpos.Torso,
                Wearpos.Legs,
                Wearpos.Hands,
                Wearpos.Feet,
                Wearpos.Back,
            )
    }
}
