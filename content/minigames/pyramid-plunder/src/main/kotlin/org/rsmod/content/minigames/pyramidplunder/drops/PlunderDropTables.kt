package org.rsmod.content.minigames.pyramidplunder.drops

import org.rsmod.api.random.GameRandom

object PlunderDropTables {
    val ARTEFACTS: List<String> =
        listOf(
            "obj.ntk_ivory_comb",
            "obj.ntk_scarab_pottery",
            "obj.ntk_statuette_pottery",
            "obj.ntk_seal_stone",
            "obj.ntk_scarab_stone",
            "obj.ntk_statuette_stone",
            "obj.ntk_seal_gold",
            "obj.ntk_scarab_gold",
            "obj.ntk_statuette_gold",
        )

    const val SCEPTRE = "obj.pharaohs_sceptre_charged_initial"

    fun chestArtefact(room: Int, random: GameRandom): String {
        val odd = room % 2 == 1
        return if (odd) {
            val centre = 4 + (room - 1) / 2
            val tier =
                when (random.of(8)) {
                    0,
                    1,
                    2 -> centre - 1
                    7 -> centre + 1
                    else -> centre
                }
            artefact(tier)
        } else {
            val lower = 3 + room / 2
            artefact(if (random.of(8) < 5) lower else lower + 1)
        }
    }

    fun sarcophagusArtefact(room: Int, random: GameRandom): String {
        val odd = room % 2 == 1
        return if (odd) {
            val centre = 4 + (room - 1) / 2 - 2
            val tier =
                when (random.of(4)) {
                    0 -> centre - 1
                    3 -> centre + 1
                    else -> centre
                }
            artefact(tier)
        } else {
            val lower = 3 + room / 2 - 2
            artefact(if (random.of(2) == 0) lower else lower + 1)
        }
    }

    // Urn weights for rooms 1-4 are extrapolated.
    fun urnArtefact(room: Int, random: GameRandom): String {
        val weights = URN_TIER_WEIGHTS[room - 1]
        var roll = random.of(weights.sum())
        for (tier in weights.indices) {
            roll -= weights[tier]
            if (roll < 0) {
                return artefact(tier)
            }
        }
        return artefact(weights.lastIndex)
    }

    private val URN_TIER_WEIGHTS: Array<IntArray> =
        arrayOf(
            intArrayOf(17, 46, 27, 8, 2, 0, 0, 0, 0),
            intArrayOf(0, 17, 46, 27, 8, 2, 0, 0, 0),
            intArrayOf(0, 17, 46, 27, 8, 2, 0, 0, 0),
            intArrayOf(0, 0, 17, 46, 27, 8, 2, 0, 0),
            intArrayOf(0, 0, 3, 7, 5, 1, 0, 0, 0),
            intArrayOf(0, 0, 0, 22, 42, 9, 2, 2, 0),
            intArrayOf(0, 0, 0, 54, 146, 87, 18, 17, 3),
            intArrayOf(0, 0, 0, 0, 104, 137, 47, 10, 10),
        )

    private fun artefact(tier: Int): String = ARTEFACTS[tier.coerceIn(ARTEFACTS.indices)]

    fun rollSceptre(denominator: Int, random: GameRandom): Boolean = random.of(denominator) == 0
}
