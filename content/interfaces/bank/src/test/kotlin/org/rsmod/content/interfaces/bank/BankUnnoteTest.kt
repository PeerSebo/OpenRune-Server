package org.rsmod.content.interfaces.bank

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BankUnnoteTest {
    @Test
    fun `one note can be replaced in a full inventory`() {
        assertEquals(1, maxUnnoteCount(freeSlots = 0, noteCount = 1))
    }

    @Test
    fun `consuming the entire stack makes its own slot available`() {
        assertEquals(2, maxUnnoteCount(freeSlots = 1, noteCount = 2))
        assertEquals(28, maxUnnoteCount(freeSlots = 27, noteCount = 28))
    }

    @Test
    fun `a partially consumed stack still needs its original slot`() {
        assertEquals(0, maxUnnoteCount(freeSlots = 0, noteCount = 2))
        assertEquals(1, maxUnnoteCount(freeSlots = 1, noteCount = 3))
        assertEquals(27, maxUnnoteCount(freeSlots = 27, noteCount = Int.MAX_VALUE))
    }

    @Test
    fun `exchange never exceeds the notes held`() {
        assertEquals(1, maxUnnoteCount(freeSlots = 27, noteCount = 1))
        assertEquals(10, maxUnnoteCount(freeSlots = 27, noteCount = 10))
    }

    @Test
    fun `selected count is the largest exchange that fits for every inventory size`() {
        for (freeSlots in 0..27) {
            for (notes in 1..100) {
                val feasible = (0..notes).filter { amount ->
                    amount + (if (amount < notes) 1 else 0) <= freeSlots + 1
                }
                assertEquals(feasible.max(), maxUnnoteCount(freeSlots, notes))
            }
        }
    }
}
