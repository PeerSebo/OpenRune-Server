package org.rsmod.content.areas.misc.barbarianvillage

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerInit
import org.rsmod.content.areas.misc.barbarianvillage.configs.SecurityQuestion
import org.rsmod.content.areas.misc.barbarianvillage.configs.StrongholdDoorRules
import org.rsmod.content.areas.misc.barbarianvillage.configs.StrongholdQuestions
import org.rsmod.content.areas.misc.barbarianvillage.configs.StrongholdRefs
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StrongholdDoors : PluginScript() {
    private var ProtectedAccess.flapUnlocked by boolVarBit(StrongholdRefs.FlapVarbit)
    private var ProtectedAccess.slapHeadUnlocked by boolVarBit(StrongholdRefs.SlapHeadVarbit)
    private var ProtectedAccess.ideaUnlocked by boolVarBit(StrongholdRefs.IdeaVarbit)
    private var ProtectedAccess.stampUnlocked by boolVarBit(StrongholdRefs.StampVarbit)

    override fun ScriptContext.startup() {
        onPlayerInit { StrongholdGreetingState.migrate(player) }
        onOpLoc1(StrongholdRefs.WarDoor) { door(it.loc, WarDoor, greeting = true) }
        onOpLoc1(StrongholdRefs.WarDoorMirror) { door(it.loc, WarDoor, greeting = true) }

        onOpLoc1(StrongholdRefs.FamineDoor) { door(it.loc, FamineDoor) }
        onOpLoc1(StrongholdRefs.FamineDoorMirror) { door(it.loc, FamineDoor) }

        onOpLoc1(StrongholdRefs.PestilenceDoor) { door(it.loc, PestilenceDoor) }
        onOpLoc1(StrongholdRefs.PestilenceDoorMirror) { door(it.loc, PestilenceDoor) }

        onOpLoc1(StrongholdRefs.DeathDoor) { door(it.loc, DeathDoor) }
        onOpLoc1(StrongholdRefs.DeathDoorMirror) { door(it.loc, DeathDoor) }
    }

    private suspend fun ProtectedAccess.door(
        loc: BoundLocInfo,
        door: DoorSpirit,
        greeting: Boolean = false,
    ) {
        arriveDelay()

        val destination = crossingDestination(loc) ?: return

        if (greeting && !StrongholdGreetingState.get(player)) {
            firstEverDoor(door, destination)
            return
        }

        val allClaimed = flapUnlocked && slapHeadUnlocked && ideaUnlocked && stampUnlocked
        if (StrongholdDoorRules.shouldAskQuestion(random, allClaimed)) {
            interrogate(door)
        }

        crossDoor(destination)
    }

    private suspend fun ProtectedAccess.firstEverDoor(door: DoorSpirit, destination: CoordGrid) {
        startDialogue {
            chatDoor(
                door,
                "Greetings Adventurer. This place is kept safe by the spirits within the doors. " +
                    "As you pass through you will be asked questions about security. Hopefully " +
                    "you will learn much from us.",
            )
            chatDoor(
                door,
                "Please pass through and begin your adventure, beware of the various monsters " +
                    "that dwell within.",
            )
        }

        // An interrupted greeting must replay on the next interaction.
        StrongholdGreetingState.set(player, true)

        crossDoor(destination)

        startDialogue {
            chatPlayer(
                shocked,
                "Oh my! I just got sucked through that door... what a weird feeling! Still, I " +
                    "guess I should expect it as these evidently aren't your average kind of " +
                    "doors.... they talk and look creepy!",
            )
        }
    }

    private suspend fun ProtectedAccess.interrogate(door: DoorSpirit) {
        val first = random.pick(StrongholdQuestions.Bank)
        var correct = false
        startDialogue { correct = ask(door, first) }
        if (correct) {
            return
        }
        val followUps = StrongholdQuestions.Bank.filter { it !== first }
        val second = random.pick(followUps)
        startDialogue { ask(door, second) }
    }

    private suspend fun Dialogue.ask(door: DoorSpirit, question: SecurityQuestion): Boolean {
        chatDoor(door, question.text)

        val answers = question.answers
        val picked =
            if (answers.size == 2) {
                choice2(answers[0].text, 0, answers[1].text, 1)
            } else {
                choice3(answers[0].text, 0, answers[1].text, 1, answers[2].text, 2)
            }

        question.replyTo(picked)?.let { chatDoor(door, it) }

        return question.isCorrect(picked)
    }

    private suspend fun Dialogue.chatDoor(door: DoorSpirit, text: String) {
        chatNpcSpecific(door.title, door.npc, mesanim(DoorMesAnim), text)
    }

    // Reset before animating: a pending sequence is otherwise replaced only by a higher-priority one.
    private suspend fun ProtectedAccess.crossDoor(destination: CoordGrid) {
        resetAnim()
        anim(StrongholdRefs.DoorDragSeq)
        soundSynth(StrongholdRefs.ThroughDoorSound)
        ifClose()

        delay(1)
        telejump(destination)

        delay(1)
        resetAnim()
        anim(StrongholdRefs.DoorAppearSeq)
    }

    private fun ProtectedAccess.crossingDestination(loc: BoundLocInfo): CoordGrid? {
        if (loc.shape != LocShape.WallStraight) {
            return null
        }

        val normalX: Int
        val normalZ: Int
        when (loc.angle) {
            LocAngle.West -> {
                normalX = -1
                normalZ = 0
            }
            LocAngle.North -> {
                normalX = 0
                normalZ = 1
            }
            LocAngle.East -> {
                normalX = 1
                normalZ = 0
            }
            LocAngle.South -> {
                normalX = 0
                normalZ = -1
            }
        }

        val projection = (player.coords.x - loc.x) * normalX + (player.coords.z - loc.z) * normalZ
        val playerOnLocSide = projection <= 0
        return if (playerOnLocSide) {
            loc.coords.translate(normalX, normalZ)
        } else {
            loc.coords
        }
    }

    private data class DoorSpirit(val npc: String, val title: String)

    private companion object {
        private val WarDoor =
            DoorSpirit(StrongholdRefs.WarDoorNpc, StrongholdRefs.WarDoorTitle)
        private val FamineDoor =
            DoorSpirit(StrongholdRefs.FamineDoorNpc, StrongholdRefs.FamineDoorTitle)
        private val PestilenceDoor =
            DoorSpirit(StrongholdRefs.PestilenceDoorNpc, StrongholdRefs.PestilenceDoorTitle)
        private val DeathDoor =
            DoorSpirit(StrongholdRefs.DeathDoorNpc, StrongholdRefs.DeathDoorTitle)

        private const val DoorMesAnim = "mesanim.sos_door"
    }
}
