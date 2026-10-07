package org.rsmod.api.farming

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Validate every crop's declared anchors against the cache before accepting runtime
 * interactions.
 */
@Singleton
public class FarmingCropRegistry @Inject constructor(private val patches: FarmingPatchRegistry) {
    private val logger = InlineLogger()

    private val crops = LinkedHashMap<String, FarmingCrop>()

    public fun register(crop: FarmingCrop): FarmingCrop {
        val previous = crops.put(crop.id, crop)
        check(previous == null || previous == crop) {
            "Two different farming crops registered under id '${crop.id}': $previous / $crop"
        }
        return crop
    }

    public fun registerAll(crops: Iterable<FarmingCrop>) {
        crops.forEach(::register)
    }

    public fun find(id: String?): FarmingCrop? = id?.let(crops::get)

    public fun all(): Collection<FarmingCrop> = crops.values

    public fun ofType(type: FarmingPatchType): List<FarmingCrop> =
        crops.values.filter { it.patchType == type }

    public fun validate() {
        validateHeads()
        for (type in crops.values.map(FarmingCrop::patchType).distinct()) {
            val table =
                referenceTable(type)
                    ?: error(
                        "Farming: $type has registered crops but no resolvable patch loc to " +
                            "validate them against."
                    )
            validateFamily(type, table, crops.values.filter { it.patchType == type })
        }
        logger.debug {
            "Validated ${crops.size} farming crop(s) against the cache transform tables."
        }
    }

    internal fun validateFamily(
        type: FarmingPatchType,
        table: FarmingTransformTable,
        family: List<FarmingCrop>,
        resolve: (String) -> Int? = FarmingTransformTable::locIdOrNull,
    ) {
        val claims = HashMap<Int, IndexClaim>()
        for (crop in family) {
            check(crop.patchType == type) {
                "Farming crop '${crop.id}' is ${crop.patchType}, not $type."
            }
            validateCrop(crop, table, claims, resolve)
        }
    }

    private data class IndexClaim(
        val cropId: String,
        val anchorName: String,
        val anchorLoc: String,
    )

    /** Validate the common weed/empty prefix only for families with registered crops. */
    private fun validateHeads() {
        val inUse = crops.values.mapTo(HashSet(), FarmingCrop::patchType)
        for (type in FarmingPatchType.entries) {
            if (type !in inUse) {
                continue
            }
            val reference = referenceTable(type) ?: continue
            val expected =
                FarmingTransformTable.locIdOrNull(type.emptyLocName)
                    ?: error(
                        "Farming: empty-patch anchor '${type.emptyLocName}' for $type does not " +
                            "resolve to a loc id."
                    )
            val actual = reference.at(FarmingConstants.EMPTY_TRANSFORM_INDEX)
            check(actual == expected) {
                "Farming: $type transform table (loc ${reference.locId}) does not have " +
                    "'${type.emptyLocName}' ($expected) at index " +
                    "${FarmingConstants.EMPTY_TRANSFORM_INDEX}; found $actual. The 4-entry weeds " +
                    "head assumption is broken for this family."
            }
        }
    }

    private fun validateCrop(
        crop: FarmingCrop,
        table: FarmingTransformTable,
        claims: MutableMap<Int, IndexClaim>,
        resolve: (String) -> Int?,
    ) {
        if (crop.waterable && crop.visuals.watered == null) {
            logger.warn {
                "Farming crop '${crop.id}' is marked waterable but has no watered anchor; " +
                    "watering it will render as unwatered."
            }
        }

        // Disease-capable crops require a dead state; poison ivy uses zero disease chance.
        check(!crop.canBeDiseased || crop.canDie) {
            "Farming crop '${crop.id}' has a diseased anchor and a non-zero disease chance but no " +
                "dead anchor. A diseased plant does not grow and the only exit from disease is " +
                "death, so this crop would freeze permanently. Give it a dead anchor, or set " +
                "diseaseChance = 0 to make it immune."
        }

        // Check-health crops require distinct unchecked and checked mature states.
        check(!crop.checkHealth || crop.visuals.unchecked != null) {
            "Farming crop '${crop.id}' pays Check-health but has no 'unchecked' anchor, so a fully " +
                "grown plant would never show the Check-health state."
        }
        check(crop.visuals.unchecked == null || crop.visuals.grownChecked != null) {
            "Farming crop '${crop.id}' has an 'unchecked' anchor but no 'grownChecked' one. After " +
                "the player claims Check-health there would be nothing to render, so the patch " +
                "would stay stuck on the Check-health state permanently - a tree could never be " +
                "chopped. Point it at the '<crop>_fullygrown' state that carries Chop down / " +
                "Chop-down / Pick-<fruit>."
        }

        for ((name, anchor) in crop.visuals.anchors()) {
            for (offset in reachableOffsets(crop, name)) {
                val index = FarmingRenderer.indexFor(table, anchor, offset, resolve)
                check(index != null) {
                    "Farming crop '${crop.id}' anchor '$name' cannot resolve offset $offset. " +
                        (if (anchor.isPerStage) {
                            "Its explicit stage list has ${anchor.stageLocs.size} entries starting " +
                                "at offset ${anchor.stage}; one of them is missing from the " +
                                "${crop.patchType} transform table (loc ${table.locId})."
                        } else {
                            "'${anchor.loc}' is not in the ${crop.patchType} transform table " +
                                "(loc ${table.locId}) - either the symbol is wrong or the crop is " +
                                "not plantable in this patch family."
                        })
                }
                claimIndex(crop, table, name, anchor, offset, index, claims)
            }
        }
    }

