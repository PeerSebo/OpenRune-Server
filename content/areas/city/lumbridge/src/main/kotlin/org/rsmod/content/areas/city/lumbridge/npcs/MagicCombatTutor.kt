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

class MagicCombatTutor : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_magic") { talkTo(it.npc) }
        onOpNpc3("npc.aide_tutor_magic") { claimRunes(it.npc) }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        startDialogue(npc) { greeting() }
    }

    private suspend fun Dialogue.greeting() {
        if (!AideTutorSupplyEligibility.magic(player)) {
            chatNpc(neutral, LevelRefusal)
            return
        }
        chatNpc(
            happy,
            "Hello there! I'm Mikasi, the Magic combat tutor. I can teach you the basics of " +
                "Magic, or give you some runes to get you started if you don't have any.",
        )
    }

    private suspend fun ProtectedAccess.claimRunes(npc: Npc) {
        arriveDelay()
        faceEntitySquare(npc)

        if (!AideTutorSupplyEligibility.magic(player)) {
            mes(LevelRefusal)
            return
        }

        if (aideTutorClaimOnCooldown()) {
            mes("Mikasi has already given you some runes recently. Come back later.")
            return
        }

        val alreadyHasRunes =
            MindRune in inv || MindRune in bank || AirRune in inv || AirRune in bank
        if (alreadyHasRunes) {
            mes("Mikasi looks at your runes and decides you don't need any more just yet.")
            return
        }

        val transaction = player.invTransaction(inv) {
            val inventory = select(inv)
            add(inventory, MindRune.asRSCM(), RuneCount)
            add(inventory, AirRune.asRSCM(), RuneCount)
        }
        if (transaction.failure) {
            mes("You don't have enough inventory space for the runes.")
            return
        }

        markAideTutorClaimed()

        doubleobjbox(
            MindRune,
            AirRune,
            "Mikasi gives you $RuneCount mind runes and $RuneCount air runes.",
        )
    }

    private companion object {
        private const val LevelRefusal =
            "Free starter runes are only available below base Magic level " +
                "${AideTutorSupplyEligibility.LevelLimit}. You're ready to obtain your own runes."
        private const val MindRune = "obj.mindrune"
        private const val AirRune = "obj.airrune"
        private const val RuneCount = 30
    }
}
