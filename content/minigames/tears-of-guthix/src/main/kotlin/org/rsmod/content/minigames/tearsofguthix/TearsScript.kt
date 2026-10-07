package org.rsmod.content.minigames.tearsofguthix

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.events.PlayerLogoutRequest
import org.rsmod.api.player.events.PlayerMoveClick
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.*
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.manager.QuestJournalRegistry
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TearsScript @Inject constructor(private val game: TearsGame) : PluginScript() {
    override fun ScriptContext.startup() {
        TearsData.rules
        TearsData.skills
        game.initialiseStreams()
        QuestJournalRegistry.registerStatus(QuestRow.getRow("dbrow.quest_tearsofguthix").id) {
            TearsJournal.open(it, game)
        }
        onEvent<GameLifecycle.StartCycle> { game.enforceWalk() }
        onEvent<GameLifecycle.LateCycle> { game.tick() }
        onEvent<GameLifecycle.PreShutdown> { game.shutdown() }
        onEvent<PlayerMoveClick> { game.move(this) }
        onEvent<PlayerLogoutRequest> {
            if (TearsState.active(player)) {
                consumed = true
                game.logoutRequest(player)
            }
        }
        onPlayerLogin { game.login(player) }
        onPlayerLogout { game.leave(player) }
        for (juna in listOf("loc.tog_juna", "loc.tog_juna_1op", "loc.tog_juna_2ops")) {
            onOpLoc1(juna) { talkToJuna() }
        }
        onOpLoc2("loc.tog_juna_2ops") { talkToJuna() }
        onOpLoc1("loc.tog_weepingwall") {
            val wall = TearsData.walls.indexOfFirst { wall -> wall.coords == it.loc.coords }
            if (!TearsState.active(player)) {
                mes("Ask Juna for permission before collecting tears.")
            } else if (wall >= 0 && player.vars[TearsState.Active] == 1) {
                game.stopCollecting(player)
                if (!game.checkWall(player, wall)) return@onOpLoc1
                playerWalk(TearsData.walls[wall].stand)
                arriveDelay()
                game.collect(player, wall)
            }
        }
        onOpLoc1("loc.tog_cave_down") { teleport(CoordGrid(3219, 9532, 2)) }
        onOpLoc1("loc.tog_cave_up") { teleport(CoordGrid(3226, 9542, 0)) }
        onOpLoc1("loc.tog_climbing_rocks_up") {
            faceLoc(it.loc)
            anim("seq.tog_climb_slope_down")
            soundSynth("synth.CLIMBING_LOOP", loops = 5)
            exactMove(it.loc.coords.translateX(-1), it.loc.coords.translateX(1), 0, 100, 512)
            delay(4)
        }
        onOpLoc1("loc.tog_climbing_rocks_down") {
            faceLoc(it.loc)
            anim("seq.tog_climb_slope_up")
            soundSynth("synth.CLIMBING_LOOP", loops = 5)
            exactMove(it.loc.coords.translateX(1), it.loc.coords.translateX(-1), 0, 100, 1536)
            delay(4)
        }
    }

    private suspend fun ProtectedAccess.talkToJuna() {
        if (TearsState.active(player)) {
            mes("Finish your current visit first.")
            return
        }
        startDialogue {
            chatNpcSpecific("Juna", "npc.tog_juna_dummy", neutral,
                "The tears of Guthix bring balance. Would you like to collect them?")
            when (choice3("Collect tears.", 1, "When can I collect tears again?", 2,
                "Not right now.", 3)) {
                1 -> {
                    val reason = game.canEnter(player) ?: game.handsRequired(player)
                    if (reason != null) {
                        chatNpcSpecific("Juna", "npc.tog_juna_dummy", neutral, reason)
                    } else {
                        chatPlayer(neutral, "I'd like to collect the tears.")
                        game.enter(player)?.let {
                            chatNpcSpecific("Juna", "npc.tog_juna_dummy", neutral, it)
                        }
                    }
                }
                2 -> chatNpcSpecific("Juna", "npc.tog_juna_dummy", neutral,
                    game.canEnter(player) ?: "You may collect tears now. Blue tears fill your bowl; green tears empty it.")
            }
        }
    }
}
