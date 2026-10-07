package org.rsmod.content.minigames.tearsofguthix

import org.rsmod.api.player.protect.ProtectedAccess
import toRs

internal object TearsJournal {
    fun open(access: ProtectedAccess, game: TearsGame) {
        with(access) {
            runClientScript("clientscript.[clientscript,quest_journal_reset]")
            ifSetText("component.questjournal:title", "<col=7f0000>Tears of Guthix</col>")
            val lines = listOf(
                "I can visit Juna beneath Lumbridge to collect the tears of Guthix.",
                "Blue tears fill my bowl; green tears empty it.",
                "The reward gives experience to my lowest eligible skill.",
                "My best score is ${player.vars[TearsState.Best]} tears.",
                game.canEnter(player) ?: "I am eligible to collect tears now.",
            ).flatMap { it.toRs(wrapAt = 64).split("<br>") }
            lines.forEachIndexed { index, text -> ifSetText("component.questjournal:qj${index + 1}", text) }
            ifOpenOverlay("interface.questjournal")
            ifSetHide("component.questjournal:switch", true)
            runClientScript("clientscript.[clientscript,script2523]", 0, lines.size)
        }
    }
}
