package org.rsmod.content.other.achievementdiaries

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

class AchievementDiaryRewards @Inject constructor() {
    enum class ClaimResult { NotComplete, AlreadyClaimed, InventoryFull, Claimed }

    fun claimEasy(player: Player): ClaimResult = claim(player, DiaryData.easy)

    fun claimMedium(player: Player): ClaimResult = claim(player, DiaryData.medium)
    fun claimHard(player: Player): ClaimResult = claim(player, DiaryData.hard)

    private fun claim(player: Player, tier: DiaryTier): ClaimResult {
        if (player.vars[tier.rewardVar] > 0) return ClaimResult.AlreadyClaimed
        if (DiaryData.tasks(tier).any { it.unavailable.isNotEmpty() } ||
            DiaryProgress.completedCount(player, tier) != DiaryProgress.taskCount(tier) ||
            DiaryData.tiers.any { it.difficulty < tier.difficulty && player.vars[it.rewardVar] == 0 }) {
            return ClaimResult.NotComplete
        }
        val transaction = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            insert {
                into = inventory
                obj = tier.ring.asRSCM()
                strictCount = 1
            }
            insert {
                into = inventory
                obj = tier.lamp.asRSCM()
                strictCount = 1
            }
        }
        if (!transaction.success) return ClaimResult.InventoryFull
        VarPlayerIntMapSetter.set(player, tier.rewardVar, 1)
        DiaryProgress.synchronize(player)
        return ClaimResult.Claimed
    }

    fun reclaimRing(access: ProtectedAccess): Boolean {
        val tier = DiaryData.tiers.lastOrNull { access.player.vars[it.rewardVar] > 0 }
        if (tier == null) {
            access.mes("You must claim your Easy diary rewards first.")
            return false
        }
        if (tier.ring in access.inv || tier.ring in access.worn || tier.ring in access.bank) {
            access.mes("You already have your Explorer's ring.")
            return false
        }
        if (access.invAdd(access.inv, tier.ring, 1).failure) {
            access.mes("You need a free inventory slot to receive your ring.")
            return false
        }
        return true
    }
}
