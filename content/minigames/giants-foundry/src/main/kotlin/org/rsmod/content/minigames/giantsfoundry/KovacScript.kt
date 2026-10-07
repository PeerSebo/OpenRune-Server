package org.rsmod.content.minigames.giantsfoundry

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.*
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsService
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsState
import org.rsmod.content.minigames.giantsfoundry.quest.sleepingGiantsStage
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KovacScript @Inject constructor(
    private val game: FoundryGame,
    private val rewards: FoundryRewardService,
    private val sleepingGiants: SleepingGiantsService,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (npc in listOf("npc.giants_foundry_kovac", "npc.giants_foundry_kovac_1op")) {
            onOpNpc1(npc) { talk(it.npc) }
            onOpNpc3(npc) { handIn() }
            onOpNpc4(npc) { commission() }
            onOpNpc5(npc) { rewards.open(player) }
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        if (player.sleepingGiantsStage < SleepingGiantsState.COMPLETE &&
            choice2("Ask about Sleeping Giants", true, "Use the foundry services", false)
        ) {
            sleepingGiants.talk(this, npc)
            return
        }
        val option = choice5(
            "I'd like a commission.", 1,
            "I'd like to hand in my sword.", 2,
            "Show me your rewards.", 3,
            "How do I make a sword?", 4,
            "Cancel my current commission.", 5,
        )
        when (option) {
            1 -> commission()
            2 -> handIn()
            3 -> rewards.open(player)
            4 -> instructions(npc)
            5 -> cancelCommission()
        }
    }

    private suspend fun ProtectedAccess.instructions(npc: Npc) {
        if (SleepingGiantsState.inTutorial(player)) {
            sleepingGiants.talk(this, npc)
            return
        }
        mesbox(
            "Get a commission, then choose a forte, blade and tip at the mould jig. " +
                "Fill the crucible with 28 bars' worth of metal and pour it into the mould."
        )
        mesbox(
            "Cool the preform with water or ice gloves, then pick it up. " +
                "Follow the tools and temperature ranges shown on the display."
        )
        mesbox(
            "Heat the sword in the lava pool and cool it at the waterfall. " +
                "Bring me the finished sword for your reward."
        )
    }

    private suspend fun ProtectedAccess.cancelCommission() {
        if (!FoundryGame.hasCommission(player)) return
        if (choice2("Cancel and lose my current sword", true, "Keep working on it", false)) {
            game.cancel(player)
            mes("Your commission has been cancelled.")
        }
    }

    private suspend fun ProtectedAccess.commission() {
        if (player.smithingLvl < 15) {
            mes("You need a Smithing level of 15 to work at the Giants' Foundry.")
            return
        }
        game.commission(player, random)
        val firstWord = FoundryData.words.getValue(player.foundryWord1).lowercase()
        val secondWord = FoundryData.words.getValue(player.foundryWord2).lowercase()
        mes("Kovac wants a $firstWord and $secondWord sword.")
    }

    private suspend fun ProtectedAccess.handIn() {
        if (sleepingGiants.handIn(this)) return
        if (player.foundryStartQuality <= 0) {
            mes("You don't have a sword to hand in.")
            return
        }
        if (player.foundryStored != 0) {
            mes("Take your preform out of storage before handing it in.")
            return
        }
        if (player.foundryCompletion < 1000 && player.foundryQuality > 0) {
            mes("Your sword isn't finished yet.")
            return
        }
        val reward = game.handIn(player)
        if (reward == null) {
            mes("You need your preform equipped and room to receive your reward.")
            return
        }
        mes(
            "Kovac rewards you with ${reward.quality} Foundry reputation " +
                "and ${reward.coins} coins for your sword."
        )
        if (choice2("Yes", true, "No", false, title = "Receive another commission?")) commission()
    }
}
