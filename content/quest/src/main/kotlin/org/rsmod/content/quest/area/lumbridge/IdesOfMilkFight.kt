package org.rsmod.content.quest.area.lumbridge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.knockback
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.table.MusicRow
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityExactMove
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Translation
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

class IdesOfMilkFight
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val idesOfMilk: IdesOfMilk,
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val playerHitModifier: PlayerHitModifier,
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val protectedAccess: ProtectedAccessLauncher,
    private val musicPlayer: MusicPlayer,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val npcDeath: NpcDeath,
    private val combatGate: BrutusCombatGate,
    private val worldQueues: WorldQueueList,
) : InstanceScript(registry) {

    private val stepValidator by lazy { StepValidator(collision) }

    private val quest
        get() = idesOfMilk.quest

    private var Player.dontAskAgain by intVarBit(IdesOfMilkRefs.VarbitDontAskAgain)

    private val fights = HashMap<Long, BullFight>()

    private val bullType: NpcServerType by lazy {
        ServerCacheManager.getNpc(IdesOfMilkRefs.Bull.asRSCM(dev.openrune.rscm.RSCMType.NPC))
            ?: error("Missing npc type: ${IdesOfMilkRefs.Bull}")
    }

    override fun settingsRow(): String = IdesOfMilkRefs.InstanceSettingsRow

    override fun area(): InstanceArea =
        InstanceArea.template(
            template = IdesOfMilkArenaTemplate,
            enterCoord = IdesOfMilkCoords.FightEntry.asRegionLocal(),
            exitCoord = IdesOfMilkCoords.FightExit,
            npcSpawns = listOf(InstanceNpc(IdesOfMilkRefs.Bull, IdesOfMilkCoords.BullSpawn)),
        )

    override fun ScriptContext.configure() {
        onEnterObject { releaseGate() }

        onExitObject { exitGate() }

        onOpLoc2(IdesOfMilkRefs.GateExitLeft) { exitGate() }
        onOpLoc2(IdesOfMilkRefs.GateExitRight) { exitGate() }

        onAiOpPlayer2(bullType) { attackTarget(it.target) }
        onAiApPlayer2(bullType) { attackTarget(it.target) }

        onNpcQueue(bullType, "queue.death") { bullDies() }

        onNpcQueue(bullType, IdesOfMilkRefs.QueueStomp) { resolveStomp() }
        onNpcQueue(bullType, IdesOfMilkRefs.QueueCharge) { resolveCharge() }
        onNpcQueue(bullType, IdesOfMilkRefs.QueueRecover) { resumeCombat() }

        onPlayerSoftTimer(IdesOfMilkRefs.TimerBossRespawn) { respawnBoss(player) }

        onPlayerCoordsChanged { reclaimAbandoned(player) }

        onPlayerLogin { discardFight(player) }
    }

    private suspend fun ProtectedAccess.releaseGate() {
        val stage = quest.getQuestStage(player)
        when {
            stage < IdesOfMilkStage.BullTaskGiven -> {
                startDialogue {
                    chatPlayer(neutral, "Releasing the bull into the cow pen is a bad idea.")
                }
                mes("You need to have completed The Ides of Milk quest to release this bull.")
            }
            stage < IdesOfMilkStage.BullSlain -> promptRelease(postQuest = false)
            stage < IdesOfMilkStage.Complete ->
                startDialogue { chatPlayer(neutral, "Brutus has already been dealt with.") }
            else ->
                promptRelease(postQuest = true)
        }
    }

    private suspend fun ProtectedAccess.promptRelease(postQuest: Boolean) {
        if (player.uuid?.let(fights::containsKey) == true) {
            return
        }
        if (player.dontAskAgain == 0) {
            val choice =
                choice3(
                    "Yes.",
                    1,
                    "Yes, and don't ask again.",
                    2,
                    "No.",
                    3,
                    title = if (postQuest) PostQuestPromptTitle else QuestPromptTitle,
                )
            when (choice) {
                2 -> player.dontAskAgain = 1
                3 -> return
                1 -> Unit
                else -> return
            }
        }
        beginFight(postQuest)
    }

    private suspend fun ProtectedAccess.beginFight(postQuest: Boolean) {
        val entryMessage = if (postQuest) PostQuestEntryMessage else QuestEntryMessage
        mesboxNp(entryMessage)
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FadeDuration,
        )
        hideHealthHud()
        delay(1)
        minimapHideMap()
        delay(1)

        val region = regionRepo.add(IdesOfMilkArenaTemplate)
        if (region == null) {
            minimapReset()
            showHealthHud()
            fadeOverlay(0, 0, 0, 255, FadeDuration)
            mes(constants.dm_default)
            return
        }
        regionRepo.protect(region)

        val scene = SceneRegion(region)
        val fight = BullFight(player, region, scene, postQuest)
        player.uuid?.let { fights[it] = fight }

        // Persist the exit on entry; account loading consumes it before building the login scene.
        player.loginExitCoord = IdesOfMilkCoords.FightExit
        telejump(scene[IdesOfMilkCoords.FightEntry])
        rebuildAppearance()
        faceSquare(scene[IdesOfMilkCoords.FightEntryFace])

        val track = MusicRow.getRow(IdesOfMilkRefs.MusicBossMoosic)
        musicPlayer.unlock(player, track)
        musicPlayer.play(player, track)
        soundSynth(IdesOfMilkRefs.SynthBullSpawn)

        spawnBull(fight, scene)
        dressField(fight, scene)
        swapGates(scene)
        delay(2)

        minimapReset()
        showHealthHud()
        fadeOverlay(0, 0, 0, 255, FadeDuration)
        mesbox(entryMessage)
    }

    private fun spawnBull(fight: BullFight, scene: SceneRegion) {
        val bull = Npc(bullType, scene[IdesOfMilkCoords.BullSpawn])
        combatGate.register(bull, fight.player, checkNotNull(fight.region))
        bull.respawnDir = Direction.North
        npcRepo.add(bull, FightLifespanTicks)
        bull.anim(IdesOfMilkRefs.SeqBullSpawn)
        if (!fight.postQuest) {
            bull.nameChange(BullName)
        }
        bull.facePlayer(fight.player)
        fight.bull = bull
    }

    // Exclude cows whose wander range reaches the boss spawn or release gates.
    private fun dressField(fight: BullFight, scene: SceneRegion) {
        for (zx in ArenaZoneX until ArenaZoneX + ArenaZoneWidth) {
            for (zz in ArenaZoneZ until ArenaZoneZ + ArenaZoneLengthGround) {
                for (npc in npcRepo.findAll(ZoneKey(zx, zz, 0))) {
                    if (!BrutusScenery.shouldClone(npc)) {
                        continue
                    }
                    val inBlock =
                        npc.coords.x in (ArenaZoneX * 8) until (ArenaZoneX + ArenaZoneWidth) * 8 &&
                            npc.coords.z in
                                (ArenaZoneZ * 8) until (ArenaZoneZ + ArenaZoneLengthGround) * 8
                    if (!inBlock) {
                        continue
                    }
                    val clone = Npc(npc.type, scene[npc.coords])
                    npcRepo.add(clone, FightLifespanTicks)
                    clone.hideAllOps()
                    fight.decoratives += clone
                }
            }
        }
    }

    private fun swapGates(scene: SceneRegion) {
        swapLoc(
            scene,
            IdesOfMilkCoords.EntryGateLeft,
            IdesOfMilkRefs.GateEntryLeft,
            IdesOfMilkRefs.GateEntryLeftNoop,
        )
        swapLoc(
            scene,
            IdesOfMilkCoords.EntryGateRight,
            IdesOfMilkRefs.GateEntryRight,
            IdesOfMilkRefs.GateEntryRightNoop,
        )
        swapLoc(scene, IdesOfMilkCoords.ExitGateRight, WorldGateRight, IdesOfMilkRefs.GateExitRight)
        swapLoc(scene, IdesOfMilkCoords.ExitGateLeft, WorldGateLeft, IdesOfMilkRefs.GateExitLeft)
    }

    private fun swapLoc(
        scene: SceneRegion,
        worldCoord: CoordGrid,
        worldType: String,
        into: String,
    ) {
        val typeId = worldType.asRSCM()
        val worldLoc = locRepo.findAll(worldCoord).firstOrNull { it.id == typeId } ?: return
        locRepo.add(scene[worldCoord], into, FightLifespanTicks, worldLoc.angle, worldLoc.shape)
    }

    private fun StandardNpcAccess.attackTarget(target: Player) {
        if (!target.isValidTarget() || !combatGate.allows(target, npc)) {
            resetMode()
            return
        }
        if (actionDelay > mapClock) {
            return
        }
        if (!npc.isInCombat()) {
            resetMode()
            return
        }

        val fight = fights.values.firstOrNull { it.bull === npc }
        if (fight != null && chooseSpecial(fight, target)) {
            return
        }

        actionDelay = mapClock + AttackRate
        anim(IdesOfMilkRefs.SeqBullAttack)
        target.soundSynth(IdesOfMilkRefs.SynthLeatherHit)

        val landed = accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, random)
        var damage =
            if (landed) {
                random.of(maxHits.getMeleeMaxHit(npc, target, MeleeAttackType.Crush) + 1)
            } else {
                0
            }
        damage = min(damage, max(0, target.hitpoints - 1))
        target.queueHit(npc, AutoHitDelay, HitType.Melee, damage, modifier = playerHitModifier)
        target.queueCombatRetaliate(npc)
        target.combatPlayDefendAnim(clientDelay = 0)

        if (fight != null) {
            fight.autosSinceSpecial++
        }
    }

    private fun StandardNpcAccess.chooseSpecial(fight: BullFight, target: Player): Boolean {
        if (fight.stompsLeftInSet > 0) {
            fight.stompsLeftInSet--
            actionDelay = mapClock + SpecialAttackRate
            announceStomp(fight, target)
            return true
        }
        if (fight.autosSinceSpecial < fight.autosBeforeNextSet) {
            return false
        }
        fight.autosSinceSpecial = 0
        fight.autosBeforeNextSet = AutosBetweenSets
        actionDelay = mapClock + SpecialAttackRate
        if (random.randomBoolean()) {
            fight.stompsLeftInSet = StompsPerSet - 1
            announceStomp(fight, target)
        } else {
            announceCharge(fight, target)
        }
        return true
    }

    // Commit special direction and affected tiles at announcement; do not retarget during windup.
    private fun StandardNpcAccess.announceStomp(fight: BullFight, target: Player) {
        say("*snorts*")
        anim(IdesOfMilkRefs.SeqBullStomp, delay = StompAnimClientDelay)
        target.soundSynth(IdesOfMilkRefs.SynthSnort)
        val dir = quantizeDir8(npc.coords, target.coords)
        fight.pendingStompTiles = stompTiles(npc.coords, dir)
        freezeForWindup(dir)
        npc.queue(IdesOfMilkRefs.QueueStomp, SpecialResolveTicks)
    }

    private fun StandardNpcAccess.announceCharge(fight: BullFight, target: Player) {
        say("*growls*")
        anim(IdesOfMilkRefs.SeqBullCharge, delay = ChargeAnimClientDelay)
        target.soundSynth(IdesOfMilkRefs.SynthGrowl)
        val dir = quantizeDir4(npc.coords, target.coords)
        fight.pendingChargeDir = dir
        freezeForWindup(dir)
        npc.queue(IdesOfMilkRefs.QueueCharge, SpecialResolveTicks)
    }

    private fun StandardNpcAccess.freezeForWindup(dir: Direction8) {
        noneMode()
        // NoneMode alone does not stop retaliation; suppress retaliation during the windup too.
        npc.ignoreCombatInteractions = true
        npc.setHunt(0)
        val faceX = npc.coords.x + 1 + dir.dx * FaceProjection
        val faceZ = npc.coords.z + 1 + dir.dz * FaceProjection
        npc.faceSquare(CoordGrid(faceX, faceZ, npc.coords.level))
        npc.queue(IdesOfMilkRefs.QueueRecover, SpecialRecoverTicks)
    }

    // Freeze the bull before computing exact-move deltas so the client and server share the same origin.
    private fun StandardNpcAccess.resumeCombat() {
        // Restore hunt and retaliation on teardown so interrupted windups cannot leave an inert NPC.
        npc.ignoreCombatInteractions = false
        npc.setHunt(npc.type.huntRange)

        val fight = fights.values.firstOrNull { it.bull === npc } ?: return
        val target = fight.player
        if (npc.isSlotAssigned && target.isSlotAssigned && target.isValidTarget()) {
            opPlayer2(target, aiPlayerInteractions)
        }
    }

    private fun stompTiles(sw: CoordGrid, dir: Direction8): List<CoordGrid> {
        val sx = dir.dx
        val sz = dir.dz
        if (sx == 0 || sz == 0) {
            return when {
                sz > 0 -> (0 until BullSize).map { CoordGrid(sw.x + it, sw.z + BullSize, sw.level) }
                sz < 0 -> (0 until BullSize).map { CoordGrid(sw.x + it, sw.z - 1, sw.level) }
                sx > 0 -> (0 until BullSize).map { CoordGrid(sw.x + BullSize, sw.z + it, sw.level) }
                else -> (0 until BullSize).map { CoordGrid(sw.x - 1, sw.z + it, sw.level) }
            }
        }
        val cornerX = if (sx > 0) sw.x + BullSize else sw.x - 1
        val cornerZ = if (sz > 0) sw.z + BullSize else sw.z - 1
        return listOf(
            CoordGrid(cornerX, cornerZ, sw.level),
            CoordGrid(cornerX - sx, cornerZ, sw.level),
            CoordGrid(cornerX - sx * 2, cornerZ, sw.level),
            CoordGrid(cornerX, cornerZ - sz, sw.level),
            CoordGrid(cornerX, cornerZ - sz * 2, sw.level),
        )
    }

    private fun StandardNpcAccess.resolveStomp() {
        val fight = fights.values.firstOrNull { it.bull === npc } ?: return
        val tiles = fight.pendingStompTiles ?: return
        fight.pendingStompTiles = null
        if (!npc.isSlotAssigned) {
            return
        }

        for (tile in tiles) {
            worldRepo.spotanimMap(stompSpotanim, tile)
        }
        val target = fight.player
        if (target.isSlotAssigned && target.coords in tiles) {
            specialHit(target)
        }
    }

    private fun StandardNpcAccess.resolveCharge() {
        val fight = fights.values.firstOrNull { it.bull === npc } ?: return
        val dir = fight.pendingChargeDir ?: return
        fight.pendingChargeDir = null
        if (!npc.isSlotAssigned) {
            return
        }
        val target = fight.player
        if (!target.isValidTarget() || !combatGate.allows(target, npc)) {
            return
        }

        val distance = random.of(ChargeTilesMin, ChargeTilesMax)
        val start = npc.coords
        var end = start
        var steps = 0
        while (steps < distance) {
            val canStep =
                stepValidator.canTravel(
                    level = end.level,
                    x = end.x,
                    z = end.z,
                    offsetX = dir.dx,
                    offsetZ = dir.dz,
                    size = BullSize,
                )
            if (!canStep) {
                break
            }
            end = end.translate(dir.dx, dir.dz)
            steps++
        }

        npc.resetFaceEntity()
        if (steps > 0) {
            chargeMove(start, end, dir)
        }

        if (brutusChargeHits(start, end, BullSize, target.coords)) {
            specialHit(target)
            worldQueues.add(SpecialHitDelay) {
                if (!npc.isSlotAssigned || npc.hitpoints <= 0 ||
                    !target.isValidTarget() || !combatGate.allows(target, npc)
                ) return@add
                val destination = brutusChargePushDestination(target.coords, dir, stepValidator)
                target.knockback(destination, (dir.exactMoveAngle + 1024) and 2047, collision)
            }
        }
    }

    private fun StandardNpcAccess.chargeMove(start: CoordGrid, end: CoordGrid, dir: Direction8) {
        PathingEntityCommon.teleport(npc, collision, end)
        val delta = Translation.between(start, end)
        npc.pendingExactMove =
            EntityExactMove(
                deltaX1 = delta.x,
                deltaZ1 = delta.z,
                clientDelay1 = 0,
                deltaX2 = 0,
                deltaZ2 = 0,
                clientDelay2 = ChargeMoveClientDelay,
                direction = dir.exactMoveAngle,
            )
        npc.faceAngle = dir.exactMoveAngle
    }

    private fun quantizeDir8(bullSw: CoordGrid, playerCoord: CoordGrid): Direction8 {
        val dx = playerCoord.x - (bullSw.x + 1)
        val dz = playerCoord.z - (bullSw.z + 1)
        if (dx == 0 && dz == 0) {
            return Direction8.North
        }
        val octant = (atan2(dz.toDouble(), dx.toDouble()) / (PI / 4)).roundToInt().mod(8)
        return Octants[octant]
    }

    private fun quantizeDir4(bullSw: CoordGrid, playerCoord: CoordGrid): Direction8 {
        val dx = playerCoord.x - (bullSw.x + 1)
        val dz = playerCoord.z - (bullSw.z + 1)
        return if (abs(dx) >= abs(dz)) {
            if (dx >= 0) Direction8.East else Direction8.West
        } else {
            if (dz >= 0) Direction8.North else Direction8.South
        }
    }

    private fun StandardNpcAccess.specialHit(target: Player) {
        val level = target.statBase(HitpointsStat)
        val percent = random.of(SpecialPercentMin, SpecialPercentMax)
        val damage = ((level * percent) / 100).coerceIn(1, SpecialMaxHit)
        target.queueHit(
            npc,
            SpecialHitDelay,
            HitType.Melee,
            damage,
            modifier = BullSpecialHitModifier,
        )
    }

    private suspend fun StandardNpcAccess.bullDies() {
        val fight = fights.values.firstOrNull { it.bull === npc }
        if (fight == null) {
            npcDeath.deathWithDrops(this)
            return
        }

        // Clear the committed special as a cancellation token and cancel its recovery queue on death.
        fight.pendingStompTiles = null
        fight.pendingChargeDir = null
        npc.clearQueue(IdesOfMilkRefs.QueueRecover)
        npc.ignoreCombatInteractions = false
        fight.stompsLeftInSet = 0
        fight.autosSinceSpecial = 0
        fight.autosBeforeNextSet = AutosBeforeFirstSet

        say("m-moo...")
        hideAllOps()
        noneMode()
        // Reset before the death sequence so the pending special pose cannot suppress it.
        resetAnim()
        anim(IdesOfMilkRefs.SeqBullDeath)
        delay(DeathHoldTicks - 1)

        val player = fight.player
        if (fight.postQuest) {
            bossFelled(fight)
            return
        }
        if (!player.isSlotAssigned) {
            despawnBull(fight)
            return
        }
        val launched = protectedAccess.launch(player) { bullFelled(fight) }
        if (!launched) {
            queueDeath()
        }
    }

    private fun StandardNpcAccess.bossFelled(fight: BullFight) {
        if (npc.isSlotAssigned) {
            npcDeath.spawnDrops(this)
        }
        // Finish all death work before deleting the NPC; deletion invalidates its protected access.
        val player = fight.player
        if (player.isSlotAssigned) {
            player.softTimer(IdesOfMilkRefs.TimerBossRespawn, BossRespawnTicks)
        }
        despawnBull(fight)
    }

    private fun respawnBoss(player: Player) {
        val fight = player.uuid?.let(fights::get) ?: return
        if (!fight.postQuest || fight.bull != null || fight.released) {
            return
        }
        val region = fight.region ?: return
        if (!regionRepo.isValid(region) || !region.containsCoord(player.coords)) {
            return
        }
        spawnBull(fight, fight.scene)
        musicPlayer.play(player, MusicRow.getRow(IdesOfMilkRefs.MusicBossMoosic))
        player.soundSynth(IdesOfMilkRefs.SynthBullSpawn)
    }

    private fun despawnBull(fight: BullFight) {
        fight.bull?.let(combatGate::remove)
        fight.bull?.takeIf { it.isSlotAssigned }?.let { npcRepo.del(it, Int.MAX_VALUE) }
        fight.bull = null
    }

    private suspend fun ProtectedAccess.bullFelled(fight: BullFight) {
        quest.setQuestStage(this, IdesOfMilkStage.BullSlain)
        mes(BrutusTagMessage)
        despawnBull(fight)
        mesbox(BrutusTagMessage)
        leaveFight()
    }

    private suspend fun ProtectedAccess.exitGate() {
        if (player.uuid?.let(fights::containsKey) == true) {
            leaveFight()
            return
        }
        defaultLeaveFlow()
    }

    private suspend fun ProtectedAccess.leaveFight() {
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FadeDuration,
        )
        minimapHideMap()
        delay(1)

        player.loginExitCoord = null
        telejump(IdesOfMilkCoords.FightExit)
        rebuildAppearance()
        resetAnim()
        clearHealthHud()
        showHealthHud()
        discardFight(player)
        delay(1)

        minimapReset()
        fadeOverlay(0, 0, 0, 255, FadeDuration)
    }

    private class BullFight(
        val player: Player,
        val region: Region?,
        val scene: SceneRegion,
        val postQuest: Boolean,
    ) {
        var bull: Npc? = null
        val decoratives = mutableListOf<Npc>()

        var autosSinceSpecial: Int = 0

        var autosBeforeNextSet: Int = AutosBeforeFirstSet

        var stompsLeftInSet: Int = 0

        var pendingStompTiles: List<CoordGrid>? = null

        var pendingChargeDir: Direction8? = null

        var released: Boolean = false
    }

    private fun reclaimAbandoned(player: Player) {
        val uuid = player.uuid ?: return
        val fight = fights[uuid] ?: return
        val region = fight.region
        if (region != null && region.containsCoord(player.coords)) {
            return
        }
        Camera.camReset(player)
        discardFight(player)
        player.loginExitCoord = null
    }

    private fun discardFight(player: Player) {
        val fight = player.uuid?.let(fights::remove) ?: return
        release(fight)
    }

    private fun release(fight: BullFight) {
        if (fight.released) {
            return
        }
        fight.released = true
        try {
            // Cancel respawn timers on teardown so a previous kill cannot respawn into a discarded fight.
            fight.player.clearSoftTimer(IdesOfMilkRefs.TimerBossRespawn)
            despawnBull(fight)
            fight.decoratives.removeAll { !it.isSlotAssigned }
            fight.decoratives.forEach { npcRepo.del(it, Int.MAX_VALUE) }
            fight.decoratives.clear()
        } finally {
            // Unprotect, not delete; reclaim only when the region is empty.
            fight.region?.let(regionRepo::unprotect)
        }
    }

    private val stompSpotanim by lazy {
        dev.openrune.types.aconverted.SpotanimType(IdesOfMilkRefs.SpotanimStomp.asRSCM())
    }

    private companion object {
        const val BullName = "Bull"

        const val QuestPromptTitle = "Release the bull?"

        const val PostQuestPromptTitle = "Release Brutus?"

        const val QuestEntryMessage = "The bull enters. The cows scatter."

        const val PostQuestEntryMessage = "Brutus enters. The cows scatter."

        const val BrutusTagMessage =
            "You notice a strange tag float to the ground as the bull falls. It reads 'Brutus'."

        const val BossRespawnTicks = 35

        const val FadeDuration = 50

        const val AttackRate = 5

        const val AutoHitDelay = 1

        const val SpecialHitDelay = 1

        const val SpecialAttackRate = 8

        const val SpecialResolveTicks = 4

        const val SpecialRecoverTicks = 6

        const val AutosBeforeFirstSet = 2

        const val AutosBetweenSets = 4

        const val StompsPerSet = 3

        const val StompAnimClientDelay = 30

        const val ChargeAnimClientDelay = 60

        const val ChargeMoveClientDelay = 60

        const val FaceProjection = 5

        const val ChargeTilesMin = 4

        const val ChargeTilesMax = 7

        const val BullSize = 3

        const val DeathHoldTicks = 5

        const val SpecialPercentMin = 30

        const val SpecialPercentMax = 40

        const val SpecialMaxHit = 19

        const val HitpointsStat = "stat.hitpoints"

        const val FightLifespanTicks = 3000

        const val WorldGateLeft = "loc.fencegate_l"

        const val WorldGateRight = "loc.fencegate_r"
    }
}

