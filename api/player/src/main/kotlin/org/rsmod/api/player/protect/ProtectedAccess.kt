package org.rsmod.api.player.protect

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.TypedParamType
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HitmarkTypeGroup
import dev.openrune.types.InvScope
import dev.openrune.types.ItemServerType
import dev.openrune.types.MesAnimType
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.SequenceServerType
import dev.openrune.types.WalkTriggerType
import dev.openrune.types.aconverted.CategoryType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.types.aconverted.SynthType
import dev.openrune.types.aconverted.interf.IfSubType
import dev.openrune.types.enums.EnumTypeMap
import dev.openrune.types.hunt.HuntVis
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.reflect.KClass
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.hunt.PlayerSearch
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invAddAll
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invClear
import org.rsmod.api.invtx.invCompress
import org.rsmod.api.invtx.invDel
import org.rsmod.api.invtx.invMoveAll
import org.rsmod.api.invtx.invSwap
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.invtx.select
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.cinematic.CameraMode
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.cinematic.MinimapState
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.processQueuedHit
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.processor.StandardPlayerHitProcessor
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePObjDialogInput
import org.rsmod.api.player.input.ResumePStringDialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.isInPvnCombat
import org.rsmod.api.player.isInPvpCombat
import org.rsmod.api.player.isOutOfCombat
import org.rsmod.api.player.mapMultiway
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.ClientScripts.chatDefaultRestoreInput
import org.rsmod.api.player.output.ClientScripts.mesLayerMode14
import org.rsmod.api.player.output.ClientScripts.mesLayerMode7
import org.rsmod.api.player.output.ClientScripts.mesLayerMode9
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.objExamine
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.queueDeath
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statDrain
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statRandom
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifChatNpcSpecific
import org.rsmod.api.player.ui.ifChatPlayer
import org.rsmod.api.player.ui.ifChoice
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifConfirmDestroy
import org.rsmod.api.player.ui.ifConfirmOverlay
import org.rsmod.api.player.ui.ifConfirmOverlayClose
import org.rsmod.api.player.ui.ifDoubleobjbox
import org.rsmod.api.player.ui.ifLevelUp
import org.rsmod.api.player.ui.ifMenu
import org.rsmod.api.player.ui.ifMesbox
import org.rsmod.api.player.ui.ifObjbox
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.api.player.ui.ifOpenMain
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenMainSidePair
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifOpenSide
import org.rsmod.api.player.ui.ifOpenSub
import org.rsmod.api.player.ui.ifSetAnim
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetModel
import org.rsmod.api.player.ui.ifSetNpcHead
import org.rsmod.api.player.ui.ifSetObj
import org.rsmod.api.player.ui.ifSetPlayerHead
import org.rsmod.api.player.ui.ifSetPosition
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapDelegate
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.table.MusicRow
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.KeyedEvent
import org.rsmod.events.SuspendEvent
import org.rsmod.events.UnboundEvent
import org.rsmod.game.damage.recordDamageOn
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.player.ProtectedAccessLostException
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.get
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.type.cert
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.normalize
import org.rsmod.game.type.uncert
import org.rsmod.game.ui.Component
import org.rsmod.game.vars.VarPlayerStrMap
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds
import org.rsmod.objtx.TransactionResultList
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

private val logger = InlineLogger()

