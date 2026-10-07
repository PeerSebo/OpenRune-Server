package org.rsmod.content.skills.farming.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.farming.FarmingBinAddResult
import org.rsmod.api.farming.FarmingBinDef
import org.rsmod.api.farming.FarmingBinFill
import org.rsmod.api.farming.FarmingBinKey
import org.rsmod.api.farming.FarmingBinManager
import org.rsmod.api.farming.FarmingBinOpenResult
import org.rsmod.api.farming.FarmingBinPhase
import org.rsmod.api.farming.FarmingBinProduct
import org.rsmod.api.farming.FarmingBinState
import org.rsmod.api.farming.FarmingBinTakeResult
import org.rsmod.api.farming.FarmingBinType
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLocCategory5
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.farming.configs.FarmingMessages
import org.rsmod.content.skills.farming.configs.FarmingParams
import org.rsmod.content.skills.farming.configs.FarmingServiceData
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal sealed interface CompostBinTask {
    val binKey: String
}

internal data class CompostBinTakeTask(override val binKey: String) : CompostBinTask

internal data class CompostBinFillTask(
    override val binKey: String,
    val objId: Int,
    val origin: CoordGrid,
) : CompostBinTask

internal data class CompostBinAshUpgradeTask(override val binKey: String, val ashSpent: Int) :
    CompostBinTask

