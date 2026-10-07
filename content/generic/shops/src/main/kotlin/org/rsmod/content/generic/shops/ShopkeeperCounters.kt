package org.rsmod.content.generic.shops

internal object ShopkeeperCounters {
    fun requiresApproach(binding: ShopkeeperBinding): Boolean {
        if (binding.inventory == "inv.teashop") return false
        return binding.key in ShopkeeperAccessData.byKey ||
            binding.inventory.startsWith("inv.port_roberts_") && binding.inventory.endsWith("_trader")
    }
}
