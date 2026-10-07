package org.rsmod.content.skills.hunter.scripts

import org.rsmod.api.random.GameRandom

internal object LuckyImplingRewards {
    internal data class Entry(val obj: String, val minimum: Int, val maximum: Int, val noted: Boolean, val weight: Double)
    internal val tiers = listOf("easy", "medium", "hard", "elite", "master")
    internal val tables: Map<String, List<Entry>> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/hunter/lucky-impling-rewards.tsv"))
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                val fields = line.split('\t')
                fields[0] to Entry(fields[1], fields[2].toInt(), fields[3].toInt(), fields[4].toBoolean(), fields[5].toDouble() / fields[6].toDouble())
            }.toList().groupBy({ it.first }, { it.second })
        }
    }

    fun roll(random: GameRandom): List<ImplingLoot.Reward> {
        val entries = tables.getValue(tiers[random.of(tiers.size)])
        // Normalize published decimal-denominator rounding residuals; do not invent a nothing outcome.
        var value = random.randomDouble() * entries.sumOf { it.weight }
        val entry = entries.firstOrNull { value -= it.weight; value < 0 } ?: entries.last()
        return listOf(ImplingLoot.Reward(entry.obj, random.of(entry.minimum, entry.maximum), entry.noted))
    }
}
