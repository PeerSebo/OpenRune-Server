package org.rsmod.content.minigames.roguesden.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.StatRegenPrevention
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.minigames.roguesden.configs.RoguesDenExit
import org.rsmod.content.minigames.roguesden.configs.RoguesDenMaze
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.clearDisarmedTraps
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.grantedMazeAccess
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.inRoguesDenMaze
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MazeEntryScript @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(RoguesDenMaze.MAZE_MASTER) { talkToBrian(it.npc) }
        onOpLoc1(RoguesDenMaze.MAZE_ENTRANCE) { enterMaze(it.loc) }
    }

    private suspend fun ProtectedAccess.talkToBrian(npc: Npc) {
        startDialogue(npc) {
            if (player.grantedMazeAccess) {
                returningVisit()
            } else {
                firstVisit()
            }
        }
    }

    private suspend fun Dialogue.firstVisit() {
        chatNpc(quiz, "Hi there, looking for a challenge are you?")
        while (true) {
            val choice =
                choice3(
                    "Yes actually, what've you got?",
                    BrianMenu.Challenge,
                    "What is this place?",
                    BrianMenu.Place,
                    "Actually I think I'll pass thanks.",
                    BrianMenu.Decline,
                )
            when (choice) {
                BrianMenu.Challenge -> {
                    chatPlayer(quiz, "Yes actually, what've you got?")
                    offerChallenge()
                    return
                }
                BrianMenu.Place -> whatIsThisPlace()
                BrianMenu.Decline -> {
                    chatPlayer(neutral, "Actually I think I'll pass thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.offerChallenge() {
        if (player.statBase(RoguesDenMaze.THIEVING_STAT) < RoguesDenMaze.THIEVING_REQ) {
            chatNpc(
                sad,
                "Shame, I don't think I have anything for you. Train up your Thieving skill to " +
                    "at least ${RoguesDenMaze.THIEVING_REQ} and I might be able to help you out.",
            )
            return
        }
        if (player.statBase(RoguesDenMaze.AGILITY_STAT) < RoguesDenMaze.AGILITY_REQ) {
            chatNpc(
                sad,
                "Shame, I don't think I have anything for you. Train up your Agility skill to " +
                    "at least ${RoguesDenMaze.AGILITY_REQ} and I might be able to help you out.",
            )
            return
        }
        chatNpc(
            happy,
            "Aha, I have the perfect thing for you! See if you can get to the centre of my " +
                "maze, the further you get the greater the rewards. There's even some special " +
                "prizes if you make it right to the end.",
        )
        val accept =
            choice2("Ok that sounds good!", true, "Actually I think I'll pass thanks.", false)
        if (!accept) {
            chatPlayer(neutral, "Actually I think I'll pass thanks.")
            return
        }
        chatPlayer(happy, "Ok that sounds good!")
        chatNpc(
            happy,
            "Great! When you enter the maze, I'll give you a jewel - it'll allow you to get out " +
                "of the maze at any time. However that's all you're allowed to take in with " +
                "you, no cheating!",
        )
        chatNpc(
            neutral,
            "Oh one last thing, if you happen to see my harmonica I'd really like to have it back.",
        )
        player.grantedMazeAccess = true
    }

    private suspend fun Dialogue.returningVisit() {
        chatNpc(happy, "Hi again, what can I do for you?")
        while (true) {
            val choice =
                choice3(
                    "I want to try the maze again!",
                    BrianMenu.Challenge,
                    "What is this place?",
                    BrianMenu.Place,
                    "Nothing thanks.",
                    BrianMenu.Decline,
                )
            when (choice) {
                BrianMenu.Challenge -> {
                    chatPlayer(happy, "I want to try the maze again!")
                    chatNpc(
                        happy,
                        "Certainly, when you enter the maze I'll give you another jewel. Good " +
                            "luck!",
                    )
                    return
                }
                BrianMenu.Place -> whatIsThisPlace()
                BrianMenu.Decline -> {
                    chatPlayer(neutral, "Nothing thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            neutral,
            "Ah welcome to my humble home, well actually it belongs to mummsie but she's " +
                "getting on a bit so I look after the place for her.",
        )
        chatNpc(quiz, "So are you interested in a challenge?")
    }

    private suspend fun ProtectedAccess.enterMaze(door: BoundLocInfo) {
        arriveDelay()

        if (player.coords.z > door.coords.z) {
            mes(RoguesDenMaze.DENY_EXIT)
            return
        }

        if (!player.grantedMazeAccess) {
            startDialogue { brianSays(RoguesDenMaze.DENY_NO_ACCESS) }
            return
        }

        val agilityOk = player.statBase(RoguesDenMaze.AGILITY_STAT) >= RoguesDenMaze.AGILITY_REQ
        val thievingOk = player.statBase(RoguesDenMaze.THIEVING_STAT) >= RoguesDenMaze.THIEVING_REQ
        if (!agilityOk || !thievingOk) {
            mes(RoguesDenMaze.DENY_LEVEL)
            return
        }

        val onlyJewels = inv.all { it == null || it.isType(RoguesDenMaze.MYSTIC_JEWEL) }
        if (!onlyJewels || !worn.isEmpty()) {
            startDialogue { brianSays(RoguesDenMaze.DENY_ITEMS) }
            return
        }

        // TODO: enforce the follower entry restriction.

        statRestore(RoguesDenMaze.AGILITY_STAT)
        statRestore(RoguesDenMaze.THIEVING_STAT)
        statSub(RoguesDenMaze.PRAYER_STAT, 99, 0)

        RoguesDenExit.ensureSingleJewel(player)
        mes(RoguesDenMaze.JEWEL_GRANT_MES)

        player.clearDisarmedTraps()
        player.inRoguesDenMaze = true

        StatRegenPrevention.addAllStats(player, RoguesDenMaze.ROGUES_DEN_REGEN_SOURCE)

        crossDoorway(door)
    }

    private suspend fun Dialogue.brianSays(text: String) {
        chatNpcSpecific(RoguesDenMaze.MAZE_MASTER_NAME, RoguesDenMaze.MAZE_MASTER, neutral, text)
    }

    private suspend fun ProtectedAccess.crossDoorway(door: BoundLocInfo) {
        val route = crossingRoute(door)
        val openTicks = crossingOpenTicks(crossingTiles(route))

        locRepo.change(door, RoguesDenMaze.MAZE_ENTRANCE_OPEN, openTicks)
        soundSynth(RoguesDenMaze.MAZE_ENTRANCE_OPEN_SOUND)

        val trimmed = route.dropWhile { it == player.coords }
        if (trimmed.isNotEmpty()) {
            forcedWalk(trimmed, crossTiles = crossingTiles(trimmed))
        }
    }

    private fun BoundLocInfo.acrossTile(): CoordGrid =
        when (angle.id) {
            0 -> CoordGrid(coords.x - 1, coords.z, coords.level)
            1 -> CoordGrid(coords.x, coords.z + 1, coords.level)
            2 -> CoordGrid(coords.x + 1, coords.z, coords.level)
            else -> CoordGrid(coords.x, coords.z - 1, coords.level)
        }

    // Wall ops may fire from a flank; route through the doorway anchor before crossing.
    private fun ProtectedAccess.crossingRoute(door: BoundLocInfo): List<CoordGrid> {
        val onTile = door.coords
        val across = door.acrossTile()
        return if (player.coords == onTile) listOf(across) else listOf(onTile, across)
    }

    private fun ProtectedAccess.crossingTiles(route: List<CoordGrid>): Int {
        var tiles = 0
        var from = player.coords
        for (waypoint in route) {
            tiles += from.chebyshevDistance(waypoint)
            from = waypoint
        }
        return maxOf(1, tiles)
    }

    private fun crossingOpenTicks(tiles: Int): Int = maxOf(MinOpenTicks, tiles + OpenTailTicks)

    private enum class BrianMenu {
        Challenge,
        Place,
        Decline,
    }

    private companion object {
        private const val MinOpenTicks = 4
        private const val OpenTailTicks = 2
    }
}