private fun CoordGrid.asRegionLocal(): RegionLocal =
    RegionLocal(level = level, regionZoneX = mx, regionZoneZ = mz, localX = lx, localZ = lz)

internal enum class Direction8(val dx: Int, val dz: Int, val exactMoveAngle: Int) {
    East(1, 0, 1536),
    NorthEast(1, 1, 1280),
    North(0, 1, 1024),
    NorthWest(-1, 1, 768),
    West(-1, 0, 512),
    SouthWest(-1, -1, 256),
    South(0, -1, 0),
    SouthEast(1, -1, 1792),
}

internal fun brutusChargeHits(start: CoordGrid, end: CoordGrid, size: Int, target: CoordGrid): Boolean =
    target.level == start.level &&
        target.x in min(start.x, end.x)..(max(start.x, end.x) + size - 1) &&
        target.z in min(start.z, end.z)..(max(start.z, end.z) + size - 1)

internal fun brutusChargePushDestination(
    start: CoordGrid,
    direction: Direction8,
    steps: StepValidator,
): CoordGrid {
    var destination = start
    repeat(3) {
        if (!steps.canTravel(
                level = destination.level,
                x = destination.x,
                z = destination.z,
                offsetX = direction.dx,
                offsetZ = direction.dz,
            )
        ) return destination
        destination = destination.translate(direction.dx, direction.dz)
    }
    return destination
}

private val Octants =
    arrayOf(
        Direction8.East,
        Direction8.NorthEast,
        Direction8.North,
        Direction8.NorthWest,
        Direction8.West,
        Direction8.SouthWest,
        Direction8.South,
        Direction8.SouthEast,
    )

// Specials bypass prayer but must still respect god mode.
private object BullSpecialHitModifier : PlayerHitModifier {
    override fun HitBuilder.modify(target: Player) {
        if (target.adminGodMode) {
            damage = 0
        }
    }
}
