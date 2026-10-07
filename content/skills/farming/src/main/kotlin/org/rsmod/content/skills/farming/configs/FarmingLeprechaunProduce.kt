package org.rsmod.content.skills.farming.configs

object FarmingLeprechaunProduce {
    val NOTEABLE: Set<String> by lazy {
        buildSet {
            for (crop in FarmingCrops.ALL) {
                crop.produce?.let(::add)
                crop.sapling?.let(::add)
            }
            for (rule in FarmingServiceData.notingRules) {
                if (rule.allowed) add(rule.item) else remove(rule.item)
            }
        }
    }
}
