package org.rsmod.api.player.stat

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/**
 * Suppress natural recovery only. Explicit potion/restoration changes remain allowed, and
 * overlapping sources must be removed independently.
 */
public object StatRegenPrevention {
    private val statSources =
        AttributeKey<MutableMap<String, MutableSet<String>>>(resetOnDeath = true, temp = true)

    private val globalSources = AttributeKey<MutableSet<String>>(resetOnDeath = true, temp = true)

    public fun add(player: Player, stat: String, source: String) {
        require(stat.isNotBlank()) { "`stat` must not be blank." }

        require(source.isNotBlank()) { "`source` must not be blank." }

        val byStat = player.attr.getOrPut(statSources) { mutableMapOf() }

        byStat.getOrPut(stat) { mutableSetOf() }.add(source)
    }

    public fun add(player: Player, stats: Iterable<String>, source: String) {
        stats.forEach { stat -> add(player = player, stat = stat, source = source) }
    }

    public fun addAllStats(player: Player, source: String) {
        require(source.isNotBlank()) { "`source` must not be blank." }

        player.attr.getOrPut(globalSources) { mutableSetOf() }.add(source)
    }

    public fun remove(player: Player, stat: String, source: String) {
        val byStat = player.attr[statSources] ?: return

        val sources = byStat[stat] ?: return

        sources.remove(source)

        if (sources.isEmpty()) {
            byStat.remove(stat)
        }

        if (byStat.isEmpty()) {
            player.attr.remove(statSources)
        }
    }

    public fun remove(player: Player, stats: Iterable<String>, source: String) {
        stats.forEach { stat -> remove(player = player, stat = stat, source = source) }
    }

    public fun removeAllStats(player: Player, source: String) {
        val sources = player.attr[globalSources] ?: return

        sources.remove(source)

        if (sources.isEmpty()) {
            player.attr.remove(globalSources)
        }
    }

    public fun removeSource(player: Player, source: String) {
        val byStat = player.attr[statSources]

        if (byStat != null) {
            val iterator = byStat.iterator()

            while (iterator.hasNext()) {
                val entry = iterator.next()

                entry.value.remove(source)

                if (entry.value.isEmpty()) {
                    iterator.remove()
                }
            }

            if (byStat.isEmpty()) {
                player.attr.remove(statSources)
            }
        }

        removeAllStats(player = player, source = source)
    }

    public fun prevents(player: Player, stat: String): Boolean {
        if (preventsAll(player)) {
            return true
        }

        return player.attr[statSources]?.get(stat).orEmpty().isNotEmpty()
    }

    public fun preventsAll(player: Player): Boolean {
        return player.attr[globalSources].orEmpty().isNotEmpty()
    }
}

public fun Player.clearStatDrain(stat: String) {
    if (stat(stat) < statBase(stat)) {
        statRestore(stat)
    }
}

public fun Player.clearStatDrains(stats: Iterable<String>) {
    stats.forEach { stat -> clearStatDrain(stat) }
}
