package org.rsmod.content.interfaces.makeover

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.api.table.FacialHairStylesRow
import org.rsmod.api.table.HairStylesRow
import org.rsmod.api.table.HandStylesRow
import org.rsmod.api.table.LeggingStylesRow
import org.rsmod.api.table.ShoeStylesRow
import org.rsmod.api.table.SleeveStylesRow
import org.rsmod.api.table.TorsoStylesRow

internal data class MakeoverStyle(val kitTypeA: Int, val kitTypeB: Int) {
    fun kitFor(bodyType: Int): Int = if (bodyType == BODY_TYPE_B) kitTypeB else kitTypeA

    internal companion object {
        const val BODY_TYPE_B: Int = 1
    }
}

internal class MakeoverStyleTable(private val styles: List<MakeoverStyle>) {
    val size: Int
        get() = styles.size

    fun styleAt(index: Int): MakeoverStyle? = styles.getOrNull(index)

    fun kitAt(index: Int, bodyType: Int): Int? = styleAt(index)?.kitFor(bodyType)

    fun indexOfKit(kit: Int, bodyType: Int): Int =
        styles.indexOfFirst { it.kitFor(bodyType) == kit }.takeIf { it >= 0 } ?: 0

    fun typeAColumn(): List<Int> = styles.map { it.kitTypeA }
}

internal object MakeoverStyles {
    private val logger = InlineLogger()

    val PROVEN_HAIR_TYPE_A: List<Int> =
        listOf(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
            129, 130, 131, 132, 133, 134, 151, 144, 145, 146, 147, 148, 149, 150,
            221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233,
            201, 202, 216, 217, 203, 204, 205, 206, 207, 208,
            209, 210, 211, 212, 213, 214, 215, 218, 219, 220,
        )

    private val HAIR_TYPE_B: List<Int> =
        listOf(
            45, 47, 48, 49, 174, 51, 52, 53, 54, 175,
            120, 176, 177, 178, 128, 179, 152, 180, 181, 182,
            183, 184, 185, 186, 46, 50, 55, 118, 119, 121,
            122, 123, 124, 125, 126, 127, 143, 154, 155, 169,
            170, 156, 157, 158, 159, 160, 161, 162, 163, 164,
            165, 166, 167, 168, 171, 172, 173,
        )

    private val JAW_TYPE_A: List<Int> =
        listOf(14, 10, 11, 111, 12, 13, 15, 16, 17, 112, 113, 114, 115, 116, 117)

    private val JAW_TYPE_B: List<Int> =
        listOf(296, 292, 293, 300, 294, 295, 297, 298, 299, 301, 302, 303, 304, 305, 306)

    private val TORSO_TYPE_A: List<Int> =
        listOf(
            18, 24, 105, 106, 107, 19, 20, 21, 22, 25,
            108, 109, 110, 254, 255, 256, 257, 258, 259, 23,
        )

    private val TORSO_TYPE_B: List<Int> =
        listOf(
            56, 60, 89, 90, 91, 265, 266, 267, 268, 270,
            271, 272, 273, 57, 58, 59, 92, 93, 94, 269,
        )

    private val ARMS_TYPE_A: List<Int> =
        listOf(26, 27, 29, 28, 32, 85, 30, 31, 86, 87, 88, 260, 84, 263, 264, 262, 261)

    private val ARMS_TYPE_B: List<Int> =
        listOf(61, 63, 65, 248, 97, 96, 249, 250, 251, 252, 253, 62, 95, 98, 99, 66, 64)

    private val LEGS_TYPE_A: List<Int> =
        listOf(
            36, 38, 39, 40, 37, 41, 100, 101, 102, 103,
            104, 281, 282, 283, 284, 285, 286, 287, 288, 289,
            290, 291,
        )

    private val LEGS_TYPE_B: List<Int> =
        listOf(
            70, 72, 76, 75, 274, 275, 276, 277, 278, 279,
            280, 71, 73, 74, 77, 78, 135, 136, 137, 138,
            139, 140,
        )

    private val FEET_TYPE_A: List<Int> = listOf(42, 43)
    private val FEET_TYPE_B: List<Int> = listOf(79, 80)

