package org.rsmod.api.farming

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.farming.persistence.FarmingApplier
import org.rsmod.api.farming.persistence.FarmingPipeline
import org.rsmod.plugin.module.PluginModule

public class FarmingModule : PluginModule() {
    override fun bind() {
        bindInstance<FarmingPatchRegistry>()
        bindInstance<FarmingCropRegistry>()
        bindInstance<FarmingTransmitVars>()
        bindInstance<FarmingRenderer>()
        bindInstance<FarmingGrowth>()
        bindInstance<FarmingSceneIndex>()
        bindInstance<FarmingManager>()
        bindInstance<FarmingBinRegistry>()
        bindInstance<FarmingBinRenderer>()
        bindInstance<FarmingBinManager>()
        bindInstance<FarmingApplier>()
        // Content supplies optional pre-resolve hooks through addSetBinding.
        newSetBinding<FarmingPreResolveHook>()
        addSetBinding<CharacterDataStage.Pipeline>(FarmingPipeline::class.java)
    }
}