class CompostBinScript
@Inject
constructor(
    private val binManager: FarmingBinManager,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {
    private val compostFillByObj: Map<Int, Pair<FarmingBinFill, String>> by lazy {
        FarmingServiceData.compostClassification
    }

    private val compostPotionDoseByObj: Map<Int, Int> by lazy {
        (1..4).associate { dose ->
            FarmingParams.SUPERCOMPOST_POTION_DOSES[dose].asRSCM(RSCMType.OBJ) to dose
        }
    }

    private val volcanicAshObjId: Int by lazy { FarmingParams.VOLCANIC_ASH.asRSCM(RSCMType.OBJ) }

    override fun ScriptContext.startup() {
        onOpLocCategory5(FarmingParams.PATCH_CATEGORY) { dumpBin(it.loc) }
        onPlayerQueueWithArgs<CompostBinTask>(FarmingParams.QUEUE_BIN_TAKE) { dispatchQueue(it.args) }
    }

    fun opOne(access: ProtectedAccess, def: FarmingBinDef) {
        with(access) { opOneOnBin(def) }
    }

    fun useItem(access: ProtectedAccess, def: FarmingBinDef, objId: Int) {
        with(access) { useItemOnBin(def, objId) }
    }

    private fun ProtectedAccess.opOneOnBin(def: FarmingBinDef) {
        clearWeakQueue(FarmingParams.QUEUE_BIN_TAKE)
        val state = binManager.state(player, def)
        when (state.phase) {
            FarmingBinPhase.FILLING ->
                if (state.count >= def.capacity) closeBin(def)
            FarmingBinPhase.ROTTING,
            FarmingBinPhase.READY -> openBin(def, state)
            FarmingBinPhase.OPEN -> startTake(def)
        }
    }

    private fun ProtectedAccess.closeBin(def: FarmingBinDef) {
        if (!binManager.close(player, def)) return
        anim(CloseAnimation)
        soundSynth("synth.COMPOST_CLOSE")
        val state = binManager.state(player, def)
        mes(FarmingMessages.BIN_CLOSED)
        mes(
            if (state.product == FarmingBinProduct.ROTTEN_TOMATOES) {
                FarmingMessages.BIN_TOMATOES_ROTTING
            } else {
                FarmingMessages.BIN_CONTENTS_ROTTING
            }
        )
    }

    private fun ProtectedAccess.openBin(def: FarmingBinDef, state: FarmingBinState) {
        when (binManager.open(player, def)) {
            FarmingBinOpenResult.NOT_READY ->
                mes(
                    if (state.product == FarmingBinProduct.ROTTEN_TOMATOES) {
                        FarmingMessages.BIN_TOMATOES_NOT_ROTTED
                    } else {
                        FarmingMessages.BIN_NOT_ROTTED
                    }
                )
            FarmingBinOpenResult.OPENED -> mes(FarmingMessages.BIN_OPENED)
            FarmingBinOpenResult.NOTHING_TO_OPEN -> {}
        }
    }

    private fun ProtectedAccess.startTake(def: FarmingBinDef) {
        clearWeakQueue(FarmingParams.QUEUE_BIN_TAKE)
        val state = binManager.state(player, def)
        if (state.phase != FarmingBinPhase.OPEN || state.count <= 0) return
        if (state.product != FarmingBinProduct.ROTTEN_TOMATOES &&
            inv.count(FarmingParams.BUCKET_EMPTY) <= 0
        ) {
            mes(FarmingMessages.BIN_NEED_BUCKET)
            return
        }
        // Loc ops run after queue processing; initial delays need no +1.
        weakQueue(
            FarmingParams.QUEUE_BIN_TAKE,
            FarmingParams.BIN_TAKE_CADENCE_TICKS,
            CompostBinTakeTask(def.key.id),
        )
    }

    private fun ProtectedAccess.takeTick(task: CompostBinTakeTask) {
        val def = binManager.binByKey(FarmingBinKey(task.binKey)) ?: return
        val state = binManager.state(player, def)
        if (state.phase != FarmingBinPhase.OPEN || state.count <= 0) return
        val product = state.product ?: return

        val needsBucket = product != FarmingBinProduct.ROTTEN_TOMATOES
        if (needsBucket && inv.count(FarmingParams.BUCKET_EMPTY) <= 0) {
            mes(FarmingMessages.BIN_NEED_BUCKET)
            return
        }
        val item = FarmingServiceData.compostProducts.getValue(product).item
        // Prepare the swap before changing the bin; the consumed bucket frees its output slot.
        val collection = player.invTransaction(inv, autoCommit = false) {
            takeCompostProduct(
                select(inv),
                item.asRSCM(RSCMType.OBJ),
                if (needsBucket) FarmingParams.BUCKET_EMPTY.asRSCM(RSCMType.OBJ) else null,
            )
        }
        if (collection.failure) {
            mes(
                if (product == FarmingBinProduct.ROTTEN_TOMATOES) {
                    FarmingMessages.BIN_NO_SPACE_ROTTEN_TOMATO
                } else {
                    Constants.dm_invspace
                }
            )
            return
        }

        val take = binManager.takeOne(player, def)
        if (!take.taken) {
            mes(FarmingMessages.BIN_NOTHING_TO_TAKE)
            return
        }
        // Read the take result: the final take clears state.product.
        val taken = take.product ?: return
        collection.commitAll()
        statAdvance(FarmingParams.FARMING_STAT, FarmingServiceData.compostProducts.getValue(taken).binXp * xpMod())

        if (take.result == FarmingBinTakeResult.TAKEN_LAST) return
        // Queue-handler rescheduling requires +1.
        weakQueue(FarmingParams.QUEUE_BIN_TAKE, FarmingParams.BIN_TAKE_CADENCE_TICKS + 1, task)
    }

    private fun ProtectedAccess.useItemOnBin(def: FarmingBinDef, objId: Int) {
        val state = binManager.state(player, def)
        if (state.phase == FarmingBinPhase.OPEN) {
            if (objId == FarmingParams.BUCKET_EMPTY.asRSCM(RSCMType.OBJ)) startTake(def)
            else tryUpgrade(def, state, objId)
        } else {
            tryFill(def, objId)
        }
    }

    private fun ProtectedAccess.tryFill(def: FarmingBinDef, objId: Int) {
        val entry = compostFillByObj[objId]
        if (entry == null) {
            mes(Constants.dm_default)
            return
        }
        clearWeakQueue(FarmingParams.QUEUE_BIN_TAKE)
        fillTick(CompostBinFillTask(def.key.id, objId, player.coords), fromOp = true)
    }

    private fun ProtectedAccess.fillTick(task: CompostBinFillTask, fromOp: Boolean = false) {
        // Also stop feeding after forced movement; it can bypass ordinary weak-queue cancellation.
        if (!fromOp && (player.coords != task.origin || player.hasMovedThisCycle || player.routeRequest != null || player.routeDestination.isNotEmpty())) return
        val def = binManager.binByKey(FarmingBinKey(task.binKey)) ?: return
        val (fillClass, itemName) = compostFillByObj[task.objId] ?: return
        val state = binManager.state(player, def)
        if (state.phase != FarmingBinPhase.FILLING || state.count >= def.capacity) {
            if (fromOp) mes(FarmingMessages.BIN_MUST_BE_EMPTIED)
            return
        }
        if (inv.count(itemName) <= 0) return
        // Commit item deletion and bin acceptance together to prevent loss on refusal.
        val deposit = player.invTransaction(inv, autoCommit = false) {
            val inventory = select(inv)
            delete { from = inventory; obj = task.objId; strictCount = 1 }
        }
        if (deposit.failure) return
        val result = binManager.addItem(player, def, fillClass)
        if (result == FarmingBinAddResult.NOT_ACCEPTING || result == FarmingBinAddResult.FULL) {
            if (fromOp) mes(FarmingMessages.BIN_MUST_BE_EMPTIED)
            return
        }
        deposit.commitAll()
        // Sequence 832 has no loopback; let its 46 client cycles finish without an immediate reset.
        anim(FillAnimation)
        soundSynth("synth.FARMING_PUTIN")
        if (result == FarmingBinAddResult.ADDED_FULL || inv.count(itemName) <= 0) return
        // Loc ops run after queues; later queue-handler additions require +1.
        weakQueue(FarmingParams.QUEUE_BIN_TAKE, FillCadenceTicks + if (fromOp) 0 else 1, task)
    }

    private fun ProtectedAccess.tryUpgrade(def: FarmingBinDef, state: FarmingBinState, objId: Int) {
        val dose = compostPotionDoseByObj[objId]
        if (dose != null) {
            upgradeWithPotion(def, state, dose)
            return
        }
        if (objId == volcanicAshObjId) {
            upgradeWithAsh(def, state)
            return
        }
        mes(Constants.dm_default)
    }

    private fun ProtectedAccess.upgradeWithPotion(
        def: FarmingBinDef,
        state: FarmingBinState,
        dose: Int,
    ) {
        if (state.product != FarmingBinProduct.COMPOST) {
            mes(FarmingMessages.BIN_WRONG_TIER_FOR_POTION)
            return
        }
        val doseItem = FarmingParams.SUPERCOMPOST_POTION_DOSES[dose]
        if (invDel(inv, doseItem, 1).failure) return
        if (!binManager.upgrade(player, def, FarmingBinProduct.SUPERCOMPOST)) {
            invAdd(inv, doseItem, 1)
            return
        }
        val leftover = FarmingParams.SUPERCOMPOST_POTION_DOSES[dose - 1]
        if (invAdd(inv, leftover, 1).failure) {
            invAddOrDrop(objRepo, leftover, 1)
        }
        mes(FarmingMessages.BIN_APPLY_POTION)
        mes(FarmingMessages.BIN_COMPOST_TO_SUPERCOMPOST)
    }

    private fun ProtectedAccess.upgradeWithAsh(def: FarmingBinDef, state: FarmingBinState) {
        if (state.product != FarmingBinProduct.SUPERCOMPOST) {
            mes(FarmingMessages.BIN_WRONG_TIER_FOR_ASH)
            return
        }
        val required =
            if (def.type == FarmingBinType.FARMING_GUILD) {
                FarmingParams.ASH_PER_BIG_BIN
            } else {
                FarmingParams.ASH_PER_BIN
            }
        if (invTotal(inv, FarmingParams.VOLCANIC_ASH) < required) {
            mes(FarmingMessages.binNeedsAsh(required))
            return
        }
        startAshUpgrade(def, required)
    }

    private fun ProtectedAccess.startAshUpgrade(def: FarmingBinDef, required: Int) {
        // Sequence 7699 loops; defer its reset until the animation reaches the client.
        anim(FarmingParams.SEQ_ULTRACOMPOST_MAKE)
        weakQueue(
            FarmingParams.QUEUE_BIN_TAKE,
            FarmingParams.ACTION_CADENCE_TICKS,
            CompostBinAshUpgradeTask(def.key.id, required),
        )
    }

    private fun ProtectedAccess.finishAshUpgrade(task: CompostBinAshUpgradeTask) {
        resetAnim()
        val def = binManager.binByKey(FarmingBinKey(task.binKey)) ?: return
        val state = binManager.state(player, def)
        if (state.phase != FarmingBinPhase.OPEN || state.product != FarmingBinProduct.SUPERCOMPOST) {
            return
        }

        if (invDel(inv, FarmingParams.VOLCANIC_ASH, task.ashSpent).failure) return
        if (!binManager.upgrade(player, def, FarmingBinProduct.ULTRACOMPOST)) {
            invAdd(inv, FarmingParams.VOLCANIC_ASH, task.ashSpent)
            return
        }
        mes(FarmingMessages.BIN_SUPERCOMPOST_TO_ULTRACOMPOST)
    }

    private fun ProtectedAccess.dumpBin(loc: BoundLocInfo) {
        val def = binManager.binOf(loc) ?: return
        clearWeakQueue(FarmingParams.QUEUE_BIN_TAKE)
        if (!binManager.dump(player, def)) return
        mes(FarmingMessages.BIN_EMPTIED)
    }

    private fun ProtectedAccess.dispatchQueue(task: CompostBinTask) {
        when (task) {
            is CompostBinFillTask -> fillTick(task)
            is CompostBinTakeTask -> takeTick(task)
            is CompostBinAshUpgradeTask -> finishAshUpgrade(task)
        }
    }

    private fun ProtectedAccess.xpMod(): Double = xpMods.get(player, FarmingParams.FARMING_STAT)

    internal companion object {
        const val FillAnimation = "seq.human_pickuptable"
        const val CloseAnimation = "seq.human_closechest"

        const val FillCadenceTicks = 2
    }
}