    private fun claimIndex(
        crop: FarmingCrop,
        table: FarmingTransformTable,
        name: String,
        anchor: FarmingAnchor,
        offset: Int,
        index: Int,
        claims: MutableMap<Int, IndexClaim>,
    ) {
        check(index in 0..FarmingConstants.MAX_TRANSFORM_INDEX) {
            "Farming crop '${crop.id}' anchor '$name' would transmit $index at offset $offset, " +
                "outside the byte-wide transmit varbit."
        }
        check(index > FarmingConstants.EMPTY_TRANSFORM_INDEX) {
            "Farming crop '${crop.id}' anchor '$name' resolves offset $offset to index $index, " +
                "which is inside the patch's weeds/empty head (0.." +
                "${FarmingConstants.EMPTY_TRANSFORM_INDEX}). The block runs backwards off its start."
        }
        check(!table.isFiller(index)) {
            "Farming crop '${crop.id}' anchor '$name' resolves offset $offset to index $index, " +
                "which is the ${crop.patchType} filler loc (${table.filler}). The block is shorter " +
                "than the crop's ${crop.growthStages} stage(s) plus ${crop.produceCapacity} " +
                "produce state(s), so the anchor or the counts are wrong."
        }

        val claim = IndexClaim(crop.id, name, anchor.loc)
        val existing = claims.put(index, claim)
        if (existing == null || existing == claim) {
            return
        }
        val sharedBlock = existing.anchorName == name && existing.anchorLoc == anchor.loc
        check(sharedBlock) {
            "Farming crop '${crop.id}' anchor '$name' (offset $offset) resolves to index $index, " +
                "which '${existing.cropId}' already claims through its '${existing.anchorName}' " +
                "anchor ('${existing.anchorLoc}'). Index $index renders loc ${table.at(index)}. " +
                "Two crops sharing an index is only legal when both name the same loc for the same " +
                "state (as every herb does for 'loc.herb_1_dead'); otherwise one of these blocks " +
                "runs into the other's states and would silently draw the wrong crop. If this " +
                "crop's block is interleaved with another's, declare it with FarmingAnchor.stages."
        }
    }

    /**
     * Validate reachable offsets only: stage zero and mature stages do not disease, and checked
     * crops leave the growing band. Widen validation if mature-plant disease is implemented.
     */
    private fun reachableOffsets(crop: FarmingCrop, anchorName: String): IntRange {
        val grown = crop.grownStage
        return when (anchorName) {
            FarmingCropVisuals.GROWING ->
                if (crop.checkHealth) {
                    0..(grown - 1).coerceAtLeast(0)
                } else {
                    0..(grown + crop.produceCapacity)
                }
            FarmingCropVisuals.WATERED -> 0..(grown - 1).coerceAtLeast(0)
            FarmingCropVisuals.DISEASED,
            FarmingCropVisuals.DEAD -> 1..(grown - 1).coerceAtLeast(1)
            FarmingCropVisuals.GROWN_CHECKED -> 0..crop.produceCapacity
            else -> 0..0
        }
    }

    private fun referenceTable(type: FarmingPatchType): FarmingTransformTable? {
        val def = patches.all().firstOrNull { it.type == type && it.transforms != null }
        return def?.transforms
    }
}
