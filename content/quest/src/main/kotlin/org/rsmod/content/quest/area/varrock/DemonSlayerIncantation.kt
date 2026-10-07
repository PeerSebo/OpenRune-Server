package org.rsmod.content.quest.area.varrock

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

internal object DemonSlayerIncantation {
    val Words: List<String> = listOf("Carlem", "Aber", "Camerinthum", "Purchai", "Gabindo")

    private val Varbits: List<String> =
        listOf(
            "varbit.delrith_incantation_1",
            "varbit.delrith_incantation_2",
            "varbit.delrith_incantation_3",
            "varbit.delrith_incantation_4",
            "varbit.delrith_incantation_5",
        )

    fun roll(player: Player, random: GameRandom) {
        val order = IntArray(Words.size) { it }
        for (i in order.lastIndex downTo 1) {
            val j = random.of(i + 1)
            val swap = order[i]
            order[i] = order[j]
            order[j] = swap
        }
        for (i in Varbits.indices) {
            VarPlayerIntMapSetter.set(player, Varbits[i], order[i])
        }
    }

    fun order(player: Player): List<Int> = Varbits.map { player.vars[it] }

    // An all-zero incantation is uninitialized; a valid permutation cannot repeat an index.
    fun isRolled(player: Player): Boolean = order(player).toSet().size == Words.size

    fun recite(player: Player): String {
        val words = order(player).map(Words::get)
        return words.dropLast(1).joinToString(separator = " ") { "$it..." } + " ${words.last()}!"
    }

    fun wordAt(sub: Int): String? = Words.getOrNull(sub - 1)
}
