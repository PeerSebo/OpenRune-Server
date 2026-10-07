package org.rsmod.content.skills.hunter

import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.game.entity.Player

internal object HunterGuildOutfit {
    val pieces = listOf("obj.hg_hunter_hood", "obj.hg_hunter_top", "obj.hg_hunter_legs", "obj.hg_hunter_boots")
    private val catchBonuses = listOf(20, 80, 60, 40)

    fun owns(player: Player, item: String): Boolean {
        val collection = player.collectionTransmit
        return (sequenceOf(player.inv, player.worn) + player.invMap.values.asSequence().filter { it !== collection })
            .any { it.count(item) > 0 }
    }

    fun catchBonus(player: Player): Int {
        val worn = pieces.indices.filter { player.worn.count(pieces[it]) > 0 }
        if (worn.isEmpty()) return 0
        return worn.sumOf { catchBonuses[it] } + if (pieces.all { owns(player, it) }) 50 else 0
    }

    fun rareBonus(player: Player): Int = catchBonus(player) * 2

    // Additive versus multiplicative catch bonuses remain unverified.
    fun catchChance(player: Player, chance: Int, denominator: Int = 256): Int =
        (chance * (10_000 + catchBonus(player)) / 10_000).coerceAtMost(denominator)
}
