package org.rsmod.content.other.pouches.runepouch

class RunePouchContents(val types: IntArray, val counts: IntArray) {
    init {
        require(types.size == counts.size) { "types and counts must be the same size" }
    }

    val slots: Int
        get() = types.size

    fun isEmpty(): Boolean = counts.all { it == 0 }

    fun slotOf(compactId: Int): Int =
        types.indices.firstOrNull { types[it] == compactId && counts[it] > 0 } ?: -1

    fun freeSlot(): Int = types.indices.firstOrNull { counts[it] == 0 } ?: -1

    fun add(compactId: Int, amount: Int): Int {
        if (amount <= 0) {
            return 0
        }
        var slot = slotOf(compactId)
        if (slot == -1) {
            slot = freeSlot()
        }
        if (slot == -1) {
            return 0
        }
        val stored = minOf(MAX_STACK - counts[slot], amount)
        if (stored <= 0) {
            return 0
        }
        types[slot] = compactId
        counts[slot] += stored
        return stored
    }

    fun remove(slot: Int, amount: Int): Int {
        val removed = minOf(counts[slot], amount).coerceAtLeast(0)
        counts[slot] -= removed
        if (counts[slot] == 0) {
            types[slot] = 0
        }
        return removed
    }

    fun clear() {
        types.fill(0)
        counts.fill(0)
    }

    companion object {
        const val MAX_STACK: Int = 16_000

        fun empty(slots: Int): RunePouchContents = RunePouchContents(IntArray(slots), IntArray(slots))
    }
}

sealed class PouchAmount {
    data class Fixed(val count: Int) : PouchAmount()

    data object All : PouchAmount()

    data object Prompt : PouchAmount()
}

object RunePouchQuantity {
    const val MODE_ONE: Int = 0
    const val MODE_FIVE: Int = 1
    const val MODE_X: Int = 2
    const val MODE_ALL: Int = 3

    /** `rune_pouch_customquantity` is a 14-bit varbit. */
    const val MAX_CUSTOM: Int = 16_383

    const val OP_EXAMINE: Int = 10

    fun resolve(op: Int, mode: Int, custom: Int): PouchAmount? =
        when (op) {
            1 ->
                when (mode) {
                    MODE_FIVE -> PouchAmount.Fixed(5)
                    MODE_X -> customOrPrompt(custom)
                    MODE_ALL -> PouchAmount.All
                    else -> PouchAmount.Fixed(1)
                }
            2 -> PouchAmount.Fixed(1)
            3 -> PouchAmount.Fixed(5)
            4 -> customOrPrompt(custom)
            5 -> PouchAmount.Prompt
            6 -> PouchAmount.All
            else -> null
        }

    private fun customOrPrompt(custom: Int): PouchAmount =
        if (custom > 0) PouchAmount.Fixed(custom) else PouchAmount.Prompt
}
