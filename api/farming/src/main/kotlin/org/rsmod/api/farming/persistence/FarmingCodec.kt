package org.rsmod.api.farming.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.rsmod.api.farming.FarmingBinFill
import org.rsmod.api.farming.FarmingBinKey
import org.rsmod.api.farming.FarmingBinPhase
import org.rsmod.api.farming.FarmingBinProduct
import org.rsmod.api.farming.FarmingBinState
import org.rsmod.api.farming.FarmingBins
import org.rsmod.api.farming.FarmingCompost
import org.rsmod.api.farming.FarmingConstants
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPatchState
import org.rsmod.api.farming.FarmingPatches

/**
 * Preserve explicit JSON keys and unknown crop/patch identities across compatible saves.
 * Transient scene membership is not persisted.
 */
internal object FarmingCodec {
    const val VERSION: Int = 1

    fun encode(mapper: ObjectMapper, patches: FarmingPatches?, bins: FarmingBins?): String =
        mapper.writeValueAsString(toDto(patches, bins))

    fun decode(mapper: ObjectMapper, json: String): FarmingDocument? {
        val dto = mapper.readValue(json, FarmingDocumentDto::class.java) ?: return null
        return FarmingDocument(patches = fromDto(dto), bins = binsFromDto(dto))
    }

    private fun toDto(patches: FarmingPatches?, bins: FarmingBins?): FarmingDocumentDto =
        FarmingDocumentDto(
            version = VERSION,
            patches = patchRows(patches),
            bins = binRows(bins),
        )

    private fun patchRows(patches: FarmingPatches?): List<FarmingPatchDto> {
        if (patches == null) {
            return emptyList()
        }
        return patches
            .asMap()
            .entries
            .filterNot { it.value.isPristine() }
            .map { (key, state) ->
                FarmingPatchDto(
                    key = key.id,
                    crop = state.cropId,
                    stage = state.stage,
                    rake = state.rakeStage,
                    watered = state.watered,
                    diseased = state.diseased,
                    dead = state.dead,
                    stump = state.stump,
                    lives = state.lives,
                    compost = state.compost.name,
                    protected = state.protected,
                    paidProtected = state.paidProtected,
                    lastGrowthMinute = state.lastGrowthMinute,
                    lastProduceMinute = state.lastProduceMinute,
                    checkHealthClaimed = state.checkHealthClaimed,
                    plantXpPaid = state.plantXpPaid,
                    plantXpOwedCrop = state.plantXpOwedCrop,
                    produce = state.produceCount,
                )
            }
    }

    private fun binRows(bins: FarmingBins?): List<FarmingBinDto> {
        if (bins == null) {
            return emptyList()
        }
        return bins
            .asMap()
            .entries
            .filterNot { it.value.isPristine() }
            .map { (key, state) ->
                FarmingBinDto(
                    key = key.id,
                    phase = state.phase.name,
                    fill = state.fill.name,
                    product = state.product?.name,
                    count = state.count,
                    closedMinute = state.closedMinute,
                )
            }
    }

    /** Keep unknown patch and crop identities; definitions may return after a compatible update. */
    private fun fromDto(dto: FarmingDocumentDto): FarmingPatches {
        val patches = FarmingPatches()
        for (row in dto.patches) {
            if (row.key.isBlank()) {
                continue
            }
            val state = FarmingPatchState()
            state.cropId = row.crop?.takeIf(String::isNotBlank)
            state.stage = row.stage.coerceAtLeast(0)
            state.rakeStage = row.rake.coerceIn(0, FarmingConstants.RAKE_STAGES)
            state.watered = row.watered
            state.diseased = row.diseased
            state.dead = row.dead
            state.stump = row.stump
            state.lives = row.lives.coerceAtLeast(0)
            state.compost = FarmingCompost.find(row.compost) ?: FarmingCompost.NONE
            state.protected = row.protected
            state.paidProtected = row.paidProtected
            state.lastGrowthMinute = row.lastGrowthMinute.coerceAtLeast(0)
            state.lastProduceMinute = row.lastProduceMinute.coerceAtLeast(0)
            state.checkHealthClaimed = row.checkHealthClaimed
            state.plantXpPaid = row.plantXpPaid
            state.plantXpOwedCrop = row.plantXpOwedCrop?.takeIf(String::isNotBlank)
            state.produceCount = row.produce.coerceAtLeast(0)
            patches.put(FarmingPatchKey(row.key), state)
        }
        return patches
    }

    /** Reset invalid bin phases or missing products to an empty usable bin without blocking login. */
    private fun binsFromDto(dto: FarmingDocumentDto): FarmingBins {
        val bins = FarmingBins()
        for (row in dto.bins) {
            if (row.key.isBlank()) {
                continue
            }
            val state = FarmingBinState()
            val phase = FarmingBinPhase.find(row.phase)
            state.phase = phase ?: FarmingBinPhase.FILLING
            state.fill = FarmingBinFill.find(row.fill) ?: FarmingBinFill.NONE
            state.product = FarmingBinProduct.find(row.product)
            state.count = row.count.coerceAtLeast(0)
            state.closedMinute = row.closedMinute.coerceAtLeast(0)
            // Unknown phases must clear the whole load to avoid a full bin with no usable action.
            val invalidFill =
                state.phase == FarmingBinPhase.FILLING && state.fill == FarmingBinFill.NONE
            val invalidProduct =
                state.phase != FarmingBinPhase.FILLING && state.product == null
            if (phase == null || state.count == 0 || invalidFill || invalidProduct) {
                state.clearToEmpty()
            }
            bins.put(FarmingBinKey(row.key), state)
        }
        return bins
    }
}

internal class FarmingDocument(val patches: FarmingPatches, val bins: FarmingBins)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class FarmingDocumentDto(
    @param:JsonProperty("v") val version: Int = FarmingCodec.VERSION,
    @param:JsonProperty("p") val patches: List<FarmingPatchDto> = emptyList(),
    @param:JsonProperty("b") val bins: List<FarmingBinDto> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class FarmingBinDto(
    @param:JsonProperty("k") val key: String = "",
    @param:JsonProperty("ph") val phase: String? = null,
    @param:JsonProperty("f") val fill: String? = null,
    @param:JsonProperty("pd") val product: String? = null,
    @param:JsonProperty("n") val count: Int = 0,
    @param:JsonProperty("cm") val closedMinute: Long = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class FarmingPatchDto(
    @param:JsonProperty("k") val key: String = "",
    @param:JsonProperty("c") val crop: String? = null,
    @param:JsonProperty("s") val stage: Int = 0,
    @param:JsonProperty("r") val rake: Int = 0,
    @param:JsonProperty("w") val watered: Boolean = false,
    @param:JsonProperty("di") val diseased: Boolean = false,
    @param:JsonProperty("de") val dead: Boolean = false,
    @param:JsonProperty("st") val stump: Boolean = false,
    @param:JsonProperty("l") val lives: Int = 0,
    @param:JsonProperty("cp") val compost: String? = null,
    @param:JsonProperty("pr") val protected: Boolean = false,
    // Persist paid protection as "pp"; "pr" remains independent companion protection.
    @param:JsonProperty("pp") val paidProtected: Boolean = false,
    @param:JsonProperty("g") val lastGrowthMinute: Long = 0,
    @param:JsonProperty("q") val lastProduceMinute: Long = 0,
    @param:JsonProperty("ch") val checkHealthClaimed: Boolean = false,
    @param:JsonProperty("xp") val plantXpPaid: Boolean = false,
    @param:JsonProperty("xc") val plantXpOwedCrop: String? = null,
    @param:JsonProperty("n") val produce: Int = 0,
)
