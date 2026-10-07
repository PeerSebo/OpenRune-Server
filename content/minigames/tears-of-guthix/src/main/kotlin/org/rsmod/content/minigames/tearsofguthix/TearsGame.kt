package org.rsmod.content.minigames.tearsofguthix

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.events.PlayerMoveClick
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.table.MusicRow
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class TearsGame @Inject constructor(
    private val clock: MapClock,
    private val locs: LocRepository,
    private val collision: CollisionFlagMap,
    private val events: EventBus,
    private val music: MusicPlayer,
    private val world: WorldRepository,
    random: GameRandom,
) {
    private val streams by lazy { TearsStreams(random, TearsData.rules.streamTicks) }
    private val visuals = mutableMapOf<Player, BowlVisual>()
    private var lastCycle = Int.MIN_VALUE

    internal fun canEnter(player: Player): String? {
        if (TearsState.active(player)) return "You are already taking part in Tears of Guthix."
        if (!QuestRequirements.hasCompleted(player, "quest_tearsofguthix") &&
            player.vars["varbit.tog_juna_bowl"] < 2) {
            return "You must complete Tears of Guthix before collecting tears."
        }
        return TearsState.eligibility(player)
    }

    internal fun enter(player: Player): String? {
        canEnter(player)?.let { return it }
        handsRequired(player)?.let { return it }
        val rules = TearsData.rules
        val tabs = (0..6).toList() + listOf(11, 12)
        val overlays = player.ui.overlays.entries().filter { (target, _) ->
            target in tabs.map { RSCM.getRSCM("component.toplevel_osrs_stretch:side$it") }
        }.associate { (target, interf) ->
            RSCM.getReverseMapping(RSCMType.COMPONENT, target) to
                RSCM.getReverseMapping(RSCMType.INTERFACE, interf)
        }
        visuals[player] = BowlVisual(player.bas, player.appearance.wornOverrides, overlays,
            player.vars["varbit.tog_qp_before_return"])
        TearsState.set(player, TearsState.RunMode, player.vars["varp.option_run"])
        TearsState.clear(player)
        TearsState.set(player, TearsState.Active, 4)
        TearsState.set(player, TearsState.Start, clock.cycle)
        TearsState.set(player, TearsState.Countdown, duration(player))
        TearsState.set(player, "varbit.tog_qp_before_return", duration(player))
        player.loginExitCoord = rules.returnCoord
        player.clearInteraction()
        player.abortRoute()
        player.forcedRoute = true
        player.walk(rules.entry)
        protectPassage(player)
        player.setActiveMoveSpeed(MoveSpeed.Walk)
        player.appearance.wornOverrides = player.appearance.wornOverrides +
            mapOf(Wearpos.RightHand.slot to InvObj("obj.tog_bowl", 1), Wearpos.LeftHand.slot to null)
        val walk = "seq.tog_walk_bowl".asRSCM(RSCMType.SEQ)
        player.bas = BasType(
            readyAnim = "seq.tog_ready_bowl".asRSCM(RSCMType.SEQ),
            turnOnSpot = checkNotNull(ServerCacheManager.getBas("bas.human_default".asRSCM())).turnOnSpot,
            walkForward = walk, walkBack = walk, walkLeft = walk, walkRight = walk,
            running = -1,
        )
        overlays.values.forEach { player.ifCloseOverlay(it, events) }
        player.ifOpenOverlay("interface.tog_sidepanel", "component.toplevel_osrs_stretch:side4", events)
        player.runClientScript("clientscript.[clientscript,toplevel_sidebutton_switch]", 4)
        music.startEncounter(player, MusicRow.getRow("dbrow.music_tears_of_guthix_music"))
        player.mes("You have a short time to collect as many blue tears as you can.")
        return null
    }

    internal fun handsRequired(player: Player): String? =
        if (player.worn[Wearpos.RightHand.slot] != null || player.worn[Wearpos.LeftHand.slot] != null)
            "You need both hands free to hold the bowl." else null

    internal fun duration(player: Player): Int =
        player.vars["varp.qp"].coerceIn(TearsData.rules.optionalTicks, 1023)

    internal fun collect(player: Player, wall: Int) {
        if (player.vars[TearsState.Active] != 1 || wall !in TearsData.walls.indices) return
        if (player.coords != TearsData.walls[wall].stand) return
        if (!checkWall(player, wall)) return
        if (player.vars[TearsState.Wall] == wall + 1) return
        if (player.vars[TearsState.Deadline] == 0) {
            TearsState.set(player, TearsState.Deadline, clock.cycle + duration(player))
        }
        TearsState.set(player, TearsState.Wall, wall + 1)
        TearsState.set(player, "varbit.tog_minigame_collecting", 1)
        player.faceSquare(TearsData.walls[wall].coords)
        player.anim("seq.tog_lean_forward_bowl")
    }

    internal fun checkWall(player: Player, wall: Int): Boolean {
        if (player.vars[TearsState.Active] != 1 || wall !in TearsData.walls.indices) return false
        if (streams.colour(wall) != 0) return true
        stopCollecting(player)
        player.mes("There are no tears to collect from that part of the wall.")
        return false
    }

    internal fun move(event: PlayerMoveClick) {
        if (!event.consumed && !event.player.isDelayed && event.player.vars[TearsState.Active] == 1) {
            stopCollecting(event.player)
        }
    }

    internal fun stopCollecting(player: Player) {
        if (player.vars[TearsState.Wall] == 0) return
        TearsState.set(player, TearsState.Wall, 0)
        TearsState.set(player, "varbit.tog_minigame_collecting", 0)
        player.resetAnim()
        player.anim("seq.tog_lean_back_bowl")
    }

    internal fun enforceWalk() {
        visuals.keys.toList().forEach { player ->
            if (player.forceDisconnect || player.clientDisconnected.get() || player.pendingShutdown ||
                player.pendingLogout || player.loggingOut) {
                leave(player)
                return@forEach
            }
            player.setActiveMoveSpeed(MoveSpeed.Walk)
            if (player.vars[TearsState.Active] != 1) protectPassage(player)
        }
    }

    private fun protectPassage(player: Player) {
        // Native packet handlers ignore delayed input without cancelling scripted movement.
        player.delay = maxOf(player.delay, clock.cycle + 1)
    }

    internal fun shutdown() = visuals.keys.toList().forEach(::leave)

    internal fun logoutRequest(player: Player) {
        if (!TearsState.active(player)) return
        TearsState.set(player, TearsState.LogoutAfter, 1)
        when (player.vars[TearsState.Active]) {
            1 -> finish(player)
            4 -> {
                leave(player)
                player.manualLogout = true
            }
        }
    }

    internal fun initialiseStreams() {
        for (wall in TearsData.walls) {
            check(locs.findLoc(wall.coords, "loc.tog_weepingwall")) {
                "Missing native weeping wall at ${wall.coords}."
            }
        }
        TearsData.walls.indices.forEach(::displayWall)
    }

    internal fun tick() {
        val cycle = clock.cycle
        if (cycle == lastCycle) return
        lastCycle = cycle
        streams.advance(cycle).forEach(::displayWall)
        for (player in visuals.keys.toList()) {
            val phase = player.vars[TearsState.Active]
            val inside = TearsData.rules.contains(player.coords)
            val approaching = phase == 4 && player.coords.level == TearsData.rules.entry.level &&
                player.coords.chebyshevDistance(TearsData.rules.entry) <= 16
            if (!inside && !approaching) {
                leave(player)
                continue
            }
            when (player.vars[TearsState.Active]) {
                1 -> collectTick(player, cycle)
                2 -> returnTick(player, cycle)
                3 -> if (cycle >= player.vars[TearsState.Deadline]) reward(player)
                4 -> if (crossingTick(player, cycle, entering = true)) {
                    TearsState.set(player, TearsState.Active, 1)
                    TearsState.set(player, TearsState.Start, cycle)
                }
                5 -> if (cycle >= player.vars[TearsState.Deadline] &&
                    player.lastMovement < cycle && player.routeDestination.isEmpty()) {
                    drink(player, cycle)
                }
                else -> leave(player)
            }
        }
    }

    private fun collectTick(player: Player, cycle: Int) {
        if (player.vars[TearsState.Deadline] == 0 &&
            cycle - player.vars[TearsState.Start] >= TearsData.rules.approachTicks) {
            TearsState.set(player, TearsState.Deadline, cycle + duration(player))
        }
        val deadline = player.vars[TearsState.Deadline]
        if (deadline != 0) {
            TearsState.set(player, TearsState.Countdown, (deadline - cycle).coerceAtLeast(0))
            if (cycle >= deadline) {
                finish(player)
                return
            }
        }
        val wall = player.vars[TearsState.Wall] - 1
        if (wall !in TearsData.walls.indices) return
        if (player.coords != TearsData.walls[wall].stand || player.routeDestination.isNotEmpty()) {
            stopCollecting(player)
            return
        }
        val colour = streams.colour(wall)
        if (colour == 0) {
            stopCollecting(player)
            return
        }
        TearsState.set(player, TearsState.Collected,
            (player.vars[TearsState.Collected] + colour).coerceIn(0, 511))
        player.soundSynth(
            if (colour > 0) "synth.COLLECT_GOOD_TEAR" else "synth.COLLECT_BAD_TEAR")
    }

    private fun finish(player: Player) {
        stopCollecting(player)
        player.mes("Your time in the cave is up.")
        TearsState.set(player, TearsState.Active, 2)
        TearsState.set(player, TearsState.Start, clock.cycle)
        TearsState.set(player, TearsState.Deadline, 0)
        TearsState.set(player, TearsState.RouteStep, 0)
        player.clearInteraction()
        player.abortRoute()
        player.forcedRoute = true
        player.walk(TearsData.rules.entryRoute.last())
        protectPassage(player)
    }

    private fun crossingTick(player: Player, cycle: Int, entering: Boolean): Boolean {
        val rules = TearsData.rules
        if (cycle - player.vars[TearsState.Start] >= rules.crossingTimeoutTicks) {
            player.mes("Your passage through Juna's entrance was interrupted. Please speak to her again.")
            val logout = player.vars[TearsState.LogoutAfter] != 0
            leave(player)
            if (logout) player.manualLogout = true
            return false
        }
        val route = if (entering) rules.entryRoute else rules.entryRoute.asReversed()
        val index = player.vars[TearsState.RouteStep]
        val target = route[index]
        if (player.coords != target) {
            if (player.routeDestination.isEmpty()) player.walk(target)
            return false
        }
        val tailIndex = if (entering) 0 else route.lastIndex - 2
        if (index == tailIndex) {
            val deadline = player.vars[TearsState.Deadline]
            if (deadline == 0) {
                player.faceSquare(rules.entryRoute[1])
                locs.findExact(rules.entryRoute[1], LocShape.CentrepieceStraight)?.let {
                    world.locAnim(it, "seq.tog_snake_openclose")
                }
                TearsState.set(player, TearsState.Deadline, cycle + rules.tailPauseTicks)
                return false
            }
            if (cycle < deadline) return false
        }
        TearsState.set(player, TearsState.Deadline, 0)
        if (index == route.lastIndex) {
            player.abortRoute()
            player.resetPendingFaceSquare()
            return true
        }
        TearsState.set(player, TearsState.RouteStep, index + 1)
        player.resetPendingFaceSquare()
        player.walk(route[index + 1])
        return false
    }

    private fun returnTick(player: Player, cycle: Int) {
        if (!crossingTick(player, cycle, entering = false)) return
        TearsState.set(player, TearsState.Active, 5)
        TearsState.set(player, TearsState.Deadline, cycle + TearsData.rules.arrivalPauseTicks)
    }

    private fun drink(player: Player, cycle: Int) {
        player.abortRoute()
        player.faceSquare(TearsData.rules.entryRoute[1])
        player.soundSynth("synth.JUNA_HISS")
        player.anim("seq.tog_drink_bowl")
        player.soundSynth("synth.DRINK_AND_MAGIC")
        TearsState.set(player, TearsState.Active, 3)
        TearsState.set(player, TearsState.Deadline, cycle + 2)
        music.startEncounter(player, MusicRow.getRow("dbrow.music_the_power_of_tears"))
    }

    private fun reward(player: Player) {
        val count = player.vars[TearsState.Collected]
        val skill = TearsRewards.award(player, count)
        TearsState.set(player, TearsState.Best, maxOf(player.vars[TearsState.Best], count))
        TearsState.rememberReward(player)
        player.mes("Tears collected: <col=ef1020>$count</col>. Personal best: " +
            "<col=ef1020>${player.vars[TearsState.Best]}</col>.")
        events.publish(PlayerActionCompleteEvent(player, PlayerAction.CollectTears,
            "varbit.tog_tears_collected", count = count))
        player.mes(if (skill == null) "You drink the tears." else
            "You drink the tears and gain experience in ${skill.replaceFirstChar(Char::uppercase)}.")
        val logOut = player.vars[TearsState.LogoutAfter] != 0
        leave(player)
        if (logOut) player.manualLogout = true
    }

    internal fun leave(player: Player) {
        val visual = visuals.remove(player) ?: return
        player.abortRoute()
        player.resetAnim()
        player.bas = visual.bas
        player.appearance.wornOverrides = visual.worn
        TearsState.set(player, "varbit.tog_qp_before_return", visual.previousDuration)
        player.ifCloseOverlay("interface.tog_sidepanel", events)
        visual.tabs.forEach { (target, interf) -> player.ifOpenOverlay(interf, target, events) }
        player.setActiveMoveSpeed(if (player.vars[TearsState.RunMode] == 1) MoveSpeed.Run else MoveSpeed.Walk)
        if (TearsData.rules.contains(player.coords)) {
            PathingEntityCommon.telejump(player, collision, TearsData.rules.returnCoord)
        }
        if (player.loginExitCoord == TearsData.rules.returnCoord) player.loginExitCoord = null
        TearsState.clear(player)
        music.finishEncounter(player, MusicRow.getRow("dbrow.music_the_power_of_tears"))
        music.finishEncounter(player, MusicRow.getRow("dbrow.music_tears_of_guthix_music"))
    }

    internal fun login(player: Player) {
        if (player.vars["varbit.tog_minigame_collecting"] != 0 ||
            TearsData.rules.contains(player.coords)) {
            player.abortRoute()
            player.resetAnim()
            PathingEntityCommon.telejump(player, collision, TearsData.rules.returnCoord)
        }
        TearsState.clear(player)
        val day = TearsState.day()
        if (player.vars[TearsState.HasPlayed] != 0 && player.vars["varbit.tog_helper_disabled"] == 0 &&
            player.vars[TearsState.NotifyDay] != day && canEnter(player) == null) {
            player.mes("<col=ef1020>You are eligible to drink from the Tears of Guthix.</col>")
            TearsState.set(player, TearsState.NotifyDay, day)
        }
    }

    private fun displayWall(index: Int) {
        val wall = TearsData.walls[index]
        val type = when (streams.colour(index)) { 1 -> wall.blue; -1 -> wall.green; else -> wall.dry }
        locs.add(wall.coords, type, Int.MAX_VALUE, LocAngle.values().single { it.id == wall.angle },
            LocShape.values().single { it.id == 4 })
    }

    private data class BowlVisual(
        val bas: BasType?, val worn: Map<Int, InvObj?>, val tabs: Map<String, String>,
        val previousDuration: Int,
    )
}
