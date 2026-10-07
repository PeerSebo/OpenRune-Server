package org.rsmod.content.quest.area.varrock

import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

internal class RomeoApproach(
    private val quest: Quest,
    private val random: GameRandom,
    private val dialogue: RomeoAndJulietDialogue,
    private val protectedAccess: ProtectedAccessLauncher,
) {
    private val greetedUntil = mutableMapOf<PlayerUid, Int>()

    fun ScriptContext.register(playerRepo: PlayerRepository) {
        onAiTimer("npc.romeo") { npc.approachTick(playerRepo) }
    }

    private fun Npc.approachTick(playerRepo: PlayerRepository) {
        aiTimer(random.of(ScanIntervalMin..ScanIntervalMax))

        if (isBusy || coords.level != spawnCoords.level) {
            return
        }

        val target =
            playerRepo
                .findAll(ZoneKey.from(coords), zoneRadius = ScanZoneRadius)
                .filter { wantsToTalkTo(it) }
                .minByOrNull { it.coords.chebyshevDistance(coords) } ?: return

        if (target.coords.chebyshevDistance(coords) <= GreetDistance) {
            greet(target)
            return
        }

        walk(target.coords) { greet(target) }
    }

    private fun Npc.greet(target: Player) {
        if (!wantsToTalkTo(target) || target.coords.chebyshevDistance(coords) > GreetDistance) {
            return
        }

        facePathingEntitySquare(target)

        val opened =
            protectedAccess.launch(target) { with(dialogue) { romeoApproaches(this@greet) } }
        if (opened) {
            greetedUntil[target.uid] = target.currentMapClock + GreetCooldown
        }
    }

    private fun Npc.wantsToTalkTo(player: Player): Boolean {
        if (player.isDelayed || !quest.isQuestNotStarted(player)) {
            return false
        }
        if (player.currentMapClock < (greetedUntil[player.uid] ?: Int.MIN_VALUE)) {
            return false
        }
        return player.coords.level == spawnCoords.level &&
            player.coords.chebyshevDistance(spawnCoords) <= ApproachRadius
    }

    private companion object {
        const val WanderRange = 5
        const val ApproachRadius = WanderRange + 3

        const val ScanZoneRadius = 2

        const val GreetDistance = 1

        const val ScanIntervalMin = 10
        const val ScanIntervalMax = 18

        const val GreetCooldown = 300
    }
}
