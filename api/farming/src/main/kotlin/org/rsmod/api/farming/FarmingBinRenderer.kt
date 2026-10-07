package org.rsmod.api.farming

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * Find the named state loc in this bin's transform table. Big-bin bands are non-contiguous, so
 * base-plus-count arithmetic is invalid.
 */
@Singleton
public class FarmingBinRenderer @Inject constructor(private val vars: FarmingTransmitVars) {
    private val logger = InlineLogger()

    public fun stateValue(def: FarmingBinDef, state: FarmingBinState): Int {
        val table = def.transforms
        if (table == null) {
            logger.warn { "No transform table for compost bin ${def.key} (${def.locName})." }
            return FarmingConstants.BIN_EMPTY_TRANSFORM_INDEX
        }
        val value = stateValue(table, def.type, state)
        if (
            value == null || value > FarmingConstants.MAX_TRANSFORM_INDEX || table.isFiller(value)
        ) {
            logger.warn {
                "Compost bin state $state cannot be drawn in ${def.key} (${def.locName}); " +
                    "resolved index $value. Rendering the bin as empty."
            }
            return FarmingConstants.BIN_EMPTY_TRANSFORM_INDEX
        }
        return value
    }

    public fun render(player: Player, def: FarmingBinDef, state: FarmingBinState) {
        val slot = def.transmitSlot
        if (slot == null) {
            logger.warn { "Compost bin ${def.key} (${def.locName}) has no transmit slot." }
            return
        }
        val value = stateValue(def, state)
        if (vars.get(player, slot) == value) {
            return
        }
        vars.set(player, slot, value)
    }

    public companion object {
        public fun stateValue(
            table: FarmingTransformTable,
            type: FarmingBinType,
            state: FarmingBinState,
        ): Int? = stateValue(table, type, state, FarmingTransformTable::locIdOrNull)

        internal fun stateValue(
            table: FarmingTransformTable,
            type: FarmingBinType,
            state: FarmingBinState,
            resolve: (String) -> Int?,
        ): Int? {
            val locName = stateLocName(type, state) ?: return null
            val locId = resolve(locName) ?: return null
            return table.indexOf(locId).takeIf { it >= 0 }
        }

        internal fun stateLocName(type: FarmingBinType, state: FarmingBinState): String? {
            val count = state.count
            if (state.phase == FarmingBinPhase.FILLING) {
                if (count <= 0 || state.fill == FarmingBinFill.NONE) {
                    return type.emptyLocName
                }
                // Invalid counts must reach the caller's warning instead of rendering an empty bin.
                return type.fillLocName(state.fill, count)
            }
            val product = state.product ?: return null
            return when (state.phase) {
                FarmingBinPhase.ROTTING -> type.rottingLocName(product)
                FarmingBinPhase.READY -> type.readyLocName(product)
                FarmingBinPhase.OPEN ->
                    if (count <= 0) {
                        type.emptyLocName
                    } else {
                        type.productLocName(product, count)
                    }
                FarmingBinPhase.FILLING -> type.emptyLocName
            }
        }
    }
}
