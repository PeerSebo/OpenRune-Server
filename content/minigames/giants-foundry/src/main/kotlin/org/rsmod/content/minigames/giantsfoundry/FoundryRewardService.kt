package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.int
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryOrePackData
import org.rsmod.content.minigames.giantsfoundry.state.foundryReputation
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

var Player.foundrySelectedReward by intVarp("varp.giants_foundry_reward_shop_selected_item")

@Singleton
class FoundryRewardService @Inject constructor(
    private val interfaces: FoundryInterfaces,
    private val random: GameRandom,
) {
    fun open(player: Player) {
        player.foundrySelectedReward = 0
        VarPlayerIntMapSetter.set(player, "varbit.giants_foundry_reward_shop_filter", 0)
        interfaces.openRewards(player)
    }

    fun purchase(player: Player, index: Int, amount: Int = 1): Boolean {
        val reward = FoundryData.rewards.find { it.index == index } ?: return false
        if (amount !in 1..10) return false
        val mould = reward.mould
        val count = if (mould != null) 1 else amount
        if (mould != null && player.vars[requireNotNull(mould.unlockVarbit)] != 0) {
            player.mes("You have already unlocked this mould.")
            return false
        }
        if (reward.item == DOUBLE_MOULD && !canBuyDoubleMould(player)) {
            player.mes("You must complete Dwarf Cannon before buying a double ammo mould.")
            return false
        }
        val cost = reward.cost.toLong() * count
        if (cost > player.foundryReputation) {
            player.mes("You don't have enough Foundry Reputation to buy that.")
            return false
        }
        if (mould != null) {
            VarPlayerIntMapSetter.set(player, requireNotNull(mould.unlockVarbit), 1)
        } else {
            val item = requireNotNull(reward.item)
            val transaction = player.invTransaction(player.inv) {
                add(select(player.inv), item.asRSCM(), count)
            }
            if (transaction.failure) {
                player.mes("You don't have enough inventory space for that purchase.")
                return false
            }
        }
        player.foundryReputation -= cost.toInt()
        reward.item?.let { CollectionLog.grant(player, it, count) }
        if (mould != null) player.mes("You unlock the ${mould.name} mould.")
        if (player.ui.containsModal(FoundryInterfaces.REWARDS)) interfaces.refreshRewards(player)
        return true
    }

    fun refundValue(item: String): Int? = FoundryData.rewards.firstOrNull {
        it.item == item && it.item != GROG && it.item != BLADE
    }?.let { it.cost * 4 / 5 }

    fun refund(player: Player, item: String, slot: Int, amount: Int = 1): Boolean {
        val value = refundValue(item) ?: return false
        if (amount <= 0 || player.inv[slot]?.id != item.asRSCM()) return false
        val credit = value.toLong() * amount
        if (player.foundryReputation.toLong() + credit > Int.MAX_VALUE) {
            player.mes("You cannot hold any more Foundry Reputation.")
            return false
        }
        val transaction = player.invTransaction(player.inv) {
            delete(select(player.inv), item.asRSCM(), amount, slot = slot)
        }
        if (transaction.failure) return false
        player.foundryReputation += credit.toInt()
        player.mes("Kovac refunds $credit Foundry Reputation.")
        return true
    }

    fun openOrePack(player: Player, slot: Int): Boolean {
        if (player.inv[slot]?.id != FoundryOrePackData.PACK.asRSCM()) return false
        val capacity = player.invTransaction(player.inv, autoCommit = false) {
            val inventory = select(player.inv)
            delete(inventory, FoundryOrePackData.PACK.asRSCM(), 1, slot = slot)
            for (ore in FoundryOrePackData.outcomes) {
                add(inventory, ore.item, FoundryOrePackData.ROLLS, cert = true)
            }
        }
        if (capacity.failure) {
            player.mes("You need room for five types of noted ore to open this pack.")
            return false
        }
        val contents = FoundryOrePackData.roll(random)
        val transaction = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            delete(inventory, FoundryOrePackData.PACK.asRSCM(), 1, slot = slot)
            for ((item, count) in contents) add(inventory, item, count, cert = true)
        }
        if (transaction.failure) return false
        player.mes("You open the pack and receive 30 noted ores.")
        return true
    }

    fun combineGloves(player: Player): Boolean {
        val transaction = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            delete(inventory, SMITHS_GLOVES.asRSCM(), 1)
            delete(inventory, ICE_GLOVES.asRSCM(), 1)
            add(inventory, ICED_GLOVES.asRSCM(), 1)
        }
        if (transaction.success) player.mes("You combine your smiths gloves with your ice gloves.")
        return transaction.success
    }

    fun separateGloves(player: Player, slot: Int): Boolean {
        if (player.inv[slot]?.id != ICED_GLOVES.asRSCM()) return false
        val transaction = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            delete(inventory, ICED_GLOVES.asRSCM(), 1, slot = slot)
            add(inventory, SMITHS_GLOVES.asRSCM(), 1)
            add(inventory, ICE_GLOVES.asRSCM(), 1)
        }
        player.mes(if (transaction.success) "You separate the two pairs of gloves." else "You need another inventory space to separate the gloves.")
        return transaction.success
    }

    private fun canBuyDoubleMould(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, "quest_dwarfcannon") ||
            player.vars["varp.mcannon"] >= DbHelper.row("dbrow.quest_dwarfcannon").int("dbcol.quest:endstate")

    companion object {
        const val DOUBLE_MOULD = "obj.double_ammo_mould"
        const val GROG = "obj.kovacs_grog"
        const val BLADE = "obj.giants_foundry_colossal_blade"
        const val SMITHS_GLOVES = "obj.smithing_uniform_gloves"
        const val ICED_GLOVES = "obj.smithing_uniform_gloves_ice"
        const val ICE_GLOVES = "obj.ice_gloves"
        const val KOVAC = "npc.giants_foundry_kovac"
    }
}
