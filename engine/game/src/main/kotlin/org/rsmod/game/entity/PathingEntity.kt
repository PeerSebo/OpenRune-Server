package org.rsmod.game.entity

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.WalkTriggerType
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlin.coroutines.startCoroutine
import org.rsmod.annotations.InternalApi
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.coroutine.suspension.GameCoroutineSimpleCompletion
import org.rsmod.game.damage.DamageContributions
import org.rsmod.game.entity.player.ProtectedAccessLostException
import org.rsmod.game.entity.util.EntityExactMove
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.entity.util.EntityFaceTarget
import org.rsmod.game.hero.HeroPoints
import org.rsmod.game.interact.Interaction
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.CardinalDirection
import org.rsmod.game.map.Direction
import org.rsmod.game.map.Direction.Companion.angleBetween
import org.rsmod.game.map.Direction.Companion.between
import org.rsmod.game.map.Direction.Companion.cardinalBetween
import org.rsmod.game.map.Direction.Companion.ordinalBetween
import org.rsmod.game.map.OrdinalDirection
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteDestination
import org.rsmod.game.movement.RouteRequest
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy
import org.rsmod.routefinder.util.Rotations
import org.rsmod.utils.sorting.QuickSort

@OptIn(InternalApi::class)
public sealed class PathingEntity {
    public abstract val avatar: PathingEntityAvatar

    public abstract val collisionStrategy: CollisionStrategy?
    public abstract val blockWalkCollisionFlag: Int?

    public abstract val heroPoints: HeroPoints

    public abstract val damageContributions: DamageContributions

    public var slotId: Int = INVALID_SLOT

    /**
     * A copy of the _most up-to-date_ map clock based on the current game tick. This serves as a
     * consistent baseline for delayed actions, such as `queue`, `timer`, and `delay`.
     */
    public var currentMapClock: Int = -1

    /**
     * Delay checks use the last processed clock so next-tick input cannot cancel a delayed
     * coroutine before it resumes.
     */
    public var processedMapClock: Int = -1

    public var delay: Int = Int.MIN_VALUE
    public var lastMovement: Int = Int.MIN_VALUE

    /**
     * **Internal flag** indicating whether the entity is considered hidden.
     *
     * **Note:** This does **not** visually hide the entity.
     * - To visually hide a npc, use [`NpcRepository.hide`](#).
     * - To visually hide a player, use [`PlayerRepository.hide`](#).
     */
    @InternalApi(
        "This flag is for internal use only and does not visually hide entities. " +
            "Use `NpcRepository.hide` for npcs and `PlayerRepository.hide` for players."
    )
    public var hidden: Boolean = false

    public var activeCoroutine: GameCoroutine? = null
    public val routeDestination: RouteDestination = RouteDestination()
    public var routeRequest: RouteRequest? = null

    /**
     * Scripted routes may ignore collision; reset the override when the route ends or is
     * cancelled.
     */
    public var forcedRoute: Boolean = false

    public var tempMoveSpeed: MoveSpeed? = null
    public var moveSpeed: MoveSpeed = MoveSpeed.Stationary
    public var cachedMoveSpeed: MoveSpeed = MoveSpeed.Stationary

    public var lastProcessedZone: ZoneKey = ZoneKey.NULL
    public var lastProcessedCoord: CoordGrid = CoordGrid.NULL
    public var pendingTeleport: Boolean = false
    public var pendingTelejump: Boolean = false
    public var pendingStepCount: Int = 0

    public var pendingExactMove: EntityExactMove? = null

    public var faceEntity: EntityFaceTarget = EntityFaceTarget.NULL
    public var lastFaceEntity: Int = Int.MIN_VALUE

    /**
     * Fallback bearing is used while the facing target is outside the observer's
     * high-resolution set.
     */
    public var faceEntityFallbackAngle: Int = 0

    public var pendingFaceAngle: EntityFaceAngle = EntityFaceAngle.NULL

    /**
     * Last face angle actually handed to the info protocol, used to suppress re-sending an
     * unchanged angle on every cycle. Only the facing-lock path consults this: live re-asserts a
     * held facing at most twice in a row, where an unguarded lock emits a FACING block every
     * single tick for as long as it is held.
     */
    public var lastTransmittedFaceAngle: Int = Int.MIN_VALUE

    // The angle the entity is currently facing, synced to new observers. (0 = south, matching the
    // log-in default)
    public var faceAngle: Int = 0

    public var pendingFaceSquare: CoordGrid = CoordGrid.NULL
    public var pendingFaceWidth: Int = 1
    public var pendingFaceLength: Int = 1

    public var pendingSequence: EntitySeq = EntitySeq.NULL
    public val pendingSpotanims: LongArrayList = LongArrayList()

    public var interaction: Interaction? = null

    /**
     * The currently active [WalkTriggerType].
     *
     * _Note: Use the [PathingEntity.walkTrigger] function to set this value._
     */
    public var walkTrigger: WalkTriggerType? = null
        private set

    internal var animProtect: Boolean = false

