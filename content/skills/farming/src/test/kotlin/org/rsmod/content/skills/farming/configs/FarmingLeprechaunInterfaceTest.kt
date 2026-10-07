package org.rsmod.content.skills.farming.configs

import dev.openrune.types.aconverted.interf.IfButtonOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FarmingLeprechaunInterfaceTest {
    @Test
    fun `every native menu keeps the selected quantity first without losing another option`() {
        val buttons = listOf(IfButtonOp.Op1, IfButtonOp.Op2, IfButtonOp.Op3, IfButtonOp.Op4)
        for (selected in 0..3) {
            val modes = buttons.map { FarmingLeprechaunInterface.quantityMode(selected, it) }
            assertEquals(selected, modes.first())
            assertEquals(setOf(0, 1, 2, 3), modes.toSet())
        }
    }

    @Test
    fun `all selected menu sends X from the fourth option`() {
        assertEquals(3, FarmingLeprechaunInterface.quantityMode(2, IfButtonOp.Op4))
        assertEquals(1, FarmingLeprechaunInterface.quantityMode(2, IfButtonOp.Op3))
    }

    @Test
    fun `examine and unavailable operations never perform a transfer`() {
        for (selected in 0..3) {
            assertNull(FarmingLeprechaunInterface.quantityMode(selected, IfButtonOp.Op5))
            assertNull(FarmingLeprechaunInterface.quantityMode(selected, IfButtonOp.Op10))
        }
    }

    @Test
    fun `init arguments match the revision 240 clientscript signatures`() {
        val expected = listOf(
            "universe", "rake", "dibber", "spade", "secateurs", "wateringcan", "trowel",
            "plantcure", "bucket", "compost", "supercompost", "ultracompost", "bottomless_bucket",
        )
        assertEquals(expected, FarmingLeprechaunInterface.sideArguments.map { it.substringAfter(':') })
        assertEquals(expected + "quantity_selection", FarmingLeprechaunInterface.mainArguments.map { it.substringAfter(':') })
    }
}
