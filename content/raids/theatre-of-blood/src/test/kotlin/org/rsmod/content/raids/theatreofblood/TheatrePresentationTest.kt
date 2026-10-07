package org.rsmod.content.raids.theatreofblood

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class TheatrePresentationTest {
    @Test
    fun `native party states distinguish missing dead and live players`() {
        assertEquals(31, TheatreHudState.memberStatus(99, 99, false, false))
        assertEquals(30, TheatreHudState.memberStatus(99, 99, true, true))
        assertEquals(30, TheatreHudState.memberStatus(0, 99, false, true))
        assertEquals(27, TheatreHudState.memberStatus(99, 99, false, true))
        assertEquals(27, TheatreHudState.memberStatus(115, 99, false, true))
        assertEquals(14, TheatreHudState.memberStatus(50, 100, false, true))
        assertEquals(1, TheatreHudState.memberStatus(1, 99, false, true))
    }

    @Test
    fun `progress rescales health to the available native varbit bounds`() {
        assertEquals(3500 to 3500, TheatreHudState.progress(3500, 3500, 16383))
        assertEquals(511 to 1023, TheatreHudState.progress(1750, 3500, 1023))
        assertEquals(0 to 1023, TheatreHudState.progress(-1, 3500, 1023))
        assertEquals(1023 to 1023, TheatreHudState.progress(3501, 3500, 1023))
        assertEquals(31 to 31, TheatreHudState.progress(31, 31, 1023))
    }

    @Test
    fun `native progress animation uses bounded relative widths and safe long arithmetic`() {
        assertEquals(0, TheatreHudState.progressWidth(0, 100))
        assertEquals(8192, TheatreHudState.progressWidth(50, 100))
        assertEquals(16384, TheatreHudState.progressWidth(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(16384, TheatreHudState.progressWidth(101, 100))
    }
}
