package dev.openrune.codec.osrs.impl

import dev.openrune.definition.type.ItemType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemServerCodecTest {
    @Test
    fun `custom transferability and native exchange eligibility remain independent after packing`() {
        for (market in listOf(false, true)) {
            for (tradeable in listOf(false, true)) {
                val base = ItemType(id = 42, stockMarket = market)
                val custom = ItemServerType(id = 42, tradeable = tradeable)
                val combined = ItemServerType(id = 42)
                ItemServerCodec(240, mapOf(42 to base), mapOf(42 to custom)).run {
                    combined.createData()
                }
                assertEquals(market, combined.stockmarket)
                assertEquals(tradeable, combined.tradeable)
                assertEquals(market, base.stockMarket)

                val codec = ItemServerCodec(240)
                val buffer = codec.encodeToBuffer(combined)
                val restored = codec.loadData(combined.id, buffer)
                assertEquals(market, restored.stockmarket)
                assertEquals(tradeable, restored.tradeable)
            }
        }
    }

    @Test
    fun `an unrelated overlay does not enable exchange trading for a non exchange item`() {
        val base = ItemType(id = 42, stockMarket = false)
        val custom = ItemServerType(id = 42, contentGroup = 35)
        val combined = ItemServerType(id = 42)
        ItemServerCodec(240, mapOf(42 to base), mapOf(42 to custom)).run {
            combined.createData()
        }
        assertEquals(35, combined.contentGroup)
        assertFalse(combined.stockmarket)
        assertTrue(combined.tradeable)
    }

    @Test
    fun `native only items retain default transferability without inferring it from exchange status`() {
        for (market in listOf(false, true)) {
            val base = ItemType(id = 42, stockMarket = market)
            val combined = ItemServerType(id = 42)
            ItemServerCodec(240, mapOf(42 to base)).run { combined.createData() }
            assertEquals(market, combined.stockmarket)
            assertTrue(combined.tradeable)
        }
    }
}