    public val isSlotAssigned: Boolean
        get() = slotId != INVALID_SLOT

    public val isInvisible: Boolean
        get() = hidden || (this is Player && staffInvisible)

    public val isVisible: Boolean
        get() = !isInvisible

    public val size: Int
        get() = avatar.size

    public val hasMovedThisCycle: Boolean
        get() = lastMovement >= processedMapClock

    public val hasMovedPreviousCycle: Boolean
        get() = lastMovement == processedMapClock - 1

    public val cyclesWithoutMovement: Int
        get() = processedMapClock - lastMovement

    public val isFacingEntity: Boolean
        get() = faceEntity != EntityFaceTarget.NULL

    public val isFacingPlayer: Boolean
        get() = faceEntity.isPlayer

    public val isFacingNpc: Boolean
        get() = faceEntity.isNpc

    public var coords: CoordGrid
        get() = avatar.coords
        set(value) {
            avatar.coords = value
        }

    public var x: Int
        get() = avatar.coords.x
        set(value) {
            avatar.coords = avatar.coords.copy(x = value)
        }

    public var z: Int
        get() = avatar.coords.z
        set(value) {
            avatar.coords = avatar.coords.copy(z = value)
        }

    public var level: Int
        get() = avatar.coords.level
        set(value) {
            avatar.coords = avatar.coords.copy(level = value)
        }

    public var previousCoords: CoordGrid
        get() = avatar.previousCoords
        set(value) {
            avatar.previousCoords = value
        }

    public fun delay(cycles: Int = 1) {
        this.delay = currentMapClock + cycles
    }

    public fun launch(
        coroutine: GameCoroutine = GameCoroutine(),
        block: suspend GameCoroutine.() -> Unit,
    ): GameCoroutine {
        cancelActiveCoroutine()
        val completion = GameCoroutineSimpleCompletion
        block.startCoroutine(coroutine, completion)
        if (coroutine.isSuspended) {
            activeCoroutine = coroutine
        }
        return coroutine
    }

    /**
     * Overlay input must not cancel a suspended action before its protection checks run. Only
     * [activeCoroutine] is advanced by the game loop, so a suspended side action is cancelled.
     */
    public fun launchBeside(block: suspend GameCoroutine.() -> Unit): Boolean {
        val coroutine = GameCoroutine()
        block.startCoroutine(coroutine, GameCoroutineSimpleCompletion)
        if (coroutine.isSuspended) {
            coroutine.cancel()
            return false
        }
        return true
    }

    public fun launchNonInterrupting(block: suspend GameCoroutine.() -> Unit) {
        check(launchBeside(block)) {
            "`launchNonInterrupting` block must not suspend. entity=$this"
        }
    }

    public fun advanceActiveCoroutine() {
        try {
            activeCoroutine?.advance()
        } catch (_: ProtectedAccessLostException) {
        }
        if (activeCoroutine?.isIdle == true) {
            activeCoroutine = null
        }
    }

    public fun resumeActiveCoroutine(withValue: Any) {
        try {
            activeCoroutine?.resumeWith(withValue)
        } catch (_: ProtectedAccessLostException) {
        }
        if (activeCoroutine?.isIdle == true) {
            activeCoroutine = null
        }
    }

    public fun cancelActiveCoroutine() {
        activeCoroutine?.cancel()
        activeCoroutine = null
    }

    public fun abortRoute() {
        routeRequest = null
        tempMoveSpeed = null
        routeDestination.clear()
        // Belt-and-braces: a scripted walk cannot outlive the route it was granted for. Its
        // caller clears this in a `finally`, but a leaked flag would let the player walk through
        // walls, so the shared teardown clears it too.
        forcedRoute = false
    }

    public fun clearInteraction() {
        interaction = null
    }

