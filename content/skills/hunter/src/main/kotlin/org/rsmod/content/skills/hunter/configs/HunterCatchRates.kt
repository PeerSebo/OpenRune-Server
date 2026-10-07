package org.rsmod.content.skills.hunter.configs

data class CatchRate(
    val low: Int,
    val high: Int,
    val chart: Map<Int, Int> = emptyMap(),
    val roundToNearest: Boolean = false,
)

object HunterCatchRates {
    const val DENOMINATOR: Int = 256

    fun chance(rate: CatchRate, level: Int): Int {
        rate.chart[level]?.let { return it.coerceIn(0, DENOMINATOR) }
        val span = rate.high - rate.low
        // floorDiv preserves floor rounding for negative spans; / truncates toward zero.
        val rounding = if (rate.roundToNearest) 49 else 0
        val raw = rate.low + Math.floorDiv(span * (level - 1) + rounding, 98)
        return raw.coerceIn(0, DENOMINATOR)
    }

    // Per-level chart values: https://oldschool.runescape.wiki/w/Net_trap.
    private val NET_TRAP_RATES: Map<NetTrapColour, CatchRate> =
        mapOf(
            NetTrapColour.SWAMP to
                chartedNetRate(
                    low = 53,
                    high = 361,
                    firstLevel = 29,
                    chances = listOf(
                        141, 144, 147, 150, 154, 157, 160, 163, 166, 169, 172, 176, 179,
                        182, 185, 188, 191, 194, 198, 201, 204, 207, 210, 213, 216, 220,
                        223, 226, 229, 232, 235, 238, 242, 245, 248, 251, 254, 256,
                    ),
                ),
            NetTrapColour.ORANGE to
                chartedNetRate(
                    low = 17,
                    high = 289,
                    firstLevel = 47,
                    chances = listOf(
                        145, 147, 150, 153, 156, 159, 161, 164, 167, 170, 172, 175, 178,
                        181, 184, 186, 189, 192, 195, 197, 200, 203, 206, 209, 211, 214,
                        217, 220, 222, 225, 228, 231, 233, 236, 239, 242, 245, 247, 250,
                        253, 256,
                    ),
                ),
            NetTrapColour.RED to
                chartedNetRate(
                    low = 1,
                    high = 241,
                    firstLevel = 59,
                    chances = listOf(
                        143, 145, 148, 150, 153, 155, 158, 160, 163, 165, 168, 170, 172,
                        175, 177, 180, 182, 185, 187, 190, 192, 194, 197, 199, 202, 204,
                        207, 209, 212, 214, 217, 219, 221, 224, 226, 229, 231, 234, 236,
                        239, 241,
                    ),
                ),
            NetTrapColour.BLACK to
                chartedNetRate(
                    low = 1,
                    high = 213,
                    firstLevel = 67,
                    chances = listOf(
                        144, 146, 148, 150, 152, 155, 157, 159, 161, 163, 165, 168, 170,
                        172, 174, 176, 178, 181, 183, 185, 187, 189, 191, 194, 196, 198,
                        200, 202, 204, 207, 209, 211, 213,
                    ),
                ),
            NetTrapColour.MOUNTAIN to
                chartedNetRate(
                    low = 2,
                    high = 213,
                    firstLevel = 79,
                    chances = listOf(
                        170, 172, 174, 176, 179, 181, 183, 185, 187, 189, 191, 194, 196,
                        198, 200, 202, 204, 207, 209, 211, 213,
                    ),
                ),
        )

    fun netTrap(colour: NetTrapColour): CatchRate = NET_TRAP_RATES.getValue(colour)

    private fun chartedNetRate(
        low: Int,
        high: Int,
        firstLevel: Int,
        chances: List<Int>,
    ): CatchRate =
        CatchRate(
            low = low,
            high = high,
            chart = chances.mapIndexed { index, chance -> firstLevel + index to chance }.toMap(),
            roundToNearest = true,
        )

    private val LEVEL_REQ_ANCHORS: List<Pair<Int, Int>> =
        listOf(1 to 101, 9 to 111, 11 to 113, 19 to 130, 29 to 141, 47 to 145, 53 to 145)

    private fun atLevelReq(levelReq: Int): Int {
        val first = LEVEL_REQ_ANCHORS.first()
        val last = LEVEL_REQ_ANCHORS.last()
        if (levelReq <= first.first) {
            return first.second
        }
        if (levelReq >= last.first) {
            return last.second
        }
        for (i in 0 until LEVEL_REQ_ANCHORS.size - 1) {
            val (x0, y0) = LEVEL_REQ_ANCHORS[i]
            val (x1, y1) = LEVEL_REQ_ANCHORS[i + 1]
            if (levelReq in x0..x1) {
                return y0 + Math.floorDiv((y1 - y0) * (levelReq - x0), x1 - x0)
            }
        }
        return last.second
    }

    // Fallback endpoints are estimates where exact species rates are unpublished.
    fun estimated(levelReq: Int): CatchRate {
        val atReq = atLevelReq(levelReq)
        val at99 = 320
        if (levelReq >= 99) {
            return CatchRate(low = atReq, high = at99)
        }
        val low = atReq - Math.floorDiv((at99 - atReq) * (levelReq - 1), 99 - levelReq)
        return CatchRate(low = low, high = at99)
    }
}
