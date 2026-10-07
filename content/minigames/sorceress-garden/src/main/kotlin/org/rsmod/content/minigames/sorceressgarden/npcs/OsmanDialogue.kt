package org.rsmod.content.minigames.sorceressgarden.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.minigames.sorceressgarden.configs.GardenSeason
import org.rsmod.content.minigames.sorceressgarden.configs.osmanApprenticeUnlocked

// Share Osman's existing dialogue registration; do not register a competing Talk-to handler.
suspend fun Dialogue.sqirkJuiceTopic() {
    val juiceSeason = GardenSeason.entries.firstOrNull { access.invTotal(access.inv, it.juiceObj) > 0 }
    if (juiceSeason != null) {
        sellJuice(juiceSeason)
        return
    }

    if (player.osmanApprenticeUnlocked) {
        chatNpc(shifty, "The garden awaits, if you think you're sneaky enough for it.")
        return
    }

    chatPlayer(quiz, "I heard you might know something about sq'irks.")
    chatNpc(
        shifty,
        "Ah, the Sorceress's Garden. My apprentice can show you the way in, if you think you're " +
            "quick and quiet enough to avoid the elementals that guard it.",
    )
    player.osmanApprenticeUnlocked = true
    chatPlayer(happy, "I'll go and find your apprentice, then.")
}

private suspend fun Dialogue.sellJuice(season: GardenSeason) {
    chatPlayer(neutral, "I've got some sq'irkjuice for you, Osman.")
    chatNpc(shifty, "Excellent work. Here is your payment.")
    access.invDel(access.inv, season.juiceObj, 1)
    access.statAdvance("stat.thieving", season.osmanXp)
}
