package org.rsmod.api.npc.access

import dev.openrune.TypedParamType
import dev.openrune.types.HitmarkTypeGroup
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.SequenceServerType
import dev.openrune.types.aconverted.CategoryType
import dev.openrune.types.hunt.HuntVis
import kotlin.getValue
import kotlin.setValue
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.hunt.PlayerSearch
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.process
import org.rsmod.api.npc.hit.processor.NpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.mapMultiway
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.random.GameRandom
import org.rsmod.api.route.RayCastValidator
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.get
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

public class StandardNpcAccess(
    public val npc: Npc,
    private val coroutine: GameCoroutine,
    private val context: StandardNpcAccessContext,
) {
    public val random: GameRandom by context::random

    public val coords: CoordGrid by npc::coords
    public val mapClock: Int by npc::currentMapClock

    public var actionDelay: Int by npc::actionDelay

    public fun walk(dest: CoordGrid) {
        npc.walk(dest)
    }

    public suspend fun delay(cycles: Int = 1) {
        require(cycles > 0) { "`cycles` must be greater than 0. (cycles=$cycles)" }
        npc.delay(cycles)
        coroutine.pause { npc.isNotDelayed }
    }

    public suspend fun arriveDelay() {
        if (!npc.hasMovedPreviousCycle) {
            return
        }
        delay()
    }

    public suspend fun delay(seq: SequenceServerType) {
        val ticks = seq.tickDuration
        check(ticks > 0) { "Seq tick duration must be positive: $seq" }
        delay(cycles = ticks)
    }

    public fun telejump(dest: CoordGrid, collision: CollisionFlagMap) {
        if (!collision.isZoneValid(dest)) {
            // TODO: Decide if we want to silently-fail, log, or let `PathingEntityCommon.telejump`
            //  error, which will cause the `npc` to be deleted by the engine.
            return
        }
        PathingEntityCommon.telejump(npc, collision, dest)
    }

    public fun teleport(dest: CoordGrid, collision: CollisionFlagMap) {
        if (!collision.isZoneValid(dest)) {
            // TODO: Decide if we want to silently-fail, log, or let `PathingEntityCommon.teleport`
            //  error, which will cause the `npc` to be deleted by the engine.
            return
        }
        PathingEntityCommon.teleport(npc, collision, dest)
    }

    public fun queueDeath() {
        npc.queueDeath()
    }

    /**
     * Standard-hit modifiers run when queued, not at impact. Returned NPC damage remains
     * provisional because impact hooks can still alter it; combat XP is separate.
     */
    public fun queueHit(
        source: Npc,
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        sourceWeapon: ItemServerType? = null,
        sourceSecondary: ItemServerType? = null,
        modifier: NpcHitModifier = context.hitModifier,
    ): Hit =
        npc.queueHit(
            source = source,
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            sourceWeapon = sourceWeapon,
            sourceSecondary = sourceSecondary,
            modifier = modifier,
        )

    /**
     * Player weapon metadata is snapshotted when queued. Modifiers run now, but NPC impact
     * hooks can still alter final damage.
     */
    public fun queueHit(
        source: Player,
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        specific: Boolean = false,
        sourceSecondary: ItemServerType? = null,
        modifier: NpcHitModifier = context.hitModifier,
    ): Hit =
        npc.queueHit(
            source = source,
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            specific = specific,
            sourceSecondary = sourceSecondary,
            modifier = modifier,
        )

    public fun queueHit(
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        modifier: NpcHitModifier = context.hitModifier,
    ): Hit =
        npc.queueHit(
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            modifier = modifier,
        )

    @InternalApi
    public fun processQueuedHit(hit: Hit, processor: NpcHitProcessor = context.hitProcessor): Unit =
        processor.process(this, hit)

    public fun aiTimer(cycles: Int) {
        npc.aiTimer(cycles)
    }

    public fun timer(timerType: String, cycles: Int) {
        npc.timer(timerType, cycles)
    }

    public fun queue(queue: String, cycles: Int, args: Any? = null) {
        npc.queue(queue, cycles, args)
    }

    public fun clearQueue(queue: String) {
        npc.clearQueue(queue)
    }

    public fun heroPoints(source: Player, points: Int) {
        npc.heroPoints(source, points)
    }

    public fun findHero(playerList: PlayerList): Player? {
        return npc.findHero(playerList)
    }

    public fun topDamager(playerList: PlayerList): Player? =
        npc.damageContributions.topPlayer(playerList)

    public fun leastDamager(playerList: PlayerList): Player? =
        npc.damageContributions.leastPlayer(playerList)

    public fun inArea(area: String, coords: CoordGrid, checker: AreaChecker): Boolean {
        return checker.inArea(area, coords)
    }

    public fun mapMultiway(checker: AreaChecker): Boolean {
        return npc.mapMultiway(checker)
    }

    public fun say(text: String) {
        npc.say(text)
    }

    public fun resetMode() {
        npc.resetMode()
    }

    public fun defaultMode() {
        npc.defaultMode()
    }

    public fun noneMode() {
        npc.noneMode()
    }

    public fun playerEscape(target: Player) {
        npc.playerEscape(target)
    }

    public fun playerFaceClose(target: Player) {
        npc.playerFaceClose(target)
    }

    public fun playerFace(target: Player) {
        npc.playerFace(target)
    }

    public fun opPlayer2(target: Player, interactions: AiPlayerInteractions) {
        npc.opPlayer2(target, interactions)
    }

    public fun anim(seq: String, delay: Int = 0) {
        npc.anim(seq, delay)
    }

    public fun resetAnim() {
        npc.resetAnim()
    }

    public fun setIdleAnim(seq: String) {
        npc.setIdleAnim(seq)
    }

    public fun clearIdleAnim() {
        npc.clearIdleAnim()
    }

    public fun animProtect(animProtect: Boolean) {
        PathingEntityCommon.setAnimProtect(npc, animProtect)
    }

    public fun spotanim(spot: String, delay: Int = 0, height: Int = 0, slot: Int = 0) {
        npc.spotanim(spot, delay, height, slot)
    }

    public fun showAllOps() {
        npc.showAllOps()
    }

    public fun hideAllOps() {
        npc.hideAllOps()
    }

    /**
     * Transmogrifies the [npc] into [type], reassigning its internal `uid`. Since npc interactions
     * validate `uid`s before processing, this will automatically cancel any ongoing interactions
     * with this [npc].
     *
     * @param duration The cycle duration that the [npc] will remain as [type] before automatically
     *   changing back to its original type. Set to `Int.MAX_VALUE` to bypass this behavior.
     */
    @OptIn(InternalApi::class)
    public fun changeType(type: NpcServerType, duration: Int) {
        npc.transmog(type, duration)
        npc.assignUid()
    }

    public fun isWithinDistance(
        target: CoordGrid,
        distance: Int,
        width: Int = 1,
        length: Int = 1,
    ): Boolean = npc.isWithinDistance(target, distance, width, length)

    public fun isWithinDistance(other: PathingEntity, distance: Int): Boolean =
        npc.isWithinDistance(other, distance)

    public fun isWithinDistance(loc: BoundLocInfo, distance: Int): Boolean =
        npc.isWithinDistance(loc, distance)

    public fun isWithinArea(southWest: CoordGrid, northEast: CoordGrid): Boolean =
        npc.isWithinArea(southWest, northEast)

    public fun distanceTo(target: CoordGrid, width: Int = 1, length: Int = 1): Int =
        npc.distanceTo(target, width, length)

    public fun distanceTo(other: PathingEntity): Int = npc.distanceTo(other)

    public fun distanceTo(loc: BoundLocInfo): Int = npc.distanceTo(loc)

    public fun mapFindSquareNone(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
        collision: CollisionFlagMap,
    ): CoordGrid? {
        val squares = validatedSquares(centre, minRadius, maxRadius, collision)
        return squares.firstOrNull()
    }

    public fun mapFindSquareLineOfWalk(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
        collision: CollisionFlagMap,
    ): CoordGrid? {
        val squares = validatedLineOfWalkSquares(centre, minRadius, maxRadius, collision)
        return squares.firstOrNull()
    }

    public fun mapFindSquareLineOfSight(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
        collision: CollisionFlagMap,
    ): CoordGrid? {
        val squares = validatedLineOfSightSquares(centre, minRadius, maxRadius, collision)
        return squares.firstOrNull()
    }

    public fun validatedLineOfWalkSquares(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
        collision: CollisionFlagMap,
    ): Sequence<CoordGrid> {
        val validator = RayCastValidator(collision)
        val squares = shuffledSquares(centre, minRadius, maxRadius)
        return squares.filter {
            validator.hasLineOfWalk(centre, it, extraFlag = CollisionFlag.BLOCK_PLAYERS)
        }
    }

    public fun validatedLineOfSightSquares(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
        collision: CollisionFlagMap,
    ): Sequence<CoordGrid> {
        val validator = RayCastValidator(collision)
        val squares = shuffledSquares(centre, minRadius, maxRadius)
        return squares.filter {
            validator.hasLineOfSight(centre, it, extraFlag = CollisionFlag.BLOCK_PLAYERS)
        }
    }

    public fun validatedSquares(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
        collision: CollisionFlagMap,
    ): Sequence<CoordGrid> {
        val squares = shuffledSquares(centre, minRadius, maxRadius)
        return squares.filter {
            val flag = collision[it]
            flag and CollisionFlag.BLOCK_WALK == 0
        }
    }

    private fun shuffledSquares(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
    ): Sequence<CoordGrid> {
        require(minRadius <= maxRadius) {
            "`minRadius` must be less than or equal to `maxRadius`. " +
                "(centre=$centre, minRadius=$minRadius, maxRadius=$maxRadius)"
        }
        val base = centre.translate(-maxRadius, -maxRadius)
        val bounds = Bounds(base, 2 * maxRadius + 1, 2 * maxRadius + 1)
        return bounds.shuffled().filter { centre.chebyshevDistance(it) >= minRadius }
    }

    public fun lineOfWalk(from: CoordGrid, to: CoordGrid, collision: CollisionFlagMap): Boolean {
        val validator = RayCastValidator(collision)
        return validator.hasLineOfWalk(from, to, extraFlag = CollisionFlag.BLOCK_PLAYERS)
    }

    public fun lineOfSight(from: CoordGrid, to: CoordGrid, collision: CollisionFlagMap): Boolean {
        val validator = RayCastValidator(collision)
        return validator.hasLineOfSight(from, to, extraFlag = CollisionFlag.BLOCK_PLAYERS)
    }

    public fun lineOfWalk(from: CoordGrid, bounds: Bounds, collision: CollisionFlagMap): Boolean {
        val validator = RayCastValidator(collision)
        val squares = bounds.asSequence()
        return squares.all {
            validator.hasLineOfWalk(from, it, extraFlag = CollisionFlag.BLOCK_PLAYERS)
        }
    }

    public fun lineOfSight(from: CoordGrid, bounds: Bounds, collision: CollisionFlagMap): Boolean {
        val validator = RayCastValidator(collision)
        val squares = bounds.asSequence()
        return squares.all {
            validator.hasLineOfSight(from, it, extraFlag = CollisionFlag.BLOCK_PLAYERS)
        }
    }

    public fun npcFind(
        coord: CoordGrid,
        npc: String,
        distance: Int,
        checkVis: HuntVis,
        search: NpcSearch,
    ): Npc? {
        return search.find(coord, npc, distance, checkVis)
    }

    public fun npcFindCat(
        coord: CoordGrid,
        category: CategoryType,
        distance: Int,
        checkVis: HuntVis,
        search: NpcSearch,
    ): Npc? {
        return search.findCat(coord, category, distance, checkVis)
    }

    public fun npcFindAllAny(
        coord: CoordGrid,
        distance: Int,
        checkVis: HuntVis,
        search: NpcSearch,
    ): Sequence<Npc> {
        return search.findAllAny(coord, distance, checkVis)
    }

    public fun npcFindAll(
        coord: CoordGrid,
        npc: String,
        distance: Int,
        checkVis: HuntVis,
        search: NpcSearch,
    ): Sequence<Npc> {
        return search.findAll(coord, npc, distance, checkVis)
    }

    public fun npcFindAllZone(
        coord: CoordGrid,
        distance: Int,
        checkVis: HuntVis,
        search: NpcSearch,
    ): Sequence<Npc> {
        return search.findAllZone(coord, distance, checkVis)
    }

    public fun huntAll(
        coord: CoordGrid,
        distance: Int,
        checkVis: HuntVis,
        search: PlayerSearch,
    ): Sequence<Player> {
        return search.findAll(coord, distance, checkVis)
    }

    public fun npcHunt(
        coord: CoordGrid,
        distance: Int,
        checkVis: HuntVis,
        search: NpcSearch,
    ): Npc? {
        return search.hunt(coord, distance, checkVis)
    }

    public fun npcHuntAll(
        coord: CoordGrid,
        npc: NpcServerType,
        distance: Int,
        checkVis: HuntVis,
        search: NpcSearch,
    ): Sequence<Npc> {
        return search.huntAll(coord, npc, distance, checkVis)
    }

    public fun <T : Any> paramOrNull(param: TypedParamType<T>): T? = npc.paramOrNull(param)

    public fun <T : Any> param(param: TypedParamType<T>): T = npc.param(param)

    override fun toString(): String = "StandardNpcAccess(npc=$npc, coroutine=$coroutine)"
}
