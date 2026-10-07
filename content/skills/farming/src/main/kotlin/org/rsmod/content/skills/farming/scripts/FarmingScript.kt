package org.rsmod.content.skills.farming.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.farming.FarmingBinManager
import org.rsmod.api.farming.FarmingClock
import org.rsmod.api.farming.FarmingCompost
import org.rsmod.api.farming.FarmingCropRegistry
import org.rsmod.api.farming.FarmingCureMethod
import org.rsmod.api.farming.FarmingHarvestResult
import org.rsmod.api.farming.FarmingManager
import org.rsmod.api.farming.FarmingPatchDef
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPatchType
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocCategory1
import org.rsmod.api.script.onOpLocCategory2
import org.rsmod.api.script.onOpLocCategory4
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.StatComponentsRow
import org.rsmod.content.interfaces.skill.guides.openSkillGuide
import org.rsmod.content.skills.farming.FarmingCompanionProtectionHook
import org.rsmod.content.skills.farming.configs.FarmingCropDef
import org.rsmod.content.skills.farming.configs.FarmingCrops
import org.rsmod.content.skills.farming.configs.FarmingMessages
import org.rsmod.content.skills.farming.configs.FarmingParams
import org.rsmod.content.skills.farming.configs.FarmingSaplingData
import org.rsmod.content.skills.farming.configs.FarmingSaplingDef
import org.rsmod.content.skills.farming.configs.FarmingServiceData
import org.rsmod.content.skills.farming.configs.HesporiSeedDrops
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal data class FarmingPatchTask(val patch: String)

internal data class FarmingPlantTask(val patch: String, val cropId: String)

internal data class FarmingCompostTask(
    val patch: String,
    val tier: FarmingCompost,
    val bucket: String,
)

internal data class FarmingWaterTask(val patch: String, val canDoses: Int)

internal data class FarmingSeedlingTask(val cropId: String, val canDoses: Int)

