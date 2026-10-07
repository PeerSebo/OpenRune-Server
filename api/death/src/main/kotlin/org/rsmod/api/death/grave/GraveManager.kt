package org.rsmod.api.death.grave

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.InvStackType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.player.vars.typeCoordVarp
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

/**
 * Grave contents and duration persist on the player; the world NPC is reconstructible session
 * state. Commit transfers before removing source items, and retain any items Death storage
 * refuses.
 */
@Singleton
public class GraveManager
@Inject
constructor(
    private val mapClock: MapClock,
    private val npcRepo: NpcRepository,
    private val collapseHooks: Set<GraveCollapseHook>,
    private val pauseHooks: Set<GraveTimerPauseHook>,
) {
    private val logger = InlineLogger()

    private val gravesByCharacter = HashMap<Int, Grave>()
    private val usedTokens = HashSet<Int>()

    private val pendingResync = HashSet<Int>()

    private var lastWarnCycle = Int.MIN_VALUE

    private var Player.graveToken: Int by intVarBit(VISIBLE_VARBIT)
    private var Player.graveDuration: Int by intVarBit(DURATION_VARBIT)
    private var Player.graveTypeVar: Int by intVarBit(TYPE_VARBIT)
    private var Player.graveDeathCoord: CoordGrid? by typeCoordVarp(DEATH_COORD_VARP)

    public fun grave(player: Player): Grave? = gravesByCharacter[player.characterId]

    public fun hasGrave(player: Player): Boolean = grave(player) != null

    public fun remainingTicks(player: Player): Int = player.graveDuration

    /**
     * The reclaimable container. Created on first use rather than seeded into
     * [org.rsmod.api.inv.map.InvMapInit.defaultInvs] so that a bad `inv.gravestone` definition
     * cannot break every login; a persisted grave inv is restored regardless, because
     * `CharacterInventoryApplier` does `invMap.getOrPut(loaded.invKey)`.
     */
    public fun graveInv(player: Player): Inventory =
        player.invMap.getOrPut(GRAVE_INV).also { it.owner = player }

    public fun deathStorageInv(player: Player): Inventory =
        player.invMap.getOrPut(DEATH_STORAGE_INV).also { it.owner = player }

    public fun isOwner(player: Player, npc: Npc): Boolean = grave(player)?.npc === npc

    // Commit items before publishing the grave. After the first successful insertion,
    // exceptions must not return the same items to the caller.
    public fun deposit(
        player: Player,
        coords: CoordGrid,
        items: List<InvObj>,
        variant: GraveVariant = GraveVariant.Default,
    ): List<InvObj> {
        if (items.isEmpty()) {
            return emptyList()
        }

        // Flipped the moment objs land in `inv.gravestone`. Past that point an unexpected throw
        // must NOT hand the same objs back for a floor drop - that would duplicate them.
        var committed = false
        return try {
            if (hasGrave(player) || player.graveToken != 0) {
                collapse(player, GraveCollapseReason.ReplacedByNewDeath)
            }

            val inv =
                runCatching { graveInv(player) }
                    .onFailure { warnThrottled { "Grave inv unavailable for $player: ${it.message}" } }
                    .getOrNull() ?: return items

            val token = nextFreeToken()
            if (token == null) {
                warnThrottled {
                    "All $MAX_TOKENS grave tokens are in use; $player fell back to a floor drop."
                }
                return items
            }

            val (stored, overflow) = inv.depositAll(items)
            if (stored.isEmpty()) {
                return overflow
            }
            committed = true

            val npc = spawnToken(coords, variant, token)
            if (npc == null) {
                for (obj in stored) {
                    inv.removeFirstMatching(obj)
                }
                committed = false
                return items
            }

            usedTokens += token
            gravesByCharacter[player.characterId] =
                Grave(player.characterId, token, coords, variant, npc)

            // Write order copied from live: coord varp, then the grave varp. Live writes the
            // duration twice (1500, then 1501) at creation; the extra +1 is not reproduced - it is
            // a one-unit HUD difference on the creation tick only.
            player.graveDeathCoord = coords
            if (player.graveTypeVar != variant.varbitValue) {
                player.graveTypeVar = variant.varbitValue
            }
            player.graveDuration = DURATION_TICKS
            player.graveToken = token

            player.softTimer(COUNTDOWN_TIMER, COUNTDOWN_INTERVAL)
            overflow
        } catch (t: Throwable) {
            warnThrottled { "GraveManager.deposit failed for $player at $coords: $t" }
            // Committed objs stay in `inv.gravestone`; it is `Perm`-scoped, so they persist and the
            // next collapse hands them to Death's storage. Nothing is destroyed or duplicated.
            if (committed) emptyList() else items
        }
    }

    /** Expiry messaging also applies to manual collapse, but not replacement by another death. */
    public fun collapse(player: Player, reason: GraveCollapseReason): Boolean =
        try {
            collapseInternal(player, reason)
        } catch (t: Throwable) {
            // Nothing here can duplicate an obj: `transferToDeathStorage` only clears a grave slot
            // after the storage deposit succeeded. A partial collapse leaves the objs in the
            // `Perm`-scoped `inv.gravestone`, recoverable on the next collapse.
            warnThrottled { "GraveManager.collapse($reason) failed for $player: $t" }
            true
        }

    private fun collapseInternal(player: Player, reason: GraveCollapseReason): Boolean {
        val grave = gravesByCharacter.remove(player.characterId)
        val hadVarState = player.graveToken != 0
        if (grave == null && !hadVarState) {
            return false
        }
        pendingResync -= player.characterId

        if (grave != null) {
            usedTokens -= grave.token
            despawn(grave)
        }

        // Live's order, and it matters: zeroing the duration first leaves the token visible for
        // one packet (`VarpSmall(1697, 125)`), then the token write clears the whole varp.
        if (player.graveDuration != 0) {
            player.graveDuration = 0
        }
        if (player.graveTypeVar != 0) {
            player.graveTypeVar = 0
        }
        if (hadVarState) {
            player.graveToken = 0
        }
        player.clearSoftTimer(COUNTDOWN_TIMER)

        // Live does NOT clear `varp.gravestone_creation_death_coord` on collapse, so neither do we.

        val (moved, stranded) = transferToDeathStorage(player)
        if (stranded.isNotEmpty()) {
            logger.warn {
                "Death storage full: ${stranded.size} obj(s) left in `$GRAVE_INV` for " +
                    "$player (characterId=${player.characterId}, reason=$reason). Nothing was destroyed."
            }
        }

        if (reason != GraveCollapseReason.ReplacedByNewDeath) {
            player.mes(GRAVE_EXPIRED_MESSAGE)
        }

        for (hook in collapseHooks) {
            runCatching { hook.onCollapse(player, reason, moved, stranded) }
                .onFailure { logger.error(it) { "GraveCollapseHook failed: $hook (player=$player)" } }
        }
        return true
    }

    // Restore session bookkeeping on login; persisted contents and duration survive logout.
    public fun onLogin(player: Player): Unit =
        guarded(player, "onLogin") {
            if (player.graveToken == 0) {
                return@guarded
            }
            pendingResync += player.characterId
            player.softTimer(COUNTDOWN_TIMER, COUNTDOWN_INTERVAL)
        }

    public fun onLogout(player: Player): Unit =
        guarded(player, "onLogout") { pendingResync -= player.characterId }

    /**
     * One countdown step. Called from the `timer.gravestone_countdown` soft timer, which fires
     * every [COUNTDOWN_INTERVAL] ticks and takes [COUNTDOWN_INTERVAL] units off the duration -
     * matching live, which transmits `varp.gravestone_varp` once per three ticks with a delta of
     * three.
     */
    public fun countdownTick(player: Player): Unit =
        guarded(player, "countdownTick") { countdownTickInternal(player) }

    /**
     * Runs [block] and swallows anything it throws.
     *
     * Every caller of this reaches [GraveManager] from a per-tick engine processor or the login
     * sequence, and an exception in either is not a gravestone bug - it is a frozen cycle or a
     * `tryOrDisconnect`. A stalled grave with a rate-limited warn naming the player is the better
     * failure. Deposit and collapse are guarded separately, at their own call sites.
     */
    private inline fun guarded(player: Player, label: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            warnThrottled { "GraveManager.$label failed for $player: $t" }
        }
    }

    private fun countdownTickInternal(player: Player) {
        val token = player.graveToken
        if (token == 0) {
            pendingResync -= player.characterId
            player.clearSoftTimer(COUNTDOWN_TIMER)
            return
        }

        if (!ensureRestored(player, token)) {
            return
        }

        // The login sequence does not re-send these two varps, and `varp.gravestone_varp` would
        // only arrive on the next duration write anyway. Live has both present in the tick-0
        // resync of a session that starts holding a grave.
        if (pendingResync.remove(player.characterId)) {
            player.resyncVar(GRAVE_VARP)
            player.resyncVar(DEATH_COORD_VARP)
        }

        if (pauseHooks.any { it.isPaused(player) }) {
            return
        }

        val remaining = player.graveDuration - COUNTDOWN_INTERVAL
        if (remaining > 0) {
            player.graveDuration = remaining
            return
        }
        collapse(player, GraveCollapseReason.Expired)
    }

    /**
     * Rebuilds the in-memory half of a grave whose npc did not survive a server restart.
     *
     * The contents (`inv.gravestone`) and the state (`varp.gravestone_varp`,
     * `varp.gravestone_creation_death_coord`) are all `Perm`-scoped and come back on their own; the
     * spawned npc cannot. Without this the owner would have items they can see on the HUD timer and
     * no body to click.
     *
     * @return `false` if the grave could not be reconstructed and was collapsed instead.
     */
    private fun ensureRestored(player: Player, token: Int): Boolean {
        val existing = gravesByCharacter[player.characterId]
        if (existing != null && existing.npc != null) {
            return true
        }

        // `CoordGrid.NULL` is packed -1, which live also writes into this varp for "no grave"; a
        // packed 0 is a never-written varp, and (0, 0, 0) is off-map. Spawning a token at either
        // would push block-walk collision onto a nonsense tile, so collapse instead.
        val coords = player.graveDeathCoord?.takeIf { it != CoordGrid.NULL && it.packed != 0 }
        if (existing == null && coords == null) {
            warnThrottled {
                "Grave state for $player has token=$token but no usable death coord " +
                    "(varp.gravestone_creation_death_coord=${player.graveDeathCoord}); collapsing it."
            }
            collapse(player, GraveCollapseReason.Expired)
            return false
        }

        val restoreCoords = existing?.coords ?: coords ?: CoordGrid.NULL
        val variant = existing?.variant ?: GraveVariant.fromVarbitValue(player.graveTypeVar)

        // A restart can leave two offline players holding the same token. Whoever gets here first
        // keeps it; the other is re-tokened.
        val restoreToken = if (token in usedTokens && existing == null) nextFreeToken() else token
        if (restoreToken == null) {
            warnThrottled { "No free grave token to restore $player's grave; collapsing it." }
            collapse(player, GraveCollapseReason.Expired)
            return false
        }

        val npc = spawnToken(restoreCoords, variant, restoreToken)
        if (npc == null) {
            collapse(player, GraveCollapseReason.Expired)
            return false
        }

        usedTokens += restoreToken
        gravesByCharacter[player.characterId] =
            Grave(player.characterId, restoreToken, restoreCoords, variant, npc)

        if (restoreToken != token) {
            player.graveToken = restoreToken
        }
        return true
    }

    private fun nextFreeToken(): Int? = (1..MAX_TOKENS).firstOrNull { it !in usedTokens }

    private fun spawnToken(coords: CoordGrid, variant: GraveVariant, token: Int): Npc? {
        val internal = variant.tokenNpcName(token)
        return runCatching {
                val npc = Npc(internal, coords)
                npcRepo.add(npc, duration = Int.MAX_VALUE)
                npc
            }
            .onFailure {
                warnThrottled { "Failed to spawn grave npc '$internal' at $coords: ${it.message}" }
            }
            .getOrNull()
    }

    private fun despawn(grave: Grave) {
        val npc = grave.npc ?: return
        grave.npc = null
        runCatching { npcRepo.del(npc, duration = Int.MAX_VALUE) }
            .onFailure { warnThrottled { "Failed to delete grave npc for $grave: ${it.message}" } }
    }

    private fun transferToDeathStorage(player: Player): Pair<List<InvObj>, List<InvObj>> {
        val grave =
            runCatching { graveInv(player) }
                .onFailure { warnThrottled { "Grave inv unavailable for $player: ${it.message}" } }
                .getOrNull() ?: return emptyList<InvObj>() to emptyList()

        val contents = grave.filterNotNull { true }
        if (contents.isEmpty()) {
            return emptyList<InvObj>() to emptyList()
        }

        val storage =
            runCatching { deathStorageInv(player) }
                .onFailure { warnThrottled { "Death storage unavailable for $player: ${it.message}" } }
                .getOrNull() ?: return emptyList<InvObj>() to contents

        val (moved, stranded) = storage.depositAll(contents)
        for (obj in moved) {
            grave.removeFirstMatching(obj)
        }
        return moved to stranded
    }

    private fun warnThrottled(message: () -> String) {
        val cycle = mapClock.cycle
        if (cycle - lastWarnCycle < WARN_INTERVAL_TICKS) {
            return
        }
        lastWarnCycle = cycle
        logger.warn(message)
    }

    public companion object {
        public const val GRAVE_INV: String = "inv.gravestone"
        public const val DEATH_STORAGE_INV: String = "inv.death_permanent"

        public const val GRAVE_NPC: String = "npc.gravestone_default"
        public const val ANGEL_GRAVE_NPC: String = "npc.gravestone_angel"

        public const val COUNTDOWN_TIMER: String = "timer.gravestone_countdown"

        public const val DURATION_TICKS: Int = 1500

        /** Live transmits the countdown once per three ticks, decrementing by three. */
        public const val COUNTDOWN_INTERVAL: Int = 3

        public const val MAX_TOKENS: Int = 255

        /** Type-0 game message, due one tick after death. */
        public const val GRAVE_CREATED_MESSAGE: String =
            "Some of your dropped items are being held in a gravestone, near where you died."

        public const val GRAVE_EXPIRED_MESSAGE: String =
            "<col=ef1020>Your gravestone has expired.</col> Items held there can now be reclaimed " +
                "at <col=ef1020>Death's Office</col>, accessed from near the respawn points."

        private const val GRAVE_VARP = "varp.gravestone_varp"
        private const val DEATH_COORD_VARP = "varp.gravestone_creation_death_coord"
        private const val VISIBLE_VARBIT = "varbit.gravestone_visible"
        private const val DURATION_VARBIT = "varbit.gravestone_duration"
        private const val TYPE_VARBIT = "varbit.gravestone_type"

        private const val WARN_INTERVAL_TICKS = 100

        private fun Inventory.depositAll(items: List<InvObj>): Pair<List<InvObj>, List<InvObj>> {
            val stored = ArrayList<InvObj>(items.size)
            val overflow = ArrayList<InvObj>()
            for (item in items) {
                if (deposit(item)) stored += item else overflow += item
            }
            return stored to overflow
        }

        private fun Inventory.deposit(item: InvObj): Boolean {
            val stacks = type.stack == InvStackType.Always || getInvObj(item).isStackable
            if (stacks) {
                for (slot in indices) {
                    val existing = this[slot] ?: continue
                    if (existing.id != item.id || existing.vars != item.vars) {
                        continue
                    }
                    val total = existing.count.toLong() + item.count.toLong()
                    if (total > Int.MAX_VALUE) {
                        break
                    }
                    this[slot] = existing.copy(count = total.toInt())
                    return true
                }
            }
            val free = objs.indexOfFirst { it == null }
            if (free < 0) {
                return false
            }
            this[free] = item
            return true
        }

        /**
         * Subtracts [obj] from the first slot that holds it, so a partially-successful transfer can
         * be rolled back without disturbing anything else in the container.
         */
        private fun Inventory.removeFirstMatching(obj: InvObj) {
            for (slot in indices) {
                val existing = this[slot] ?: continue
                if (existing.id != obj.id || existing.vars != obj.vars) {
                    continue
                }
                val remaining = existing.count - obj.count
                this[slot] = if (remaining > 0) existing.copy(count = remaining) else null
                return
            }
        }
    }
}
