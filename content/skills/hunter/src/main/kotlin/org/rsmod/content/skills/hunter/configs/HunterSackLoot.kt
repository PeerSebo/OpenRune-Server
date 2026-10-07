package org.rsmod.content.skills.hunter.configs

import org.rsmod.api.random.GameRandom

internal data class HunterSackReward(val obj: String, val count: Int = 1, val noted: Boolean = false)

internal enum class HunterSackTier(val obj: String, val rolls: Int, val uniques: Boolean, val petDenominator: Int?) {
    BASIC("obj.hg_lootsack_t0", 5, false, null),
    ADEPT("obj.hg_lootsack_t1", 7, true, null),
    EXPERT("obj.hg_lootsack_t2", 9, true, 1000),
    MASTER("obj.hg_lootsack_t3", 11, true, 1000),
    SUPPLIES("obj.hg_lootsack_trade", 11, false, null)
}

internal object HunterSackLoot {
    private data class Slot(val obj: String, val minimum: Int = 1, val maximum: Int = minimum, val noted: Boolean = false)
    private val feed = Slot("obj.hg_seedsack")
    private val coins = Slot("obj.coins", 750, 1250)
    private val tips = Slot("obj.hg_speartip", 15, 30)
    private val shards = Slot("obj.blessed_bone_shard", 100, 200)
    private val kyatt = Slot("obj.hunting_kyatt_meat", 2, noted = true)
    private val fox = Slot("obj.hunting_fennecfox_meat", 3, noted = true)
    private val sunlight = Slot("obj.hunting_antelopesun_meat", 2, noted = true)
    private val logs = Slot("logs")
    private val herbs = Slot("herbs")
    private val basic = listOf(feed, coins, tips, shards, kyatt, fox, logs)
    private val adept = listOf(feed, Slot("obj.bird_nest_empty", noted = true), Slot("obj.bird_nest_empty", 2, 4, true),
        Slot("obj.sun_kissed_bone", 2, noted = true), shards, tips, kyatt, fox, sunlight, coins, herbs, logs)
    private val master = listOf(feed, Slot("obj.bird_nest_empty", noted = true), Slot("obj.bird_nest_empty", 2, 4, true),
        Slot("obj.sun_kissed_bone", 2, noted = true), Slot("obj.sun_kissed_bone", 3, noted = true), shards, tips, kyatt, fox,
        sunlight, Slot("obj.hunting_antelopemoon_meat", 2, noted = true), coins, Slot("obj.coins", 2500, 3500), herbs, logs)

    private data class Cascade(val obj: String, val low: Int, val high: Int)
    private val logCascade = listOf(Cascade("obj.magic_logs", -60, 60), Cascade("obj.yew_logs", -50, 90),
        Cascade("obj.mahogany_logs", -40, 130), Cascade("obj.maple_logs", 0, 160), Cascade("obj.teak_logs", 999, 999))
    private val herbCascade = listOf(Cascade("obj.unidentified_lantadyme", -30, 60), Cascade("obj.unidentified_cadantine", -10, 70),
        Cascade("obj.unidentified_kwuarm", 10, 85), Cascade("obj.unidentified_avantoe", 20, 100), Cascade("obj.unidentified_irit", 30, 115),
        Cascade("obj.unidentified_ranarr", 10, 170), Cascade("obj.unidentified_tarromin", 70, -20), Cascade("obj.unidentified_harralander", 999, 999))

    private fun slots(tier: HunterSackTier) = when (tier) { HunterSackTier.BASIC -> basic; HunterSackTier.ADEPT -> adept; else -> master }
    fun tableSize(tier: HunterSackTier): Int = slots(tier).size

    fun rollResource(tier: HunterSackTier, woodcutting: Int, herblore: Int, random: GameRandom): HunterSackReward {
        val table = slots(tier)
        val slot = table[random.of(table.size)]
        return when (slot.obj) {
            "logs" -> HunterSackReward(cascade(logCascade, woodcutting, random), 4, true)
            "herbs" -> HunterSackReward(cascade(herbCascade, herblore, random), 4, true)
            else -> HunterSackReward(slot.obj, if (slot.minimum == slot.maximum) slot.minimum else random.of(slot.minimum, slot.maximum), slot.noted)
        }
    }

    private fun cascade(table: List<Cascade>, level: Int, random: GameRandom): String =
        table.first { random.of(256) < chartChance(it.low, it.high, level) }.obj

    // Round interpolation, then add one, matching Module:Skilling_success_chart.
    fun chartChance(low: Int, high: Int, level: Int): Int {
        val safeLevel = level.coerceIn(1, 99)
        return (low + Math.floorDiv((high - low) * (safeLevel - 1) + 49, 98) + 1).coerceIn(0, 256)
    }
}
