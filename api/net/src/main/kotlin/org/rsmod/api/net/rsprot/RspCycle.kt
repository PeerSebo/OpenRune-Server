package org.rsmod.api.net.rsprot

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcInfoPacket
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerAvatarExtendedInfo
import net.rsprot.protocol.game.outgoing.info.util.BuildArea
import net.rsprot.protocol.game.outgoing.info.util.PacketResult
import net.rsprot.protocol.game.outgoing.info.util.getOrThrow
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2
import net.rsprot.protocol.game.outgoing.map.RebuildRegionV2
import net.rsprot.protocol.game.outgoing.map.RebuildWorldEntityV4
import net.rsprot.protocol.game.outgoing.map.util.RebuildRegionZone
import net.rsprot.protocol.game.outgoing.map.util.ReferenceZone
import net.rsprot.protocol.message.OutgoingGameMessage
import org.rsmod.api.config.refs.params
import org.rsmod.api.game.process.player.WorldEntityZoneUpdates
import org.rsmod.api.net.rsprot.ext.setFaceTarget
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.game.client.ClientCycle
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class RspCycle(
    private val session: Session<Player>,
    private val infos: Infos,
    private val regions: RegionRegistry,
    private val worldEntities: WorldEntityRegistry,
    private val worldZoneUpdates: WorldEntityZoneUpdates,
) : ClientCycle {
    private var knownCoords: CoordGrid = CoordGrid.ZERO

    private var knownBuildArea: CoordGrid = CoordGrid.NULL

    private var knownRootBuildArea: CoordGrid = CoordGrid.NULL

    private var knownAboard: Boolean = false

    private var forceAreaRebuild: Boolean = false

    /**
     * World entity indices this client currently has a map for. Populated when rsprot reports a
     * world as freshly added, drained from `InfoPackets.removedWorldIndices` - which exists
     * precisely to let "the server stop tracking the worlds for zone updates".
     */
    private val trackedWorldIndices: MutableSet<Int> = HashSet()

    private var worldEntityZoneProvider: RebuildWorldEntityV4.RebuildWorldEntityZoneProvider? = null

    private var knownCachedSpeed: MoveSpeed? = null

    private var knownFaceEntity: Int? = -1

    private var knownSyncedAngle: Int = -1

    private var knownRegionUid: Int? = null

    private var cachedRegionZoneProvider: RebuildRegionV2.RebuildRegionZoneProvider? = null

    private val playerInfo
        get() = infos.playerInfo

    private val playerExtendedInfo: PlayerAvatarExtendedInfo
        get() = playerInfo.avatar.extendedInfo

    /**
     * Rebuild V2 worldArea identifies the active world, not an instance region. Deck and root
     * rebuilds must use their respective client world.
     */
    private val worldArea: Int
        get() = -1

    fun init(player: Player) {
        player.updateCoords()
        player.queueRebuildLogin()
    }

    private fun Player.queueRebuildLogin() {
        val rebuild = RebuildLoginV2(x shr 3, z shr 3, worldArea, playerInfo)
        session.queue(rebuild)
    }

    override fun update(player: Player) {
        player.updateMoveSpeed()
        player.updateCoords()
        player.rebuildArea()
        player.applyExactMove()
        player.applyPublicMessage()
        player.applyFacePathingEntity()
        player.applyFaceAngle()
        player.syncFaceAngle()
        player.applyAnim()
        player.applySpotanims()
        player.applySay()
        player.applyTinting()
        player.applyHeadbars()
        player.applyHitmarks()
        player.syncAppearance()
    }

    override fun flush(player: Player) {
        val infoPackets = infos.getPackets()
        val rootPackets = infoPackets.rootWorldInfoPackets

        session.queue(rootPackets.activeWorld)
        session.queue(rootPackets.npcUpdateOrigin)
        session.queuePacketResult(rootPackets.worldEntityInfo)
        session.queuePacketResult(rootPackets.playerInfo)
        session.queueNpcInfoPacket(rootPackets.npcInfo)

        // Carries no packet of its own: the client drops a world once world entity info stops
        // transmitting it. All the server has to do is stop tracking it. Drained before the add
        // loop so a same-cycle remove-then-add cannot clear a freshly tracked index.
        for (removed in infoPackets.removedWorldIndices) {
            trackedWorldIndices.remove(removed)
        }

        for (world in infoPackets.activeWorlds) {
            session.queue(world.activeWorld)
            if (world.added) {
                queueWorldEntityRebuild(world.worldId)
            }
            worldEntities[world.worldId]?.let {
                worldZoneUpdates.send(player, it, world.added) { message -> session.queue(message) }
            }
            session.queue(world.npcUpdateOrigin)
            session.queueNpcInfoPacket(world.npcInfo)
        }

        session.queue(rootPackets.activeWorld)
    }

    /**
     * A newly visible boat needs its deck rebuild before any updates addressed to that client
     * world.
     */
    private fun queueWorldEntityRebuild(index: Int) {
        val worldEntity = worldEntities[index] ?: return
        if (!trackedWorldIndices.add(index)) {
            return
        }
        val provider = worldEntityZoneProvider ?: createWorldEntityZoneProvider()
        worldEntityZoneProvider = provider
        val base = worldEntity.instanceSouthWest
        // `baseX`/`baseZ` are absolute TILE coords here (the packet does `baseX ushr 3` itself),
        // unlike `RebuildRegionV2` whose first two arguments are already zone coords.
        val rebuild =
            RebuildWorldEntityV4(
                baseX = base.x,
                baseZ = base.z,
                sizeX = worldEntity.zoneSizeX,
                sizeZ = worldEntity.zoneSizeZ,
                zoneProvider = provider,
            )
        session.queue(rebuild)
    }

    private fun createWorldEntityZoneProvider(): RebuildWorldEntityV4.RebuildWorldEntityZoneProvider =
        RebuildWorldEntityV4.RebuildWorldEntityZoneProvider { zoneX, zoneZ, level ->
            val copyZone = regions[ZoneKey(zoneX, zoneZ, level)]
            if (copyZone == RegionZoneCopy.NULL) {
                null
            } else {
                val ref = ReferenceZone(copyZone.packed)
                RebuildRegionZone(
                    zoneX = ref.zoneX,
                    zoneZ = ref.zoneZ,
                    level = ref.level,
                    rotation = ref.rotation,
                )
            }
        }

    override fun release() {
        val infoPackets = infos.getPackets()
        val rootPackets = infoPackets.rootWorldInfoPackets

        releasePacketResult(rootPackets.worldEntityInfo)
        releasePacketResult(rootPackets.playerInfo)
        releaseNpcInfoPacket(rootPackets.npcInfo)
        for (world in infoPackets.activeWorlds) {
            releaseNpcInfoPacket(world.npcInfo)
        }
    }

    private fun <T : OutgoingGameMessage> Session<Player>.queuePacketResult(
        result: PacketResult<T>
    ) {
        queue(result.getOrThrow())
    }

    private fun Session<Player>.queueNpcInfoPacket(result: PacketResult<NpcInfoPacket>) {
        if (result.isEmpty()) {
            result.safeReleaseOrThrow()
            return
        }
        queuePacketResult(result)
    }

    private fun <T : OutgoingGameMessage> releasePacketResult(result: PacketResult<T>) {
        result.getOrThrow().safeRelease()
    }

    private fun releaseNpcInfoPacket(result: PacketResult<NpcInfoPacket>) {
        if (result.isEmpty()) {
            result.safeReleaseOrThrow()
            return
        }
        result.getOrThrow().safeRelease()
    }

    private fun Player.updateMoveSpeed() {
        val cachedSpeed = cachedMoveSpeed.takeUnless { it == MoveSpeed.Stationary } ?: varMoveSpeed
        if (knownCachedSpeed != cachedSpeed) {
            playerExtendedInfo.setMoveSpeed(cachedSpeed.steps)
            knownCachedSpeed = cachedSpeed
        }
        val moveSpeed = resolvePendingMoveSpeed()
        if (moveSpeed != knownCachedSpeed && coords != knownCoords) {
            playerExtendedInfo.setTempMoveSpeed(moveSpeed.steps)
        }
    }

    private fun Player.resolvePendingMoveSpeed(): MoveSpeed =
        when {
            pendingTelejump -> MoveSpeed.Stationary
            pendingTeleport -> MoveSpeed.Walk
            pendingStepCount == 1 -> MoveSpeed.Walk
            pendingStepCount == 2 -> MoveSpeed.Run
            else -> moveSpeed
        }

    private fun Player.updateCoords() {
        infos.updateRootCoord(level, x, z)
        knownCoords = coords
    }

    private fun Player.rebuildArea() {
        // `findByInstanceCoords` rejects any coord below the instance working area (x < 6400)
        // before it scans anything, so every player who is not standing on a world entity's deck -
        // which is every player on a server that has never spawned one - falls straight through to
        // the original code path below, byte-for-byte unchanged.
        val worldEntity = worldEntities.findByInstanceCoords(coords)
        if (worldEntity != null) {
            rebuildAreaAboard(worldEntity)
            return
        }

        if (knownAboard) {
            // Disembarked this cycle. Force a recompute even in the pathological case where the
            // new build area happens to equal the instance-land one we last stored.
            knownAboard = false
            knownRootBuildArea = CoordGrid.NULL
            forceAreaRebuild = true
        }

        val recalcBuildArea =
            buildArea != CoordGrid.NULL && (forceAreaRebuild || knownBuildArea != buildArea)
        forceAreaRebuild = false
        if (recalcBuildArea) {
            val zone = ZoneKey.from(buildArea)
            val area = BuildArea(zone.x, zone.z)
            infos.updateRootBuildArea(area)
        }

        if (!recalcBuildArea) {
            return
        }

        if (knownBuildArea == CoordGrid.NULL) {
            knownBuildArea = buildArea
            return
        }

        if (regionUid == null) {
            val rebuild = RebuildNormalV2(x shr 3, z shr 3, worldArea)
            knownBuildArea = buildArea
            knownRegionUid = null
            cachedRegionZoneProvider = null
            session.queue(rebuild)
            return
        }

        val region = regions[coords]

        // The player's region uid should be reassigned every cycle before calling this function,
        // as such we should expect the region to always be valid at this point.
        checkNotNull(region) { "Unexpected invalid region: uid=$regionUid, coords=$coords" }

        // TODO: When implementing `net` module properly, figure out what the best way would be to
        //  "invalidate" the `cachedRebuildRegion` if the region is somehow altered. This can
        //  happen in regions such as the Gauntlet. (If we decide to keep this as a cached value
        //  as opposed to reconstructing it every time)
        if (regionUid != knownRegionUid) {
            cachedRegionZoneProvider = createRegionZoneProvider(region)
            knownRegionUid = regionUid
        }

        val zoneProvider = cachedRegionZoneProvider ?: createRegionZoneProvider(region)
        val rebuild = RebuildRegionV2(x shr 3, z shr 3, true, zoneProvider)
        knownBuildArea = buildArea
        cachedRegionZoneProvider = zoneProvider
        session.queue(rebuild)
    }

    /**
     * While aboard, rsprot still needs a valid root-world build area for other entities; deck
     * coordinates cannot stand in for it.
     */
    private fun Player.rebuildAreaAboard(worldEntity: WorldEntity) {
        knownAboard = true

        val rootCoords = worldEntity.rootCoords
        val rootBuildArea = BuildAreaUtils.calculateBuildArea(ZoneKey.from(rootCoords))
        if (knownRootBuildArea == rootBuildArea) {
            return
        }

        val zone = ZoneKey.from(rootBuildArea)
        infos.updateRootBuildArea(BuildArea(zone.x, zone.z))

        knownRootBuildArea = rootBuildArea
        knownBuildArea = buildArea
        knownRegionUid = null
        cachedRegionZoneProvider = null

        val rebuild = RebuildNormalV2(rootCoords.x shr 3, rootCoords.z shr 3, worldArea)
        session.queue(rebuild)
    }

    private fun createRegionZoneProvider(
        region: Region
    ): RebuildRegionV2.RebuildRegionZoneProvider {
        val regionZones = region.toZoneList()
        val rebuildZones =
            regionZones.associateWith { zone ->
                val copyZone = regions[zone]
                if (copyZone == RegionZoneCopy.NULL) {
                    return@associateWith null
                }
                ReferenceZone(copyZone.packed)
            }
        val zoneProvider =
            RebuildRegionV2.RebuildRegionZoneProvider { zoneX, zoneZ, level ->
                rebuildZones[ZoneKey(zoneX, zoneZ, level)]?.let { ref ->
                    RebuildRegionZone(
                        zoneX = ref.zoneX,
                        zoneZ = ref.zoneZ,
                        level = ref.level,
                        rotation = ref.rotation,
                    )
                }
            }
        return zoneProvider
    }

    private fun Player.applyPublicMessage() {
        val message = publicMessage ?: return
        playerExtendedInfo.setChat(
            colour = message.colour,
            effects = message.effect,
            modicon = message.modIcon,
            autotyper = message.autoTyper,
            text = message.text,
            pattern = message.pattern,
        )
        publicMessage = null
    }

    private fun Player.applyFacePathingEntity() {
        if (knownFaceEntity != faceEntity.entitySlot) {
            playerExtendedInfo.setFaceTarget(faceEntity, faceEntityFallbackAngle)
            knownFaceEntity = faceEntity.entitySlot
        }
    }

    private fun Player.applyFaceAngle() {
        if (pendingFaceAngle != EntityFaceAngle.NULL) {
            playerExtendedInfo.setFaceAngle(pendingFaceAngle.intValue)
        }
    }

    private fun Player.syncFaceAngle() {
        // Silently keeps the avatar's angle up to date so new observers see the player facing the
        // correct direction, without replaying a face-angle update for existing observers.
        if (faceAngle != knownSyncedAngle) {
            playerExtendedInfo.syncAngle(faceAngle)
            knownSyncedAngle = faceAngle
        }
    }

    private fun Player.applyAnim() {
        when (pendingSequence) {
            EntitySeq.NULL -> return
            EntitySeq.ZERO -> playerExtendedInfo.setSequence(-1, 0)
            else -> playerExtendedInfo.setSequence(pendingSequence.id, pendingSequence.delay)
        }
    }

    private fun Player.applySpotanims() {
        if (pendingSpotanims.isEmpty) {
            return
        }
        for (packed in pendingSpotanims.longIterator()) {
            val (id, delay, height, slot) = EntitySpotanim(packed)
            playerExtendedInfo.setSpotAnim(slot, id, delay, height)
        }
    }

    private fun Player.applySay() {
        val text = pendingSay ?: return
        playerExtendedInfo.setSay(text)
    }

    private fun Player.applyTinting() {
        val tint = pendingTinting ?: return
        playerExtendedInfo.setTinting(
            startTime = tint.startCycle,
            endTime = tint.endCycle,
            hue = tint.hue,
            saturation = tint.saturation,
            lightness = tint.lightness,
            weight = tint.weight,
        )
    }

    private fun Player.applyExactMove() {
        val move = pendingExactMove ?: return
        playerExtendedInfo.setExactMove(
            deltaX1 = move.deltaX1,
            deltaZ1 = move.deltaZ1,
            delay1 = move.clientDelay1,
            deltaX2 = move.deltaX2,
            deltaZ2 = move.deltaZ2,
            delay2 = move.clientDelay2,
            angle = move.direction,
        )
    }

    private fun Player.applyHeadbars() {
        for (packedHeadbar in activeHeadbars.longIterator()) {
            val headbar = Headbar(packedHeadbar)
            playerExtendedInfo.addHeadBar(
                sourceIndex = if (headbar.isNoSource) -1 else headbar.sourceSlot,
                selfType = headbar.self,
                otherType = if (headbar.isPrivate) -1 else headbar.public,
                startFill = headbar.startFill,
                endFill = headbar.endFill,
                startTime = headbar.startTime,
                endTime = headbar.endTime,
            )
        }
    }

    private fun Player.applyHitmarks() {
        for (packedHitmark in activeHitmarks.longIterator()) {
            val hitmark = Hitmark(packedHitmark)
            playerExtendedInfo.addHitMark(
                sourceIndex = if (hitmark.isNoSource) -1 else hitmark.sourceSlot,
                selfType = hitmark.self,
                sourceType = hitmark.source,
                otherType = if (hitmark.isPrivate) -1 else hitmark.public,
                value = hitmark.damage,
                delay = hitmark.delay,
            )
        }
    }

    private fun Player.syncAppearance() {
        if (!appearance.rebuild) {
            return
        }
        val info = playerExtendedInfo

        val colours = appearance.coloursSnapshot()
        for (i in colours.indices) {
            info.setColour(i, colours[i].toInt())
        }

        val identKit = appearance.identKitSnapshot()
        for (i in identKit.indices) {
            info.setIdentKit(i, identKit[i].toInt())
        }

        info.setName(displayName)
        info.setOverheadIcon(overheadIcon ?: -1)
        info.setSkullIcon(skullIcon ?: -1)
        info.setCombatLevel(combatLevel)
        info.setBodyType(appearance.bodyType)
        info.setPronoun(appearance.pronoun)
        info.setHidden(appearance.softHidden)

        info.setNameExtras(
            beforeName = appearance.namePrefix ?: "",
            afterName = appearance.nameSuffix ?: "",
            afterCombatLevel = appearance.combatLvlSuffix ?: "",
        )

        val bas = this.appearance.bas
        // `hideObjs` renders the appearance identkit-only: the worn entries are skipped below, and
        // the weapon must not contribute a base animation set the (empty) equipment block cannot
        // justify - live's stripped-gear appearances carry the default human bas.
        val hideObjs = this.appearance.hideObjs
        val weapon = if (hideObjs) null else this.righthand
        val transmog = this.transmog

        val readyAnim: Int
        val turnOnSpotAnim: Int
        val walkForwardAnim: Int
        val walkBackAnim: Int
        val walkLeftAnim: Int
        val walkRightAnim: Int
        val runningAnim: Int

        if (bas != null) {
            readyAnim = bas.readyAnim
            turnOnSpotAnim = bas.turnOnSpot
            walkForwardAnim = bas.walkForward
            walkBackAnim = bas.walkBack
            walkLeftAnim = bas.walkLeft
            walkRightAnim = bas.walkRight
            runningAnim = bas.running
        } else if (transmog != null) {
            readyAnim = transmog.standAnim
            turnOnSpotAnim = transmog.rotateBackAnim
            walkForwardAnim = transmog.walkAnim
            walkBackAnim = transmog.walkAnim
            walkLeftAnim = transmog.walkLeftAnim
            walkRightAnim = transmog.walkRightAnim
            runningAnim = transmog.runSequence
        } else if (weapon != null) {
            val type = getInvObj(weapon)
            readyAnim = type.param(params.bas_readyanim).id
            turnOnSpotAnim = type.param(params.bas_turnonspot).id
            walkForwardAnim = type.param(params.bas_walk_f).id
            walkBackAnim = type.param(params.bas_walk_b).id
            walkLeftAnim = type.param(params.bas_walk_l).id
            walkRightAnim = type.param(params.bas_walk_r).id
            runningAnim = type.param(params.bas_running).id
        } else {
            val default =
                ServerCacheManager.getBas("bas.human_default".asRSCM())
                    ?: error("Unable to find Bas Human")
            readyAnim = default.readyAnim
            turnOnSpotAnim = default.turnOnSpot
            walkForwardAnim = default.walkForward
            walkBackAnim = default.walkBack
            walkLeftAnim = default.walkLeft
            walkRightAnim = default.walkRight
            runningAnim = default.running
        }

        info.setTransmogrification(transmog?.id ?: -1)
        info.setBaseAnimationSet(
            readyAnim = readyAnim,
            turnAnim = turnOnSpotAnim,
            walkAnim = walkForwardAnim,
            walkAnimBack = walkBackAnim,
            walkAnimLeft = walkLeftAnim,
            walkAnimRight = walkRightAnim,
            runAnim = runningAnim,
        )

        for (wearpos in Wearpos.visibleWearpos) {
            val obj = appearance.wornObj(wearpos.slot, worn[wearpos.slot])
            if (obj == null) {
                info.setWornObj(wearpos.slot, -1, -1, -1)
                continue
            }
            val objType = getInvObj(obj)
            info.setWornObj(wearpos.slot, obj.id, objType.wearpos2, objType.wearpos3)
        }
    }
}