    public abstract fun anim(
        seq: String,
        delay: Int = 0,
        priority: Int = ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ))!!.priority,
    )

    public abstract fun resetAnim()

    public abstract fun spotanim(spot: String, delay: Int = 0, height: Int = 0, slot: Int = 0)

    public fun faceSquare(target: CoordGrid, targetWidth: Int = 1, targetLength: Int = 1) {
        pendingFaceSquare = target
        pendingFaceWidth = targetWidth
        pendingFaceLength = targetLength
    }

    public fun faceDirection(direction: Direction) {
        val target = coords.translate(direction.xOff, direction.zOff)
        faceSquare(target, targetWidth = 1, targetLength = 1)
    }

    public fun faceLoc(loc: BoundLocInfo) {
        faceSquare(loc.coords, loc.adjustedWidth, loc.adjustedLength)
    }

    /** Width and length must already reflect the loc's rotation. */
    public fun faceLoc(loc: LocInfo, width: Int, length: Int) {
        val rotatedWidth = Rotations.rotate(loc.angleId, width, length)
        val rotatedLength = Rotations.rotate(loc.angleId, length, width)
        faceSquare(loc.coords, rotatedWidth, rotatedLength)
    }

    public fun facePathingEntitySquare(target: PathingEntity) {
        faceSquare(target.coords, target.size, target.size)
    }

    public fun resetPendingFaceSquare() {
        pendingFaceSquare = CoordGrid.NULL
        pendingFaceLength = 1
        pendingFaceWidth = 1
    }

    public fun distanceTo(target: CoordGrid, width: Int = 1, length: Int = 1): Int =
        avatar.distanceTo(Bounds(target, width, length))

    public fun distanceTo(other: PathingEntity): Int = avatar.distanceTo(other.bounds())

    public fun distanceTo(other: Controller): Int = avatar.distanceTo(other.bounds())

    public fun distanceTo(loc: BoundLocInfo): Int = avatar.distanceTo(loc.bounds())

    public fun isWithinDistance(
        target: CoordGrid,
        distance: Int,
        width: Int = 1,
        length: Int = 1,
    ): Boolean = avatar.isWithinDistance(Bounds(target, width, length), distance)

    public fun isWithinDistance(other: PathingEntity, distance: Int): Boolean =
        avatar.isWithinDistance(other.bounds(), distance)

    public fun isWithinDistance(other: Controller, distance: Int): Boolean =
        avatar.isWithinDistance(other.bounds(), distance)

    public fun isWithinDistance(loc: BoundLocInfo, distance: Int): Boolean =
        avatar.isWithinDistance(loc.bounds(), distance)

    public fun isWithinArea(southWest: CoordGrid, northEast: CoordGrid): Boolean {
        require(southWest.level == northEast.level) {
            "`southWest` and `northEast` must be on same level. (sw=$southWest, ne=$northEast)"
        }
        if (level != southWest.level) {
            return false
        }
        return x in southWest.x..northEast.x && z in southWest.z..northEast.z
    }

    public fun calculateDirection(target: PathingEntity): Direction {
        return between(bounds(), target.bounds())
    }

    public fun calculateCardinalDirection(target: PathingEntity): CardinalDirection {
        return cardinalBetween(bounds(), target.bounds())
    }

    public fun calculateOrdinalDirection(target: PathingEntity): OrdinalDirection {
        return ordinalBetween(bounds(), target.bounds())
    }

    public fun calculateAngle(target: CoordGrid, width: Int, length: Int): Int? =
        when (target) {
            CoordGrid.ZERO -> 0
            coords -> null
            else -> {
                val targetBounds = Bounds(target, width, length)
                angleBetween(bounds(), targetBounds)
            }
        }

    public fun bounds(): Bounds = avatar.bounds()

    public fun addBlockWalkCollision(collision: CollisionFlagMap, base: CoordGrid) {
        val collisionFlag = blockWalkCollisionFlag ?: return
        for (z in 0 until size) {
            for (x in 0 until size) {
                collision.add(base.x + x, base.z + z, base.level, collisionFlag)
            }
        }
    }

    public fun removeBlockWalkCollision(collision: CollisionFlagMap, base: CoordGrid) {
        val collisionFlag = blockWalkCollisionFlag ?: return
        for (z in 0 until size) {
            for (x in 0 until size) {
                collision.remove(base.x + x, base.z + z, base.level, collisionFlag)
            }
        }
    }

    /**
     * Returns false when [WalkTriggerPriority] prevents replacing the current trigger.
     */
    public fun walkTrigger(trigger: String): Boolean {

        val triggerType =
            ServerCacheManager.getWalkTrigger(trigger.asRSCM(RSCMType.WALKTRIGGER))
                ?: error("Invalid walk trigger: $trigger")

        val previous = walkTrigger?.priority
        if (!triggerType.priority.canOverwrite(previous)) {
            return false
        }
        walkTrigger = triggerType
        return true
    }

    public fun clearWalkTrigger() {
        walkTrigger = null
    }

    public fun heroPoints(source: Player, points: Int) {
        if (points <= 0) {
            return
        }
        val uuid = source.uuid ?: error("Unexpected null uuid for player: $source")
        heroPoints.add(uuid, points)
    }

    public fun recordDamage(source: Player, damage: Int) {
        if (damage <= 0) {
            return
        }
        damageContributions.record(source, damage)
        heroPoints(source, damage)
    }

    public fun recordDamage(source: Npc, damage: Int) {
        if (damage <= 0) {
            return
        }
        damageContributions.record(source, damage)
    }

    public fun findHero(playerList: PlayerList): Player? {
        val heroes = heroPoints.toMutableList()
        if (heroes.isEmpty()) {
            return null
        }

        QuickSort.alternating(heroes) { a, b -> b.points - a.points }

        val hero = heroes[0]
        return playerList.firstOrNull { it.uuid == hero.uuid }
    }

    @InternalApi
    public fun clearHeroPoints() {
        heroPoints.clear()
        damageContributions.clear()
    }

    @InternalApi
    public fun hasInteraction(): Boolean {
        return interaction != null
    }

    public fun destroy() {
        cancelActiveCoroutine()
    }

    public companion object {
        public const val INVALID_SLOT: Int = -1
        public const val DEFAULT_AP_RANGE: Int = 10
    }
}