    private val HANDS_TYPE_A: List<Int> = listOf(33, 34)
    private val HANDS_TYPE_B: List<Int> = listOf(67, 68)

    private val tables: Map<MakeoverPage, MakeoverStyleTable> =
        mapOf(
            MakeoverPage.Hair to table(PROVEN_HAIR_TYPE_A, HAIR_TYPE_B),
            MakeoverPage.FacialHair to table(JAW_TYPE_A, JAW_TYPE_B),
            MakeoverPage.Top to table(TORSO_TYPE_A, TORSO_TYPE_B),
            MakeoverPage.Arms to table(ARMS_TYPE_A, ARMS_TYPE_B),
            MakeoverPage.Bottoms to table(LEGS_TYPE_A, LEGS_TYPE_B),
            MakeoverPage.Feet to table(FEET_TYPE_A, FEET_TYPE_B),
            MakeoverPage.Wrist to table(HANDS_TYPE_A, HANDS_TYPE_B),
        )

    fun tableFor(page: MakeoverPage): MakeoverStyleTable? = tables[page]

    fun load() {
        checkStyleCounts()
        logCacheCrossCheck()
    }

    private fun checkStyleCounts() {
        for ((page, table) in tables) {
            if (table.size != page.styleCount) {
                logger.error {
                    "Makeover page $page: measured table has ${table.size} styles but live's " +
                        "component-7 range implies ${page.styleCount}. Picks past " +
                        "${table.size - 1} will be refused."
                }
            }
        }
    }

    // The dbtable contains the same kits in a different order; compare membership, not grid order.
    private fun logCacheCrossCheck() {
        val cache = runCatching(::readCacheTables).getOrElse { cause ->
            logger.info(cause) {
                "Makeover style dbtables could not be read for the boot cross-check. Harmless: the " +
                    "window runs off the capture-measured tables either way."
            }
            return
        }
        for ((page, cacheTable) in cache) {
            val measured = tables[page] ?: continue
            val cacheKits = cacheTable.typeAColumn().toSet()
            val measuredKits = measured.typeAColumn().toSet()
            if (cacheKits == measuredKits) {
                continue
            }
            logger.info {
                "Makeover page $page: dbtable and the live capture describe different style sets " +
                    "(cache-only=${cacheKits - measuredKits}, capture-only=${measuredKits - cacheKits}). " +
                    "The capture is authoritative; re-derive the tables if the revision moved."
            }
        }
    }

    private fun readCacheTables(): Map<MakeoverPage, MakeoverStyleTable> =
        mapOf(
            MakeoverPage.Hair to
                HairStylesRow.all().toTable({ it.playerKitIdTypeA }, { it.playerKitIdTypeB }),
            MakeoverPage.FacialHair to
                FacialHairStylesRow.all().toTable({ it.playerKitIdTypeA }, { it.playerKitIdTypeB }),
            MakeoverPage.Top to
                TorsoStylesRow.all().toTable({ it.playerKitIdTypeA }, { it.playerKitIdTypeB }),
            MakeoverPage.Arms to
                SleeveStylesRow.all().toTable({ it.playerKitIdTypeA }, { it.playerKitIdTypeB }),
            MakeoverPage.Bottoms to
                LeggingStylesRow.all().toTable({ it.playerKitIdTypeA }, { it.playerKitIdTypeB }),
            MakeoverPage.Feet to
                ShoeStylesRow.all().toTable({ it.playerKitIdTypeA }, { it.playerKitIdTypeB }),
            MakeoverPage.Wrist to
                HandStylesRow.all().toTable({ it.playerKitIdTypeA }, { it.playerKitIdTypeB }),
        )

    private fun <T> List<T>.toTable(typeA: (T) -> Int, typeB: (T) -> Int): MakeoverStyleTable =
        MakeoverStyleTable(map { MakeoverStyle(typeA(it), typeB(it)) })

    private fun table(typeA: List<Int>, typeB: List<Int>): MakeoverStyleTable {
        require(typeA.size == typeB.size) {
            "Makeover style columns must be the same length (a=${typeA.size}, b=${typeB.size})."
        }
        return MakeoverStyleTable(typeA.indices.map { MakeoverStyle(typeA[it], typeB[it]) })
    }
}
