package org.rsmod.api.shops.cost

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StandardGpCostCalculationsTest {
    @Test fun `bulk purchase stops at wallet balance without overflowing total price`() {
        val result = StandardGpCostCalculations.calculateShopSellBulkParameters(
            initialStock = 50, currentStock = 50, baseCost = 1_500_000_000,
            requestedCount = 50, availableCurrency = Int.MAX_VALUE,
            sellPercentage = 100.0, changePercentage = 0.0,
        )
        assertEquals(1, result.count)
        assertEquals(1_500_000_000, result.totalValue)
    }

    @Test fun `bulk sale stops at coin capacity without overflowing payment`() {
        val result = StandardGpCostCalculations.calculateShopBuyBulkParameters(
            initialStock = 0, currentStock = 0, baseCost = 1_500_000_000,
            requestedCount = 50, currencyCap = Int.MAX_VALUE,
            buyPercentage = 100.0, changePercentage = 0.0,
        )
        assertEquals(1, result.count)
        assertEquals(1_500_000_000, result.totalValue)
    }
}
