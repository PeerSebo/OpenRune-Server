package org.rsmod.api.farming

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo

/** Resolve elapsed growth before mutating a patch, then persist and render successful changes. */
@Singleton
public class FarmingManager
@Inject
constructor(
    private val patches: FarmingPatchRegistry,
    private val crops: FarmingCropRegistry,
    private val growth: FarmingGrowth,
    private val renderer: FarmingRenderer,
    private val random: GameRandom,
    /** Hooks may inject FarmingManager; resolving the set lazily avoids a construction cycle. */
    private val preResolveHooks: Provider<Set<FarmingPreResolveHook>>,
) {

    public fun patchOf(locId: Int): FarmingPatchDef? = patches.byLoc(locId)

    public fun patchOf(loc: LocInfo): FarmingPatchDef? = patches.byLoc(loc.id)

    public fun patchOf(loc: BoundLocInfo): FarmingPatchDef? = patches.byLoc(loc.id)

    public fun patchByKey(key: FarmingPatchKey): FarmingPatchDef? = patches.byKey(key)

    public fun crop(id: String?): FarmingCrop? = crops.find(id)

    public fun cropAt(player: Player, def: FarmingPatchDef): FarmingCrop? =
        crops.find(state(player, def).cropId)

    public fun state(player: Player, def: FarmingPatchDef): FarmingPatchState {
        val state = player.farmingPatchesOrCreate().getOrCreate(def.key)
        if (growth.resolve(state, def.type)) {
            renderer.render(player, def, state)
        }
        return state
    }

    public fun stateAt(player: Player, loc: BoundLocInfo): FarmingPatchState? {
        val def = patchOf(loc) ?: return null
        return state(player, def)
    }

    /** Read without growth catch-up so companion hooks cannot resolve disease before protection. */
    public fun peek(player: Player, def: FarmingPatchDef): FarmingPatchState? =
        player.farmingPatches()?.getOrNull(def.key)

    public fun refresh(player: Player, visible: List<FarmingPatchDef>) {
        if (visible.isEmpty()) {
            return
        }
        // Apply every companion hook before any patch resolves disease.
        runPreResolveHooks(player, visible)
        val states = player.farmingPatchesOrCreate()
        for (def in visible) {
            val state = states.getOrCreate(def.key)
            growth.resolve(state, def.type)
            renderer.render(player, def, state)
        }
    }

    public fun resolveAll(player: Player) {
        val states = player.farmingPatches() ?: return
        runPreResolveHooks(player, states.asMap().keys.mapNotNull(patches::byKey))
        for ((key, state) in states.asMap()) {
            growth.resolve(state, patches.byKey(key)?.type)
        }
    }

    private fun runPreResolveHooks(player: Player, defs: Collection<FarmingPatchDef>) {
        val hooks = preResolveHooks.get()
        if (hooks.isEmpty()) {
            return
        }
        for (def in defs) {
            for (hook in hooks) {
                hook.beforeResolve(player, def)
            }
        }
    }

    /** Raking leaves the globally aligned weed clock intact. */
    public fun rake(player: Player, def: FarmingPatchDef): Boolean {
        val state = state(player, def)
        if (state.cropId != null || state.stump) {
            return false
        }
        if (state.rakeStage >= FarmingConstants.RAKE_STAGES) {
            return false
        }
        state.rakeStage++
        renderer.render(player, def, state)
        return true
    }

    public fun isPlantable(player: Player, def: FarmingPatchDef): Boolean {
        val state = state(player, def)
        return state.cropId == null &&
            !state.stump &&
            state.rakeStage >= FarmingConstants.RAKE_STAGES
    }

    /** Anchor planting at the current or preceding growth boundary, not at the click time. */
    public fun plant(player: Player, def: FarmingPatchDef, crop: FarmingCrop): Boolean {
        if (crop.patchType != def.type) {
            return false
        }
        val state = state(player, def)
        if (state.cropId != null || state.stump || state.rakeStage < FarmingConstants.RAKE_STAGES) {
            return false
        }
        val compost = state.compost
        state.cropId = crop.id
        state.stage = 0
        state.rakeStage = FarmingConstants.RAKE_STAGES
        state.watered = false
        state.diseased = false
        state.dead = false
        state.stump = false
        state.produceCount = 0
        state.checkHealthClaimed = false
        state.plantXpPaid = false
        // Replanting must not transfer the previous crop's deferred XP to the new crop.
        state.plantXpOwedCrop = null
        state.lives = startingLives(crop, compost)
        state.lastGrowthMinute =
            FarmingClock.boundaryAtOrBefore(FarmingClock.currentMinute(), crop.cycleMinutes)
        state.lastProduceMinute =
            if (crop.produceCapacity > 0) {
                FarmingClock.boundaryAtOrBefore(
                    FarmingClock.currentMinute(),
                    crop.produceRegrowMinutes,
                )
            } else {
                0
            }
        renderer.render(player, def, state)
        return true
    }

    /**
     * Apply harvest-life upgrades as the difference between compost tiers, not a fresh
     * baseline. Mid-growth ultracompost follows the compost/supercompost rule by inference.
     */
    public fun compost(player: Player, def: FarmingPatchDef, tier: FarmingCompost): Boolean {
        if (tier == FarmingCompost.NONE) {
            return false
        }
        val state = state(player, def)
        if (state.dead || state.stump) {
            return false
        }
        if (state.compost.ordinal >= tier.ordinal) {
            return false
        }
        val previous = state.compost
        state.compost = tier
        val crop = crops.find(state.cropId)
        if (crop != null && crop.baseLives > 0) {
            state.lives += tier.extraLives - previous.extraLives
        }
        return true
    }

    public fun water(player: Player, def: FarmingPatchDef): Boolean {
        val state = state(player, def)
        val crop = crops.find(state.cropId) ?: return false
        if (!crop.waterable || crop.visuals.watered == null) {
            return false
        }
        if (state.watered || state.diseased || state.dead || state.stump) {
            return false
        }
        if (state.stage >= crop.grownStage) {
            return false
        }
        state.watered = true
        renderer.render(player, def, state)
        return true
    }

    public fun cureMethod(def: FarmingPatchDef): FarmingCureMethod = def.type.cure

    /** Secateurs use successOutOf/128; plant cure passes the full denominator. */
    public fun cure(
        player: Player,
        def: FarmingPatchDef,
        successOutOf: Int = FarmingConstants.DISEASE_DENOMINATOR,
    ): Boolean {
        val state = state(player, def)
        if (!state.diseased || state.dead) {
            return false
        }
        val chance = successOutOf.coerceIn(0, FarmingConstants.DISEASE_DENOMINATOR)
        if (random.of(FarmingConstants.DISEASE_DENOMINATOR) >= chance) {
            return false
        }
        state.diseased = false
        renderer.render(player, def, state)
        return true
    }

    /** Bank deferred planting XP before clearing a dead or fully grown crop. */
    public fun clear(player: Player, def: FarmingPatchDef) {
        val state = state(player, def)
        applyClear(state, crops.find(state.cropId))
        renderer.render(player, def, state)
    }

    internal fun applyClear(state: FarmingPatchState, crop: FarmingCrop?) {
        val grown = crop != null && state.stage >= crop.grownStage
        if (state.dead || state.stump || grown) {
            bankPlantXpDebt(state)
        }
        state.clearToEmpty()
    }

    public fun markStump(player: Player, def: FarmingPatchDef): Boolean {
        val state = state(player, def)
        if (state.cropId == null || state.stump || state.dead) {
            return false
        }
        state.stump = true
        renderer.render(player, def, state)
        return true
    }

    public fun claimCheckHealth(player: Player, def: FarmingPatchDef): Boolean {
        val state = state(player, def)
        val crop = crops.find(state.cropId) ?: return false
        if (!applyCheckHealth(state, crop)) {
            return false
        }
        renderer.render(player, def, state)
        return true
    }

    internal fun applyCheckHealth(
        state: FarmingPatchState,
        crop: FarmingCrop,
        nowMinute: Long = FarmingClock.currentMinute(),
    ): Boolean {
        if (!crop.checkHealth || state.checkHealthClaimed) {
            return false
        }
        if (state.dead || state.diseased || state.stump || state.stage < crop.grownStage) {
            return false
        }
        state.checkHealthClaimed = true
        // The first harvest is already full; regrowth replaces only fruit picked afterward.
        if (crop.produceCapacity > 0) {
            state.produceCount = crop.produceCapacity
            state.lastProduceMinute =
                FarmingClock.boundaryAtOrBefore(nowMinute, crop.produceRegrowMinutes)
        }
        return true
    }

    /**
     * Bank deferred planting XP before a terminal harvest clears crop state; claim it at most
     * once.
     */
    public fun claimPlantXp(player: Player, def: FarmingPatchDef): Boolean =
        applyClaimPlantXp(state(player, def))

    internal fun applyClaimPlantXp(state: FarmingPatchState): Boolean {
        if (state.plantXpOwedCrop != null) {
            state.plantXpOwedCrop = null
            return true
        }
        if (state.cropId == null || state.plantXpPaid) {
            return false
        }
        state.plantXpPaid = true
        return true
    }

    public fun plantXpOwed(player: Player, def: FarmingPatchDef): FarmingCrop? =
        crops.find(state(player, def).plantXpOwedCrop)

    /**
     * Companion/scarecrow immunity is independent of paid protection and must be recomputed
     * before growth catch-up.
     */
    public fun setProtected(player: Player, def: FarmingPatchDef, protect: Boolean): Boolean {
        val state = player.farmingPatchesOrCreate().getOrCreate(def.key)
        if (state.protected == protect) {
            return false
        }
        state.protected = protect
        return true
    }

    /**
     * Paid protection lasts until harvest or clearing; keep it separate from recomputed
     * companion protection.
     */
    public fun setPaidProtected(player: Player, def: FarmingPatchDef, protect: Boolean): Boolean {
        val state = player.farmingPatchesOrCreate().getOrCreate(def.key)
        if (state.paidProtected == protect) {
            return false
        }
        state.paidProtected = protect
        return true
    }

    /** Read stored state without resolving growth during the payment dialogue. */
    public fun isPaidProtected(player: Player, def: FarmingPatchDef): Boolean =
        player.farmingPatchesOrCreate().getOrCreate(def.key).paidProtected

    /**
     * Harvest shape comes from the crop: finite lives, regrowing produce, or one-shot harvest.
     * Bank deferred XP before clearing the last crop state.
     */
    public fun harvestOne(
        player: Player,
        def: FarmingPatchDef,
        saveChanceOutOf256: Int = 0,
    ): FarmingHarvestResult {
        val state = state(player, def)
        val crop = crops.find(state.cropId) ?: return FarmingHarvestResult.NOT_HARVESTABLE
        val result = applyHarvest(state, crop, saveChanceOutOf256)
        if (result != FarmingHarvestResult.NOT_HARVESTABLE) {
            renderer.render(player, def, state)
        }
        return result
    }

    internal fun applyHarvest(
        state: FarmingPatchState,
        crop: FarmingCrop,
        saveChanceOutOf256: Int,
    ): FarmingHarvestResult {
        if (state.dead || state.diseased || state.stump || state.stage < crop.grownStage) {
            return FarmingHarvestResult.NOT_HARVESTABLE
        }
        if (crop.checkHealth && !state.checkHealthClaimed) {
            return FarmingHarvestResult.NOT_HARVESTABLE
        }

        if (crop.baseLives <= 0 && crop.produceCapacity > 0) {
            if (state.produceCount <= 0) {
                return FarmingHarvestResult.NOT_HARVESTABLE
            }
            state.produceCount--
            return FarmingHarvestResult.PICKED
        }

        if (crop.baseLives <= 0) {
            bankPlantXpDebt(state)
            state.clearToEmpty()
            return FarmingHarvestResult.PICKED_LAST
        }

        if (state.lives <= 0) {
            bankPlantXpDebt(state)
            state.clearToEmpty()
            return FarmingHarvestResult.DEPLETED
        }

        val saved = saveChanceOutOf256 > 0 && random.of(SAVE_DENOMINATOR) < saveChanceOutOf256
        if (!saved) {
            state.lives--
        }
        if (state.lives <= 0) {
            bankPlantXpDebt(state)
            state.clearToEmpty()
            return FarmingHarvestResult.PICKED_LAST
        }
        return if (saved) FarmingHarvestResult.PICKED_SAVED else FarmingHarvestResult.PICKED
    }

    /** Mark planting XP as paid when banking its debt so terminal paths cannot bank it twice. */
    private fun bankPlantXpDebt(state: FarmingPatchState) {
        if (state.cropId == null || state.plantXpPaid) {
            return
        }
        state.plantXpPaid = true
        state.plantXpOwedCrop = state.cropId
    }

    private fun startingLives(crop: FarmingCrop, compost: FarmingCompost): Int =
        if (crop.baseLives <= 0) 0 else crop.baseLives + compost.extraLives

    private companion object {
        /** Chance-to-save is published as `x/256`. */
        private const val SAVE_DENOMINATOR: Int = 256
    }
}

public enum class FarmingHarvestResult {
    NOT_HARVESTABLE,

    PICKED,

    PICKED_SAVED,

    PICKED_LAST,

    DEPLETED,
}
