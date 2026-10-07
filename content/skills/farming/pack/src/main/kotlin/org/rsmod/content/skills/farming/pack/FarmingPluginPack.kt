package org.rsmod.content.skills.farming.pack

import dev.openrune.pack.PluginPack

class FarmingPluginPack : PluginPack() {
    override fun dbTables() = listOf(
        farmingCrops(),
        farmingSaplings(),
        farmingCropVisuals(),
        farmingPatchTypes(),
        farmingPatches(),
        farmingBinTypes(),
        farmingBins(),
        farmingCompostTypes(),
        farmingLeprechaunSlots(),
        farmingCompanionRules(),
        farmingPatchGroups(),
        farmingGardeners(),
        farmingLeprechaunNoting(),
        farmingCompostIngredients(),
        farmingConsumableDoses(),
        farmingCompostProducts(),
    )
}
