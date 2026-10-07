package org.rsmod.content.minigames.mahoganyhomes.planksack

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

public enum class PlankType(public val objInternal: String, private val varbit: String) {
    PLAIN("obj.woodplank", "varbit.plank_sack_plain"),
    OAK("obj.plank_oak", "varbit.plank_sack_oak"),
    TEAK("obj.plank_teak", "varbit.plank_sack_teak"),
    MAHOGANY("obj.plank_mahogany", "varbit.plank_sack_mahogany"),
    CAMPHOR("obj.plank_camphor", "varbit.plank_sack_camphor"),
    IRONWOOD("obj.plank_ironwood", "varbit.plank_sack_ironwood"),
    ROSEWOOD("obj.plank_rosewood", "varbit.plank_sack_rosewood");

    internal fun stored(player: Player): Int = player.vars[varbit]

    internal fun addStored(player: Player, amount: Int) {
        VarPlayerIntMapSetter.set(player, varbit, player.vars[varbit] + amount)
    }

    internal fun removeStored(player: Player, amount: Int) {
        val next = (player.vars[varbit] - amount).coerceAtLeast(0)
        VarPlayerIntMapSetter.set(player, varbit, next)
    }

    public companion object {
        public fun ofObj(objInternal: String): PlankType? = entries.find { it.objInternal == objInternal }
    }
}

public object PlankSack {
    public const val CAPACITY: Int = 28

    public fun isOwned(player: Player): Boolean = "obj.plank_sack" in player.inv

    public fun totalStored(player: Player): Int = PlankType.entries.sumOf { it.stored(player) }

    public fun stored(player: Player, type: PlankType): Int = type.stored(player)

    public fun freeSpace(player: Player): Int = (CAPACITY - totalStored(player)).coerceAtLeast(0)

    public fun depositUpTo(player: Player, type: PlankType, amount: Int): Int {
        val toAdd = minOf(amount, freeSpace(player))
        if (toAdd <= 0) return 0
        type.addStored(player, toAdd)
        return toAdd
    }

    public fun withdrawUpTo(player: Player, type: PlankType, amount: Int): Int {
        val toRemove = minOf(amount, type.stored(player))
        if (toRemove <= 0) return 0
        type.removeStored(player, toRemove)
        return toRemove
    }

    public fun tryConsume(player: Player, type: PlankType, amount: Int): Boolean {
        if (type.stored(player) < amount) return false
        type.removeStored(player, amount)
        return true
    }
}
