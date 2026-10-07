package org.rsmod.content.travel.fairyrings

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.definition.constants.ConstantProvider
import org.rsmod.api.table.FairyringRow
import org.rsmod.map.CoordGrid

data class FairyRing(
    val code: String,
    val dials: List<Int>,
    val logIndex: Int,
    val destination: CoordGrid?,
    val description: String?,
    val visitedVarBit: String?,
) {
    val spacedCode: String = code.toCharArray().joinToString(" ")

    val logComponent: String = "component.fairyrings_log:${code.lowercase()}"

    val favouriteIconComponent: String = "component.fairyrings_log:${code.lowercase()}_fave"

    val logText: String =
        buildString {
            append("<col=ff981f>").append(spacedCode).append("</col>")
            if (description != null) {
                append("<br>").append(description)
            }
        }
}

class FairyRings private constructor(val all: List<FairyRing>) {
    private val byTableId: Map<Int, FairyRing> = all.associateBy { FairyRingDials.tableId(it.dials) }
    private val byCode: Map<String, FairyRing> = all.associateBy { it.code }
    private val byLogIndex: Map<Int, FairyRing> = all.associateBy { it.logIndex }

    fun byDials(dials: List<Int>): FairyRing? = byTableId[FairyRingDials.tableId(dials)]

    fun byCode(code: String): FairyRing? = byCode[code.uppercase().filter { it != ' ' }]

    fun byLogIndex(index: Int): FairyRing? = byLogIndex[index]

    companion object {
        private val logger = InlineLogger()

        private val NOWHERE = CoordGrid(65, 65, 1)

        fun load(): FairyRings {
            val varbits = ConstantProvider.mappings["varbit"].orEmpty()
            val rings =
                FairyringRow.all().map { row ->
                    val code = row.code.filter { it != ' ' }.uppercase()
                    val dials = FairyRingDials.fromTableId(row.id)
                    check(FairyRingDials.code(dials) == code) {
                        "Fairy ring row ${row.rowId} spells ${FairyRingDials.code(dials)} but is $code."
                    }
                    val destination =
                        row.destCoord.takeUnless { it == NOWHERE || it.packed == 0 }
                    val visitedVarBit =
                        "varbit.fairyrings_log_${code.lowercase()}".takeIf { it in varbits }
                    FairyRing(
                        code = code,
                        dials = dials,
                        logIndex = row.multilocState,
                        destination = destination,
                        description = row.desc?.removePrefix("<br>")?.trim()?.ifEmpty { null },
                        visitedVarBit = visitedVarBit,
                    )
                }
            logger.debug {
                val reachable = rings.count { it.destination != null }
                "Loaded ${rings.size} fairy ring codes, $reachable with a destination: " +
                    rings
                        .filter { it.destination != null }
                        .joinToString { "${it.code}=${it.destination}" }
            }
            return FairyRings(rings)
        }
    }
}
