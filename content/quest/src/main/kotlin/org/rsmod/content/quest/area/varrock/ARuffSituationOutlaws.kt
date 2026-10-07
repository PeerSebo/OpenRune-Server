package org.rsmod.content.quest.area.varrock

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.output.mes
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal class ARuffSituationOutlaws @Inject constructor() : NpcDeathKillHook {
    private val first by lazy { "npc.dogq_outlaw1".asRSCM(RSCMType.NPC) }
    private val second by lazy { "npc.dogq_outlaw2".asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        val quest = Quest.get("quest_ruffsituation") ?: return
        val player = context.hero
        if (!context.npc.isSpawnOwnedBy(player)) {
            return
        }
        val next = when {
            quest.getQuestStage(player) == ARuffSituation.SECOND_PUPPY && context.npc.id == first -> 85
            quest.getQuestStage(player) == 85 && context.npc.id == second -> ARuffSituation.OUTLAWS_DEFEATED
            else -> return
        }
        quest.jumpToStage(player, next)
        if (next == ARuffSituation.OUTLAWS_DEFEATED) {
            player.mes("The outlaws have been defeated. I can rescue the last puppy.")
        }
    }
}

internal class ARuffOutlawOwnershipGate @Inject constructor() : NpcAttackValidateHook {
    private val outlawIds by lazy {
        setOf(
            "npc.dogq_outlaw1".asRSCM(RSCMType.NPC),
            "npc.dogq_outlaw2".asRSCM(RSCMType.NPC),
        )
    }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in outlawIds) {
            return NpcAttackValidateResult.Pass
        }
        return if (npc.isSpawnOwnedBy(player)) NpcAttackValidateResult.Pass
        else NpcAttackValidateResult.Deny("That outlaw is involved in another player's rescue.")
    }
}
