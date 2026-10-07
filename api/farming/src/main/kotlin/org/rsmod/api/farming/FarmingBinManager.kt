package org.rsmod.api.farming

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo

/**
 * Resolve rot timing before mutations and persist successful state changes. Inventory
 * transactions belong to the content action.
 */
@Singleton
public class FarmingBinManager
@Inject
constructor(private val bins: FarmingBinRegistry, private val renderer: FarmingBinRenderer) {

    public fun binOf(locId: Int): FarmingBinDef? = bins.byLoc(locId)

    public fun binOf(loc: LocInfo): FarmingBinDef? = bins.byLoc(loc.id)

    /**
     * Category events carry the base loc; identify the physical bin before resolving its
     * visible transform.
     */
    public fun binOf(loc: BoundLocInfo): FarmingBinDef? = bins.byLoc(loc.id)

    public fun binByKey(key: FarmingBinKey): FarmingBinDef? = bins.byKey(key)

    public fun isBinLoc(locId: Int): Boolean = bins.isBinLoc(locId)

    public fun state(player: Player, def: FarmingBinDef): FarmingBinState {
        val state = player.farmingBinsOrCreate().getOrCreate(def.key)
        if (resolve(state)) {
            renderer.render(player, def, state)
        }
        return state
    }

    public fun stateAt(player: Player, loc: BoundLocInfo): FarmingBinState? {
        val def = binOf(loc) ?: return null
        return state(player, def)
    }

    /** Read stored state without resolving rot or creating an untouched bin. */
    public fun peek(player: Player, def: FarmingBinDef): FarmingBinState? =
        player.farmingBins()?.getOrNull(def.key)

    public fun refresh(player: Player, visible: List<FarmingBinDef>) {
        if (visible.isEmpty()) {
            return
        }
        val states = player.farmingBinsOrCreate()
        for (def in visible) {
            val state = states.getOrCreate(def.key)
            resolve(state)
            renderer.render(player, def, state)
        }
    }

    public fun resolveAll(player: Player) {
        val states = player.farmingBins() ?: return
        for (state in states.asMap().values) {
            resolve(state)
        }
    }

    /** Rotting has no intermediate outcomes; resolve directly at the deadline. */
    internal fun resolve(
        state: FarmingBinState,
        nowMinute: Long = FarmingClock.currentMinute(),
    ): Boolean {
        if (state.phase != FarmingBinPhase.ROTTING) {
            return false
        }
        val cycle = FarmingConstants.BIN_ROT_CYCLE_MINUTES
        // Missing anchors start a fresh rot interval instead of completing immediately.
        if (state.closedMinute <= 0) {
            state.closedMinute = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
            return true
        }
        if (state.closedMinute > nowMinute) {
            state.closedMinute = FarmingClock.boundaryAtOrBefore(nowMinute, cycle)
            return true
        }
        if (nowMinute < state.readyMinute(cycle)) {
            return false
        }
        state.phase = FarmingBinPhase.READY
        return true
    }

    public fun addItem(
        player: Player,
        def: FarmingBinDef,
        item: FarmingBinFill,
    ): FarmingBinAddResult {
        val state = state(player, def)
        val result = applyAdd(state, def.capacity, item)
        if (result == FarmingBinAddResult.ADDED || result == FarmingBinAddResult.ADDED_FULL) {
            renderer.render(player, def, state)
        }
        return result
    }

    internal fun applyAdd(
        state: FarmingBinState,
        capacity: Int,
        item: FarmingBinFill,
    ): FarmingBinAddResult {
        if (item == FarmingBinFill.NONE) {
            return FarmingBinAddResult.NOT_ACCEPTING
        }
        if (state.phase != FarmingBinPhase.FILLING) {
            return FarmingBinAddResult.NOT_ACCEPTING
        }
        if (state.count >= capacity) {
            return FarmingBinAddResult.FULL
        }
        state.fill = state.fill.merge(item)
        state.count++
        return if (state.count >= capacity) {
            FarmingBinAddResult.ADDED_FULL
        } else {
            FarmingBinAddResult.ADDED
        }
    }

    /** Only full bins expose Close in the cache; require full capacity before starting rot. */
    public fun close(player: Player, def: FarmingBinDef): Boolean {
        val state = state(player, def)
        if (!applyClose(state, def.capacity)) {
            return false
        }
        renderer.render(player, def, state)
        return true
    }

    internal fun applyClose(
        state: FarmingBinState,
        capacity: Int,
        nowMinute: Long = FarmingClock.currentMinute(),
    ): Boolean {
        if (state.phase != FarmingBinPhase.FILLING || state.count < capacity) {
            return false
        }
        val product = state.fill.product ?: return false
        state.phase = FarmingBinPhase.ROTTING
        state.product = product
        state.closedMinute = nowMinute
        return true
    }

    public fun open(player: Player, def: FarmingBinDef): FarmingBinOpenResult {
        val state = state(player, def)
        val result =
            when (state.phase) {
                FarmingBinPhase.ROTTING -> FarmingBinOpenResult.NOT_READY
                FarmingBinPhase.READY -> {
                    state.phase = FarmingBinPhase.OPEN
                    FarmingBinOpenResult.OPENED
                }
                FarmingBinPhase.FILLING,
                FarmingBinPhase.OPEN -> FarmingBinOpenResult.NOTHING_TO_OPEN
            }
        if (result == FarmingBinOpenResult.OPENED) {
            renderer.render(player, def, state)
        }
        return result
    }

    /**
     * Content supplies the bucket, item and XP checks; this only changes the counter.
     * Read the product from the result: the final take clears [FarmingBinState.product].
     */
    public fun takeOne(player: Player, def: FarmingBinDef): FarmingBinTake {
        val state = state(player, def)
        val take = applyTake(state)
        if (take.result != FarmingBinTakeResult.NOTHING_TO_TAKE) {
            renderer.render(player, def, state)
        }
        return take
    }

    internal fun applyTake(state: FarmingBinState): FarmingBinTake {
        val product = state.product
        if (state.phase != FarmingBinPhase.OPEN || state.count <= 0 || product == null) {
            return FarmingBinTake(FarmingBinTakeResult.NOTHING_TO_TAKE, product = null)
        }
        state.count--
        if (state.count > 0) {
            return FarmingBinTake(FarmingBinTakeResult.TAKEN, product)
        }
        state.clearToEmpty()
        return FarmingBinTake(FarmingBinTakeResult.TAKEN_LAST, product)
    }

    public fun dump(player: Player, def: FarmingBinDef): Boolean {
        val state = state(player, def)
        if (state.isPristine()) {
            return false
        }
        state.clearToEmpty()
        renderer.render(player, def, state)
        return true
    }

    /**
     * Upgrade only opened bins, one tier at a time. Partly emptied bins still consume the full
     * 25/50 volcanic ash.
     */
    public fun upgrade(player: Player, def: FarmingBinDef, to: FarmingBinProduct): Boolean {
        val state = state(player, def)
        if (!applyUpgrade(state, to)) {
            return false
        }
        renderer.render(player, def, state)
        return true
    }

    internal fun applyUpgrade(state: FarmingBinState, to: FarmingBinProduct): Boolean {
        if (state.phase != FarmingBinPhase.OPEN || state.count <= 0) {
            return false
        }
        val current = state.product ?: return false
        if (current.upgradesTo != to) {
            return false
        }
        state.product = to
        return true
    }
}

public enum class FarmingBinAddResult {
    NOT_ACCEPTING,

    FULL,

    ADDED,

    ADDED_FULL,
}

public enum class FarmingBinOpenResult {
    NOTHING_TO_OPEN,

    NOT_READY,

    OPENED,
}

public data class FarmingBinTake(
    public val result: FarmingBinTakeResult,
    public val product: FarmingBinProduct?,
) {
    public val taken: Boolean
        get() = result != FarmingBinTakeResult.NOTHING_TO_TAKE
}

public enum class FarmingBinTakeResult {
    NOTHING_TO_TAKE,

    TAKEN,

    TAKEN_LAST,
}
