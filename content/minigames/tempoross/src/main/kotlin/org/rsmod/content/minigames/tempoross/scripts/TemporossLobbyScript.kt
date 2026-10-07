package org.rsmod.content.minigames.tempoross.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.events.InstancePlayerLeaveUnboundEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseFishingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onApLoc2
import org.rsmod.api.script.onApLoc3
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.minigames.tempoross.TemporossEncounterManager
import org.rsmod.content.minigames.tempoross.TemporossRewards
import org.rsmod.content.minigames.tempoross.configs.TemporossConstants
import org.rsmod.game.MapClock
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TemporossLobbyScript
@Inject
constructor(
    private val encounters: TemporossEncounterManager,
    private val worldClock: MapClock,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { encounters.tick(worldClock.cycle) }
        onEvent<InstancePlayerLeaveUnboundEvent> {
            if (key == TemporossConstants.INSTANCE_KEY) encounters.onPlayerLeave(player, instanceId)
        }

        onOpLoc1(TemporossConstants.LADDER) { board(solo = false) }
        onOpLoc2(TemporossConstants.LADDER) { board(solo = false) }
        onOpLoc3(TemporossConstants.LADDER) { board(solo = true) }
        onApLoc1(TemporossConstants.LADDER) { apBoard(it.loc, solo = false) }
        onApLoc2(TemporossConstants.LADDER) { apBoard(it.loc, solo = false) }
        onApLoc3(TemporossConstants.LADDER) { apBoard(it.loc, solo = true) }

        onOpLoc1(TemporossConstants.FERRY_BOAT) { boardFerry(it.loc.coords) }
        onOpNpc1(TemporossConstants.FERRY_TO_LOBBY) { talkFerryman() }
        onOpNpc3(TemporossConstants.FERRY_TO_LOBBY) { ferry(TemporossConstants.RETURN_COORD) }
        onOpNpc1(TemporossConstants.FERRY_FROM_LOBBY) { talkFerryman() }
        onOpNpc3(TemporossConstants.FERRY_FROM_LOBBY) { ferry(TemporossConstants.MAINLAND_FERRY_COORD) }

        onOpNpc1(TemporossConstants.LOBBY_HOST) { talkHost() }
        onOpNpc1(TemporossConstants.LOBBY_ANNOUNCER) { talkAnnouncer() }

        registerRewardPool()
    }

    private fun ProtectedAccess.board(solo: Boolean) {
        if (encounters.isCastingOff(player)) return
        if (encounters.isOnDeck(player)) {
            telejump(TemporossConstants.PIER_COORD)
            if (player.coords != TemporossConstants.PIER_COORD) return
            encounters.leaveDeck(player, worldClock.cycle)
            mes("You step back off the boat onto the pier.")
            return
        }
        if (player.baseFishingLvl < TemporossConstants.REQUIRED_FISHING_LEVEL) {
            mes("You need a Fishing level of ${TemporossConstants.REQUIRED_FISHING_LEVEL} to face Tempoross.")
            return
        }
        if (solo) {
            encounters.startSolo(player, worldClock.cycle)
            mes("You climb down and the boat casts off immediately.")
        } else {
            telejump(TemporossConstants.DECK_COORD)
            if (player.coords != TemporossConstants.DECK_COORD) return
            encounters.boardDeck(player, worldClock.cycle)
            mes("You climb down onto the waiting boat.")
        }
    }

    // Use approach range across the pier gap; op adjacency is impossible there.
    private fun ProtectedAccess.apBoard(loc: BoundLocInfo, solo: Boolean) {
        if (!isWithinApRange(loc, distance = TemporossConstants.LADDER_AP_RANGE)) {
            return
        }
        board(solo = solo)
    }

    private fun ProtectedAccess.boardFerry(clicked: CoordGrid) {
        if (clicked.x >= TemporossConstants.MAINLAND_BOAT_MIN_X) {
            ferry(TemporossConstants.RETURN_COORD)
        } else {
            ferry(TemporossConstants.MAINLAND_FERRY_COORD)
        }
    }

    private fun ProtectedAccess.talkFerryman() {
        mes("Hop aboard if you're heading the other way - I'll take you across.")
    }

    private fun ProtectedAccess.ferry(destination: CoordGrid) {
        telejump(destination)
    }

    private fun ProtectedAccess.talkHost() {
        mes("Captain Pudi says climb down the rope ladder to join the next mission against Tempoross.")
    }

    private fun ProtectedAccess.talkAnnouncer() {
        mes("Captain Dudi shouts something about climbing down the rope ladder to take on Tempoross.")
    }

    private fun ScriptContext.registerRewardPool() {
        for (pool in listOf(TemporossConstants.REWARD_POOL) + (0..8).map { "loc.tempoross_rewardpool_$it" }) {
            onOpLoc1(pool) { searchRewardPool(it.loc, batch = 1) }
            onOpLoc2(pool) { mes("You have ${player.vars[TemporossConstants.VARBIT_PERMITS]} reward permits remaining.") }
            onOpLoc3(pool) { searchRewardPool(it.loc, batch = 5) }
        }
    }

    private fun ProtectedAccess.searchRewardPool(pool: BoundLocInfo, batch: Int) {
        if (player.vars[TemporossConstants.VARBIT_PERMITS] <= 0) {
            mes("You have no reward permits. Subdue Tempoross to earn some.")
            resetAnim()
            return
        }
        if (TemporossConstants.SMALL_NET !in inv && TemporossConstants.BIG_NET !in inv) {
            if (!invAdd(inv, TemporossConstants.SMALL_NET, 1).success) {
                mes("You need room for a fishing net to search the reward pool.")
                resetAnim()
                return
            }
            mes("The Spirit Angler gives you a small fishing net.")
        }
        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            actionDelay = maxOf(actionDelay, mapClock + REWARD_INTERVAL_TICKS)
            skillAnimDelay = mapClock + REWARD_INTERVAL_TICKS
            anim(if (TemporossConstants.SMALL_NET in inv) "seq.human_smallnet" else "seq.human_largenet")
            if (batch == 5) opLoc3(pool) else opLoc1(pool)
            return
        }
        if (actionDelay != mapClock) {
            if (batch == 5) opLoc3(pool) else opLoc1(pool)
            return
        }
        actionDelay = mapClock + REWARD_INTERVAL_TICKS
        skillAnimDelay = mapClock + REWARD_INTERVAL_TICKS
        anim(if (TemporossConstants.SMALL_NET in inv) "seq.human_smallnet" else "seq.human_largenet")
        if (!collectRewards(batch) || batch == 5) {
            resetAnim()
            return
        }
        opLoc1(pool)
    }

    // Commit each permit only after its entire reward fits.
    private fun ProtectedAccess.collectRewards(batch: Int): Boolean {
        var searched = 0
        repeat(batch) {
            val permits = player.vars[TemporossConstants.VARBIT_PERMITS]
            if (permits <= 0) {
                if (searched == 0) mes("You have no reward permits. Subdue Tempoross to earn some.")
                return false
            }
            if (!grantReward()) {
                mes("Your inventory is too full to collect this reward.")
                return false
            }
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
                player,
                TemporossConstants.VARBIT_PERMITS,
                permits - 1,
            )
            statAdvance("stat.fishing", REWARD_XP_PER_SEARCH)
            searched++
        }
        return player.vars[TemporossConstants.VARBIT_PERMITS] > 0
    }

    private fun ProtectedAccess.grantReward(): Boolean =
        TemporossRewards.collect(player, player.baseFishingLvl, random)

    private companion object {
        private const val REWARD_XP_PER_SEARCH = 10.0
        private const val REWARD_INTERVAL_TICKS = 3
    }
}
