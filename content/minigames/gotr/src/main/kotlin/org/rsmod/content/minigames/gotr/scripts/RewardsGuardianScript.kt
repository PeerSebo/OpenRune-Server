package org.rsmod.content.minigames.gotr.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.minigames.gotr.GotrGame
import org.rsmod.content.minigames.gotr.gotrCatalyticPoints
import org.rsmod.content.minigames.gotr.gotrElementalPoints
import org.rsmod.content.minigames.gotr.gotrEnergySearches
import org.rsmod.content.minigames.gotr.gotrTotalSearches
import org.rsmod.content.minigames.gotr.migrateGotrRewards
import org.rsmod.content.minigames.gotr.spendGotrEnergy
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RewardsGuardianScript
@Inject
constructor(
    private val dropRegistry: DropTableRegistry,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(REWARD_RIFT) { search(1, repeatSearches = true) }
        onOpLoc2(REWARD_RIFT) { check() }
        onOpLoc3(REWARD_RIFT) { search(5) }
        onPlayerQueueWithArgs<SearchTask>(QUEUE) { resolveSearch(it.args) }
    }

    private suspend fun ProtectedAccess.search(batchSize: Int, repeatSearches: Boolean = false) {
        // Normal Search is bounded by starting energy; do not automatically convert pearls into extra searches.
        val requested = if (repeatSearches) player.gotrEnergySearches().coerceAtLeast(1) else batchSize
        val funded = minOf(requested, availableSearches())
        if (funded <= 0) {
            mesbox("There doesn't seem to be anything in the rift for you.")
            return
        }
        clearWeakQueue(QUEUE)
        // Loc ops run after queue processing; initial delays need no +1.
        weakQueue(QUEUE, CADENCE_TICKS, SearchTask(funded, batchSize))
    }

    private suspend fun ProtectedAccess.resolveSearch(task: SearchTask) {
        // Resolve loot data before spending, without suspension between payment and delivery.
        val table = dropRegistry.forLoc(REWARD_RIFT) ?: return
        var completed = 0
        for (index in 0 until minOf(task.remaining, task.batchSize)) {
            if (!spendSearch()) break
            when (val result = table.roll(player, ArgMap()).flatten()) {
                is RollResult.Nothing -> Unit
                is RollResult.Single -> give(result.result)
                is RollResult.ListOf -> result.results.forEach { give(it) }
            }
            val total = player.gotrTotalSearches.coerceIn(0, GotrGame.MAX_SEARCHES)
            player.gotrTotalSearches = (total + 1).coerceAtMost(GotrGame.MAX_SEARCHES)
            completed++
        }
        val remaining = task.remaining - completed
        if (remaining > 0 && availableSearches() > 0) {
            // Queue-handler rescheduling requires +1.
            weakQueue(QUEUE, CADENCE_TICKS + 1, task.copy(remaining = remaining))
            return
        }
        if (availableSearches() <= 0) {
            mesbox("There doesn't seem to be anything else in the rift for you.")
        }
    }

    private suspend fun ProtectedAccess.check() {
        player.migrateGotrRewards()
        val elemental = player.gotrElementalPoints
        val catalytic = player.gotrCatalyticPoints
        val searches = minOf(elemental, catalytic)
        val totalSearched = player.gotrTotalSearches
        mesbox(
            "You have $catalytic catalytic energy and $elemental elemental energy. You can use " +
                "them to search the rift $searches times. You have searched the rift " +
                "$totalSearched times.",
        )
    }

    private fun ProtectedAccess.spendSearch(): Boolean {
        if (player.spendGotrEnergy()) return true
        return inv.count(PEARL) >= PEARL_COST_PER_SEARCH &&
            invDel(inv, PEARL, PEARL_COST_PER_SEARCH).success
    }

    private fun ProtectedAccess.availableSearches(): Int =
        (player.gotrEnergySearches().toLong() + inv.count(PEARL) / PEARL_COST_PER_SEARCH)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun ProtectedAccess.give(drop: DropRollItem) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        val count = drop.rollCount(random)
        CollectionLog.grant(player, obj, count)
        invAddOrDrop(objRepo, obj, count)
        val displayName = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: return
        if (count == 1) {
            spam("You found some loot: $displayName")
        } else {
            spam("You found some loot: $count x $displayName")
        }
    }

    private data class SearchTask(val remaining: Int, val batchSize: Int)

    private companion object {
        const val REWARD_RIFT = "loc.gotr_reward_rift"
        const val QUEUE = "queue.gotr_reward_search"
        const val CADENCE_TICKS = 3
        const val PEARL = "obj.abyssal_pearl"
        const val PEARL_COST_PER_SEARCH = 25
    }
}
