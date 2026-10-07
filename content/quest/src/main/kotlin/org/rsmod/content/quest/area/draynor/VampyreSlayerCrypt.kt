package org.rsmod.content.quest.area.draynor

import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestVar
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VampyreSlayerCrypt
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(Coffin) { openCoffin(it.loc) }

        onPlayerSoftTimer(RiseTimer) { player.riseFromCoffin() }
        onPlayerSoftTimer(EmergeTimer) { player.emergeFromCoffin() }
        onPlayerSoftTimer(WatchTimer) { player.watchCount() }

        onPlayerCoordsChanged { player.armCandleBurn() }
        onPlayerSoftTimer(CandleTimer) { player.burnFeet() }
    }

    private suspend fun ProtectedAccess.openCoffin(loc: BoundLocInfo) {
        arriveDelay()

        val quest = Quest.get(VampyreSlayer.QuestKey)
        if (quest == null || !quest.isQuestInProgress(player)) {
            mesbox("The coffin is sealed shut.")
            return
        }

        // Clicking it again while he is out - or mid-climb - must not stack a second Count. The
        // coffin reverts to its closed form on the same tick he stands up, so without this the
        // window between that revert and the kill is a Count tap.
        if (countAlreadyOut(loc.coords)) {
            mes("The coffin is empty.")
            return
        }

        locRepo.change(loc, AnimatedCoffin, duration = CoffinOpenTicks)
        soundSynth(CoffinSound)

        COFFIN_COORD.set(player, loc.coords.packed)
        player.softTimer(RiseTimer, RiseDelayTicks)
    }

    private fun countAlreadyOut(coffin: CoordGrid): Boolean =
        npcRepo.findAll(coffin).any { it.isType(RisingCount) } ||
            npcRepo.findAll(coffin.countTile()).any { it.isType(Count) }

    private fun Player.riseFromCoffin() {
        clearSoftTimer(RiseTimer)

        val coffin = coffinCoord() ?: return

        val rising = Npc(RisingCount, coffin)
        npcRepo.add(rising, duration = RiseAnimTicks)
        rising.noneMode()
        rising.anim(RiseAnim)

        softTimer(EmergeTimer, EmergeDelayTicks)
    }

    private fun Player.emergeFromCoffin() {
        clearSoftTimer(EmergeTimer)

        val coffin = coffinCoord() ?: return

        val tile = coffin.countTile()
        if (npcRepo.findAll(tile).any { it.isType(Count) }) {
            return
        }

        val count = Npc(Count, tile)
        npcRepo.add(count, duration = CountLifespanTicks)
        count.anim(EmergeAnim)
        count.facePlayer(this)

        GARLIC_COUNTDOWN.set(this, 0)
        attr[WATCHED_COUNT] = count
        softTimer(WatchTimer, 1)
    }

    private fun Player.watchCount() {
        val count = attr[WATCHED_COUNT]
        if (count == null || !count.isSlotAssigned || !count.isType(Count) || count.hitpoints <= 0) {
            stopWatchingCount()
            return
        }

        val quest = Quest.get(VampyreSlayer.QuestKey)
        if (quest == null || !quest.isQuestInProgress(this)) {
            stopWatchingCount()
            return
        }

        garlicTick(this)

        val threshold = (count.baseHitpointsLvl * RegenerateThresholdPercent) / 100
        if (count.hitpoints > threshold.coerceAtLeast(1)) {
            return
        }

        val hasStake = inv.count(VampyreSlayer.Stake) > 0
        val hasHammer = inv.count(VampyreSlayer.Hammer) > 0

        if (hasStake && hasHammer) {
            return
        }

        count.hitpoints = count.baseHitpointsLvl
        if (hasStake) {
            mes("You're unable to push the stake far enough in!")
        }
        mes("The vampyre seems to regenerate!")
    }

    private fun Player.stopWatchingCount() {
        clearSoftTimer(WatchTimer)
        attr.remove(WATCHED_COUNT)
    }

    private fun Player.coffinCoord(): CoordGrid? =
        COFFIN_COORD.getOrNull(this)?.let(::CoordGrid)

    private fun garlicTick(player: Player) {
        if (player.inv.count(VampyreSlayer.Garlic) <= 0) {
            return
        }

        val remaining = GARLIC_COUNTDOWN.get(player)
        if (remaining > 0) {
            GARLIC_COUNTDOWN.set(player, remaining - 1)
            return
        }

        GARLIC_COUNTDOWN.set(player, GarlicIntervalTicks)
        player.mes("The vampyre seems to be weakened by the garlic you're carrying.")
    }

    private fun CoordGrid.countTile(): CoordGrid = translateX(1).translateZ(-1)

    private fun Player.armCandleBurn() {
        if (coords !in CandleTiles) {
            return
        }
        if (CANDLE_LOOP.get(this)) {
            return
        }
        CANDLE_LOOP.set(this, true)
        softTimer(CandleTimer, FirstBurnTicks)
    }

    private fun Player.burnFeet() {
        if (coords !in CandleTiles) {
            CANDLE_LOOP.set(this, false)
            clearSoftTimer(CandleTimer)
            return
        }

        mes("The candles on the floor burn your feet!")
        say("Ow!")
        soundSynth(hurtSound(), delay = HurtSoundDelay)

        softTimer(CandleTimer, BurnIntervalTicks)
    }

    private fun Player.hurtSound(): String {
        val male = appearance.bodyType == BodyTypeMale
        return if (male) MaleHurtSounds.random() else FemaleHurtSounds.random()
    }

    private companion object {
        private const val Coffin = "loc.vampcoffin"
        private const val AnimatedCoffin = "loc.vampcoffin_animated"

        private const val Count = "npc.count_draynor"
        private const val RisingCount = "npc.count_draynor_coffin"

        private const val RiseAnim = "seq.vampireslayer_arise"
        private const val EmergeAnim = "seq.vampireslayer_travel"

        private const val CoffinSound = "synth.BIGDOOR_OPEN"

        private const val RiseTimer = "timer.vampyre_slayer_coffin_rise"
        private const val EmergeTimer = "timer.vampyre_slayer_coffin_emerge"
        private const val CandleTimer = "timer.vampyre_slayer_candles"
        private const val WatchTimer = "timer.vampyre_slayer_count_watch"

        private val COFFIN_COORD = QuestVar.integer("COFFIN_COORD", "varp.vampyre_slayer_coffin_coord", temp = true)

        private val WATCHED_COUNT = AttributeKey<Npc>(temp = true)

        private const val RegenerateThresholdPercent = 5

        private const val GarlicIntervalTicks = 50

        private val GARLIC_COUNTDOWN = QuestVar.integer("GARLIC_COUNTDOWN", "varp.vampyre_slayer_garlic_countdown", temp = true)

        private const val RiseDelayTicks = 4

        private const val EmergeDelayTicks = 5

        private const val RiseAnimTicks = 5

        private const val CoffinOpenTicks = RiseDelayTicks + EmergeDelayTicks

        private const val CountLifespanTicks = 1000

        private val CandleTiles: Set<CoordGrid> =
            setOf(
                CoordGrid(3075, 9778, 0),
                CoordGrid(3076, 9772, 0),
                CoordGrid(3079, 9772, 0),
                CoordGrid(3080, 9778, 0),
                CoordGrid(3075, 9772, 0),
                CoordGrid(3075, 9777, 0),
                CoordGrid(3076, 9771, 0),
                CoordGrid(3076, 9773, 0),
                CoordGrid(3076, 9778, 0),
                CoordGrid(3077, 9772, 0),
                CoordGrid(3078, 9772, 0),
                CoordGrid(3079, 9771, 0),
                CoordGrid(3079, 9773, 0),
                CoordGrid(3079, 9778, 0),
                CoordGrid(3080, 9772, 0),
                CoordGrid(3080, 9777, 0),
            )

        private const val BurnIntervalTicks = 8

        private const val FirstBurnTicks = 2

        private const val HurtSoundDelay = 20

        private val FemaleHurtSounds = listOf("synth.FEMALE_HIT_01", "synth.FEMALE_HIT_02")

        private val MaleHurtSounds = listOf("synth.HUMAN_HIT_01", "synth.HUMAN_HIT_04")

        private const val BodyTypeMale = 0

        /** Whether the burn loop is already armed, so movement cannot stack a second one. */
        private val CANDLE_LOOP = QuestVar.boolean("CANDLE_LOOP", "varp.vampyre_slayer_candle_loop", temp = true)
    }
}
