package org.rsmod.api.farming

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * Resolve named anchors in the clicked patch's transform table; family-wide numeric offsets are
 * unsafe.
 */
@Singleton
public class FarmingRenderer
@Inject
constructor(private val crops: FarmingCropRegistry, private val vars: FarmingTransmitVars) {
    private val logger = InlineLogger()

    public fun stateValue(def: FarmingPatchDef, state: FarmingPatchState): Int {
        val rake = state.rakeStage.coerceIn(0, FarmingConstants.RAKE_STAGES)
        val crop = crops.find(state.cropId) ?: return rake

        val table = def.transforms
        if (table == null) {
            logger.warn { "No transform table for farming patch ${def.key} (${def.locName})." }
            return rake
        }

        val value = cropValue(table, crop, state)
        if (
            value == null || value > FarmingConstants.MAX_TRANSFORM_INDEX || table.isFiller(value)
        ) {
            // An unmatched patch table must not render an arbitrary crop.
            logger.warn {
                "Farming crop '${crop.id}' cannot be drawn in ${def.key} (${def.locName}); " +
                    "resolved index $value. Rendering the patch as empty."
            }
            return FarmingConstants.EMPTY_TRANSFORM_INDEX
        }
        return value
    }

    private fun cropValue(
        table: FarmingTransformTable,
        crop: FarmingCrop,
        state: FarmingPatchState,
    ): Int? {
        val visuals = crop.visuals
        val stage = state.stage.coerceIn(0, crop.grownStage)
        val atGrown = stage >= crop.grownStage
        val produce = state.produceCount.coerceIn(0, crop.produceCapacity)

        if (state.dead) {
            return indexFor(table, visuals.dead, stage)
        }
        if (state.stump) {
            return indexFor(table, visuals.stump, 0)
        }
        if (state.diseased) {
            return indexFor(table, visuals.diseased, stage)
        }
        if (atGrown && crop.checkHealth) {
            // Check-health crops require distinct unchecked and checked mature states.
            if (!state.checkHealthClaimed) {
                val unchecked = indexFor(table, visuals.unchecked, 0)
                if (unchecked != null) {
                    return unchecked
                }
            } else {
                // Mature fruit and berry bands use produce count as the offset.
                val checked = indexFor(table, visuals.grownChecked, produce)
                if (checked != null) {
                    return checked
                }
            }
        }
        if (state.watered) {
            val watered = indexFor(table, visuals.watered, stage)
            if (watered != null) {
                return watered
            }
        }
        // Checked crops already apply produce count in their mature band.
        val growingOffset = if (atGrown && !crop.checkHealth) stage + produce else stage
        return indexFor(table, visuals.growing, growingOffset)
    }

    public fun render(player: Player, def: FarmingPatchDef, state: FarmingPatchState) {
        val slot = def.transmitSlot
        if (slot == null) {
            logger.warn { "Farming patch ${def.key} (${def.locName}) has no transmit slot." }
            return
        }
        val value = stateValue(def, state)
        if (vars.get(player, slot) == value) {
            return
        }
        vars.set(player, slot, value)
    }

    public companion object {
        public fun anchorBase(table: FarmingTransformTable, anchor: FarmingAnchor?): Int? =
            anchorBase(table, anchor, FarmingTransformTable::locIdOrNull)

        internal fun anchorBase(
            table: FarmingTransformTable,
            anchor: FarmingAnchor?,
            resolve: (String) -> Int?,
        ): Int? {
            if (anchor == null) {
                return null
            }
            val locId = resolve(anchor.loc) ?: return null
            return anchorBase(table, locId, anchor.stage)
        }

        /**
         * Interleaved anchors require explicit locs per offset; contiguous anchors alone permit
         * base-plus-offset.
         */
        public fun indexFor(
            table: FarmingTransformTable,
            anchor: FarmingAnchor?,
            offset: Int,
        ): Int? = indexFor(table, anchor, offset, FarmingTransformTable::locIdOrNull)

        internal fun indexFor(
            table: FarmingTransformTable,
            anchor: FarmingAnchor?,
            offset: Int,
            resolve: (String) -> Int?,
        ): Int? {
            if (anchor == null) {
                return null
            }
            if (!anchor.isPerStage) {
                return anchorBase(table, anchor, resolve)?.plus(offset)
            }
            val locName = anchor.stageLocs.getOrNull(offset - anchor.stage) ?: return null
            val locId = resolve(locName) ?: return null
            return table.indexOf(locId).takeIf { it >= 0 }
        }

        public fun anchorBase(table: FarmingTransformTable, locId: Int, stage: Int): Int? {
            val index = table.indexOf(locId)
            if (index < 0) {
                return null
            }
            return index - stage
        }
    }
}
