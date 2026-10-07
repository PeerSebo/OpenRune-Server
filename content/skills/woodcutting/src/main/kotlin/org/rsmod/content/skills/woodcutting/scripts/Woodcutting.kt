package org.rsmod.content.skills.woodcutting.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.map.MapSingletons.collision
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.SequenceServerType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.config.constants
import org.rsmod.api.config.locParam
import org.rsmod.api.config.locXpParam
import org.rsmod.api.config.objParam
import org.rsmod.api.config.refs.params
import org.rsmod.api.controller.vars.intVarCon
import org.rsmod.api.player.events.skilling.SkillingProduct
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.skilling.SkillingAwardResult
import org.rsmod.api.player.skilling.awardSkillingProduct
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.script.onAiConTimer
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.api.script.onOpContentLoc3
import org.rsmod.api.script.onOpContentU
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.woodcutting.BeaverPet.rollBeaver
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasNestCapeBoost
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasStrungRabbitFoot
import org.rsmod.content.skills.woodcutting.configs.BirdNests
import org.rsmod.content.skills.woodcutting.configs.WoodcuttingParams
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Controller
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.firstStepDestination
import org.rsmod.game.type.getInvObj
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO: Implement remaining axe effects and charges.
class Woodcutting
@Inject
constructor(
    private val locRepo: LocRepository,
    private val conRepo: ControllerRepository,
    private val playerRepo: PlayerRepository,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val mapClock: MapClock,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpContentLoc1("content.tree") { attempt(it.loc, it.type) }
        onOpContentLoc3("content.tree") { cut(it.loc, it.type) }
        onOpContentU("content.tree", "content.woodcutting_axe") { attempt(it.loc, it.type) }
        onAiConTimer("controller.woodcutting_tree_duration") { controller.treeDespawnTick() }
    }

    private fun ProtectedAccess.rollBirdNest(log: String) {
        if (!BirdNests.canDropFrom(log)) {
            return
        }
        val nest =
            BirdNests.roll(random, player.hasNestCapeBoost(), player.hasStrungRabbitFoot()) ?: return
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        val dest =
            collision.firstStepDestination(
                player.coords,
                listOf(Direction.West, Direction.East, Direction.South, Direction.North),
            ) ?: player.coords
        objRepo.add(nest, dest, duration, receiver = player)
        mes("<col=ff0000>${BirdNests.MESSAGE}</col>")
        soundSynth("synth.WOODCUTTING_BIRDSNEST")
    }

    internal fun ProtectedAccess.attempt(tree: BoundLocInfo, type: ObjectServerType) {
        if (player.woodcuttingLvl < type.treeLevelReq) {
            resetAnim()
            mes("You need a Woodcutting level of ${type.treeLevelReq} to chop down this tree.")
            return
        }

        if (inv.isFull()) {
            resetAnim()
            val product = type.treeLogs
            mes("Your inventory is too full to hold any more ${product.name.lowercase()}.")
            soundSynth("synth.PILLORY_WRONG")
            return
        }

        val axe = findAxe(player, type)
        if (axe == null) {
            resetAnim()
            mesAxeMissing()
            return
        }

        // Only gathering-owned delay may reuse warm-up; unrelated action delays must not skip it.
        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            // Never shorten an existing delay; doing so would bypass combat attack timing.
            actionDelay = maxOf(actionDelay, mapClock + 3)
            skillAnimDelay = mapClock + 3
            opLoc1(tree)
        } else {
            anim(axeAnim(axe))
            spam("You swing your axe at the tree.")
            cut(tree, type)
        }
    }

    internal fun ProtectedAccess.cut(tree: BoundLocInfo, type: ObjectServerType) {
        if (locRepo.findExact(tree.coords, tree.shape)?.entity != tree.entity) {
            resetAnim()
            return
        }

        val axe = findAxe(player, type)
        if (axe == null) {
            resetAnim()
            mesAxeMissing()
            return
        }

        if (player.woodcuttingLvl < type.treeLevelReq) {
            resetAnim()
            mes("You need a Woodcutting level of ${type.treeLevelReq} to chop down this tree.")
            return
        }

        if (inv.isFull()) {
            resetAnim()
            val product = type.treeLogs
            mes("Your inventory is too full to hold any more ${product.name.lowercase()}.")
            soundSynth("synth.PILLORY_WRONG")
            return
        }

        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + 4
            anim(axeAnim(axe))
        }

        var cutLogs = false

        if (actionDelay < mapClock) {
            actionDelay = mapClock + 3
        } else if (actionDelay == mapClock) {
            val (low, high) = cutSuccessRates(type, axe)
            cutLogs = statRandom("stat.woodcutting", low, high, invisibleLvls)
        }

        if (type.hasDespawnTimer) {
            treeSwingDespawnTick(tree, type)
        }

        if (cutLogs) {
            val logs = type.treeLogs
            val log = RSCM.getReverseMapping(RSCMType.OBJ, logs.id)
            val xp = type.treeXp * xpMods.get(player, "stat.woodcutting")
            val product =
                SkillingProduct(
                    player = player,
                    skill = "stat.woodcutting",
                    item = log,
                    count = 1,
                    experience = xp,
                    grantsExperience = true,
                    source = SkillingProductSource.Woodcutting(tree, logs),
                )
            when (awardSkillingProduct(product)) {
                SkillingAwardResult.InventoryFull -> {
                    resetAnim()
                    mes("Your inventory is too full to hold any more ${logs.name.lowercase()}.")
                    soundSynth("synth.PILLORY_WRONG")
                    return
                }
                SkillingAwardResult.Cancelled -> {
                    resetAnim()
                    return
                }
                SkillingAwardResult.Success -> Unit
            }

            spam("You get some ${logs.name.lowercase()}.")
            rollBirdNest(log)
            rollBeaver(log, objRepo)

            val despawn =
                product.depletes &&
                    if (type.hasDespawnTimer) {
                        isTreeDespawnRequired(tree)
                    } else {
                        random.of(1, 255) > type.treeDepleteChance
                    }
            if (despawn) {
                val respawnTime = type.resolveRespawnTime(random)
                locRepo.change(tree, type.treeStump, respawnTime)
                conRepo.findExact(tree.coords, "controller.woodcutting_tree_duration")?.let {
                    if (it.treeLocId == tree.id) conRepo.del(it)
                }
                resetAnim()
                soundSynth("synth.TREE_FALL")
                sendLocalOverlayLoc(tree, type, respawnTime)
                return
            }
        }

        opLoc3(tree)
    }

    internal fun Controller.treeDespawnTick() {
        val type = ServerCacheManager.getObject(treeLocId)
        val tree = type?.let { locRepo.findExact(coords, it) }
        if (tree == null) {
            conRepo.del(this)
            return
        }

        if (treeLastCut == mapClock.cycle - 1) {
            treeActivelyCutTicks++
        } else {
            treeActivelyCutTicks--
        }

        if (treeActivelyCutTicks <= 0) {
            conRepo.del(this)
            return
        }

        aiTimer(1)

        resetDuration()
    }

    internal fun treeSwingDespawnTick(tree: BoundLocInfo, type: ObjectServerType) {
        val controller = conRepo.findExact(tree.coords, "controller.woodcutting_tree_duration")
        if (controller != null) {
            if (controller.treeLocId == tree.id) {
                controller.treeLastCut = mapClock.cycle
                return
            }
            conRepo.del(controller)
        }

        val spawn = Controller("controller.woodcutting_tree_duration", tree.coords)
        conRepo.add(spawn, type.treeDespawnTime)

        spawn.treeLocId = tree.id
        spawn.treeLastCut = mapClock.cycle
        spawn.treeActivelyCutTicks = 0
        spawn.aiTimer(1)
    }

    internal fun isTreeDespawnRequired(tree: BoundLocInfo): Boolean {
        val controller = conRepo.findExact(tree.coords, "controller.woodcutting_tree_duration")
        return controller != null &&
            controller.treeLocId == tree.id &&
            controller.treeActivelyCutTicks >= controller.durationStart
    }

    private fun sendLocalOverlayLoc(tree: BoundLocInfo, type: ObjectServerType, respawnTime: Int) {
        val players = playerRepo.findAll(ZoneKey.from(tree.coords), zoneRadius = 3)
        for (player in players) {
            ClientScripts.addOverlayTimerLoc(
                player = player,
                coords = tree.coords,
                loc = type,
                shape = tree.shape,
                timer = Constants.overlay_timer_woodcutting,
                ticks = respawnTime,
                colour = 16765184,
            )
        }
    }

    private fun ProtectedAccess.axeAnim(axe: InvObj): String =
        RSCM.getReverseMapping(RSCMType.SEQ, getInvObj(axe).axeWoodcuttingAnim.id)

    private fun ProtectedAccess.mesAxeMissing() {
        mes("You need an axe to chop down this tree.")
        when {
            player.hasBlockedInfernalAxe() ->
                mes("You need a Firemaking level of 85 to use the infernal axe.")
            player.hasBlockedCrystalAxe() ->
                mes("You need to complete Song of the Elves to use this axe.")
            else -> mes("You do not have an axe which you have the woodcutting level to use.")
        }
    }

    companion object {
        private const val INFERNAL_FIREMAKING_REQ = 85
        private const val SONG_OF_THE_ELVES = "quest_songoftheelves"

        var Controller.treeActivelyCutTicks: Int by intVarCon("varcon.woodcutting_tree_cut_ticks")
        var Controller.treeLastCut: Int by intVarCon("varcon.woodcutting_tree_last_cut")
        var Controller.treeLocId: Int by intVarCon("varcon.woodcutting_tree_loc")

        val ItemServerType.axeWoodcuttingReq: Int by objParam(params.levelrequire)
        val ItemServerType.axeWoodcuttingAnim: SequenceServerType by objParam(params.skill_anim)

        val ObjectServerType.treeLevelReq: Int by locParam(params.levelrequire)
        val ObjectServerType.treeLogs: ItemServerType by locParam(params.skill_productitem)
        val ObjectServerType.treeXp: Double by locXpParam(params.skill_xp)
        val ObjectServerType.treeStump: ObjectServerType by locParam(params.next_loc_stage)
        val ObjectServerType.treeDespawnTime: Int by locParam(params.despawn_time)
        val ObjectServerType.treeDepleteChance: Int by locParam(params.deplete_chance)
        val ObjectServerType.treeRespawnTime: Int by locParam(params.respawn_time)
        val ObjectServerType.treeRespawnTimeLow: Int by locParam(params.respawn_time_low)
        val ObjectServerType.treeRespawnTimeHigh: Int by locParam(params.respawn_time_high)

        private val ObjectServerType.hasDespawnTimer: Boolean
            get() = hasParam(params.despawn_time)

        fun findAxe(player: Player, tree: ObjectServerType): InvObj? {
            val worn = player.wornAxe()
            val carried = player.carriedAxes()
            val candidates = buildList {
                if (worn != null) {
                    add(worn)
                }
                addAll(carried)
            }
            return candidates.maxWithOrNull(axeComparator(tree))
        }

        private fun axeComparator(tree: ObjectServerType): Comparator<InvObj> =
            Comparator { left, right ->
                axeAverageRate(tree, left).compareTo(axeAverageRate(tree, right))
            }

        private fun axeAverageRate(tree: ObjectServerType, axe: InvObj): Int {
            val (low, high) = cutSuccessRates(tree, axe)
            return (low + high) / 2
        }

        private fun Player.wornAxe(): InvObj? {
            val righthand = righthand ?: return null
            return righthand.takeIf { getInvObj(it).isUsableAxe(this) }
        }

        private fun Player.carriedAxes(): List<InvObj> =
            inv.filterNotNull { getInvObj(it).isUsableAxe(this) }

        private fun ItemServerType.isUsableAxe(player: Player): Boolean {
            if (
                !isContentType("content.woodcutting_axe") ||
                    player.woodcuttingLvl < axeWoodcuttingReq
            ) {
                return false
            }
            return when (internalName) {
                "obj.infernal_axe" -> player.firemakingLvl >= INFERNAL_FIREMAKING_REQ
                "obj.crystal_axe",
                "obj.crystal_axe_inactive",
                "obj.crystal_axe_2h",
                "obj.crystal_axe_2h_inactive" ->
                    QuestRequirements.hasCompleted(player, SONG_OF_THE_ELVES)
                else -> true
            }
        }

        private fun Player.hasBlockedInfernalAxe(): Boolean =
            ownsAxe("obj.infernal_axe") && firemakingLvl < INFERNAL_FIREMAKING_REQ

        private fun Player.hasBlockedCrystalAxe(): Boolean =
            (ownsAxe("obj.crystal_axe") ||
                ownsAxe("obj.crystal_axe_inactive") ||
                ownsAxe("obj.crystal_axe_2h") ||
                ownsAxe("obj.crystal_axe_2h_inactive")) &&
                !QuestRequirements.hasCompleted(this, SONG_OF_THE_ELVES)

        private fun Player.ownsAxe(name: String): Boolean =
            (righthand != null && getInvObj(righthand!!).internalName == name) ||
                inv.any { it != null && getInvObj(it).internalName == name }

        private fun ObjectServerType.resolveRespawnTime(random: GameRandom): Int {
            val fixed = treeRespawnTime
            if (fixed > 0) {
                return fixed
            }
            return random.of(treeRespawnTimeLow, treeRespawnTimeHigh)
        }

        fun cutSuccessRates(treeType: ObjectServerType, axe: InvObj): Pair<Int, Int> {
            val axes = treeType.param(WoodcuttingParams.success_rates)
            val rates = axes.find { it.key.id == axe.id }?.value ?: error("Unable to get axe rates")
            val low = rates shr 16
            val high = rates and 0xFFFF
            return low to high
        }
    }
}
