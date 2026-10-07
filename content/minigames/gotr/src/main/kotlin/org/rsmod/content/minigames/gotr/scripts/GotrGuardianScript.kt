package org.rsmod.content.minigames.gotr.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.minigames.gotr.GotrGame
import org.rsmod.content.minigames.gotr.GotrPhase
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GotrGuardianScript @Inject constructor(private val game: GotrGame) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Spawn>(GREAT_GUARDIAN.asRSCM()) { makeStationary(npc) }
        onOpNpc1(GREAT_GUARDIAN) { powerUp() }
    }

    internal fun makeStationary(npc: Npc) {
        npc.abortRoute()
        npc.mode = NpcMode.None
        npc.moveRestrict = MoveRestrict.NoMove
    }

    private suspend fun ProtectedAccess.powerUp() {
        if (!game.isMember(player)) {
            mes("You need to be inside the Temple of the Eye to do this.")
            return
        }
        if (game.phase == GotrPhase.LOBBY) {
            mesbox("The Great Guardian seems to be resting. You should probably not disturb it.")
            return
        }

        var fed = 0
        for (stone in STONE_TYPES) {
            var count = inv.count(stone)
            while (count > 0) {
                if (invDel(inv, stone, 1).failure) break
                if (game.applyGuardianStone(player, stone)) {
                    fed++
                }
                count--
            }
        }

        if (fed <= 0) {
            mes("You don't have any guardian stones to power up the Great Guardian with.")
            return
        }

        game.finishGuardianFeed(player)
        anim(FEED_ANIM)
        spotanim(FEED_SPOTANIM, height = FEED_SPOTANIM_HEIGHT)
    }

    private companion object {
        const val GREAT_GUARDIAN = "npc.gotr_great_guardian"
        const val FEED_ANIM = "seq.human_runecraft_walkmerge"
        const val FEED_SPOTANIM = "spotanim.runecrafting"
        const val FEED_SPOTANIM_HEIGHT = 92
        val STONE_TYPES =
            listOf(
                GotrGame.STONE_ELEMENTAL,
                GotrGame.STONE_CATALYTIC,
                GotrGame.STONE_POLYELEMENTAL,
                GotrGame.STONE_POLYCATALYTIC,
            )
    }
}