public class ProtectedAccess(
    public val player: Player,
    public val coroutine: GameCoroutine,
    private val context: ProtectedAccessContext,
) {
    public val random: GameRandom by context::random

    /**
     * Keep internal: a public worldRepo receiver would shadow content scripts' own worldRepo
     * inside ProtectedAccess lambdas.
     */
    internal val worldRepo: WorldRepository by context::worldRepo

    public val coords: CoordGrid by player::coords
    public val mapClock: Int by player::currentMapClock
    public val isBusy2: Boolean by player::isBusy2

    public val inv: Inventory by player::inv
    public val worn: Inventory by player::worn
    public val bank: Inventory by lazy { inv("inv.bank") }
    public val tempInv: Inventory by lazy { inv("inv.tradeoffer") }

    public val vars: VarPlayerIntMapDelegate by lazy { VarPlayerIntMapDelegate.from(player) }
    public val strVars: VarPlayerStrMap by player::strVars

    public var actionDelay: Int by player::actionDelay
    public var skillAnimDelay: Int by player::skillAnimDelay
    public var refaceDelay: Int by player::refaceDelay

    private var opHeldCallCount = 0

    public fun walk(dest: CoordGrid) {
        player.walk(dest)
    }

    public suspend fun playerWalk(dest: CoordGrid) {
        playerMove(dest, MoveSpeed.Walk)
    }

    public suspend fun playerRun(dest: CoordGrid) {
        playerMove(dest, MoveSpeed.Run)
    }

    public suspend fun playerMove(dest: CoordGrid, moveSpeed: MoveSpeed = player.varMoveSpeed) {
        walk(dest)
        player.moveSpeed = moveSpeed

        val distance = coords.chebyshevDistance(dest)
        if (moveSpeed == MoveSpeed.Run && distance <= 3) {
            return
        }

        val distanceDelay = (distance - 1) / max(1, moveSpeed.steps)
        if (distanceDelay > 0) {
            delay(distanceDelay)
        }
    }

    public suspend fun playerWalkWithMinDelay(dest: CoordGrid) {
        playerMoveWithMinDelay(dest, MoveSpeed.Walk)
    }

    public suspend fun playerRunWithMinDelay(dest: CoordGrid) {
        playerMoveWithMinDelay(dest, MoveSpeed.Run)
    }

    public suspend fun playerMoveWithMinDelay(
        dest: CoordGrid,
        moveSpeed: MoveSpeed = player.varMoveSpeed,
    ) {
        walk(dest)
        player.moveSpeed = moveSpeed

        val distanceDelay = (coords.chebyshevDistance(dest) - 1) / max(1, moveSpeed.steps)
        delay(max(1, distanceDelay))
    }

    public fun toggleRun() {
        if (player.runEnergy < 100) {
            spam("You don't have enough energy left to run!")
            player.resyncVar("varp.option_run")
            return
        }
        val speed = if (player.varMoveSpeed == MoveSpeed.Run) MoveSpeed.Walk else MoveSpeed.Run
        player.setActiveMoveSpeed(speed)
    }

    public fun telejump(
        dest: CoordGrid,
        collision: CollisionFlagMap,
        teleportType: TeleportType = TeleportType.Standard,
    ) {
        if (!validateTeleport(teleportType)) {
            return
        }
        if (!collision.isZoneValid(dest)) {
            player.clearMapFlag()
            mes("Invalid teleport!", ChatType.Engine)
            return
        }
        ifClose()
        player.clearMapFlag()
        PathingEntityCommon.telejump(player, collision, dest)
    }

    public fun telejump(dest: CoordGrid, teleportType: TeleportType = TeleportType.Standard) {
        telejump(dest, context.collision, teleportType)
    }

    public fun teleport(
        dest: CoordGrid,
        collision: CollisionFlagMap,
        teleportType: TeleportType = TeleportType.Standard,
    ) {
        if (!validateTeleport(teleportType)) {
            return
        }
        if (!collision.isZoneValid(dest)) {
            player.clearMapFlag()
            mes("Invalid teleport!", ChatType.Engine)
            return
        }
        ifClose()
        player.clearMapFlag()
        PathingEntityCommon.teleport(player, collision, dest)
    }

    public fun teleport(dest: CoordGrid, teleportType: TeleportType = TeleportType.Standard) {
        teleport(dest, context.collision, teleportType)
    }

    public suspend fun spellTeleport(
        dest: CoordGrid,
        type: TeleportType = TeleportType.Standard,
        castAnim: String = "seq.human_castteleport",
        castSpotanim: String = "spotanim.teleport_casting",
        castSound: String = "synth.TELEPORT_ALL",
        windUp: Int = 4,
        arrivalAnim: String? = null,
    ) {
        player.abortRoute()
        player.clearMapFlag()
        anim(castAnim)
        spotanim(castSpotanim, height = 92)
        soundSynth(castSound)
        delay(windUp)
        val from = player.coords
        telejump(dest, type)
        if (player.coords == from && from != dest) {
            // Teleport denied mid-wind-up (e.g. teleblocked): telejump already sent the denial
            // message; do not play the landing animation while standing still.
            resetAnim()
            return
        }
        if (arrivalAnim != null) {
            anim(arrivalAnim)
        } else {
            resetAnim()
        }
    }

    /** exactmove delays use 20 ms client cycles; dir is a 0..2047 facing angle. */
    public fun exactMove(
        start: CoordGrid,
        end: CoordGrid,
        delay1: Int,
        delay2: Int,
        dir: Int,
        teleportType: TeleportType = TeleportType.Standard,
    ) {
        if (!validateTeleport(teleportType)) {
            return
        }
        val collision = context.collision
        if (!collision.isZoneValid(end)) {
            player.clearMapFlag()
            mes("Invalid teleport!", ChatType.Engine)
            return
        }
        PathingEntityCommon.exactMove(player, start, end, delay1, delay2, dir, collision)
    }

    private fun validateTeleport(teleportType: TeleportType): Boolean {
        if (teleportType == TeleportType.Exempt) {
            return true
        }
        val denial = context.teleportValidator.validate(player, teleportType, context.areaChecker)
        if (denial == null) {
            return true
        }
        player.clearMapFlag()
        mes(denial, ChatType.Engine)
        return false
    }

    public fun anim(seq: String, delay: Int = 0) {
        player.anim(seq, delay)
    }

    public fun resetAnim() {
        player.resetAnim()
    }

    public fun animProtect(animProtect: Boolean) {
        PathingEntityCommon.setAnimProtect(player, animProtect)
    }

    public fun resetSpotanim() {
        player.resetSpotanim()
    }

    public fun spotanim(spot: String?, delay: Int = 0, height: Int = 0, slot: Int = 0) {
        if (spot == null) {
            player.resetSpotanim(height = height, slot = slot)
            return
        }
        player.spotanim(spot, delay, height, slot)
    }

    public fun say(text: String) {
        player.say(text)
    }

    public fun transmog(npcType: String) {
        player.transmog = ServerCacheManager.getNpc(npcType.asRSCM(RSCMType.NPC))
    }

    public fun resetTransmog() {
        player.transmog = null
    }

    public fun hideWornAppearance() {
        player.appearance.hideObjs = true
    }

    public fun showWornAppearance() {
        player.appearance.hideObjs = false
    }

    public fun rebuildAppearance() {
        player.rebuildAppearance()
    }

    public fun isBodyType(type: Int): Boolean {
        return player.appearance.bodyType == type
    }

    public fun isBodyTypeA(): Boolean {
        return isBodyType(constants.bodytype_a)
    }

    public fun isBodyTypeB(): Boolean {
        return isBodyType(constants.bodytype_b)
    }

    public fun isWithinDistance(
        target: CoordGrid,
        distance: Int,
        width: Int = 1,
        length: Int = 1,
    ): Boolean {
        return player.isWithinDistance(target, distance, width, length)
    }

    public fun isWithinDistance(other: PathingEntity, distance: Int): Boolean {
        return player.isWithinDistance(other, distance)
    }

    public fun isWithinDistance(loc: BoundLocInfo, distance: Int): Boolean {
        return player.isWithinDistance(loc, distance)
    }

    public fun isWithinArea(southWest: CoordGrid, northEast: CoordGrid): Boolean {
        return player.isWithinArea(southWest, northEast)
    }

    public fun distanceTo(target: CoordGrid, width: Int = 1, length: Int = 1): Int {
        return player.distanceTo(target, width, length)
    }

    public fun distanceTo(other: PathingEntity): Int {
        return player.distanceTo(other)
    }

    public fun distanceTo(loc: BoundLocInfo): Int {
        return player.distanceTo(loc)
    }

    public fun apRange(distance: Int) {
        val interaction = player.interaction ?: return
        interaction.apRange = distance
        interaction.apRangeCalled = true
    }

    public fun isWithinApRange(loc: BoundLocInfo, distance: Int): Boolean {
        if (!isWithinDistance(loc, distance)) {
            apRange(distance)
            return false
        }
        return true
    }

    public fun isWithinApRange(target: PathingEntity, distance: Int): Boolean {
        if (!isWithinDistance(target, distance)) {
            apRange(distance)
            return false
        }
        return true
    }

    public fun findUid(uid: PlayerUid): Player? {
        return uid.resolve(context.playerList)
    }

    public fun findUid(uid: NpcUid): Npc? {
        return uid.resolve(context.npcList)
    }

    public fun mapFindSquareNone(centre: CoordGrid, minRadius: Int, maxRadius: Int): CoordGrid? {
        val squares = validatedSquares(centre, minRadius, maxRadius)
        return squares.firstOrNull()
    }

    public fun mapFindSquareLineOfWalk(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
    ): CoordGrid? {
        val squares = validatedLineOfWalkSquares(centre, minRadius, maxRadius)
        return squares.firstOrNull()
    }

    public fun mapFindSquareLineOfSight(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
    ): CoordGrid? {
        val squares = validatedLineOfSightSquares(centre, minRadius, maxRadius)
        return squares.firstOrNull()
    }

    public fun validatedLineOfWalkSquares(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
    ): Sequence<CoordGrid> {
        val validator = RayCastValidator(context.collision)
        val squares = shuffledSquares(centre, minRadius, maxRadius)
        return squares.filter {
            validator.hasLineOfWalk(centre, it, extraFlag = CollisionFlag.BLOCK_PLAYERS)
        }
    }

    public fun validatedLineOfSightSquares(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
    ): Sequence<CoordGrid> {
        val validator = RayCastValidator(context.collision)
        val squares = shuffledSquares(centre, minRadius, maxRadius)
        return squares.filter {
            validator.hasLineOfSight(centre, it, extraFlag = CollisionFlag.BLOCK_PLAYERS)
        }
    }

    public fun validatedSquares(
        centre: CoordGrid,
        minRadius: Int,
        maxRadius: Int,
    ): Sequence<CoordGrid> {
        val collision = context.collision
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

    public fun lineOfWalk(from: CoordGrid, to: CoordGrid): Boolean {
        val validator = RayCastValidator(context.collision)
        return validator.hasLineOfWalk(from, to, extraFlag = CollisionFlag.BLOCK_PLAYERS)
    }

    public fun lineOfSight(from: CoordGrid, to: CoordGrid): Boolean {
        val validator = RayCastValidator(context.collision)
        return validator.hasLineOfSight(from, to, extraFlag = CollisionFlag.BLOCK_PLAYERS)
    }

    public fun lineOfWalk(from: CoordGrid, bounds: Bounds): Boolean {
        val validator = RayCastValidator(context.collision)
        val squares = bounds.asSequence()
        return squares.all {
            validator.hasLineOfWalk(from, it, extraFlag = CollisionFlag.BLOCK_PLAYERS)
        }
    }

    public fun lineOfSight(from: CoordGrid, bounds: Bounds): Boolean {
        val validator = RayCastValidator(context.collision)
        val squares = bounds.asSequence()
        return squares.all {
            validator.hasLineOfSight(from, it, extraFlag = CollisionFlag.BLOCK_PLAYERS)
        }
    }

    public fun mapBlocked(coord: CoordGrid): Boolean {
        return context.collision.isWalkBlocked(coord)
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

    public fun opLoc1(loc: BoundLocInfo) {
        context.locInteractions.interact(player, loc, InteractionOp.Op1)
    }

    public fun opLoc2(loc: BoundLocInfo) {
        context.locInteractions.interact(player, loc, InteractionOp.Op2)
    }

    public fun opLoc3(loc: BoundLocInfo) {
        context.locInteractions.interact(player, loc, InteractionOp.Op3)
    }

    public fun opLoc4(loc: BoundLocInfo) {
        context.locInteractions.interact(player, loc, InteractionOp.Op4)
    }

    public fun opNpc1(npc: Npc) {
        context.npcInteractions.interact(player, npc, InteractionOp.Op1)
    }

    public fun opNpc2(npc: Npc) {
        context.npcInteractions.interact(player, npc, InteractionOp.Op2)
    }

    public fun opNpc3(npc: Npc) {
        context.npcInteractions.interact(player, npc, InteractionOp.Op3)
    }

    public fun opNpc4(npc: Npc) {
        context.npcInteractions.interact(player, npc, InteractionOp.Op4)
    }

    public fun opPlayer2(target: Player) {
        context.playerInteractions.interact(player, target, InteractionOp.Op2)
    }

    public suspend fun opHeld1(invSlot: Int, inv: Inventory = player.inv) {
        checkOpHeldCallLimit()
        context.heldInteractions.interact(this, inv, invSlot, HeldOp.Op1)
    }

    /** TODO: Add a specialized HeldInteractions.opHeld2 function returning a result type. */
    public suspend fun opHeld2(invSlot: Int, inv: Inventory = player.inv) {
        checkOpHeldCallLimit()
        context.heldInteractions.interact(this, inv, invSlot, HeldOp.Op2)
    }

    public suspend fun opHeld3(invSlot: Int, inv: Inventory = player.inv) {
        checkOpHeldCallLimit()
        context.heldInteractions.interact(this, inv, invSlot, HeldOp.Op3)
    }

    public suspend fun opHeld4(invSlot: Int, inv: Inventory = player.inv) {
        checkOpHeldCallLimit()
        context.heldInteractions.interact(this, inv, invSlot, HeldOp.Op4)
    }

    public suspend fun opHeld5(invSlot: Int, inv: Inventory = player.inv) {
        checkOpHeldCallLimit()
        context.heldInteractions.interact(this, inv, invSlot, HeldOp.Op5)
    }

    public fun invEquip(invSlot: Int, inv: Inventory = player.inv): HeldEquipResult {
        return context.heldInteractions.equip(this, inv, invSlot)
    }

    public suspend fun invDrop(invSlot: Int, inv: Inventory = player.inv) {
        checkOpHeldCallLimit()
        context.heldInteractions.drop(this, inv, invSlot)
    }

    /** TODO: Replace this function and its callers with a new opWorn1. */
    public fun wornUnequip(
        wornSlot: Int,
        into: Inventory = player.inv,
        from: Inventory = player.worn,
    ): WornUnequipResult {
        return context.wornInteractions.unequip(this, from, wornSlot, into)
    }

    public fun faceSquare(target: CoordGrid) {
        player.faceSquare(target)
    }

    public fun faceDirection(direction: Direction) {
        player.faceDirection(direction)
    }

    public fun faceLoc(loc: BoundLocInfo) {
        player.faceLoc(loc)
    }

    public fun faceEntitySquare(target: PathingEntity) {
        player.facePathingEntitySquare(target)
    }

    public fun stopAction() {
        player.clearPendingAction(context.eventBus)
        player.resetFaceEntity()
        player.clearMapFlag()
        player.abortRoute()
    }

    public fun invTransmit(inv: Inventory) {
        player.startInvTransmit(inv)
    }

    public fun invStopTransmit(inv: Inventory) {
        player.stopInvTransmit(inv)
    }

    public fun invAdd(
        inv: Inventory,
        obj: InvObj,
        slot: Int? = null,
        strict: Boolean = true,
        cert: Boolean = false,
        uncert: Boolean = false,
        autoCommit: Boolean = true,
    ): TransactionResultList<InvObj> {
        return player.invAdd(
            inv = inv,
            obj = obj.id,
            count = obj.count,
            vars = obj.vars,
            slot = slot,
            strict = strict,
            cert = cert,
            uncert = uncert,
            autoCommit = autoCommit,
        )
    }

    public fun invAdd(
        inv: Inventory,
        type: String,
        count: Int = 1,
        vars: Int = 0,
        slot: Int? = null,
        strict: Boolean = true,
        cert: Boolean = false,
        uncert: Boolean = false,
        autoCommit: Boolean = true,
        ignoreVirtualStorage: Boolean = false,
    ): TransactionResultList<InvObj> {
        return player.invAdd(
            inv = inv,
            type = type,
            count = count,
            vars = vars,
            slot = slot,
            strict = strict,
            cert = cert,
            uncert = uncert,
            autoCommit = autoCommit,
            ignoreVirtualStorage = ignoreVirtualStorage,
        )
    }

    /**
     * Attempts to add exactly [count] of [obj] into [inv]. If the inventory cannot fit the items,
     * they will instead be dropped on the floor, with [player] as the "owner," and this function
     * will return `false`. If the items are successfully placed in [inv], it returns `true`.
     */
    public fun invAddOrDropType(
        repo: ObjRepository,
        obj: ItemServerType,
        count: Int = 1,
        coords: CoordGrid = this.coords,
        inv: Inventory = this.inv,
    ): Boolean {
        return invAddOrDrop(repo, RSCM.getReverseMapping(RSCMType.OBJ, obj.id), count, coords, inv)
    }

    /**
     * Attempts to add exactly [count] of [obj] into [inv]. If the inventory cannot fit the items,
     * they will instead be dropped on the floor, with [player] as the "owner," and this function
     * will return `false`. If the items are successfully placed in [inv], it returns `true`.
     */
    public fun invAddOrDrop(
        repo: ObjRepository,
        obj: String,
        count: Int = 1,
        coords: CoordGrid = this.coords,
        inv: Inventory = this.inv,
    ): Boolean {
        return player.invAddOrDrop(repo, obj, count, coords = coords, inv = inv)
    }

    public fun invDel(
        inv: Inventory,
        type: String,
        count: Int = 1,
        slot: Int? = null,
        strict: Boolean = true,
        autoCommit: Boolean = true,
        ignoreVirtualStorage: Boolean = false,
    ): TransactionResultList<InvObj> {
        return player.invDel(
            inv = inv,
            type = type,
            count = count,
            slot = slot,
            strict = strict,
            autoCommit = autoCommit,
            ignoreVirtualStorage = ignoreVirtualStorage,
        )
    }

    public fun invDel(
        inv: Inventory,
        type1: String,
        count1: Int,
        type2: String,
        count2: Int,
        strict: Boolean = true,
        autoCommit: Boolean = true,
    ): TransactionResultList<InvObj> {
        return player.invDel(
            inv = inv,
            type1 = type1,
            count1 = count1,
            type2 = type2,
            count2 = count2,
            strict = strict,
            autoCommit = autoCommit,
        )
    }

    public fun invDel(
        inv: Inventory,
        type1: ItemServerType,
        count1: Int,
        type2: ItemServerType,
        count2: Int,
        type3: ItemServerType,
        count3: Int,
        strict: Boolean = true,
        autoCommit: Boolean = true,
    ): TransactionResultList<InvObj> {
        return player.invDel(
            inv = inv,
            type1 = type1,
            count1 = count1,
            type2 = type2,
            count2 = count2,
            type3 = type3,
            count3 = count3,
            strict = strict,
            autoCommit = autoCommit,
        )
    }

    public fun invReplace(
        inv: Inventory,
        replace: String,
        count: Int,
        replacement: String,
        vars: Int = 0,
        autoCommit: Boolean = true,
    ): TransactionResultList<InvObj> {
        return player.invTransaction(inv, autoCommit) {
            val fromInv = select(inv)
            delete {
                this.from = fromInv
                this.obj = replace.asRSCM(RSCMType.OBJ)
                this.strictCount = count
            }
            insert {
                this.into = fromInv
                this.obj = replacement.asRSCM(RSCMType.OBJ)
                this.strictCount = count
                this.vars = vars
            }
        }
    }

    public fun invReplace(
        inv: Inventory,
        slot: Int,
        count: Int,
        replacement: ItemServerType,
        vars: Int = 0,
        autoCommit: Boolean = true,
    ): TransactionResultList<InvObj> {
        val deleteObj = inv[slot]
        return player.invTransaction(inv, autoCommit) {
            val fromInv = select(inv)
            delete {
                this.from = fromInv
                this.obj = deleteObj?.id
                this.strictCount = count
                this.strictSlot = slot
            }
            insert {
                this.into = fromInv
                this.obj = replacement.id
                this.strictCount = count
                this.vars = vars
            }
        }
    }

    public fun invReplaceSlot(
        inv: Inventory,
        slot: Int,
        count: Int,
        replacement: ItemServerType,
        vars: Int = 0,
        autoCommit: Boolean = true,
    ): TransactionResultList<InvObj> {
        val deleteObj = inv[slot]
        return player.invTransaction(inv, autoCommit) {
            val fromInv = select(inv)
            delete {
                this.from = fromInv
                this.obj = deleteObj?.id
                this.strictCount = count
                this.strictSlot = slot
            }
            insert {
                this.into = fromInv
                this.obj = replacement.id
                this.strictSlot = slot
                this.strictCount = count
                this.vars = vars
            }
        }
    }

    public fun invMoveToSlot(
        from: Inventory,
        into: Inventory,
        fromSlot: Int,
        intoSlot: Int,
        strict: Boolean = true,
    ): TransactionResultList<InvObj> {
        val resolvedInto = if (from === into) null else into
        return player.invSwap(
            from = from,
            into = resolvedInto,
            fromSlot = fromSlot,
            intoSlot = intoSlot,
            strict = strict,
        )
    }

    public fun invMoveFromSlot(
        from: Inventory,
        into: Inventory,
        fromSlot: Int,
        count: Int = 1,
        intoSlot: Int? = null,
        strict: Boolean = true,
        cert: Boolean = false,
        uncert: Boolean = false,
        placehold: Boolean = false,
    ): TransactionResultList<InvObj> {
        return player.invTransfer(
            from = from,
            into = into,
            count = count,
            fromSlot = fromSlot,
            intoSlot = intoSlot,
            strict = strict,
            cert = cert,
            uncert = uncert,
            placehold = placehold,
        )
    }

    public fun invMoveInv(
        from: Inventory,
        into: Inventory,
        untransform: Boolean = false,
        intoStartSlot: Int = 0,
        intoCapacity: Int? = null,
        keepSlots: Set<Int>? = null,
    ): TransactionResultList<InvObj> {
        return player.invMoveAll(
            from = from,
            into = into,
            untransform = untransform,
            intoStartSlot = intoStartSlot,
            intoCapacity = intoCapacity,
            keepSlots = keepSlots,
        )
    }

    public fun invMoveAll(
        into: Inventory,
        objs: Iterable<InvObj>,
        startSlot: Int? = null,
        strict: Boolean = true,
        cert: Boolean = false,
        uncert: Boolean = false,
        autoCommit: Boolean = true,
    ): TransactionResultList<InvObj> {
        return player.invAddAll(
            inv = into,
            objs = objs,
            startSlot = startSlot,
            strict = strict,
            cert = cert,
            uncert = uncert,
            autoCommit = autoCommit,
        )
    }

    public fun invCompress(inventory: Inventory): TransactionResultList<InvObj> {
        return player.invCompress(inventory)
    }

    public fun invClear(inventory: Inventory) {
        player.invClear(inventory)
    }

    public fun objExamine(
        inventory: Inventory,
        slot: Int,
        marketPrices: MarketPrices = context.marketPrices,
    ) {
        val obj = inventory[slot] ?: return resendSlot(inventory, 0)
        val normalized = normalize(getInvObj(obj))
        player.objExamine(normalized, obj.count, marketPrices[normalized] ?: 0)
    }

    public fun stat(stat: String): Int {
        return player.stat(stat)
    }

    public fun statBase(stat: String): Int {
        return player.statBase(stat)
    }

    public fun statRestore(stat: String) {
        player.statRestore(stat)
    }

    public fun statRestoreAll(stats: Iterable<String>) {
        player.statRestoreAll(stats)
    }

    public fun statAdvance(
        stat: String,
        xp: Double,
        rate: Double = player.xpRate,
        globalRate: Double = player.globalXpRate,
    ): Int {
        return player.statAdvance(stat, xp, rate, globalRate)
    }

    public fun statAdd(stat: String, constant: Int, percent: Int) {
        player.statAdd(stat, constant, percent)
    }

    public fun statBoost(stat: String, constant: Int, percent: Int) {
        player.statBoost(stat, constant, percent)
    }

    public fun statSub(stat: String, constant: Int, percent: Int) {
        player.statSub(stat, constant, percent)
    }

    public fun statDrain(stat: String, constant: Int, percent: Int) {
        player.statDrain(stat, constant, percent)
    }

    public fun statHeal(stat: String, constant: Int, percent: Int) {
        player.statHeal(stat, constant, percent)
    }

    public fun statRandom(stat: String, low: Int, high: Int, invisibleBoost: Int): Boolean {
        return player.statRandom(random, stat, low, high, invisibleBoost)
    }

    public fun statRandom(
        stat: String,
        low: Int,
        high: Int,
        invisibleLevels: InvisibleLevels,
    ): Boolean {
        return player.statRandom(random, stat, low, high, invisibleLevels)
    }

    /**
     * Success roll with [flatBonus] added to the interpolated value before the floor - for OSRS
     * bonuses expressed as a flat fraction of 256 rather than a second low/high curve (the Hosidius
     * kitchen's +12.8 / +25.6 burn reduction).
     *
     * @see [org.rsmod.api.player.stat.statRandom]
     */
    public fun statRandom(
        stat: String,
        low: Int,
        high: Int,
        invisibleBoost: Int,
        flatBonus: Double,
    ): Boolean {
        return player.statRandom(random, stat, low, high, invisibleBoost, flatBonus)
    }

    public fun isInCombat(): Boolean {
        return player.isInCombat()
    }

    public fun isInPvpCombat(): Boolean {
        return player.isInPvpCombat()
    }

    public fun isInPvnCombat(): Boolean {
        return player.isInPvnCombat()
    }

    public fun isOutOfCombat(): Boolean {
        return player.isOutOfCombat()
    }

    public fun queueDeath() {
        player.queueDeath()
    }

    /**
     * Standard-hit modifiers run when queued, not at impact. Clearing the relevant queue
     * cancels the hit.
     */
    public fun queueHit(
        source: Npc,
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        specific: Boolean = false,
        sourceWeapon: ItemServerType? = null,
        sourceSecondary: ItemServerType? = null,
        modifier: PlayerHitModifier = context.hitModifier,
    ): Hit {
        return player.queueHit(
            source = source,
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            specific = specific,
            sourceWeapon = sourceWeapon,
            sourceSecondary = sourceSecondary,
            modifier = modifier,
        )
    }

    public fun queueHit(
        source: Player,
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        sourceSecondary: ItemServerType? = null,
        modifier: PlayerHitModifier = context.hitModifier,
    ): Hit {
        return player.queueHit(
            source = source,
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            sourceSecondary = sourceSecondary,
            modifier = modifier,
        )
    }

    public fun queueHit(
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        specific: Boolean = false,
        modifier: PlayerHitModifier = context.hitModifier,
        strongQueue: Boolean = true,
    ): Hit {
        return player.queueHit(
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            specific = specific,
            modifier = modifier,
            strongQueue = strongQueue,
        )
    }

    public fun takeInstantHit(
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        specific: Boolean = false,
        modifier: PlayerHitModifier = NoopPlayerHitModifier,
        processor: InstantPlayerHitProcessor = context.instantHitProcessor,
    ): Hit {
        return player.takeInstantHit(
            type = type,
            damage = damage,
            hitmark = hitmark,
            specific = specific,
            modifier = modifier,
            processor = processor,
        )
    }

    /**
     * Impact hits resolve modifiers at impact and do not cap damage to current health when
     * queued.
     */
    public fun queueImpactHit(
        source: Npc,
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        specific: Boolean = false,
        sourceWeapon: ItemServerType? = null,
        sourceSecondary: ItemServerType? = null,
        modifier: PlayerHitModifier = context.hitModifier,
    ) {
        player.queueImpactHit(
            source = source,
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            specific = specific,
            sourceWeapon = sourceWeapon,
            sourceSecondary = sourceSecondary,
            modifier = modifier,
        )
    }

    public fun queueImpactHit(
        source: Player,
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        sourceSecondary: ItemServerType? = null,
        modifier: PlayerHitModifier = context.hitModifier,
    ) {
        player.queueImpactHit(
            source = source,
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            sourceSecondary = sourceSecondary,
            modifier = modifier,
        )
    }

    public fun queueImpactHit(
        delay: Int,
        type: HitType,
        damage: Int,
        hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
        specific: Boolean = false,
        modifier: PlayerHitModifier = context.hitModifier,
    ) {
        player.queueImpactHit(
            delay = delay,
            type = type,
            damage = damage,
            hitmark = hitmark,
            specific = specific,
            modifier = modifier,
            strongQueue = true,
        )
    }

    internal fun findHitNpcSource(hit: Hit): Npc? {
        return hit.resolveNpcSource(context.npcList)
    }

    internal fun findHitPlayerSource(hit: Hit): Player? {
        return hit.resolvePlayerSource(context.playerList)
    }

    internal fun recordHitDamage(target: PathingEntity, hit: Hit, damage: Int) {
        hit.recordDamageOn(target, damage, context.playerList, context.npcList)
    }

    @InternalApi
    public fun processQueuedHit(hit: Hit) {
        processQueuedHit(hit, StandardPlayerHitProcessor)
    }

    @InternalApi
    public fun processQueuedHit(builder: HitBuilder, modifier: PlayerHitModifier) {
        processQueuedHit(builder, modifier, StandardPlayerHitProcessor)
    }

    public fun restoreToplevelTabs(tabTargets: Iterable<String>) {
        // TODO(combat): Publish gameframe-related event for `restoretabs`.
    }

    public fun restoreToplevelTabs(vararg tabTarget: String) {
        restoreToplevelTabs(tabTarget.toList())
    }

    public fun timer(timerType: String, cycles: Int) {
        player.timer(timerType, cycles)
    }

    public fun clearTimer(timerType: String) {
        player.clearTimer(timerType)
    }

    public fun softTimer(timerType: String, cycles: Int) {
        player.softTimer(timerType, cycles)
    }

    public fun clearSoftTimer(timerType: String) {
        player.clearSoftTimer(timerType)
    }

    public fun weakQueue(queue: String, cycles: Int, args: Any? = null) {
        player.weakQueue(queue, cycles, args)
    }

    public fun clearWeakQueue(queue: String) {
        player.clearWeakQueue(queue)
    }

    public fun softQueue(queue: String, cycles: Int, args: Any? = null) {
        player.softQueue(queue, cycles, args)
    }

    public fun queue(queue: String, cycles: Int, args: Any? = null) {
        player.queue(queue, cycles, args)
    }

    public fun strongQueue(queue: String, cycles: Int, args: Any? = null) {
        player.strongQueue(queue, cycles, args)
    }

    public fun longQueueAccelerate(queue: String, cycles: Int, args: Any? = null) {
        player.longQueueAccelerate(queue, cycles, args)
    }

    public fun longQueueDiscard(queue: String, cycles: Int, args: Any? = null) {
        player.longQueueDiscard(queue, cycles, args)
    }

    public fun clearQueue(queue: String) {
        player.clearQueue(queue)
    }

    public fun heroPoints(source: Player, points: Int) {
        player.heroPoints(source, points)
    }

    public fun findHero(): Player? {
        return player.findHero(context.playerList)
    }

    public fun topDamager(): Player? = player.damageContributions.topPlayer(context.playerList)

    public fun leastDamager(): Player? = player.damageContributions.leastPlayer(context.playerList)

    public fun topDamager(target: PathingEntity): Player? =
        target.damageContributions.topPlayer(context.playerList)

    public fun leastDamager(target: PathingEntity): Player? =
        target.damageContributions.leastPlayer(context.playerList)

    public fun inArea(area: String, coords: CoordGrid): Boolean {
        return context.areaChecker.inArea(area, coords)
    }

    public fun mapMultiway(): Boolean {
        return player.mapMultiway(context.areaChecker)
    }

    public fun musicPlay(musicRow: MusicRow) {
        context.musicPlayer.unlockAndPlay(player, musicRow)
    }

    public fun combatClearQueue() {
        player.combatClearQueue()
    }

    public fun clearPendingAction() {
        player.clearPendingAction(context.eventBus)
    }

    /**
     * Respects [WalkTriggerPriority]; use [trySetWalkTrigger] when the caller needs to detect rejection.
     */
    public fun walkTrigger(trigger: String) {
        player.walkTrigger(trigger)
    }

    public fun canSetWalkTrigger(trigger: WalkTriggerType): Boolean {
        val current = player.walkTrigger ?: return true
        return trigger.priority.canOverwrite(current.priority)
    }

    public fun trySetWalkTrigger(trigger: String): Boolean {
        return player.walkTrigger(trigger)
    }

    public fun publish(event: UnboundEvent) {
        context.eventBus.publish(event)
    }

    public fun publish(event: KeyedEvent) {
        context.eventBus.publish(event)
    }

    public suspend fun <T : SuspendEvent<ProtectedAccess>> publish(event: T): Boolean {
        return context.eventBus.publish(this, event)
    }

    public fun logOut() {
        if (canLogout()) {
            assertLogoutState()
            player.manualLogout = true
        }
    }

    public fun preventLogout(message: String, cycles: Int) {
        player.preventLogoutMessage = message
        player.preventLogoutUntil = mapClock + cycles
    }

    public suspend fun startDialogue(conversation: suspend Dialogue.() -> Unit) {
        val dialogue = Dialogue(this, npc = null, faceFar = false)
        conversation(dialogue)
    }

    public suspend fun startDialogue(
        npc: Npc,
        faceFar: Boolean = false,
        conversation: suspend Dialogue.() -> Unit,
    ) {
        val dialogue = Dialogue(this, npc, faceFar)
        conversation(dialogue)
    }

    public suspend fun delayBySeq(anim: String, extra: Int = 0) {
        val cycles =
            ServerCacheManager.getAnim(anim.asRSCM(RSCMType.SEQ))?.totalDelay ?: (2 + extra)
        require(cycles > 0) { "`cycles` must be greater than 0. (cycles=$cycles)" }
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        player.delay(cycles)
        coroutine.pause { player.isNotDelayed }
        resumeWithMainModalProtectedAccess(Unit, modal)
    }

    public suspend fun delay(cycles: Int = 1) {
        require(cycles > 0) { "`cycles` must be greater than 0. (cycles=$cycles)" }
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        player.delay(cycles)
        coroutine.pause { player.isNotDelayed }
        resumeWithMainModalProtectedAccess(Unit, modal)
    }

    /**
     * Use currentMapClock for previous-tick movement: processedMapClock is one cycle stale
     * during client input.
     */
    public suspend fun arriveDelay() {
        if (player.lastMovement != player.currentMapClock - 1) {
            return
        }
        delay()
    }

    public suspend fun delay(seq: SequenceServerType) {
        val ticks = seq.tickDuration
        check(ticks > 0) { "Seq tick duration must be positive: ${seq}" }
        delay(cycles = ticks)
    }

    /**
     * Delays the player for up to `10` cycles or until the `MapBuildComplete` packet is received,
     * whichever happens first.
     *
     * _Note: This function does not delay or suspend if the player's **current** coords do not
     * require rebuilding their build area._
     *
     * @see [BuildAreaUtils.requiresNewBuildArea]
     * @see [delay]
     */
    public suspend fun loadDelay() {
        val requiresBuildAreaRebuild = BuildAreaUtils.requiresNewBuildArea(player)
        if (!requiresBuildAreaRebuild) {
            return
        }
        for (i in 0 until 10) {
            if (player.lastMapBuildComplete >= mapClock) {
                break
            }
            delay(1)
        }
    }

    /**
     * Suspends without delaying the player. The main modal must remain unchanged until input
     * resumes, or protected access is lost.
     */
    private suspend fun <T : Any> await(input: KClass<T>): T {
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        val value = coroutine.pause(input)
        return resumeWithMainModalProtectedAccess(value, modal)
    }

    public suspend fun pauseButton(): ResumePauseButtonInput = await(ResumePauseButtonInput::class)

    public suspend fun mesbox(text: String) {
        val alignment = context.alignment
        val pages = alignment.generateMesPageList(text)
        for (page in pages) {
            val (pgText, lineCount) = page
            val lineHeight = alignment.mesLineHeight(lineCount)
            mesboxPage(pgText, lineHeight, constants.cm_pausebutton, context.eventBus)
        }
    }

    private suspend fun mesboxPage(
        text: String,
        lineHeight: Int,
        pauseText: String,
        eventBus: EventBus,
    ) {
        player.ifMesbox(text, pauseText, lineHeight, eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.messagebox:continue")
    }

    public suspend fun levelUpDialogue(iconComponent: String, line1: String, line2: String) {
        player.ifLevelUp(iconComponent, line1, line2, context.eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.levelup_display:continue")
    }

    public fun mesboxNp(text: String) {
        val alignment = context.alignment
        val pages = alignment.generateMesPageList(text)
        if (pages.size > 1) {
            throw IllegalStateException("Text too long: $text")
        }
        val (pgText, lineCount) = pages.first()
        val lineHeight = alignment.mesLineHeight(lineCount)
        player.ifMesbox(pgText, pauseText = "", lineHeight, context.eventBus)
    }

    public suspend fun objbox(obj: String, text: String) {
        objbox(obj, zoom = 400, text)
    }

    public suspend fun objbox(obj: String, zoom: Int, text: String) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        for (page in pages) {
            objboxPage(obj, zoom, page.text, constants.cm_pausebutton, context.eventBus)
        }
    }

    private suspend fun objboxPage(
        obj: String,
        zoom: Int,
        text: String,
        pauseText: String,
        eventBus: EventBus,
    ) {
        player.ifObjbox(text, obj.asRSCM(RSCMType.OBJ), zoom, pauseText, eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.objectbox:universe")
    }

    public suspend fun confirmObjbox(
        obj: String,
        text: String,
        cancel: String = "No.",
        confirm: String = "Yes.",
    ): Boolean {
        player.ifObjbox(text, obj.asRSCM(RSCMType.OBJ), 400, "$cancel|$confirm", context.eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.objectbox:universe")
        return when (input.subcomponent) {
            0 -> false
            1 -> true
            else -> error("Invalid choice `${input.subcomponent}` for `$player`. (input=$input)")
        }
    }

    public fun objboxNp(obj: String, text: String) {
        objboxNp(obj, zoom = 400, text)
    }

    public fun objboxNp(obj: String, zoom: Int, text: String) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        if (pages.size > 1) {
            throw IllegalStateException("Text too long: $text")
        }
        val page = pages.first()
        player.ifObjbox(page.text, obj.asRSCM(RSCMType.OBJ), zoom, pauseText = "", context.eventBus)
    }

    public suspend fun objbox(obj: InvObj, text: String) {
        objbox(obj, zoom = 400, text)
    }

    public suspend fun objbox(obj: InvObj, zoom: Int, text: String) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        for (page in pages) {
            objboxPage(obj, zoom, page.text, constants.cm_pausebutton, context.eventBus)
        }
    }

    private suspend fun objboxPage(
        obj: InvObj,
        zoom: Int,
        text: String,
        pauseText: String = constants.cm_pausebutton,
        eventBus: EventBus = context.eventBus,
    ) {
        player.ifObjbox(text, obj.id, zoom, pauseText, eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.objectbox:universe")
    }

    public fun objboxNp(obj: InvObj, text: String) {
        objboxNp(obj, zoom = 400, text)
    }

    public fun objboxNp(obj: InvObj, zoom: Int, text: String) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        if (pages.size > 1) {
            throw IllegalStateException("Text too long: $text")
        }
        val page = pages.first()
        player.ifObjbox(page.text, obj.id, zoom, pauseText = "", context.eventBus)
    }

    public suspend fun doubleobjbox(obj1: String, obj2: String, text: String) {
        doubleobjbox(obj1, zoom1 = 400, obj2, zoom2 = 400, text)
    }

    public suspend fun doubleobjbox(
        obj1: String,
        zoom1: Int,
        obj2: String,
        zoom2: Int,
        text: String,
    ) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        for (page in pages) {
            doubleobjboxPage(
                obj1 = obj1,
                zoom1 = zoom1,
                obj2 = obj2,
                zoom2 = zoom2,
                text = page.text,
                pauseText = constants.cm_pausebutton,
                eventBus = context.eventBus,
            )
        }
    }

    private suspend fun doubleobjboxPage(
        obj1: String,
        zoom1: Int,
        obj2: String,
        zoom2: Int,
        text: String,
        pauseText: String,
        eventBus: EventBus,
    ) {
        player.ifDoubleobjbox(
            text,
            obj1.asRSCM(RSCMType.OBJ),
            zoom1,
            obj2.asRSCM(RSCMType.OBJ),
            zoom2,
            pauseText,
            eventBus,
        )
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.objectbox_double:pausebutton")
    }

    public fun doubleobjboxNp(obj1: String, obj2: String, text: String) {
        doubleobjboxNp(obj1, zoom1 = 400, obj2, zoom2 = 400, text)
    }

    public fun doubleobjboxNp(obj1: String, zoom1: Int, obj2: String, zoom2: Int, text: String) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        if (pages.size > 1) {
            throw IllegalStateException("Text too long: $text")
        }
        val page = pages.first()
        player.ifDoubleobjbox(
            page.text,
            obj1.asRSCM(RSCMType.OBJ),
            zoom1,
            obj2.asRSCM(RSCMType.OBJ),
            zoom2,
            "",
            context.eventBus,
        )
    }

    public suspend fun doubleobjbox(obj1: InvObj, obj2: InvObj, text: String) {
        doubleobjbox(obj1, zoom1 = 400, obj2, zoom2 = 400, text)
    }

    public suspend fun doubleobjbox(
        obj1: InvObj,
        zoom1: Int,
        obj2: InvObj,
        zoom2: Int,
        text: String,
    ) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        for (page in pages) {
            doubleobjboxPage(
                obj1 = obj1,
                zoom1 = zoom1,
                obj2 = obj2,
                zoom2 = zoom2,
                text = page.text,
                pauseText = constants.cm_pausebutton,
                eventBus = context.eventBus,
            )
        }
    }

    private suspend fun doubleobjboxPage(
        obj1: InvObj,
        zoom1: Int,
        obj2: InvObj,
        zoom2: Int,
        text: String,
        pauseText: String,
        eventBus: EventBus,
    ) {
        player.ifDoubleobjbox(text, obj1.id, zoom1, obj2.id, zoom2, pauseText, eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.objectbox_double:pausebutton")
    }

    public fun doubleobjboxNp(obj1: InvObj, obj2: InvObj, text: String) {
        doubleobjboxNp(obj1, zoom1 = 400, obj2, zoom2 = 400, text)
    }

    public fun doubleobjboxNp(obj1: InvObj, zoom1: Int, obj2: InvObj, zoom2: Int, text: String) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        if (pages.size > 1) {
            throw IllegalStateException("Text too long: $text")
        }
        val page = pages.first()
        player.ifDoubleobjbox(page.text, obj1.id, zoom1, obj2.id, zoom2, "", context.eventBus)
    }

    public suspend fun <T> choice2(
        choice1: String,
        result1: T,
        choice2: String,
        result2: T,
        title: String = constants.cm_options,
    ): T {
        player.ifChoice(title, "$choice1|$choice2", choiceCountInclusive = 2, context.eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chatmenu:options")
        return when (input.subcomponent) {
            1 -> result1
            2 -> result2
            else -> error("Invalid choice `${input.subcomponent}` for `$player`. (input=$input)")
        }
    }

    public suspend fun <T> choice3(
        choice1: String,
        result1: T,
        choice2: String,
        result2: T,
        choice3: String,
        result3: T,
        title: String = constants.cm_options,
    ): T {
        player.ifChoice(
            title,
            "$choice1|$choice2|$choice3",
            choiceCountInclusive = 3,
            context.eventBus,
        )
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chatmenu:options")
        return when (input.subcomponent) {
            1 -> result1
            2 -> result2
            3 -> result3
            else -> error("Invalid choice `${input.subcomponent}` for `$player`. (input=$input)")
        }
    }

    public suspend fun <T> choice4(
        choice1: String,
        result1: T,
        choice2: String,
        result2: T,
        choice3: String,
        result3: T,
        choice4: String,
        result4: T,
        title: String = constants.cm_options,
    ): T {
        player.ifChoice(
            title,
            "$choice1|$choice2|$choice3|$choice4",
            choiceCountInclusive = 4,
            context.eventBus,
        )
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chatmenu:options")
        return when (input.subcomponent) {
            1 -> result1
            2 -> result2
            3 -> result3
            4 -> result4
            else -> error("Invalid choice `${input.subcomponent}` for `$player`. (input=$input)")
        }
    }

    public suspend fun <T> choice5(
        choice1: String,
        result1: T,
        choice2: String,
        result2: T,
        choice3: String,
        result3: T,
        choice4: String,
        result4: T,
        choice5: String,
        result5: T,
        title: String = constants.cm_options,
    ): T {
        player.ifChoice(
            title,
            "$choice1|$choice2|$choice3|$choice4|$choice5",
            choiceCountInclusive = 5,
            context.eventBus,
        )
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chatmenu:options")
        return when (input.subcomponent) {
            1 -> result1
            2 -> result2
            3 -> result3
            4 -> result4
            5 -> result5
            else -> error("Invalid choice `${input.subcomponent}` for `$player`. (input=$input)")
        }
    }

    public suspend fun <T> choice6(
        choice1: String,
        result1: T,
        choice2: String,
        result2: T,
        choice3: String,
        result3: T,
        choice4: String,
        result4: T,
        choice5: String,
        result5: T,
        choice6: String,
        result6: T,
        title: String = constants.cm_options,
    ): T {
        player.ifChoice(
            title,
            "$choice1|$choice2|$choice3|$choice4|$choice5|$choice6",
            choiceCountInclusive = 6,
            context.eventBus,
        )
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chatmenu:options")
        return when (input.subcomponent) {
            1 -> result1
            2 -> result2
            3 -> result3
            4 -> result4
            5 -> result5
            6 -> result6
            else -> error("Invalid choice `${input.subcomponent}` for `$player`. (input=$input)")
        }
    }

    public suspend fun chatPlayerNoAnim(text: String) {
        chatPlayer(null, text)
    }

    public suspend fun chatPlayer(
        mesanim: MesAnimType?,
        text: String,
        title: String = player.displayName,
    ) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        for (page in pages) {
            val (pgText, lineCount) = page
            val lineHeight = alignment.chatLineHeight(lineCount)
            val chatanim = mesanim?.splitGetAnim(lineCount) ?: return
            chatPlayerPage(
                text = pgText,
                chatanim = ServerCacheManager.getAnim(chatanim),
                lineHeight = lineHeight,
                title = title,
                pauseText = constants.cm_pausebutton,
                eventBus = context.eventBus,
            )
        }
    }

    private suspend fun chatPlayerPage(
        text: String,
        chatanim: SequenceServerType?,
        lineHeight: Int,
        title: String,
        pauseText: String,
        eventBus: EventBus,
    ) {
        player.ifChatPlayer(title, text, chatanim, pauseText, lineHeight, eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chat_right:continue")
    }

    public suspend fun chatNpcNoAnim(
        npc: Npc,
        text: String,
        title: String = npc.resolveVisName(),
        faceFar: Boolean = false,
    ) {
        chatNpc(npc, mesanim = null, text = text, title = title, faceFar = faceFar)
    }

    public suspend fun chatNpc(
        npc: Npc,
        mesanim: MesAnimType?,
        text: String,
        title: String = npc.resolveVisName(),
        faceFar: Boolean = false,
    ) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        for (page in pages) {
            val (pgText, lineCount) = page
            val lineHeight = alignment.chatLineHeight(lineCount)
            val chatanim = mesanim?.splitGetAnim(lineCount) ?: return
            chatNpcPage(
                title = title,
                npc = npc,
                text = pgText,
                chatanim = ServerCacheManager.getAnim(chatanim),
                lineHeight = lineHeight,
                faceFar = faceFar,
                pauseText = constants.cm_pausebutton,
                eventBus = context.eventBus,
            )
        }
    }

    private suspend fun chatNpcPage(
        title: String,
        npc: Npc,
        text: String,
        chatanim: SequenceServerType?,
        lineHeight: Int,
        faceFar: Boolean,
        pauseText: String,
        eventBus: EventBus,
    ) {
        val inCombatMode = npc.mode == NpcMode.OpPlayer2 || npc.mode == NpcMode.ApPlayer2
        if (!inCombatMode) {
            npc.playerFace(player, faceFar = faceFar)
        }
        player.facePathingEntitySquare(npc)
        player.ifChatNpcSpecific(
            title,
            RSCM.getReverseMapping(RSCMType.NPC, npc.type.id),
            text,
            chatanim,
            pauseText,
            lineHeight,
            eventBus,
        )
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chat_left:continue")
    }

    public suspend fun chatNpcNoTurn(
        npc: Npc,
        mesanim: MesAnimType?,
        text: String,
        title: String = npc.resolveVisName(),
    ) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        for (page in pages) {
            val (pgText, lineCount) = page
            val lineHeight = alignment.chatLineHeight(lineCount)
            val chatanim = mesanim?.splitGetAnim(lineCount) ?: return
            chatNpcNoTurnPage(
                title = title,
                npc = npc,
                text = pgText,
                chatanim = ServerCacheManager.getAnim(chatanim),
                lineHeight = lineHeight,
                pauseText = constants.cm_pausebutton,
                eventBus = context.eventBus,
            )
        }
    }

    private suspend fun chatNpcNoTurnPage(
        title: String,
        npc: Npc,
        text: String,
        chatanim: SequenceServerType?,
        lineHeight: Int,
        pauseText: String,
        eventBus: EventBus,
    ) {
        player.facePathingEntitySquare(npc)
        player.ifChatNpcSpecific(
            title,
            RSCM.getReverseMapping(RSCMType.NPC, npc.type.id),
            text,
            chatanim,
            pauseText,
            lineHeight,
            eventBus,
        )
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chat_left:continue")
    }

    public suspend fun chatNpcSpecific(
        title: String,
        type: String,
        mesanim: MesAnimType,
        text: String,
    ) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        for (page in pages) {
            val (pgText, lineCount) = page
            val lineHeight = alignment.chatLineHeight(lineCount)
            val chatanim = mesanim.splitGetAnim(lineCount)
            val anim = ServerCacheManager.getAnim(chatanim) ?: return
            chatNpcSpecificPage(
                title = title,
                type = type,
                text = pgText,
                chatanim = anim,
                lineHeight = lineHeight,
                pauseText = constants.cm_pausebutton,
                eventBus = context.eventBus,
            )
        }
    }

    private suspend fun chatNpcSpecificPage(
        title: String,
        type: String,
        text: String,
        chatanim: SequenceServerType,
        lineHeight: Int,
        pauseText: String,
        eventBus: EventBus,
    ) {
        player.ifChatNpcSpecific(title, type, text, chatanim, pauseText, lineHeight, eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.chat_left:continue")
    }

    public fun chatNpcSpecificNp(title: String, type: String, mesanim: MesAnimType, text: String) {
        val alignment = context.alignment
        val pages = alignment.generateChatPageList(text)
        if (pages.size > 1) {
            throw IllegalStateException("Text too long: $text")
        }
        val (pgText, lineCount) = pages.first()
        val lineHeight = alignment.chatLineHeight(lineCount)
        val chatanim = mesanim.splitGetAnim(lineCount)
        val anim = ServerCacheManager.getAnim(chatanim) ?: return
        player.ifChatNpcSpecific(title, type, pgText, anim, "", lineHeight, context.eventBus)
    }

    public suspend fun stringDialog(title: String): String {
        mesLayerMode9(player, title)
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        val input = coroutine.pause(ResumePStringDialogInput::class)
        return resumeWithMainModalProtectedAccess(input.text, modal)
    }

    /**
     * **Note:** The returned integer will _always_ be positive. To allow negative values, use
     * [numberDialog] instead.
     *
     * @throws ProtectedAccessLostException if the player could not retain protected access after
     *   the coroutine suspension.
     * @see [resumeWithMainModalProtectedAccess]
     */
    public suspend fun countDialog(title: String = constants.cm_count): Int {
        mesLayerMode7(player, title)
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        val input = coroutine.pause(ResumePCountDialogInput::class)
        return resumeWithMainModalProtectedAccess(input.count.absoluteValue, modal)
    }

    public suspend fun numberDialog(title: String): Int {
        mesLayerMode7(player, title)
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        val input = coroutine.pause(ResumePCountDialogInput::class)
        return resumeWithMainModalProtectedAccess(input.count, modal)
    }

    /**
     * @param stockMarketRestriction If `true` the search will be restricted to only objs that can
     *   be found in the grand exchange.
     * @param enumRestriction If an enum (with key of `ItemServerType` and value of `Boolean`) is
     *   provided, the search is restricted to only the entries in said enum.
     * @param showLastSearched If `true` the search will present the last selected obj.
     * @throws ProtectedAccessLostException if the player could not retain protected access after
     *   the coroutine suspension.
     * @see [resumeWithMainModalProtectedAccess]
     */
    public suspend fun objDialog(
        title: String = constants.cm_obj,
        stockMarketRestriction: Boolean = true,
        enumRestriction: EnumTypeMap<ItemServerType, Boolean>? = null,
        showLastSearched: Boolean = false,
    ): ItemServerType {
        mesLayerMode14(player, title, stockMarketRestriction, enumRestriction, showLastSearched)
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        val input = coroutine.pause(ResumePObjDialogInput::class)
        return resumeWithMainModalProtectedAccess(input.obj, modal)
    }

    public suspend fun confirmDestroy(
        obj: String,
        count: Int,
        header: String,
        text: String,
    ): Boolean {
        player.ifConfirmDestroy(header, text, obj.asRSCM(RSCMType.OBJ), count, context.eventBus)
        val modal = player.ui.getModalOrNull("component.chatbox:chatmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        resumePauseButtonWithProtectedAccess(input, modal, "component.confirmdestroy:universe")
        return when (input.subcomponent) {
            0 -> false
            1 -> true
            else -> error("Invalid choice `${input.subcomponent}` for `$player`. (input=$input)")
        }
    }

    public suspend fun confirmOverlay(
        target: String,
        title: String,
        text: String,
        cancel: String,
        confirm: String,
    ): Boolean {
        player.ifConfirmOverlay(target, title, text, cancel, confirm, context.eventBus)
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        val input = coroutine.pause(ResumePCountDialogInput::class)
        val confirmed = resumeWithMainModalProtectedAccess(input.count != 0, modal)
        player.ifConfirmOverlayClose(context.eventBus)
        return confirmed
    }

    public suspend fun menu(title: String, hotkeys: Boolean, choices: List<String>): Int {
        require(choices.size < 128) { "Can only have up to 127 `choices`. (size=${choices.size})" }
        player.ifMenu(title, choices.joinToString("|"), hotkeys, context.eventBus)
        val modal = player.ui.getModalOrNull("component.toplevel_osrs_stretch:mainmodal")
        val input = coroutine.pause(ResumePauseButtonInput::class)
        chatDefaultRestoreInput(player)
        return resumeWithMainModalProtectedAccess(input.subcomponent.absoluteValue, modal)
    }

    public suspend fun menu(title: String, vararg choices: String, hotkeys: Boolean = false): Int {
        return menu(title, hotkeys, choices.toList())
    }

    /**
     * Recheck protected access when resuming suspended input; another action may have replaced
     * or cancelled its coroutine.
     */
    @Throws(ProtectedAccessLostException::class)
    private fun regainProtectedAccess() {
        if (player.isAccessProtected) {
            logger.debug { "Protected-access could not be re-obtained for player: $player" }
            throw ProtectedAccessLostException()
        }
    }

    /**
     * Resume only the modal and pause target that created this suspension; stale input must not
     * resume a different dialogue.
     */
    private fun resumePauseButtonWithProtectedAccess(
        input: ResumePauseButtonInput,
        expectedModal: Component?,
        expectedComponent: String,
    ) {
        if (expectedComponent != input.component) {
            logger.debug {
                "Protected-access was lost due to unexpected component: " +
                    "player=$player, " +
                    "received=${input.component}, " +
                    "expected=${expectedComponent}"
            }
            throw ProtectedAccessLostException()
        }
        resumeWithModalProtectedAccess(null, expectedModal, "component.chatbox:chatmodal")
    }

    /** Reacquire protected access before resuming a modal suspension. */
    private fun <T> resumeWithModalProtectedAccess(
        returnWithProtectedAccess: T,
        expectedModal: Component?,
        modalTarget: String,
    ): T {
        if (player.isDelayed) {
            logger.debug { "Protected-access was lost due to delay: player=$player" }
            throw ProtectedAccessLostException()
        }

        if (player.activeCoroutine !== coroutine) {
            logger.debug {
                "Protected-access was lost due to coroutine mismatch: " +
                    "player=$player, scopeCoroutine=$coroutine"
            }
            throw ProtectedAccessLostException()
        }

        val currentModal = player.ui.getModalOrNull(modalTarget)
        if (currentModal != expectedModal) {
            logger.debug {
                "Protected-access was lost due to unexpected modal: " +
                    "player=$player, " +
                    "received=$currentModal, " +
                    "expected=$expectedModal"
            }
            throw ProtectedAccessLostException()
        }

        return returnWithProtectedAccess
    }

    private fun <T> resumeWithMainModalProtectedAccess(
        returnWithProtectedAccess: T,
        expectedModal: Component?,
    ): T {
        return resumeWithModalProtectedAccess(
            returnWithProtectedAccess,
            expectedModal,
            "component.toplevel_osrs_stretch:mainmodal",
        )
    }

    public fun runClientScript(id: Int, vararg args: Any): Unit = player.runClientScript(id, *args)

    public fun runClientScript(id: String, vararg args: Any): Unit = player.runClientScript(id, *args)

    public fun camForceAngle(rate: Int, rate2: Int): Unit =
        ClientScripts.camForceAngle(player, rate, rate2)

    public fun interfaceInvInit(
        inv: Inventory,
        target: String,
        objRowCount: Int,
        objColCount: Int,
        dragType: Int = 0,
        dragComponent: String? = null,
        op1: String? = null,
        op2: String? = null,
        op3: String? = null,
        op4: String? = null,
        op5: String? = null,
    ): Unit =
        ClientScripts.interfaceInvInit(
            player = player,
            inv = inv,
            target = target,
            objRowCount = objRowCount,
            objColCount = objColCount,
            dragType = dragType,
            dragComponent = dragComponent,
            op1 = op1,
            op2 = op2,
            op3 = op3,
            op4 = op4,
            op5 = op5,
        )

    public fun toplevelSidebuttonSwitch(side: Int) {
        ClientScripts.toplevelSidebuttonSwitch(player, side)
    }

    public fun camLookAt(dest: CoordGrid, height: Int, rate: Int, rate2: Int) {
        Camera.camLookAt(player, dest, height, rate, rate2)
    }

    public fun camMoveTo(dest: CoordGrid, height: Int, rate: Int, rate2: Int) {
        Camera.camMoveTo(player, dest, height, rate, rate2)
    }

    public fun camLookAtV3(
        dest: CoordGrid,
        height: Int,
        rate: Int,
        rate2: Int,
        heightRelative: Boolean = false,
    ) {
        Camera.camLookAtV3(player, dest, height, rate, rate2, heightRelative)
    }

    public fun camMoveToV3(
        dest: CoordGrid,
        height: Int,
        rate: Int,
        rate2: Int,
        heightRelative: Boolean = false,
    ) {
        Camera.camMoveToV3(player, dest, height, rate, rate2, heightRelative)
    }

    public fun camShake(axis: Int, random: Int, amplitude: Int, rate: Int) {
        Camera.camShake(player, axis, random, amplitude, rate)
    }

    public fun camShakeReset(axis: Int) {
        Camera.camShakeReset(player, axis)
    }

    public fun camShakeResetAll() {
        Camera.camShakeResetAll(player)
    }

    public fun camUnlock(unlock: Boolean) {
        Camera.camUnlock(player, unlock)
    }

    public fun camReset() {
        Camera.camReset(player)
    }

    public fun camModeClose() {
        Cinematic.setCameraMode(player, CameraMode.Close)
    }

    public fun camModeFar() {
        Cinematic.setCameraMode(player, CameraMode.Far)
    }

    public fun camModeFixed() {
        Cinematic.setCameraMode(player, CameraMode.Fixed)
    }

    public fun camModeReset() {
        Cinematic.setCameraMode(player, CameraMode.Normal)
    }

    public fun minimapHideFull() {
        Cinematic.setMinimapState(player, MinimapState.Disabled)
    }

    public fun minimapNoOps() {
        Cinematic.setMinimapState(player, MinimapState.MinimapNoOp)
    }

    public fun minimapHideMap() {
        Cinematic.setMinimapState(player, MinimapState.MinimapHidden)
    }

    public fun minimapHideCompass() {
        Cinematic.setMinimapState(player, MinimapState.CompassHidden)
    }

    public fun minimapNoOpsHideCompass() {
        Cinematic.setMinimapState(player, MinimapState.MinimapNoOpCompassHidden)
    }

    public fun minimapReset() {
        Cinematic.setMinimapState(player, MinimapState.Normal)
    }

    public fun hideTopLevel() {
        Cinematic.setHideToplevel(player, hide = true)
    }

    public fun showTopLevel() {
        Cinematic.setHideToplevel(player, hide = false)
    }

    public fun clearHealthHud() {
        Cinematic.clearHealthHud(player)
    }

    public fun hideHealthHud() {
        Cinematic.setHideHealthHud(player, hide = true)
    }

    public fun showHealthHud() {
        Cinematic.setHideHealthHud(player, hide = false)
    }

    public fun tempDisableAcceptAid() {
        Cinematic.disableAcceptAid(player)
    }

    public fun restoreLastAcceptAid() {
        Cinematic.restoreAcceptAid(player)
    }

    /**
     * **Inert for cutscenes** - read [Cinematic.disableRoofs] before using this. The write reaches
     * the client and the roof stays up; live solves the same problem with a level-0-only dynamic
     * region instead.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Inert for cutscenes. See Cinematic.disableRoofs.")
    public fun tempHideRoofs(): Boolean = Cinematic.disableRoofs(player)

    @Suppress("DEPRECATION")
    @Deprecated("See tempHideRoofs.")
    public fun restoreRoofs(previous: Boolean) {
        Cinematic.restoreRoofs(player, previous)
    }

    /**
     * See [Cinematic.setMaxDrawDistance]. Live sends this on every cutscene.
     *
     * Writes the varbit only. **Pair it with [syncDrawDistance]** - placed after whatever changes
     * the viewport - or the client never re-reads the varbit and the scene renders at the
     * pre-cutscene draw distance.
     */
    public fun maxDrawDistance(enabled: Boolean) {
        Cinematic.setMaxDrawDistance(player, enabled)
    }

    /** See [Cinematic.syncDrawDistance]. Required on both the set and the clear. */
    public fun syncDrawDistance() {
        Cinematic.syncDrawDistance(player)
    }

    public fun hideEntityOps() {
        Cinematic.setHideEntityOps(player, hide = true)
    }

    public fun showEntityOps() {
        Cinematic.setHideEntityOps(player, hide = false)
    }

    public fun closeTopLevelTabs() {
        Cinematic.closeToplevelTabs(player, context.eventBus)
    }

    public fun closeTopLevelTabsLenient() {
        Cinematic.closeToplevelTabsLenient(player, context.eventBus)
    }

    public fun openTopLevelTabs() {
        Cinematic.openTopLevelTabs(player, context.eventBus)
    }

    public fun fadeOverlay(
        startColour: Int,
        startTransparency: Int,
        endColour: Int,
        endTransparency: Int,
        clientDuration: Int,
    ) {
        Cinematic.fadeOverlay(
            player,
            startColour,
            startTransparency,
            endColour,
            endTransparency,
            clientDuration,
            context.eventBus,
        )
    }

    /**
     * Scheduled overlay closes use a queue and can be delayed by protected access; use
     * immediate close when exact timing is required.
     */
    public fun closeFadeOverlay(cycles: Int = 3) {
        longQueueDiscard("queue.fade_overlay_close", cycles)
    }

    /**
     * Close immediately when the overlay must disappear this cycle; queued closes can wait
     * behind protected access.
     */
    public fun closeFadeOverlayNow() {
        Cinematic.closeFadeOverlay(player, context.eventBus)
    }

    public fun ifClose() {
        player.ifClose(context.eventBus)
    }

    public fun ifCloseSub(interf: String) {
        player.ifCloseSub(interf, context.eventBus)
    }

    public fun ifOpenSide(interf: String) {
        player.ifOpenSide(interf, context.eventBus)
    }

    public fun ifOpenMain(interf: String) {
        player.ifOpenMain(interf, context.eventBus)
    }

    public fun ifOpenMainSidePair(
        main: String,
        side: String,
        colour: Int = -1,
        transparency: Int = -1,
    ) {
        player.ifOpenMainSidePair(main, side, colour, transparency, context.eventBus)
    }

    public fun ifOpenMainModal(interf: String, colour: Int = -1, transparency: Int = -1) {
        player.ifOpenMainModal(interf, context.eventBus, colour, transparency)
    }

    public fun ifOpenOverlay(interf: String, target: String) {
        player.ifOpenOverlay(interf, target, context.eventBus)
    }

    public fun ifOpenOverlay(interf: String) {
        player.ifOpenOverlay(interf, context.eventBus)
    }

    public fun ifOpenFullOverlay(interf: String) {
        player.ifOpenFullOverlay(interf, context.eventBus)
    }

    public fun ifOpenSub(interf: String, target: String, type: IfSubType) {
        player.ifOpenSub(interf, target, type, context.eventBus)
    }

    public fun ifSetAnim(target: String, seq: SequenceServerType?) {
        player.ifSetAnim(target, seq)
    }

    public fun ifSetEvents(target: String, range: IntRange, vararg event: IfEvent) {
        player.ifSetEvents(target, range, *event)
    }

    public fun ifSetNpcHead(target: String, npc: String) {
        player.ifSetNpcHead(target, npc)
    }

    public fun ifSetPlayerHead(target: String) {
        player.ifSetPlayerHead(target)
    }

    public fun ifSetText(target: String, text: String) {
        player.ifSetText(target, text)
    }

    public fun ifSetObj(target: String, obj: String, zoom: Int) {
        player.ifSetObj(target, obj, zoom)
    }

    public fun ifSetPosition(target: String, x: Int, y: Int) {
        player.ifSetPosition(target, x, y)
    }

    public fun ifSetHide(target: String, hide: Boolean) {
        player.ifSetHide(target, hide)
    }

    public fun ifSetModel(target: String, model: Int) {
        player.ifSetModel(target, model)
    }

    public fun invTakeFee(fee: Int, inv: Inventory = this.inv): Boolean {
        return player.invTakeFee(fee, inv)
    }

    public fun invCoinTotal(inv: Inventory = this.inv): Int {
        return invTotal(inv, "obj.coins")
    }

    public fun invContentTotal(inv: Inventory, content: String): Int {
        var count = 0
        for (obj in inv) {
            val filtered = obj ?: continue
            if (getInvObj(filtered).isContentType(content)) {
                count += filtered.count
            }
        }
        return count
    }

    public fun invTotal(inv: Inventory, obj: String): Int {
        return inv.count(obj)
    }

    public fun invContains(inv: Inventory, content: String): Boolean {
        return inv.any {
            it != null && getInvObj(it).contentGroup == content.asRSCM(RSCMType.CONTENT)
        }
    }

    public fun playerContainsContent(content: String): Boolean {
        val contentId = content.asRSCM(RSCMType.CONTENT)
        return playerContainsInInventories { getInvObj(it).contentGroup == contentId }
    }

    public fun playerContainsObj(obj: String): Boolean {
        val objId = obj.asRSCM(RSCMType.OBJ)
        return playerContainsInInventories { getInvObj(it).id == objId }
    }

    public fun playerContainsAnyObj(vararg objs: String): Boolean = objs.any(::playerContainsObj)

    private inline fun playerContainsInInventories(
        crossinline predicate: (InvObj) -> Boolean
    ): Boolean {
        for (inventory in player.invMap.values) {
            if (inventory.type.scope == InvScope.Shared) continue
            if (inventory.any { slot -> slot != null && predicate(slot) }) {
                return true
            }
        }
        return false
    }

    public fun inv(inv: String): Inventory {
        return player.invMap.getOrPut(inv)
    }

    public operator fun Inventory.contains(content: String): Boolean {
        return invContains(this, content)
    }

    public fun <T : Any> lcParam(type: ObjectServerType, param: TypedParamType<T>): T {
        return type.param(param)
    }

    public fun <T : Any> lcParamOrNull(type: ObjectServerType, param: TypedParamType<T>): T? {
        return type.paramOrNull(param)
    }

    public fun lcIsContentType(loc: ObjectServerType, content: String): Boolean {
        return loc.isContentType(content)
    }

    public fun <T : Any> locParam(loc: LocInfo, param: TypedParamType<T>): T {
        return ServerCacheManager.getObject(loc.id)!!.param(param)
    }

    public fun <T : Any> locParamOrNull(loc: LocInfo, param: TypedParamType<T>): T? {
        return ServerCacheManager.getObject(loc.id)!!.paramOrNull(param)
    }

    public fun <T : Any> locParam(loc: BoundLocInfo, param: TypedParamType<T>): T {
        return ServerCacheManager.getObject(loc.id)!!.param(param)
    }

    public fun <T : Any> locParamOrNull(loc: BoundLocInfo, param: TypedParamType<T>): T? {
        return ServerCacheManager.getObject(loc.id)!!.paramOrNull(param)
    }

    public fun locIsContentType(loc: LocInfo, content: String): Boolean {
        return ServerCacheManager.getObject(loc.id)!!.isContentType(content)
    }

    public fun locIsContentType(loc: BoundLocInfo, content: String): Boolean {
        return ServerCacheManager.getObject(loc.id)!!.isContentType(content)
    }

    public fun locIsType(loc: LocInfo, type: ObjectServerType): Boolean {
        return loc.isType(type)
    }

    public fun locIsType(loc: BoundLocInfo, type: ObjectServerType): Boolean {
        return loc.isType(type)
    }

    public fun locAnim(repo: WorldRepository, loc: LocInfo, seq: String) {
        repo.locAnim(loc, seq)
    }

    public fun locAnim(repo: WorldRepository, loc: BoundLocInfo, seq: String) {
        repo.locAnim(loc, seq)
    }

    public fun spotanimMap(
        repo: WorldRepository,
        internal: String,
        coord: CoordGrid,
        height: Int = 0,
        delay: Int = 0,
    ) {
        val spotanim = SpotanimType(internal.asRSCM(RSCMType.SPOTANIM))
        repo.spotanimMap(spotanim, coord, height, delay)
    }

    public fun spotanimMap(
        repo: WorldRepository,
        spotanim: SpotanimType,
        coord: CoordGrid,
        height: Int = 0,
        delay: Int = 0,
    ) {
        repo.spotanimMap(spotanim, coord, height, delay)
    }

    public fun mes(text: String) {
        player.mes(text, ChatType.GameMessage)
    }

    public fun mes(text: String, type: ChatType) {
        player.mes(text, type)
    }

    public fun spam(text: String) {
        player.spam(text)
    }

    public fun midiJingle(jingleTrack: Int, lengthInMillis: Int = 0) {
        player.midiJingle(jingleTrack, lengthInMillis)
    }

    @Deprecated(
        "Jingle gameval ids are not index-11 track ids; the client cannot play them. " +
            "Use the Int overload with the jingle's cache track id (wiki `Cache ID`).",
        ReplaceWith("midiJingle(jingleTrack)"),
    )
    public fun midiJingle(jingle: String) {
        @Suppress("DEPRECATION") player.midiJingle(jingle)
    }

    public fun midiSong(midi: String) {
        player.midiSong(midi)
    }

    public fun <T : Any> ncParam(type: NpcServerType, param: TypedParamType<T>): T {
        return type.param(param)
    }

    public fun <T : Any> ncParamOrNull(type: NpcServerType, param: TypedParamType<T>): T? {
        return type.paramOrNull(param)
    }

    public fun ncIsContentType(type: NpcServerType, content: String): Boolean {
        return type.isContentType(content)
    }

    public fun npcPlayerFaceClose(npc: Npc, target: Player = this.player) {
        npc.playerFaceClose(target)
    }

    public fun npcPlayerFace(npc: Npc, target: Player = this.player) {
        npc.playerFace(target)
    }

    public fun npcPlayerEscape(npc: Npc, target: Player = this.player) {
        npc.playerEscape(target)
    }

    public fun npcResetMode(npc: Npc) {
        npc.resetMode()
    }

    /**
     * Transmogrifies the [npc] into [into], reassigning its internal `uid`. Since npc interactions
     * validate `uid`s before processing, this will automatically cancel any ongoing interactions
     * with this [npc].
     *
     * @param duration The cycle duration that the [npc] will remain as [into] before automatically
     *   changing back to its original type. Set to `Int.MAX_VALUE` to bypass this behavior.
     */
    @OptIn(InternalApi::class)
    public fun npcChangeType(npc: Npc, into: NpcServerType, duration: Int) {
        npc.transmog(into, duration)
        npc.assignUid()
    }

    public fun npcVisType(npc: Npc): NpcServerType {
        val multiNpc = context.npcInteractions.multiNpc(npc.visType, player.vars)
        return multiNpc ?: npc.visType
    }

    public fun <T : Any> npcParam(npc: Npc, param: TypedParamType<T>): T {
        return npc.type.param(param)
    }

    public fun <T : Any> npcParamOrNull(npc: Npc, param: TypedParamType<T>): T? {
        return npc.type.paramOrNull(param)
    }

    public fun ocCert(type: String): ItemServerType {
        return cert(type)
    }

    public fun ocUncert(type: ItemServerType): ItemServerType {
        return uncert(type)
    }

    public fun ocName(type: ItemServerType): String {
        return type.name
    }

    public fun <T : Any> ocParam(obj: InvObj, type: TypedParamType<T>): T {
        return getInvObj(obj).param(type)
    }

    public fun <T : Any> ocParamOrNull(obj: InvObj?, type: TypedParamType<T>): T? {
        return if (obj == null) null else getInvObj(obj).paramOrNull(type)
    }

    public fun ocIsContentType(obj: InvObj?, content: String): Boolean {
        return obj != null && getInvObj(obj).contentGroup == content.asRSCM(RSCMType.CONTENT)
    }

    public fun ocIsType(obj: InvObj?, type: String): Boolean {
        return obj.isType(type)
    }

    public fun ocIsType(obj: InvObj?, type: String, vararg others: String): Boolean {
        return obj.isType(type) || others.any(obj::isType)
    }

    public fun ocTradable(obj: InvObj): Boolean {
        return getInvObj(obj).tradeable
    }

    public fun ocCategory(type: ItemServerType?): CategoryType? {
        return if (type == null) null else CategoryType(type.category)
    }

    // TODO: Decide if we either want to keep this and make it public; or remove it and refactor
    //  its usage (only called in one place as of now due to laziness).
    internal fun ocType(obj: InvObj?): ItemServerType? {
        return if (obj == null) null else getInvObj(obj)
    }

    public fun seqLength(seq: SequenceServerType): Int {
        return seq.totalDelay
    }

    public fun seqTicks(seq: SequenceServerType): Int {
        return seq.tickDuration
    }

    public fun soundSynth(synth: String, loops: Int = 1, delay: Int = 0) {
        player.soundSynth(synth, loops, delay)
    }

    public fun soundSynth(synth: SynthType, loops: Int = 1, delay: Int = 0) {
        player.soundSynth(synth, loops, delay)
    }

    public fun soundSynth(synth: Int, loops: Int = 1, delay: Int = 0) {
        player.soundSynth(synth, loops, delay)
    }

    public fun soundArea(
        repo: WorldRepository,
        source: CoordGrid,
        synth: String,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
        size: Int = 0,
    ) {
        repo.soundArea(source, synth, delay, loops, radius, size)
    }

    public fun soundArea(
        repo: WorldRepository,
        source: PathingEntity,
        synth: String,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
    ) {
        repo.soundArea(source, synth, delay, loops, radius)
    }

    /**
     * Guards against unsuspended `onOpHeldN -> opHeldN` recursion on the same inventory slot.
     */
    private fun checkOpHeldCallLimit() {
        if (opHeldCallCount++ >= 25) {
            throw IllegalStateException("Detected `opHeld` infinite recursion: $this")
        }
    }

    private fun Npc.resolveVisName(): String {
        if (!type.isMultiNpc) {
            return type.name
        }
        val visType = npcVisType(this)
        return visType.name
    }

    // The player should not be able to trigger a manual logout (e.g., by clicking the logout
    // button) if the server has already initiated a logout through other means.
    private fun assertLogoutState() {
        check(!player.loggingOut) { "Player already logging out." }
        check(!player.pendingLogout) { "Player already marked for pending logout." }
        check(!player.clientDisconnected.get()) { "Client already marked as disconnected." }
        check(!player.forceDisconnect) { "Player already marked for forced disconnection." }
    }

    @OptIn(InternalApi::class)
    private fun canLogout(): Boolean {
        return !player.isPendingLogout() && !player.pendingLogout && !player.loggingOut
    }

    override fun toString(): String {
        return "ProtectedAccess(player=$player, coroutine=$coroutine)"
    }
}

private fun <T> lazy(init: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, init)

private fun MesAnimType.splitGetAnim(lines: Int) =
    when (lines) {
        1 -> len1
        2 -> len2
        3 -> len3
        else -> len4
    }

public fun Player.clearPendingAction(eventBus: EventBus) {
    check(!isDelayed) { "`clearpendingaction` should never be called while player is delayed." }
    ifClose(eventBus)
    cancelActiveCoroutine()
    clearInteraction()
}
