package org.rsmod.content.minigames.mahoganyhomes.core

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesFurniture
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesHouse

private val logger = InlineLogger()

// Classify from transform count; editable Build state is index 3, not the wiki's index 2.
public enum class MahoganyHomesHotspotKind {
    BUILD,
    REPAIR,
}

public object MahoganyHomesBuildState {
    public const val EDITABLE: Int = 3
    public const val EMPTY: Int = 4
    public const val REGULAR: Int = 5
    public const val OAK: Int = 6
    public const val TEAK: Int = 7
    public const val MAHOGANY: Int = 8

    public fun forTierOrdinal(tierOrdinal: Int): Int = REGULAR + tierOrdinal
}

public object MahoganyHomesRepairState {
    public const val FIXED: Int = 0
    public const val BROKEN: Int = 1
}

public class MahoganyHomesHotspotDef internal constructor(public val locName: String, public val slot: Int) {
    public val locId: Int? by lazy {
        runCatching { locName.asRSCM(RSCMType.LOC) }.getOrNull()?.takeIf { it >= 0 }
    }

    private val transforms: IntArray? by lazy {
        val id = locId ?: return@lazy null
        ServerCacheManager.getObject(id)?.multiLoc?.takeIf { it.isNotEmpty() }
    }

    public val kind: MahoganyHomesHotspotKind? by lazy {
        val size = transforms?.size ?: return@lazy null
        if (size >= 9) MahoganyHomesHotspotKind.BUILD else MahoganyHomesHotspotKind.REPAIR
    }

    public val furnitureKind: String? by lazy {
        val t = transforms ?: return@lazy null
        val k = kind ?: return@lazy null
        val sampleId =
            when (k) {
                MahoganyHomesHotspotKind.BUILD -> t.getOrNull(MahoganyHomesBuildState.REGULAR)
                MahoganyHomesHotspotKind.REPAIR -> t.getOrNull(MahoganyHomesRepairState.FIXED)
            }
        if (sampleId == null || sampleId < 0) return@lazy null
        val symbol = RSCM.getReverseMapping(RSCMType.LOC, sampleId) ?: return@lazy null
        extractFurnitureKind(symbol)
    }

    public fun stateLocId(stateIndex: Int): Int? = transforms?.getOrNull(stateIndex)?.takeIf { it >= 0 }

    override fun toString(): String = "MahoganyHomesHotspotDef(loc=$locName, slot=$slot, kind=$kind, furniture=$furnitureKind)"

    public companion object {
        private val HOUSE_PREFIXES =
            MahoganyHomesHouse.entries
                .flatMap { it.hotspots }
                .map { it.removePrefix("loc.").substringBeforeLast("_hotspot") }
                .distinct()

        private val STATE_SUFFIXES =
            listOf(
                "_broken",
                "_editable",
                "_regular",
                "_oak",
                "_teak",
                "_mahogany",
                "_fixed",
                "_default",
                "_empty",
            )

        internal fun extractFurnitureKind(symbol: String): String? {
            var remainder = symbol.removePrefix("loc.").removePrefix("con_contract_")
            val prefixMatch =
                HOUSE_PREFIXES.map { it.removePrefix("con_contract_") }
                    .filter { remainder.startsWith("${it}_") }
                    .maxByOrNull { it.length }
            if (prefixMatch != null) {
                remainder = remainder.removePrefix("${prefixMatch}_")
            }
            val suffixMatch = STATE_SUFFIXES.firstOrNull { remainder.endsWith(it) }
            if (suffixMatch != null) {
                remainder = remainder.removeSuffix(suffixMatch)
            }
            return remainder.takeIf { it.isNotBlank() }
        }
    }
}

public object MahoganyHomesHotspots {

    private val byLocName: Map<String, MahoganyHomesHotspotDef> =
        MahoganyHomesHouse.entries
            .flatMap { house -> house.hotspots.mapIndexed { index, loc -> loc to (index + 1) } }
            .associate { (loc, slot) -> loc to MahoganyHomesHotspotDef(loc, slot) }

    public fun get(locName: String): MahoganyHomesHotspotDef? = byLocName[locName]

    public fun validateAtBoot() {
        val failures = mutableListOf<String>()
        for (def in byLocName.values) {
            val id = def.locId
            if (id == null) {
                failures += "hotspot symbol did not resolve: ${def.locName}"
                continue
            }
            val kind = def.kind
            if (kind == null) {
                failures += "hotspot ${def.locName} ($id) has no configChangeDest"
                continue
            }
            val furnitureKind = def.furnitureKind
            if (furnitureKind == null) {
                failures += "hotspot ${def.locName} ($id) could not derive a furniture kind"
                continue
            }
            val classifies =
                when (kind) {
                    MahoganyHomesHotspotKind.BUILD ->
                        MahoganyHomesFurniture.buildPlankCost(furnitureKind) != null
                    MahoganyHomesHotspotKind.REPAIR ->
                        MahoganyHomesFurniture.repairsWithBar(furnitureKind) ||
                            MahoganyHomesFurniture.repairPlankCost(furnitureKind) != null
                }
            if (!classifies) {
                failures +=
                    "hotspot ${def.locName} ($id) derives $kind furniture kind " +
                        "'$furnitureKind' with no cost bucket - its op handler would silently " +
                        "no-op (add a MahoganyHomesFurniture bucket or KIND_ALIASES entry)"
            }
        }
        if (failures.isNotEmpty()) {
            failures.forEach { logger.error { "Mahogany Homes: $it" } }
            throw IllegalStateException(
                "Mahogany Homes: ${failures.size} hotspot(s) failed boot validation " +
                    "(see log above): ${failures.joinToString("; ")}"
            )
        }
    }
}