class FarmingScript
@Inject
constructor(
    private val manager: FarmingManager,
    private val cropRegistry: FarmingCropRegistry,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val companionProtection: FarmingCompanionProtectionHook,
    private val eventBus: EventBus,
    private val binManager: FarmingBinManager,
    private val binScript: CompostBinScript,
) : PluginScript() {
    private var Player.seedlingReady by intVarp(FarmingParams.VARP_SEEDLING_READY)

    private val farmingGuideRow: StatComponentsRow by lazy {
        StatComponentsRow.all().first { it.stat.internalName == "stat.farming" }
    }

    // Resolve lazily because gameval tables load after construction.
    private val cropsByPlantedObj: Map<Int, FarmingCropDef> by lazy {
        FarmingCrops.ALL.associateBy { it.plantedObj.asRSCM(RSCMType.OBJ) }
    }

    private val compostByObj: Map<Int, Pair<FarmingCompost, String>> by lazy {
        FarmingServiceData.compostByObj
    }

    private val wateringCanDoses: Map<Int, Int> by lazy {
        FarmingParams.WATERING_CANS.withIndex()
            .filter { it.index > 0 }
            .associate { (doses, symbol) -> symbol.asRSCM(RSCMType.OBJ) to doses }
    }

    private val pottedCrops: List<FarmingSaplingDef> by lazy { FarmingSaplingData.all }

    private val emptyPlantPotId: Int by lazy { FarmingParams.EMPTY_PLANT_POT.asRSCM(RSCMType.OBJ) }

    private val emptyWateringCanId: Int by lazy {
        FarmingParams.WATERING_CANS[0].asRSCM(RSCMType.OBJ)
    }

    override fun ScriptContext.startup() {
        FarmingServiceData.validate()
        // Register crops before GameLifecycle.Startup validates the registry.
        cropRegistry.registerAll(FarmingCrops.ALL.map(FarmingCropDef::toApiCrop))

        onOpLocCategory1(FarmingParams.PATCH_CATEGORY) { primaryOp(it.loc) }
        onOpLocCategoryU(FarmingParams.PATCH_CATEGORY) { useOnPatch(it.loc, it.objType.id) }
        onOpLocCategory2(FarmingParams.PATCH_CATEGORY) { inspectPatch(it.loc) }
        onOpLocCategory4(FarmingParams.PATCH_CATEGORY) { openGuide() }

        registerSaplingChain()

        onPlayerQueueWithArgs<FarmingPatchTask>(FarmingParams.QUEUE_RAKE) { rakeTick(it.args) }
        onPlayerQueueWithArgs<FarmingPlantTask>(FarmingParams.QUEUE_PLANT) { finishPlant(it.args) }
        onPlayerQueueWithArgs<FarmingCompostTask>(FarmingParams.QUEUE_COMPOST) {
            finishCompost(it.args)
        }
        onPlayerQueueWithArgs<FarmingWaterTask>(FarmingParams.QUEUE_WATER) { finishWater(it.args) }
        onPlayerQueueWithArgs<FarmingPatchTask>(FarmingParams.QUEUE_CURE) { finishCure(it.args) }
        onPlayerQueueWithArgs<FarmingPatchTask>(FarmingParams.QUEUE_CLEAR) { finishClear(it.args) }
        onPlayerQueueWithArgs<FarmingPatchTask>(FarmingParams.QUEUE_CHECK_HEALTH) {
            finishCheckHealth(it.args)
        }
        onPlayerQueueWithArgs<FarmingPatchTask>(FarmingParams.QUEUE_HARVEST) {
            harvestTick(it.args)
        }
        onPlayerQueueWithArgs<FarmingPatchTask>(FarmingParams.QUEUE_CHOP) { finishChop(it.args) }
        onPlayerQueueWithArgs<FarmingPatchTask>(FarmingParams.QUEUE_SEEDLING_GROW) {
            growSeedlings()
        }
        onPlayerQueueWithArgs<FarmingPatchTask>(FarmingParams.QUEUE_FILL_POT) {
            finishFillPlantPot(it.args)
        }
        onPlayerQueueWithArgs<FarmingSeedlingTask>(FarmingParams.QUEUE_WATER_SEEDLING) {
            finishWaterSeedling(it.args)
        }
    }

    private suspend fun ProtectedAccess.primaryOp(loc: BoundLocInfo) {
        growSeedlings()
        // Bins share category 210; its op1 handler can have only one subscriber.
        val bin = binManager.binOf(loc)
        if (bin != null) {
            binScript.opOne(this, bin)
            return
        }
        val def = manager.patchOf(loc) ?: return
        refreshCompanionProtection(def)
        val state = manager.state(player, def)

        if (state.dead || state.stump) {
            startClear(def)
            return
        }
        if (state.diseased) {
            startCure(def)
            return
        }
        val cropId = state.cropId
        if (cropId == null) {
            startRake(def)
            return
        }
        val crop = FarmingCrops.byId(cropId) ?: return
        val api = cropRegistry.find(cropId) ?: return
        if (state.stage < api.grownStage) {
            mes(FarmingMessages.NOT_READY)
            return
        }
        if (api.checkHealth && !state.checkHealthClaimed) {
            startCheckHealth(def)
            return
        }
        when {
            crop.placedNotGrown -> startRemoveScarecrow(def)
            crop.type == FarmingPatchType.TREE -> startChop(def)
            api.produceCapacity > 0 && state.produceCount <= 0 ->
                if (crop.type == FarmingPatchType.FRUIT_TREE) startChop(def) else startClear(def)
            else -> startHarvest(def)
        }
    }

    private suspend fun ProtectedAccess.useOnPatch(loc: BoundLocInfo, objId: Int) {
        growSeedlings()
        val bin = binManager.binOf(loc)
        if (bin != null) {
            binScript.useItem(this, bin, objId)
            return
        }
        val def = manager.patchOf(loc) ?: return
        refreshCompanionProtection(def)
        val crop = cropsByPlantedObj[objId]
        if (crop != null) {
            startPlant(def, crop)
            return
        }
        val compost = compostByObj[objId]
        if (compost != null) {
            startCompost(def, compost.first, compost.second)
            return
        }
        val doses = wateringCanDoses[objId]
        if (doses != null) {
            startWater(def, doses)
            return
        }
        if (objId == emptyWateringCanId) {
            mes(FarmingMessages.WATERING_CAN_EMPTY)
            return
        }
        if (objId == emptyPlantPotId) {
            startFillPlantPot(def)
            return
        }
        // A category handler also consumes unmatched item-on-loc events.
        when (objId) {
            FarmingParams.RAKE.asRSCM(RSCMType.OBJ) -> startRake(def)
            FarmingParams.SPADE.asRSCM(RSCMType.OBJ) -> useSpade(def)
            FarmingParams.PLANT_CURE.asRSCM(RSCMType.OBJ) -> {
                if (manager.cureMethod(def) == FarmingCureMethod.CURE) startCure(def)
                else mes(Constants.dm_default)
            }
            FarmingParams.SECATEURS.asRSCM(RSCMType.OBJ),
            FarmingParams.MAGIC_SECATEURS.asRSCM(RSCMType.OBJ) -> {
                if (manager.cureMethod(def) == FarmingCureMethod.PRUNE) startCure(def)
                else mes(Constants.dm_default)
            }
            else -> mes(Constants.dm_default)
        }
    }

    private suspend fun ProtectedAccess.useSpade(def: FarmingPatchDef) {
        val state = manager.state(player, def)
        if (state.dead || state.stump) {
            startClear(def)
            return
        }
        val crop = manager.crop(state.cropId) ?: return
        if ((def.type == FarmingPatchType.ALLOTMENT || def.type == FarmingPatchType.HERB) &&
            state.stage >= crop.grownStage
        ) {
            startHarvest(def)
            return
        }
        if ((def.type == FarmingPatchType.TREE || def.type == FarmingPatchType.FRUIT_TREE) &&
            state.stage >= crop.grownStage
        ) {
            mes("Cut the tree down before digging up its stump.")
            return
        }
        if (choice2("Dig up this plant?", true, "Leave it alone.", false)) {
            startClear(def)
        }
    }

    private fun ProtectedAccess.inspectPatch(loc: BoundLocInfo) {
        val def = manager.patchOf(loc) ?: return
        val state = manager.state(player, def)
        val cropId = state.cropId
        if (state.stump) {
            mes(FarmingMessages.INSPECT_STUMP)
            return
        }
        if (cropId == null) {
            mes(
                if (manager.isPlantable(player, def)) {
                    FarmingMessages.INSPECT_EMPTY
                } else {
                    FarmingMessages.INSPECT_WEEDS
                }
            )
            return
        }
        val crop = FarmingCrops.byId(cropId) ?: return
        val api = cropRegistry.find(cropId) ?: return
        if (crop.placedNotGrown) {
            mes(FarmingMessages.INSPECT_SCARECROW)
            return
        }
        val cropName = getInvObj(InvObj(crop.produce ?: crop.plantedObj)).name
        if (state.dead) {
            mes(FarmingMessages.inspectDead(cropName))
            return
        }
        if (state.diseased) {
            mes(FarmingMessages.inspectDiseased(cropName))
            return
        }
        if (state.stage < api.grownStage) {
            mes(FarmingMessages.inspectGrowing(cropName, state.stage, api.grownStage, state.compost))
            return
        }
        if (api.produceCapacity > 0) {
            mes(
                FarmingMessages.inspectGrownWithProduce(
                    cropName,
                    state.produceCount,
                    api.produceCapacity,
                )
            )
            return
        }
        mes(FarmingMessages.inspectGrown(cropName, state.compost))
    }

    private fun ProtectedAccess.openGuide() {
        player.openSkillGuide(
            farmingGuideRow,
            player.vars["varbit.option_skill_guide"] != 0,
            eventBus,
        )
    }

    private fun ProtectedAccess.startRake(def: FarmingPatchDef) {
        if (!hasTool(FarmingParams.RAKE)) {
            mes(FarmingMessages.NEED_RAKE)
            return
        }
        if (manager.isPlantable(player, def)) {
            mes(FarmingMessages.RAKE_ALREADY_CLEAR)
            return
        }
        clearWeakQueue(FarmingParams.QUEUE_RAKE)
        playRake()
        opQueue(FarmingParams.QUEUE_RAKE, FarmingPatchTask(def.key.id))
    }

    private fun ProtectedAccess.rakeTick(task: FarmingPatchTask) {
        val def = patchOf(task.patch) ?: return
        if (!hasTool(FarmingParams.RAKE)) {
            resetAnim()
            mes(FarmingMessages.NEED_RAKE)
            return
        }
        if (!manager.rake(player, def)) {
            resetAnim()
            return
        }
        invAddOrDrop(objRepo, FarmingParams.WEEDS, 1)
        statAdvance(FarmingParams.FARMING_STAT, FarmingParams.XP_PER_WEED * xpMod())
        spam(FarmingMessages.RAKE_WEEDS)

        if (manager.isPlantable(player, def)) {
            resetAnim()
            return
        }
        // Queue-handler rescheduling requires +1.
        playRake()
        queueQueue(FarmingParams.QUEUE_RAKE, task)
    }

    private fun ProtectedAccess.playRake() {
        anim(FarmingParams.SEQ_RAKING)
        soundSynth(FarmingParams.SYNTH_RAKING)
    }

    private fun ProtectedAccess.startPlant(def: FarmingPatchDef, crop: FarmingCropDef) {
        if (crop.type != def.type) {
            mes(FarmingMessages.WRONG_PATCH)
            return
        }
        if (!manager.isPlantable(player, def)) {
            val state = manager.state(player, def)
            mes(
                if (state.cropId != null || state.stump) {
                    FarmingMessages.PATCH_OCCUPIED
                } else {
                    FarmingMessages.PATCH_NOT_RAKED
                }
            )
            return
        }
        if (player.farmingLvl < crop.levelReq) {
            mes(
                FarmingMessages.levelRequirement(
                    crop.levelReq,
                    FarmingMessages.plantAction(crop.id),
                )
            )
            return
        }
        if (!crop.placedNotGrown) {
            val tool = if (crop.sapling != null) FarmingParams.SPADE else FarmingParams.SEED_DIBBER
            if (!hasTool(tool)) {
                mes(
                    if (crop.sapling != null) {
                        FarmingMessages.NEED_SPADE
                    } else {
                        FarmingMessages.NEED_DIBBER
                    }
                )
                return
            }
        }
        if (inv.count(crop.plantedObj) < crop.seedsPerPatch) {
            mes(FarmingMessages.notEnoughSeeds(crop.seedsPerPatch))
            return
        }
        if (crop.placedNotGrown) {
            soundSynth(FarmingParams.SYNTH_PLANT_SCARECROW)
        } else {
            playDib()
        }
        opQueue(FarmingParams.QUEUE_PLANT, FarmingPlantTask(def.key.id, crop.id))
    }

    private fun ProtectedAccess.finishPlant(task: FarmingPlantTask) {
        resetAnim()
        val def = patchOf(task.patch) ?: return
        val crop = FarmingCrops.byId(task.cropId) ?: return
        val api = cropRegistry.find(task.cropId) ?: return
        if (player.farmingLvl < crop.levelReq) return
        if (!crop.placedNotGrown) {
            val tool = if (crop.sapling != null) FarmingParams.SPADE else FarmingParams.SEED_DIBBER
            if (!hasTool(tool)) return
        }
        if (!manager.isPlantable(player, def)) {
            return
        }
        if (invDel(inv, crop.plantedObj, crop.seedsPerPatch).failure) {
            return
        }
        if (!manager.plant(player, def, api)) {
            // Roll back seeds if the patch changed before commit.
            invAdd(inv, crop.plantedObj, crop.seedsPerPatch)
            return
        }
        if (crop.sapling != null) {
            invAddOrDrop(objRepo, FarmingParams.EMPTY_PLANT_POT)
        }
        // Planting XP is deferred until health-check, final harvest or clearing a dead patch.
        mes(if (crop.placedNotGrown) FarmingMessages.SCARECROW_PLACED else FarmingMessages.PLANTED)
        refreshCompanionProtection(def)
    }

    private fun ProtectedAccess.playDib() {
        anim(FarmingParams.SEQ_SEED_DIBBING)
        soundSynth(FarmingParams.SYNTH_DIBBING)
    }

    private fun ProtectedAccess.startCompost(
        def: FarmingPatchDef,
        tier: FarmingCompost,
        bucket: String,
    ) {
        val state = manager.state(player, def)
        if (state.dead || state.stump) {
            mes(FarmingMessages.COMPOST_NOTHING_TO_TREAT)
            return
        }
        if (state.compost.ordinal >= tier.ordinal) {
            mes(FarmingMessages.COMPOST_ALREADY_BETTER)
            return
        }
        anim(FarmingParams.SEQ_COMPOST_POUR)
        soundSynth(FarmingParams.SYNTH_COMPOST)
        opQueue(FarmingParams.QUEUE_COMPOST, FarmingCompostTask(def.key.id, tier, bucket))
    }

    private fun ProtectedAccess.finishCompost(task: FarmingCompostTask) {
        resetAnim()
        val def = patchOf(task.patch) ?: return
        if (invDel(inv, task.bucket, 1).failure) {
            return
        }
        if (!manager.compost(player, def, task.tier)) {
            invAdd(inv, task.bucket, 1)
            return
        }
        if (invAdd(inv, FarmingParams.BUCKET_EMPTY, 1).failure) {
            invAddOrDrop(objRepo, FarmingParams.BUCKET_EMPTY, 1)
        }
        statAdvance(FarmingParams.FARMING_STAT, compostXp(task.tier) * xpMod())
        mes(FarmingMessages.COMPOST_APPLIED)
    }

    private fun compostXp(tier: FarmingCompost): Double = FarmingServiceData.compostXp(tier)

    private fun ProtectedAccess.startWater(def: FarmingPatchDef, doses: Int) {
        if (doses <= 0) {
            mes(FarmingMessages.WATERING_CAN_EMPTY)
            return
        }
        val state = manager.state(player, def)
        val crop = cropRegistry.find(state.cropId)
        if (crop == null || !crop.waterable || state.stage >= crop.grownStage) {
            mes(FarmingMessages.WATER_NOT_NEEDED)
            return
        }
        playWater()
        opQueue(FarmingParams.QUEUE_WATER, FarmingWaterTask(def.key.id, doses))
    }

    private fun ProtectedAccess.finishWater(task: FarmingWaterTask) {
        val def = patchOf(task.patch) ?: return
        val full = FarmingParams.WATERING_CANS.getOrNull(task.canDoses) ?: return
        val emptier = FarmingParams.WATERING_CANS.getOrNull(task.canDoses - 1) ?: return
        val swap = invReplace(inv, full, 1, emptier, autoCommit = false)
        if (swap.failure) return
        if (!manager.water(player, def)) {
            mes(FarmingMessages.WATER_NOT_NEEDED)
            return
        }
        swap.commitAll()
        mes(FarmingMessages.WATERED)
    }

    // Sequence 2293 has frameStep -1: maxLoops does not make it loop. Allow all 139 client cycles to finish.
    private fun ProtectedAccess.playWater() {
        anim(FarmingParams.SEQ_WATERING)
        soundSynth(FarmingParams.SYNTH_WATERING)
    }

    private fun ProtectedAccess.startCure(def: FarmingPatchDef) {
        if (!manager.state(player, def).diseased) {
            mes(FarmingMessages.NOTHING_TO_CURE)
            return
        }
        when (manager.cureMethod(def)) {
            FarmingCureMethod.CURE ->
                if (!hasTool(FarmingParams.PLANT_CURE)) {
                    mes(FarmingMessages.NEED_PLANT_CURE)
                    return
                }
            FarmingCureMethod.PRUNE ->
                if (!hasSecateurs()) {
                    mes(FarmingMessages.NEED_SECATEURS)
                    return
                }
        }
        playCure(manager.cureMethod(def))
        opQueue(FarmingParams.QUEUE_CURE, FarmingPatchTask(def.key.id))
    }

    private fun ProtectedAccess.finishCure(task: FarmingPatchTask) {
        resetAnim()
        val def = patchOf(task.patch) ?: return
        val state = manager.state(player, def)
        if (!state.diseased) {
            mes(FarmingMessages.NOTHING_TO_CURE)
            return
        }
        when (manager.cureMethod(def)) {
            FarmingCureMethod.CURE -> {
                if (invDel(inv, FarmingParams.PLANT_CURE, 1).failure) {
                    mes(FarmingMessages.NEED_PLANT_CURE)
                    return
                }
                if (invAdd(inv, FarmingParams.EMPTY_VIAL, 1).failure) {
                    invAddOrDrop(objRepo, FarmingParams.EMPTY_VIAL, 1)
                }
                manager.cure(player, def, FarmingParams.PLANT_CURE_SUCCESS_OUT_OF_128)
                mes(FarmingMessages.CURED)
            }
            FarmingCureMethod.PRUNE -> {
                if (!hasSecateurs()) {
                    mes(FarmingMessages.NEED_SECATEURS)
                    return
                }
                val cured = manager.cure(player, def, FarmingParams.SECATEURS_SUCCESS_OUT_OF_128)
                mes(if (cured) FarmingMessages.PRUNED else FarmingMessages.PRUNE_FAILED)
            }
        }
    }

    private fun ProtectedAccess.playCure(method: FarmingCureMethod) {
        anim(FarmingParams.SEQ_PLANT_CURE)
        soundSynth(
            if (method == FarmingCureMethod.PRUNE) {
                FarmingParams.SYNTH_PRUNE
            } else {
                FarmingParams.SYNTH_PLANT_CURE
            }
        )
    }

    private fun ProtectedAccess.startClear(def: FarmingPatchDef) {
        if (!hasTool(FarmingParams.SPADE)) {
            mes(FarmingMessages.NEED_SPADE)
            return
        }
        anim(FarmingParams.SEQ_SPADE_DIG)
        soundSynth(FarmingParams.SYNTH_DIG_SPADE)
        opQueue(FarmingParams.QUEUE_CLEAR, FarmingPatchTask(def.key.id))
    }

    private fun ProtectedAccess.finishClear(task: FarmingPatchTask) {
        resetAnim()
        val def = patchOf(task.patch) ?: return
        if (!hasTool(FarmingParams.SPADE)) return
        val wasStump = manager.state(player, def).stump

        manager.clear(player, def)
        payBankedPlantXp(def)
        mes(if (wasStump) FarmingMessages.CLEARED_STUMP else FarmingMessages.CLEARED_DEAD)
        refreshCompanionProtection(def)
    }

    private fun ProtectedAccess.startRemoveScarecrow(def: FarmingPatchDef) {
        manager.clear(player, def)
        payBankedPlantXp(def)
        mes(FarmingMessages.SCARECROW_REMOVED)
        refreshCompanionProtection(def)
    }

    // Pay deferred planting XP using the crop that incurred the debt, even after the patch resets.
    private fun ProtectedAccess.payBankedPlantXp(def: FarmingPatchDef) {
        val owed = manager.plantXpOwed(player, def) ?: return
        if (!manager.claimPlantXp(player, def)) {
            return
        }
        val crop = FarmingCrops.byId(owed.id) ?: return
        statAdvance(FarmingParams.FARMING_STAT, crop.deferredPlantXp * xpMod())
    }

    private fun ProtectedAccess.startChop(def: FarmingPatchDef) {
        if (!hasAxe()) {
            mes(FarmingMessages.NEED_AXE)
            return
        }
        opQueue(FarmingParams.QUEUE_CHOP, FarmingPatchTask(def.key.id))
    }

    private fun ProtectedAccess.finishChop(task: FarmingPatchTask) {
        resetAnim()
        if (!hasAxe()) return
        val def = patchOf(task.patch) ?: return
        val crop = FarmingCrops.byId(manager.state(player, def).cropId) ?: return
        if (!manager.markStump(player, def)) {
            return
        }
        mes(
            if (crop.type == FarmingPatchType.FRUIT_TREE) {
                FarmingMessages.CHOPPED_FRUIT_TREE
            } else {
                FarmingMessages.CHOPPED_TREE
            }
        )
    }

    private fun ProtectedAccess.startCheckHealth(def: FarmingPatchDef) {
        finishCheckHealth(FarmingPatchTask(def.key.id))
    }

    private fun ProtectedAccess.finishCheckHealth(task: FarmingPatchTask) {
        val def = patchOf(task.patch) ?: return
        val state = manager.state(player, def)
        val crop = FarmingCrops.byId(state.cropId) ?: return
        if (!manager.claimCheckHealth(player, def)) {
            mes(FarmingMessages.ALREADY_CHECKED)
            return
        }
        HesporiSeedDrops.roll(this, crop, atCheckHealth = true)
        val mod = xpMod()
        statAdvance(FarmingParams.FARMING_STAT, crop.checkHealthXp * mod)
        if (manager.claimPlantXp(player, def)) {
            statAdvance(FarmingParams.FARMING_STAT, crop.deferredPlantXp * mod)
        }
        mes(
            if (def.type == FarmingPatchType.TREE || def.type == FarmingPatchType.FRUIT_TREE) {
                "You examine the tree for signs of disease and find that it is in perfect health."
            } else FarmingMessages.CHECK_HEALTH
        )
    }

    private fun ProtectedAccess.startHarvest(def: FarmingPatchDef) {
        clearWeakQueue(FarmingParams.QUEUE_HARVEST)
        if (!checkHarvestTool(def.type)) return
        opQueue(FarmingParams.QUEUE_HARVEST, FarmingPatchTask(def.key.id))
    }

    private fun ProtectedAccess.checkHarvestTool(type: FarmingPatchType): Boolean {
        if ((type == FarmingPatchType.ALLOTMENT || type == FarmingPatchType.HERB) &&
            !hasTool(FarmingParams.SPADE)
        ) {
            mes(FarmingMessages.NEED_SPADE)
            return false
        }
        return true
    }

    private fun ProtectedAccess.harvestTick(task: FarmingPatchTask) {
        val def = patchOf(task.patch) ?: return
        val state = manager.state(player, def)
        val crop = FarmingCrops.byId(state.cropId)
        val produce = crop?.produce
        val api = crop?.let { cropRegistry.find(it.id) }
        if (crop == null || produce == null || api == null) {
            resetAnim()
            return
        }
        if (!checkHarvestTool(def.type)) {
            resetAnim()
            return
        }

        val produceType = getInvObj(InvObj(produce))
        val needsSlot = !produceType.isStackable || inv.count(produce) <= 0
        if (needsSlot && inv.freeSpace() < 1) {
            resetAnim()
            mes(Constants.dm_invspace)
            return
        }

        val result = manager.harvestOne(player, def, chanceToSave(crop))

        // The manager records planting debt only when a pick actually empties the patch.
        payBankedPlantXp(def)

        if (
            result == FarmingHarvestResult.NOT_HARVESTABLE ||
                result == FarmingHarvestResult.DEPLETED
        ) {
            resetAnim()
            if (result == FarmingHarvestResult.NOT_HARVESTABLE) {
                mes(
                    when {
                        state.stage < api.grownStage -> FarmingMessages.NOT_READY
                        api.checkHealth && !state.checkHealthClaimed ->
                            FarmingMessages.NEEDS_CHECKING
                        else -> FarmingMessages.NOTHING_LEFT
                    }
                )
            }
            return
        }

        playHarvest(def.type)

        val mod = xpMod()
        // A consumed harvest life must still deliver produce if insertion unexpectedly fails.
        if (invAdd(inv, produce, 1).failure) {
            invAddOrDrop(objRepo, produce, 1)
        }
        statAdvance(FarmingParams.FARMING_STAT, crop.harvestXp * mod)
        spam(FarmingMessages.harvested(produceType.name))
        publish(PlayerActionCompleteEvent(player, PlayerAction.HarvestCrop, produce))

        if (result == FarmingHarvestResult.PICKED_LAST) {
            HesporiSeedDrops.roll(this, crop, atCheckHealth = false)
            // Let the final pick reach the client instead of replacing it with sequence -1.
            refreshCompanionProtection(def)
            return
        }
        // Queue-handler rescheduling requires +1.
        queueQueue(FarmingParams.QUEUE_HARVEST, task)
    }

    private fun ProtectedAccess.playHarvest(type: FarmingPatchType) {
        if (type == FarmingPatchType.ALLOTMENT) {
            anim(FarmingParams.SEQ_SPADE_DIG)
            soundSynth(FarmingParams.SYNTH_DIG_SPADE)
        } else {
            playPick()
        }
    }

    private fun ProtectedAccess.chanceToSave(crop: FarmingCropDef): Int {
        val cts = crop.chanceToSave ?: return 0
        var chance = cts.atLevel(player.farmingLvl)
        if (hasMagicSecateurs()) {
            chance = chance * 110 / 100
        }
        return chance.coerceIn(0, 256)
    }

    private fun ProtectedAccess.playPick() {
        anim(FarmingParams.SEQ_PICKING)
        soundSynth(FarmingParams.SYNTH_PICK)
    }

    private fun ScriptContext.registerSaplingChain() {
        for (crop in pottedCrops) {
            val potSeed = crop.potSeed
            val potSeedWatered = crop.potSeedWatered
            val seed = checkNotNull(ServerCacheManager.getItem(crop.seed.asRSCM()))
            val seedItems = listOf(crop.seed) + seed.countObj.orEmpty().filter { it > 0 }
                .distinct().map { RSCM.getReverseMapping(RSCMType.OBJ, it) }
            for (seedItem in seedItems) {
                for (pot in FarmingParams.FILLED_PLANT_POTS) {
                    onOpHeldU(pot, seedItem) { potSeed(crop, seedItem, pot) }
                }
                onOpHeldU(FarmingParams.EMPTY_PLANT_POT, seedItem) {
                    mes("You need to fill the plant pot with soil from an empty Farming patch first.")
                }
            }
            onOpHeldU(potSeed, FarmingParams.WATERING_CANS[0]) {
                mes("Your watering can is empty.")
            }
            for (doses in 1..FarmingParams.WATERING_CAN_DOSES) {
                onOpHeldU(potSeed, FarmingParams.WATERING_CANS[doses]) {
                    waterSeedling(crop, doses)
                }
                onOpHeldU(potSeedWatered, FarmingParams.WATERING_CANS[doses]) {
                    mes(FarmingMessages.SEEDLING_ALREADY_WATERED)
                }
            }
        }
    }

    private fun ProtectedAccess.startFillPlantPot(def: FarmingPatchDef) {
        if (!hasTool(FarmingParams.GARDENING_TROWEL)) {
            mes(FarmingMessages.NEED_TROWEL)
            return
        }
        if (!manager.isPlantable(player, def)) {
            mes("You need an empty, weed-free patch to fill a plant pot.")
            return
        }
        anim(FarmingParams.SEQ_TROWEL_DIGGING)
        soundSynth(FarmingParams.SYNTH_FILL_PLANT_POT)
        opQueue(FarmingParams.QUEUE_FILL_POT, FarmingPatchTask(def.key.id))
    }

    private fun ProtectedAccess.finishFillPlantPot(task: FarmingPatchTask) {
        resetAnim()
        if (!hasTool(FarmingParams.GARDENING_TROWEL)) return
        val def = patchOf(task.patch) ?: return
        if (!manager.isPlantable(player, def)) return
        if (invDel(inv, FarmingParams.EMPTY_PLANT_POT, 1).failure) {
            return
        }
        if (invAdd(inv, FarmingParams.FILLED_PLANT_POT, 1).failure) {
            invAdd(inv, FarmingParams.EMPTY_PLANT_POT, 1)
            mes(Constants.dm_invspace)
            return
        }
        mes(FarmingMessages.POT_FILLED)
    }

    private fun ProtectedAccess.potSeed(crop: FarmingSaplingDef, seed: String, pot: String) {
        if (!hasTool(FarmingParams.GARDENING_TROWEL)) {
            mes(FarmingMessages.NEED_TROWEL)
            return
        }
        if (player.farmingLvl < crop.levelReq) {
            mes(
                FarmingMessages.levelRequirement(
                    crop.levelReq,
                    FarmingMessages.plantAction(crop.id),
                )
            )
            return
        }
        val planted = player.invTransaction(inv) {
            val inventory = select(inv)
            delete { from = inventory; obj = pot.asRSCM(); strictCount = 1 }
            delete { from = inventory; obj = seed.asRSCM(); strictCount = 1 }
            insert { into = inventory; obj = crop.potSeed.asRSCM(); strictCount = 1 }
        }
        if (planted.failure) return
        mes(FarmingMessages.SEED_POTTED)
    }

    private fun ProtectedAccess.waterSeedling(crop: FarmingSaplingDef, doses: Int) {
        playWater()
        // Held-item handlers run before queue processing; initial delays require +1.
        queueQueue(FarmingParams.QUEUE_WATER_SEEDLING, FarmingSeedlingTask(crop.id, doses))
    }

    private fun ProtectedAccess.finishWaterSeedling(task: FarmingSeedlingTask) {
        val crop = pottedCrops.find { it.id == task.cropId } ?: return
        val potSeed = crop.potSeed
        val potSeedWatered = crop.potSeedWatered
        val full = FarmingParams.WATERING_CANS.getOrNull(task.canDoses) ?: return
        val emptier = FarmingParams.WATERING_CANS.getOrNull(task.canDoses - 1) ?: return
        val watered = player.invTransaction(inv) {
            waterSeedling(
                select(inv),
                potSeed.asRSCM(RSCMType.OBJ),
                potSeedWatered.asRSCM(RSCMType.OBJ),
                full.asRSCM(RSCMType.OBJ),
                emptier.asRSCM(RSCMType.OBJ),
            )
        }
        if (watered.failure) return
        scheduleWateredSeedlings()
        mes(FarmingMessages.SEEDLING_WATERED)
    }

    private fun ProtectedAccess.growSeedlings() {
        val deadline = player.seedlingReady
        if (deadline <= 0 || FarmingClock.currentMinute() < deadline.toLong()) {
            return
        }
        var grown = false
        for (crop in pottedCrops) {
            val watered = crop.potSeedWatered
            val sapling = crop.sapling
            val held = inv.count(watered)
            if (held <= 0) {
                continue
            }
            if (invDel(inv, watered, held).failure) {
                continue
            }
            if (invAdd(inv, sapling, held).failure) {
                invAdd(inv, watered, held)
                continue
            }
            grown = true
        }
        if (grown) {
            // Keep the elapsed deadline for watered seedlings still in the bank.
            mes(FarmingMessages.SEEDLING_GROWN)
        }
    }

    private fun ProtectedAccess.refreshCompanionProtection(def: FarmingPatchDef) {
        companionProtection.beforeResolve(player, def)
    }

    private fun patchOf(key: String): FarmingPatchDef? = manager.patchByKey(FarmingPatchKey(key))

    private fun ProtectedAccess.hasTool(obj: String): Boolean = inv.count(obj) > 0

    private fun ProtectedAccess.hasMagicSecateurs(): Boolean =
        hasTool(FarmingParams.MAGIC_SECATEURS) ||
            player.righthand?.id == FarmingParams.MAGIC_SECATEURS.asRSCM(RSCMType.OBJ)

    private fun ProtectedAccess.hasSecateurs(): Boolean =
        hasTool(FarmingParams.SECATEURS) || hasMagicSecateurs()

    private fun ProtectedAccess.hasAxe(): Boolean {
        val worn = player.righthand
        if (worn != null && getInvObj(worn).isContentType(FarmingParams.AXE_CONTENT)) {
            return true
        }
        return inv.filterNotNull { getInvObj(it).isContentType(FarmingParams.AXE_CONTENT) }
            .isNotEmpty()
    }

    private fun ProtectedAccess.xpMod(): Double = xpMods.get(player, FarmingParams.FARMING_STAT)

    // Loc-op handlers run after queue processing; use the literal delay.
    private fun ProtectedAccess.opQueue(queue: String, task: Any) {
        weakQueue(queue, FarmingParams.ACTION_CADENCE_TICKS, task)
    }

    // Queue and held-item handlers add before decrement; compensate with +1.
    private fun ProtectedAccess.queueQueue(queue: String, task: Any) {
        weakQueue(queue, FarmingParams.ACTION_CADENCE_TICKS + 1, task)
    }
}
