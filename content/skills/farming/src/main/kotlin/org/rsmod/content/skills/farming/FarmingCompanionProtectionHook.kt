package org.rsmod.content.skills.farming

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.farming.FarmingCropRegistry
import org.rsmod.api.farming.FarmingManager
import org.rsmod.api.farming.FarmingPatchDef
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPreResolveHook
import org.rsmod.content.skills.farming.configs.FarmingCrops
import org.rsmod.content.skills.farming.configs.FarmingProtection
import org.rsmod.game.entity.Player

@Singleton
class FarmingCompanionProtectionHook
@Inject
constructor(private val manager: FarmingManager, private val crops: FarmingCropRegistry) :
    FarmingPreResolveHook {
    override fun beforeResolve(player: Player, def: FarmingPatchDef) {
        val farm = FarmingPatchGroups.forPatch(def.key.id) ?: return
        val flowerPatch = manager.patchByKey(FarmingPatchKey(farm.flower)) ?: return

        val flowerState = manager.state(player, flowerPatch)
        val flowerCrop = FarmingCrops.byId(flowerState.cropId)
        val flowerApi = crops.find(flowerState.cropId)
        val active =
            flowerCrop != null &&
                flowerApi != null &&
                !flowerState.dead &&
                !flowerState.diseased &&
                flowerState.stage >= flowerApi.grownStage

        for (patch in farm.allotments) {
            val key = FarmingPatchKey(patch)
            val allotment = manager.patchByKey(key) ?: continue
            // Use peek for the dependent patch: resolving it here would roll disease before protection is set.
            val state = manager.peek(player, allotment) ?: continue
            val cropId = state.cropId ?: continue
            val protect =
                active &&
                    if (flowerCrop!!.id == FarmingCrops.SCARECROW_ID) {
                        cropId in FarmingProtection.SCARECROW_PROTECTS
                    } else {
                        FarmingProtection.flowerProtects(flowerCrop.id, cropId)
                    }
            manager.setProtected(player, allotment, protect)
        }
    }
}
