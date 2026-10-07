package org.rsmod.content.travel.fairyrings

// The cache encodes dial values as decimal digits, not a base-four index.
object FairyRingDials {
    const val COUNT: Int = 3
    const val POSITIONS: Int = 4

    private val LETTERS: List<List<Char>> =
        listOf(listOf('A', 'D', 'C', 'B'), listOf('I', 'L', 'K', 'J'), listOf('P', 'S', 'R', 'Q'))

    fun code(dials: List<Int>): String {
        require(dials.size == COUNT) { "Expected $COUNT dial values, got $dials." }
        return dials
            .mapIndexed { dial, value ->
                LETTERS[dial].getOrNull(value) ?: error("Dial ${dial + 1} cannot show $value.")
            }
            .joinToString("")
    }

    fun dials(code: String): List<Int>? {
        val letters = code.uppercase().filter { it != ' ' }
        if (letters.length != COUNT) {
            return null
        }
        val dials = letters.mapIndexed { dial, letter -> LETTERS[dial].indexOf(letter) }
        return dials.takeIf { values -> values.none { it == -1 } }
    }

    fun tableId(dials: List<Int>): Int = dials.fold(0) { acc, value -> acc * 10 + value }

    fun fromTableId(id: Int): List<Int> = listOf(id / 100 % 10, id / 10 % 10, id % 10)

    fun rotate(value: Int, steps: Int): Int = Math.floorMod(value + steps, POSITIONS)
}
