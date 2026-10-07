package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.BirdhouseParams
import org.rsmod.content.skills.hunter.configs.BirdhouseSeeds
import org.rsmod.content.skills.hunter.configs.BirdhouseSpot
import org.rsmod.content.skills.hunter.configs.BirdhouseTier
import org.rsmod.content.skills.hunter.configs.HunterMessages
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal data class BirdhouseBuildTask(val spot: BirdhouseSpot, val tier: BirdhouseTier, val origin: CoordGrid, val craft: Boolean = false)

internal data class BirdhouseSeedTask(val spot: BirdhouseSpot, val seed: String, val count: Int, val origin: CoordGrid, val state: Int, val filled: Int = 0)

internal enum class BirdhouseAction {
    GIVE_UP,

    EMPTY,

    RESET,
}

internal data class BirdhouseDismantleTask(val spot: BirdhouseSpot, val action: BirdhouseAction, val origin: CoordGrid, val state: Int, val seededMinute: Int)

class BirdhouseScript
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
    private val players: PlayerList,
    private val marketPrices: MarketPrices,
) : PluginScript() {
    private var lastRefreshMinute = Int.MIN_VALUE

    // Native spot varps drive multilocs; custom timestamps track offline growth.
    private var Player.stateA by intVarp("varp.birdhouse_transmit_a")
    private var Player.stateB by intVarp("varp.birdhouse_transmit_b")
    private var Player.stateC by intVarp("varp.birdhouse_transmit_c")
    private var Player.stateD by intVarp("varp.birdhouse_transmit_d")

    private var Player.seededA by intVarp("varp.hunter_birdhouse_seeded_1")
    private var Player.seededB by intVarp("varp.hunter_birdhouse_seeded_2")
    private var Player.seededC by intVarp("varp.hunter_birdhouse_seeded_3")
    private var Player.seededD by intVarp("varp.hunter_birdhouse_seeded_4")

    private val spotsByBaseLoc: Map<Int, BirdhouseSpot> by lazy {
        BirdhouseSpot.entries.associateBy { it.baseLoc.asRSCM(RSCMType.LOC) }
    }

    private val houseObjIds: Map<Int, BirdhouseTier> by lazy {
        BirdhouseTier.entries.associateBy { it.houseObj.asRSCM(RSCMType.OBJ) }
    }

    private val logObjIds: Map<Int, BirdhouseTier> by lazy {
        BirdhouseTier.entries.associateBy { it.logObj.asRSCM(RSCMType.OBJ) }
    }

    private val seedObjIds: Map<Int, String> by lazy {
        BirdhouseSeeds.ALL.associateBy { it.asRSCM(RSCMType.OBJ) }
    }

    override fun ScriptContext.startup() {
        onPlayerLogin { refreshBirdhouses(player) }
        onEvent<GameLifecycle.LateCycle> {
            val minute = currentMinute()
            if (minute != lastRefreshMinute) {
                lastRefreshMinute = minute
                players.forEach { refreshBirdhouses(it, minute) }
            }
        }
        onOpLoc1(BirdhouseParams.NOT_BUILT_LOC) { startBuild(it.loc) }
        onOpLocU(BirdhouseParams.NOT_BUILT_LOC) { useOnEmptySpot(it.loc, it.objType) }

        for (tier in BirdhouseTier.entries) {
            onOpLoc2(tier.builtLoc) { startSeeding(it.loc, seed = null) }
            onOpLocU(tier.builtLoc) { useOnBirdhouse(it.loc, it.objType, seeded = false) }

            onOpLoc2(tier.seededLoc) { refreshBirdhouses(player); mes(HunterMessages.BIRDHOUSE_ALREADY_SEEDED) }
            onOpLocU(tier.seededLoc) { useOnBirdhouse(it.loc, it.objType, seeded = true) }
            onOpLoc3(tier.seededLoc) { startDismantle(it.loc) }

            onOpLoc1(tier.finishedLoc) { startEmpty(it.loc) }
            onOpLoc2(tier.finishedLoc) { mes(HunterMessages.BIRDHOUSE_ALREADY_SEEDED) }
            onOpLocU(tier.finishedLoc) { useOnBirdhouse(it.loc, it.objType, seeded = true) }
            onOpLoc3(tier.finishedLoc) { startEmpty(it.loc) }
            onOpLoc4(tier.finishedLoc) { startReset(it.loc) }
        }

        onPlayerQueueWithArgs<BirdhouseBuildTask>(BirdhouseParams.BUILD_QUEUE) {
            finishBuild(it.args)
        }
        onPlayerQueueWithArgs<BirdhouseSeedTask>(BirdhouseParams.SEED_QUEUE) {
            finishSeeding(it.args)
        }
        onPlayerQueueWithArgs<BirdhouseDismantleTask>(BirdhouseParams.DISMANTLE_QUEUE) {
            finishDismantle(it.args)
        }
    }

    private fun ProtectedAccess.startBuild(loc: BoundLocInfo) {
        val spot = spotOf(loc) ?: return
        if (state(spot) != BirdhouseTier.EMPTY_VAR_VALUE) {
            mes(HunterMessages.BIRDHOUSE_OCCUPIED)
            return
        }

        val carried = BirdhouseTier.entries.filter { inv.count(it.houseObj) > 0 }
        if (carried.isEmpty()) {
            val craftable = craftableTier(reuseClockwork = false)
            if (craftable == null) mes("You need a suitable bird house, or logs, clockwork, a hammer and a chisel to build one.")
            else beginBuild(loc, spot, craftable, craft = true)
            return
        }
        val usable = carried.filter { player.hunterLvl >= it.levelReq }
        if (usable.isEmpty()) {
            val craftable = craftableTier(reuseClockwork = false)
            if (craftable != null) {
                beginBuild(loc, spot, craftable, craft = true)
                return
            }
            val cheapest = carried.minBy { it.levelReq }
            mes(HunterMessages.levelRequirement(cheapest.levelReq, HunterMessages.BUILD_BIRD_HOUSE))
            return
        }
        beginBuild(loc, spot, usable.maxBy { it.levelReq })
    }

    private fun ProtectedAccess.useOnEmptySpot(loc: BoundLocInfo, objType: ItemServerType) {
        val spot = spotOf(loc) ?: return
        val craft = objType.id in logObjIds
        val tier = houseObjIds[objType.id] ?: logObjIds[objType.id]
        if (tier == null) {
            // Registered handlers consume unmatched events; provide fallback feedback explicitly.
            mes(constants.dm_default)
            return
        }
        if (state(spot) != BirdhouseTier.EMPTY_VAR_VALUE) {
            mes(HunterMessages.BIRDHOUSE_OCCUPIED)
            return
        }
        if (player.hunterLvl < tier.levelReq) {
            mes(HunterMessages.levelRequirement(tier.levelReq, HunterMessages.BUILD_BIRD_HOUSE))
            return
        }
        if (craft && (!hasCraftingTools() || player.craftingLvl < tier.craftingLevel || inv.count(BirdhouseParams.CLOCKWORK) < 1)) {
            mes("You need level ${tier.craftingLevel} Crafting, clockwork, a hammer and a chisel to make this bird house.")
            return
        }
        beginBuild(loc, spot, tier, craft)
    }

    private fun ProtectedAccess.beginBuild(
        loc: BoundLocInfo,
        spot: BirdhouseSpot,
        tier: BirdhouseTier,
        craft: Boolean = false,
    ) {
        clearBirdhouseQueues()
        faceLoc(loc)
        anim(BirdhouseParams.BUILD_ANIM)
        queue(
            BirdhouseParams.BUILD_QUEUE,
            BirdhouseParams.BUILD_TICKS,
            BirdhouseBuildTask(spot, tier, coords, craft),
        )
    }

    internal fun ProtectedAccess.finishBuild(task: BirdhouseBuildTask) {
        if (!canUseBirdhouses()) return
        // Sequence 5212 loops beyond building cadence; reset it after completion.
        resetAnim()

        // Recheck state after the delay; another build may already have completed.
        if (coords != task.origin || state(task.spot) != BirdhouseTier.EMPTY_VAR_VALUE) {
            return
        }
        if (player.hunterLvl < task.tier.levelReq) {
            mes(HunterMessages.levelRequirement(task.tier.levelReq, HunterMessages.BUILD_BIRD_HOUSE))
            return
        }
        if (!(if (task.craft) craftHouse(task.tier, reuseClockwork = false) else invDel(inv, task.tier.houseObj, count = 1).success)) {
            mes(HunterMessages.BIRDHOUSE_NO_HOUSE)
            return
        }
        setState(task.spot, task.tier.builtVarValue)
        setSeededMinute(task.spot, 0)
        mes(HunterMessages.BIRDHOUSE_BUILT)
    }

    private suspend fun ProtectedAccess.startSeeding(loc: BoundLocInfo, seed: String?) {
        val spot = spotOf(loc) ?: return
        val varValue = refreshState(spot)
        if (!BirdhouseTier.isBuiltEmpty(varValue)) {
            mes(
                if (BirdhouseTier.isSeeded(varValue) || BirdhouseTier.isFinished(varValue)) {
                    HunterMessages.BIRDHOUSE_ALREADY_SEEDED
                } else {
                    constants.dm_default
                }
            )
            return
        }

        val chosen = seed ?: pickSeed()
        if (chosen == null) {
            mes(HunterMessages.BIRDHOUSE_NEED_SEEDS)
            return
        }
        val required = BirdhouseSeeds.REQUIRED_COUNT[chosen]
        if (required == null) {
            mes(HunterMessages.BIRDHOUSE_BAD_SEED)
            return
        }
        val filled = seedFill(spot)
        val needed = (10 - filled + 10 / required - 1) / (10 / required)
        val count = minOf(inv.count(chosen), needed)
        if (count <= 0) {
            mes(HunterMessages.BIRDHOUSE_NEED_SEEDS)
            return
        }

        val origin = coords
        if (shouldWarnAboutSeed(player, chosen) && !choice2("Use these seeds.", true, "Keep my seeds.", false,
                title = "These seeds exceed your birdhouse warning value. Use them?")) return
        if (coords != origin || state(spot) != varValue || seedFill(spot) != filled) return

        faceLoc(loc)
        clearBirdhouseQueues()
        anim(BirdhouseParams.SEED_ANIM)
        queue(
            BirdhouseParams.SEED_QUEUE,
            BirdhouseParams.SEED_TICKS,
            BirdhouseSeedTask(spot, chosen, count, coords, varValue, filled),
        )
    }

    private fun ProtectedAccess.pickSeed(): String? =
        BirdhouseSeeds.ALL.firstOrNull { seed ->
            inv.count(seed) > 0
        }

    private suspend fun ProtectedAccess.useOnBirdhouse(
        loc: BoundLocInfo,
        objType: ItemServerType,
        seeded: Boolean,
    ) {
        val seed = seedObjIds[objType.id]
        if (seed == null) {
            mes(constants.dm_default)
            return
        }
        if (seeded) {
            mes(HunterMessages.BIRDHOUSE_ALREADY_SEEDED)
            return
        }
        startSeeding(loc, seed)
    }

    internal fun ProtectedAccess.finishSeeding(task: BirdhouseSeedTask) {
        resetAnim()

        val varValue = refreshState(task.spot)
        if (coords != task.origin || varValue != task.state || !BirdhouseTier.isBuiltEmpty(varValue) || seedFill(task.spot) != task.filled) {
            return
        }
        val tier = BirdhouseTier.ofVarValue(varValue) ?: return
        val required = BirdhouseSeeds.REQUIRED_COUNT[task.seed] ?: return
        val units = 10 / required
        val needed = (10 - task.filled + units - 1) / units
        if (task.count !in 1..needed || !invDel(inv, task.seed, count = task.count).success) {
            mes(HunterMessages.BIRDHOUSE_NEED_SEEDS)
            return
        }
        val filled = (task.filled + task.count * units).coerceAtMost(10)
        if (filled == 10) {
            setState(task.spot, tier.seededVarValue)
            setSeededMinute(task.spot, currentMinute())
            mes(HunterMessages.BIRDHOUSE_SEEDED)
        } else {
            // Negative timestamps encode partial bait counts; positive values encode seeded epoch minutes.
            setSeededMinute(task.spot, -filled)
            mes("You add ${task.count} seeds to the bird house. It is now ${filled * 10}% full of seeds.")
        }
    }

    internal fun shouldWarnAboutSeed(player: Player, seed: String): Boolean {
        if (player.vars["varbit.settings_birdhouse_seed_warning_enabled"] == 0) return false
        val type = ServerCacheManager.getItem(seed.asRSCM(RSCMType.OBJ)) ?: return false
        val price = marketPrices[type] ?: return false
        return price > player.vars["varp.settings_birdhouse_seed_warning_value"].coerceAtLeast(0)
    }

    private fun ProtectedAccess.seedFill(spot: BirdhouseSpot): Int = (-seededMinute(spot)).coerceIn(0, 9)

    private suspend fun ProtectedAccess.startDismantle(loc: BoundLocInfo) {
        val spot = spotOf(loc) ?: return
        val value = refreshState(spot)
        if (BirdhouseTier.isFinished(value)) {
            startDismantleAction(loc, spot, BirdhouseAction.EMPTY)
            return
        }
        if (!BirdhouseTier.isSeeded(value)) {
            mes(constants.dm_default)
            return
        }
        val origin = coords
        val seeded = seededMinute(spot)
        if (!choice2("Dismantle the bird house.", true, "Leave it alone.", false,
                title = "The birds are not ready. Dismantling now loses the house, seeds and loot.")) return
        if (coords != origin || seededMinute(spot) != seeded || state(spot) != value) return
        startDismantleAction(loc, spot, BirdhouseAction.GIVE_UP)
    }

    private fun ProtectedAccess.startEmpty(loc: BoundLocInfo) {
        val spot = spotOf(loc) ?: return
        if (!BirdhouseTier.isFinished(refreshState(spot))) {
            mes(constants.dm_default)
            return
        }
        startDismantleAction(loc, spot, BirdhouseAction.EMPTY)
    }

    private fun ProtectedAccess.startReset(loc: BoundLocInfo) {
        val spot = spotOf(loc) ?: return
        if (!BirdhouseTier.isFinished(refreshState(spot))) {
            mes(constants.dm_default)
            return
        }
        startDismantleAction(loc, spot, BirdhouseAction.RESET)
    }

    private fun ProtectedAccess.startDismantleAction(
        loc: BoundLocInfo,
        spot: BirdhouseSpot,
        action: BirdhouseAction,
    ) {
        clearBirdhouseQueues()
        faceLoc(loc)
        anim(BirdhouseParams.DISMANTLE_ANIM)
        val ticks =
            if (action == BirdhouseAction.RESET) {
                BirdhouseParams.RESET_TICKS
            } else {
                BirdhouseParams.DISMANTLE_TICKS
            }
        queue(BirdhouseParams.DISMANTLE_QUEUE, ticks, BirdhouseDismantleTask(spot, action, coords, state(spot), seededMinute(spot)))
    }

    internal fun ProtectedAccess.finishDismantle(task: BirdhouseDismantleTask) {
        resetAnim()

        // Revalidate after suspension before clearing or awarding the house.
        val varValue = refreshState(task.spot)
        val tier = BirdhouseTier.ofVarValue(varValue)
        val sameState = varValue == task.state || (BirdhouseTier.isSeeded(task.state) && tier?.finishedVarValue == varValue && BirdhouseTier.ofVarValue(task.state) == tier)
        if (coords != task.origin || !sameState || seededMinute(task.spot) != task.seededMinute) return
        when (task.action) {
            BirdhouseAction.GIVE_UP -> {
                if (tier == null || (!BirdhouseTier.isSeeded(varValue) && !BirdhouseTier.isFinished(varValue))) {
                    return
                }
                // Clear before payout to prevent duplicate rewards.
                setState(task.spot, BirdhouseTier.EMPTY_VAR_VALUE)
                setSeededMinute(task.spot, 0)
                invAddOrDrop(objRepo, BirdhouseParams.CLOCKWORK, count = 1)
                if (BirdhouseTier.isFinished(varValue)) {
                    payoutBirdhouseLoot(tier)
                    mes(HunterMessages.BIRDHOUSE_EMPTIED)
                } else mes(HunterMessages.BIRDHOUSE_DISMANTLED_EARLY)
            }
            BirdhouseAction.EMPTY -> {
                if (tier == null || !BirdhouseTier.isFinished(varValue)) {
                    return
                }
                setState(task.spot, BirdhouseTier.EMPTY_VAR_VALUE)
                setSeededMinute(task.spot, 0)
                invAddOrDrop(objRepo, BirdhouseParams.CLOCKWORK, count = 1)
                payoutBirdhouseLoot(tier)
                mes(HunterMessages.BIRDHOUSE_EMPTIED)
            }
            BirdhouseAction.RESET -> {
                if (tier == null || !BirdhouseTier.isFinished(varValue)) {
                    return
                }
                val premade = BirdhouseTier.entries.lastOrNull { player.hunterLvl >= it.levelReq && inv.count(it.houseObj) > 0 }
                val crafted = if (premade == null) craftableTier(reuseClockwork = true) else null
                val replacement = when {
                    premade != null && invDel(inv, premade.houseObj, 1).success -> premade
                    crafted != null && craftHouse(crafted, reuseClockwork = true) -> crafted
                    else -> null
                }
                setState(task.spot, replacement?.builtVarValue ?: BirdhouseTier.EMPTY_VAR_VALUE)
                setSeededMinute(task.spot, 0)
                if (replacement == null || premade != null) invAddOrDrop(objRepo, BirdhouseParams.CLOCKWORK, count = 1)
                payoutBirdhouseLoot(tier)
                mes(if (replacement != null) "You empty and rebuild the bird house. It needs seeds." else HunterMessages.BIRDHOUSE_EMPTIED)
            }
        }
    }

    private fun ProtectedAccess.payoutBirdhouseLoot(tier: BirdhouseTier) {
        objRepo.add(
            BirdhouseParams.RAW_BIRD_MEAT,
            coords,
            player.lootDropDuration ?: constants.lootdrop_duration,
            receiver = player,
            count = BirdhouseParams.RAW_BIRD_MEAT_COUNT,
        )
        invAddOrDrop(objRepo, BirdhouseParams.FEATHER, count = BirdhouseParams.featherCount(random))

        if (
            statRandom(
                HunterParams.HUNTER_STAT,
                BirdhouseParams.SEED_NEST_ROLL_LOW,
                BirdhouseParams.SEED_NEST_ROLL_HIGH,
                invisibleBoost = 0,
            )
        ) {
            invAddOrDrop(objRepo, BirdhouseParams.SEED_NEST, count = 1)
        }

        val hasRabbitFoot = worn.count(BirdhouseParams.STRUNG_RABBIT_FOOT) > 0
        val publishedChance = BirdhouseParams.publishedNestChance(tier, player.hunterLvl)
        var cluesAwarded = false
        repeat(BirdhouseParams.NEST_TABLE_ROLLS) {
            val gotNest = random.randomDouble() < publishedChance
            if (gotNest) {
                val clues = if (cluesAwarded) emptyList() else BirdhouseClues.roll(player, random)
                if (clues.isEmpty()) {
                    invAddOrDrop(objRepo, rollNestTableItem(hasRabbitFoot), count = 1)
                } else {
                    cluesAwarded = true
                    for (clue in clues) with(BirdhouseClues) { awardClue(objRepo, clue) }
                }
            }
        }

        statAdvance(
            HunterParams.HUNTER_STAT,
            tier.xp * xpMods.get(player, HunterParams.HUNTER_STAT),
        )
    }

    private fun rollNestTableItem(hasRabbitFoot: Boolean): String {
        val denominator =
            if (hasRabbitFoot) {
                BirdhouseParams.NEST_TABLE_DENOMINATOR_WITH_FOOT
            } else {
                BirdhouseParams.NEST_TABLE_DENOMINATOR_BASE
            }
        var roll = random.of(1, denominator)
        if (roll <= BirdhouseParams.NEST_TABLE_RING_WEIGHT) {
            return BirdhouseParams.RING_NEST
        }
        roll -= BirdhouseParams.NEST_TABLE_RING_WEIGHT
        val eggSlots = BirdhouseParams.EGG_NESTS.size * BirdhouseParams.NEST_TABLE_EGG_WEIGHT_EACH
        if (roll <= eggSlots) {
            val index = (roll - 1) / BirdhouseParams.NEST_TABLE_EGG_WEIGHT_EACH
            return BirdhouseParams.EGG_NESTS[index]
        }
        return BirdhouseParams.EMPTY_NEST
    }

    private fun ProtectedAccess.clearBirdhouseQueues() {
        clearQueue(BirdhouseParams.BUILD_QUEUE)
        clearQueue(BirdhouseParams.SEED_QUEUE)
        clearQueue(BirdhouseParams.DISMANTLE_QUEUE)
    }

    private fun ProtectedAccess.hasCraftingTools(): Boolean = inv.count("obj.chisel") > 0 &&
        listOf("obj.hammer", "obj.imcando_hammer", "obj.imcando_hammer_offhand").any { inv.count(it) > 0 || worn.count(it) > 0 }

    private fun ProtectedAccess.craftableTier(reuseClockwork: Boolean): BirdhouseTier? {
        if (!hasCraftingTools() || (!reuseClockwork && inv.count(BirdhouseParams.CLOCKWORK) == 0)) return null
        return BirdhouseTier.entries.lastOrNull { player.hunterLvl >= it.levelReq && player.craftingLvl >= it.craftingLevel && inv.count(it.logObj) > 0 }
    }

    private fun ProtectedAccess.craftHouse(tier: BirdhouseTier, reuseClockwork: Boolean): Boolean {
        if (!hasCraftingTools() || player.craftingLvl < tier.craftingLevel || player.hunterLvl < tier.levelReq) return false
        val consumed = if (reuseClockwork) invDel(inv, tier.logObj, 1).success else invDel(inv, tier.logObj, 1, BirdhouseParams.CLOCKWORK, 1).success
        if (consumed) statAdvance("stat.crafting", tier.craftingXp * xpMods.get(player, "stat.crafting"))
        return consumed
    }

    internal fun refreshBirdhouses(player: Player, minute: Int = currentMinute()) {
        for (spot in BirdhouseSpot.entries) {
            val value = player.vars[spot.stateVarp]
            val tier = BirdhouseTier.ofVarValue(value) ?: continue
            if (BirdhouseTier.isSeeded(value) && isReady(player.vars[spot.seededMinuteVarp], minute)) {
                VarPlayerIntMapSetter.set(player, spot.stateVarp, tier.finishedVarValue)
            }
        }
    }

    internal fun isReady(seeded: Int, minute: Int): Boolean = seeded > 0 && minute.toLong() - seeded >= BirdhouseParams.FILL_MINUTES

    private fun ProtectedAccess.spotOf(loc: BoundLocInfo): BirdhouseSpot? =
        if (canUseBirdhouses()) spotsByBaseLoc[loc.id] else null

    private fun ProtectedAccess.canUseBirdhouses(): Boolean {
        if (org.rsmod.content.quest.manager.QuestRequirements.hasCompleted(player, "quest_bonevoyage")) return true
        mes("You need to complete Bone Voyage before using bird houses on Fossil Island.")
        return false
    }

    private fun ProtectedAccess.state(spot: BirdhouseSpot): Int =
        when (spot) {
            BirdhouseSpot.ONE -> player.stateA
            BirdhouseSpot.TWO -> player.stateB
            BirdhouseSpot.THREE -> player.stateC
            BirdhouseSpot.FOUR -> player.stateD
        }

    private fun ProtectedAccess.setState(spot: BirdhouseSpot, value: Int) {
        when (spot) {
            BirdhouseSpot.ONE -> player.stateA = value
            BirdhouseSpot.TWO -> player.stateB = value
            BirdhouseSpot.THREE -> player.stateC = value
            BirdhouseSpot.FOUR -> player.stateD = value
        }
    }

    private fun ProtectedAccess.refreshState(spot: BirdhouseSpot): Int {
        val varValue = state(spot)
        if (!BirdhouseTier.isSeeded(varValue) || !isReady(spot)) {
            return varValue
        }
        val tier = BirdhouseTier.ofVarValue(varValue) ?: return varValue
        setState(spot, tier.finishedVarValue)
        return tier.finishedVarValue
    }

    private fun ProtectedAccess.seededMinute(spot: BirdhouseSpot): Int =
        when (spot) {
            BirdhouseSpot.ONE -> player.seededA
            BirdhouseSpot.TWO -> player.seededB
            BirdhouseSpot.THREE -> player.seededC
            BirdhouseSpot.FOUR -> player.seededD
        }

    private fun ProtectedAccess.setSeededMinute(spot: BirdhouseSpot, value: Int) {
        when (spot) {
            BirdhouseSpot.ONE -> player.seededA = value
            BirdhouseSpot.TWO -> player.seededB = value
            BirdhouseSpot.THREE -> player.seededC = value
            BirdhouseSpot.FOUR -> player.seededD = value
        }
    }

    private fun ProtectedAccess.isReady(spot: BirdhouseSpot): Boolean {
        val seeded = seededMinute(spot)
        if (seeded <= 0) {
            return false
        }
        return isReady(seeded, currentMinute())
    }

    // Use epoch time: MapClock resets on restart, but birdhouse growth must persist.
    private fun currentMinute(): Int =
        (System.currentTimeMillis() / BirdhouseParams.MILLIS_PER_MINUTE).toInt()
}
