package org.rsmod.content.minigames.pyramidplunder.scripts

import jakarta.inject.Inject
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseThievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoom
import org.rsmod.content.minigames.pyramidplunder.PlunderState.setUrnState
import org.rsmod.content.minigames.pyramidplunder.PlunderState.urnState
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderRooms
import org.rsmod.content.minigames.pyramidplunder.drops.PlunderDropTables
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlunderUrnScript
@Inject
constructor(private val objRepo: ObjRepository, private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        for (leaf in PlunderLocs.URN_CLOSED) {
            onOpLoc1(leaf) { urn -> search(urnIndex(urn.loc.id), SearchKind.CLOSED) }
            onOpLoc2(leaf) { urn -> checkForSnakes(urnIndex(urn.loc.id)) }
        }
        for (leaf in PlunderLocs.URN_OPENED) {
            onOpLoc1(leaf) { searchAlreadyOpened() }
        }
        for (leaf in PlunderLocs.URN_SNAKE) {
            onOpLoc1(leaf) { urn -> search(urnIndex(urn.loc.id), SearchKind.SNAKE_SHOWN) }
            onOpLoc3(leaf) { urn -> charmSnake(urnIndex(urn.loc.id)) }
        }
        for (leaf in PlunderLocs.URN_SNAKE_CHARMED) {
            onOpLoc1(leaf) { urn -> search(urnIndex(urn.loc.id), SearchKind.CHARMED) }
        }

        onPlayerQueueWithArgs<UrnTask>(QUEUE_URN_SEARCH) { runStage(it.args) }
    }

    private fun urnIndex(parentLocId: Int): Int =
        PlunderLocs.URN_PARENT_TO_INDEX[parentLocId]
            ?: error("Unmapped Pyramid Plunder urn parent loc id: $parentLocId")

    private enum class SearchKind(val expectedState: Int) {
        CLOSED(URN_STATE_CLOSED),
        SNAKE_SHOWN(URN_STATE_SNAKE),

        // Guaranteed charmed-search success is unverified.
        CHARMED(URN_STATE_CHARMED),
    }

    private enum class UrnStage {
        STAGE2,
        STAGE3,
        RESULT,
        COMMIT,
    }

    private enum class UrnOutcome {
        SUCCESS,
        FAIL_BITTEN,
        FAIL_POISONED,
    }

    private data class UrnTask(
        val stage: UrnStage,
        val urn: Int,
        val room: Int,
        val outcome: UrnOutcome,
        val xp: Double,
    )

    private suspend fun ProtectedAccess.search(urn: Int, kind: SearchKind) {
        arriveDelay()
        if (QUEUE_URN_SEARCH in player.queueList) {
            // Keep the attempt lock through the result stage; clicks must not start overlapping searches.
            return
        }
        val room = player.plunderRoom
        if (room == 0) {
            return
        }
        val state = player.urnState(urn)
        if (state != kind.expectedState) {
            if (state == URN_STATE_OPENED) {
                searchAlreadyOpened()
            }
            return
        }
        val info = PlunderRooms.forRoom(room)
        if (player.baseThievingLvl < info.levelReq) {
            mes("You need a Thieving level of ${info.levelReq} to search this urn.")
            return
        }

        val success =
            when (kind) {
                SearchKind.CHARMED -> true
                SearchKind.CLOSED ->
                    rollPassed(PlunderRooms.URN_SEARCH_LOW, PlunderRooms.URN_SEARCH_HIGH, room)
                SearchKind.SNAKE_SHOWN ->
                    rollPassed(
                        PlunderRooms.URN_SEARCH_WITH_SNAKE_LOW,
                        PlunderRooms.URN_SEARCH_WITH_SNAKE_HIGH,
                        room,
                    )
            }
        val outcome =
            when {
                success -> UrnOutcome.SUCCESS
                random.of(POISON_RATIO_DENOM) < POISON_RATIO_NUM -> UrnOutcome.FAIL_POISONED
                else -> UrnOutcome.FAIL_BITTEN
            }

        val task = { stage: UrnStage -> UrnTask(stage, urn, room, outcome, info.urnXp) }
        // Loc ops run after queue processing; initial delays are literal tick gaps.
        queue(QUEUE_URN_SEARCH, cycles = 2, args = task(UrnStage.STAGE2))
        queue(QUEUE_URN_SEARCH, cycles = 3, args = task(UrnStage.STAGE3))
        queue(QUEUE_URN_SEARCH, cycles = 4, args = task(UrnStage.RESULT))
        if (success) {
            queue(QUEUE_URN_SEARCH, cycles = 7, args = task(UrnStage.COMMIT))
        }
    }

    private fun ProtectedAccess.rollPassed(low: Int, high: Int, room: Int): Boolean {
        val rate =
            SkillingSuccessRate.successRate(
                low,
                high,
                player.baseThievingLvl,
                MAX_LEVEL,
                flatBonus = -(room - 1.0),
            )
        return rate > random.randomDouble()
    }

    private fun ProtectedAccess.runStage(task: UrnTask) {
        // Reject tasks for a room that advanced or reset during suspension.
        if (player.plunderRoom != task.room) {
            return
        }
        when (task.stage) {
            UrnStage.STAGE2 -> {
                anim(PlunderLocs.SEQ_PICK_STAGE2)
                soundSynth(PlunderLocs.SYNTH_PICK_STAGE2)
            }
            UrnStage.STAGE3 -> {
                anim(PlunderLocs.SEQ_URN_SEARCH)
                if (task.outcome != UrnOutcome.SUCCESS) {
                    // Urn damage and poison severity are unverified.
                    queueHit(delay = 1, type = HitType.Typeless, damage = random.of(1, 4))
                }
            }
            UrnStage.RESULT ->
                when (task.outcome) {
                    UrnOutcome.SUCCESS -> {
                        spam("You successfully loot the urn!")
                        anim(PlunderLocs.SEQ_URN_SUCCESS)
                        soundSynth(PlunderLocs.SYNTH_JAR_OPEN)
                        statAdvance("stat.thieving", task.xp)
                    }
                    UrnOutcome.FAIL_BITTEN -> {
                        spam("You've been bitten by something moving inside the urn.")
                        failFx()
                    }
                    UrnOutcome.FAIL_POISONED -> {
                        spam("You've been poisoned by something moving inside the urn.")
                        failFx()
                        PlayerPoison.tryPoison(
                            player,
                            initialDamage = POISON_SEVERITY,
                            severity = POISON_SEVERITY,
                        )
                    }
                }
            UrnStage.COMMIT -> {
                if (player.urnState(task.urn) == URN_STATE_OPENED) {
                    // Reject duplicate completion before awarding another artefact.
                    return
                }
                player.setUrnState(task.urn, URN_STATE_OPENED)
                invAddOrDrop(objRepo, PlunderDropTables.urnArtefact(task.room, random))
            }
        }
    }

    private fun ProtectedAccess.failFx() {
        anim(PlunderLocs.SEQ_URN_BIT)
        soundSynth(PlunderLocs.SYNTH_SNAKE_ATTACK)
        soundSynth(PlunderLocs.SYNTH_JAR_SNAKE)
    }

    private fun ProtectedAccess.searchAlreadyOpened() {
        mes("There is nothing left to find in this urn.")
    }

    private suspend fun ProtectedAccess.checkForSnakes(urn: Int) {
        arriveDelay()
        val room = player.plunderRoom
        if (room == 0) {
            return
        }
        if (player.urnState(urn) != URN_STATE_CLOSED) {
            // Do not let stale Check flip a looted urn back into a lootable snake state.
            return
        }
        val info = PlunderRooms.forRoom(room)
        if (player.baseThievingLvl < info.levelReq) {
            mes("You need a Thieving level of ${info.levelReq} to check this urn.")
            return
        }
        // TODO: verify Check-for-Snakes animation.
        anim(PlunderLocs.SEQ_SEARCH)
        if (rollPassed(PlunderRooms.URN_CHECK_LOW, PlunderRooms.URN_CHECK_HIGH, room)) {
            player.setUrnState(urn, URN_STATE_SNAKE)
            mes("You spot a snake lurking inside the urn!")
        } else {
            mes("You find no sign of any snakes.")
        }
    }

    private suspend fun ProtectedAccess.charmSnake(urn: Int) {
        arriveDelay()
        val room = player.plunderRoom
        if (room == 0) {
            return
        }
        if (player.urnState(urn) != URN_STATE_SNAKE) {
            // Charm only a currently visible snake; stale ops must not restore looted urns.
            return
        }
        if (invTotal(inv, PlunderLocs.SNAKE_CHARM) == 0) {
            mes("You need a snake charm to do this.")
            return
        }
        anim(PlunderLocs.SEQ_SEARCH)
        player.setUrnState(urn, URN_STATE_CHARMED)
        mes("You charm the snake, and it slithers back into the urn.")
    }

    private companion object {
        const val MAX_LEVEL = 99
        const val URN_STATE_CLOSED = 0
        const val URN_STATE_OPENED = 1
        const val URN_STATE_SNAKE = 2
        const val URN_STATE_CHARMED = 3

        const val POISON_RATIO_NUM = 15
        const val POISON_RATIO_DENOM = 100
        const val POISON_SEVERITY = 4

        const val QUEUE_URN_SEARCH = "queue.ntk_urn_search"
    }
}
