package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryOrePackData
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FoundryRewardsScript @Inject constructor(
    private val rewards: FoundryRewardService,
    private val interfaces: FoundryInterfaces,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfModalButton(FoundryInterfaces.REWARDS_LIST) {
            val index = FoundryInterfaces.rewardIndex(it.comsub, FoundryData.rewards.size) ?: return@onIfModalButton
            when (it.op) {
                IfButtonOp.Op1 -> {
                    player.foundrySelectedReward = if (player.foundrySelectedReward == index) 0 else index
                    interfaces.selectReward(player, player.foundrySelectedReward)
                }
                IfButtonOp.Op2 -> rewards.purchase(player, index)
                IfButtonOp.Op3 -> if (FoundryData.rewards[index - 1].item != null) rewards.purchase(player, index, 5)
                IfButtonOp.Op4 -> if (FoundryData.rewards[index - 1].item != null) rewards.purchase(player, index, 10)
                else -> Unit
            }
        }
        onIfModalButton(FoundryInterfaces.REWARDS_BUY) {
            if (it.op == IfButtonOp.Op1) rewards.purchase(player, player.foundrySelectedReward)
        }
        onIfModalButton(FoundryInterfaces.REWARDS_FILTER) {
            if (it.op != IfButtonOp.Op1) return@onIfModalButton
            val filter = FoundryInterfaces.rewardFilter(it.comsub) ?: return@onIfModalButton
            VarPlayerIntMapSetter.set(player, "varbit.giants_foundry_reward_shop_filter", filter)
            interfaces.refreshRewards(player)
        }
        onOpHeld1(FoundryOrePackData.PACK) {
            if (it.inventory === inv) rewards.openOrePack(player, it.slot)
        }
        onOpHeldU(FoundryRewardService.SMITHS_GLOVES, FoundryRewardService.ICE_GLOVES) {
            rewards.combineGloves(player)
        }
        onOpHeld3(FoundryRewardService.ICED_GLOVES) {
            if (it.inventory === inv) rewards.separateGloves(player, it.slot)
        }
        for (reward in FoundryData.rewards) {
            val item = reward.item ?: continue
            val price = rewards.refundValue(item) ?: continue
            onOpNpcU(FoundryRewardService.KOVAC, item) {
                val slot = it.invSlot
                val name = requireNotNull(ServerCacheManager.getItem(item.asRSCM())).name
                startDialogue(it.npc) {
                    if (choice2("Yes, sell it.", 1, "No, keep it.", 2,
                        title = "Sell $name for $price Foundry Reputation?") == 1) {
                        rewards.refund(player, item, slot)
                    }
                }
            }
        }
    }
}
