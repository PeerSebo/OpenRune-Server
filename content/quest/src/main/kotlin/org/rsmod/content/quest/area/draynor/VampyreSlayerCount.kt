package org.rsmod.content.quest.area.draynor

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.content.quest.manager.Quest

// Drive the stake at zero HP; ordinary combat cannot complete the kill.
internal class VampyreSlayerRegenerateHook
@Inject
constructor(private val protectedAccess: ProtectedAccessLauncher) : NpcDeathKillHook {
    private val countId by lazy { CountDraynor.asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != countId) {
            return
        }

        val player = context.hero
        val quest = Quest.get(VampyreSlayer.QuestKey) ?: return
        if (!quest.isQuestInProgress(player)) {
            return
        }

        val hasStake = player.inv.count(VampyreSlayer.Stake) > 0
        val hasHammer = player.inv.count(VampyreSlayer.Hammer) > 0

        if (hasStake && hasHammer) {
            player.mes("<col=e00a19>You hammer the stake into the vampyre's chest!")

            protectedAccess.launch(player) {
                invDel(inv, VampyreSlayer.Stake, 1)
                quest.completeQuest(this)
            }
            return
        }

        if (hasStake) {
            player.mes("You're unable to push the stake far enough in!")
        }
        player.mes("The vampyre seems to regenerate!")
    }
}

internal const val CountDraynor = "npc.count_draynor"
