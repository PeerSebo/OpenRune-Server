package org.rsmod.content.areas.city.lumbridge.npcs

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RangedCombatTutor : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_ranging") { talkTo(it.npc) }
        onOpNpc3("npc.aide_tutor_ranging") { claimEquipment(it.npc) }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        startDialogue(npc) { greeting() }
    }

    private suspend fun Dialogue.greeting() {
        if (!AideTutorSupplyEligibility.ranged(player)) {
            chatNpc(neutral, LevelRefusal)
            return
        }
        chatNpc(
            happy,
            "Hello there! I'm Nemarti, the Ranged combat tutor. I can teach you the basics of " +
                "Ranged, or give you a bow and some arrows to get you started if you don't have " +
                "any.",
        )
    }

    private suspend fun ProtectedAccess.claimEquipment(npc: Npc) {
        arriveDelay()
        faceEntitySquare(npc)

        if (!AideTutorSupplyEligibility.ranged(player)) {
            mes(LevelRefusal)
            return
        }

        if (aideTutorClaimOnCooldown()) {
            mes("Nemarti has already given you some equipment recently. Come back later.")
            return
        }

        val alreadyHasEquipment =
            TrainingBow in inv || TrainingBow in bank || TrainingArrow in inv || TrainingArrow in bank
        if (alreadyHasEquipment) {
            mes("Nemarti looks at your equipment and decides you don't need any more just yet.")
            return
        }

        val transaction = player.invTransaction(inv) {
            val inventory = select(inv)
            add(inventory, TrainingBow.asRSCM(), 1)
            add(inventory, TrainingArrow.asRSCM(), ArrowCount)
        }
        if (transaction.failure) {
            mes("You don't have enough inventory space for the bow and arrows.")
            return
        }

        markAideTutorClaimed()

        doubleobjbox(
            TrainingBow,
            TrainingArrow,
            "Nemarti gives you a training bow and $ArrowCount training arrows.",
        )
    }

    private companion object {
        private const val LevelRefusal =
            "Free training bows and arrows are only available below base Ranged level " +
                "${AideTutorSupplyEligibility.LevelLimit}. You're ready to obtain your own equipment."
        private const val TrainingBow = "obj.aide_shortbow"
        private const val TrainingArrow = "obj.aide_arrow"
        private const val ArrowCount = 25
    }
}
