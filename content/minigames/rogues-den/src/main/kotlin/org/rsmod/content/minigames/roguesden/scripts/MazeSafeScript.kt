package org.rsmod.content.minigames.roguesden.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.roguesden.configs.RoguesDenExit
import org.rsmod.content.minigames.roguesden.configs.RoguesDenMaze
import org.rsmod.content.minigames.roguesden.configs.RoguesDenSafes
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Safe loot/XP commit must survive an interrupted animation coroutine.
class MazeSafeScript @Inject constructor(private val locRepo: LocRepository, private val xpMods: XpModifiers) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(RoguesDenSafes.MAZE_SAFE) { crack(it.loc) }
        onPlayerQueueWithArgs<SafeCrackTask>(RoguesDenSafes.SAFE_CRACK_QUEUE) { event ->
            finishCrack(event.args)
        }
    }

    private data class SafeCrackTask(val safe: BoundLocInfo)

    private suspend fun ProtectedAccess.crack(safe: BoundLocInfo) {
        arriveDelay()

        faceLoc(safe)
        anim(RoguesDenSafes.SAFE_CRACK_ANIM)
        soundSynth(RoguesDenSafes.SAFE_CRACK_SOUND)

        queue(RoguesDenSafes.SAFE_CRACK_QUEUE, cycles = 2, args = SafeCrackTask(safe))
    }

    private fun ProtectedAccess.finishCrack(task: SafeCrackTask) {
        val visibleThieving = stat(RoguesDenMaze.THIEVING_STAT)

        val roll = random.of(0, 199)
        var reachedLootRoll = roll <= visibleThieving

        if (!reachedLootRoll) {
            if (random.of(2) == 0) {
                RoguesDenExit.restoreAndLeaveMazeState(
                    player,
                    "The safe's mechanism catches you out - you are thrown from the maze!",
                )
                telejump(RoguesDenExit.LOBBY_LANDING)
                return
            }
            statSub(RoguesDenMaze.THIEVING_STAT, 1, 0)
            reachedLootRoll = true
        }
        check(reachedLootRoll)

        val kit = random.of(128) < 48
        val reward = if (kit) RoguesDenMaze.ROGUE_KIT else RoguesDenMaze.ROGUE_CRATE

        anim(RoguesDenSafes.SAFE_OPEN_ANIM)
        soundSynth(RoguesDenSafes.SAFE_OPEN_SOUND)
        locRepo.change(task.safe, RoguesDenSafes.MAZE_SAFE_OPEN, RoguesDenSafes.SAFE_OPEN_TICKS)

        RoguesDenExit.removeJewels(player)
        invAdd(inv, reward, 1)
        val rewardName = if (kit) "a rogue kit" else "a rogue's equipment crate"
        mes("You find $rewardName inside the safe.")

        val agilityDrained = statBase(RoguesDenMaze.AGILITY_STAT) - stat(RoguesDenMaze.AGILITY_STAT)
        val thievingDrained = statBase(RoguesDenMaze.THIEVING_STAT) - stat(RoguesDenMaze.THIEVING_STAT)
        if (agilityDrained > 0) {
            val mod = xpMods.get(player, RoguesDenMaze.AGILITY_STAT)
            val xp = RoguesDenSafes.XP_PER_DRAINED_LEVEL * agilityDrained * mod
            statAdvance(RoguesDenMaze.AGILITY_STAT, xp)
        }
        if (thievingDrained > 0) {
            val mod = xpMods.get(player, RoguesDenMaze.THIEVING_STAT)
            val xp = RoguesDenSafes.XP_PER_DRAINED_LEVEL * thievingDrained * mod
            statAdvance(RoguesDenMaze.THIEVING_STAT, xp)
        }

        RoguesDenExit.restoreAndLeaveMazeState(player, message = null)
        telejump(RoguesDenExit.LOBBY_LANDING)
    }
}
